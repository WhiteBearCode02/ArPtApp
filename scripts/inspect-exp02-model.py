"""Read canonical EXP02 tensor metadata offline, without loading native inference.

This minimal, bounded FlatBuffer reader handles only Model/SubGraph/Tensor fields
from the public TFLite schema (Model=3; TensorType.FLOAT32=0). It does not decode
operators, allocate a runtime, invoke a model, inspect images, or advance a gate.
Optional evidence output must remain local-only, ignored, and untracked by Git.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import stat
import struct
import subprocess


MODEL_NAME = "airptcoach_yolo26n_exp02_fp32.tflite"
MODEL_BYTES = 9_894_030
MODEL_SHA256 = "464cafe87c3d6e04ba71951e73efab8690367f77fb224105d4b1e50ab3d33749"
EXPECTED_INPUT = [1, 3, 640, 640]
EXPECTED_OUTPUT = [1, 6, 8400]


class FlatBufferMetadataReader:
    """Bounds-check every offset/count; never execute or reinterpret model weights."""

    def __init__(self, data: bytes):
        self.data = data

    def region(self, offset: int, length: int) -> None:
        if offset < 0 or length < 0 or offset > len(self.data) - length:
            raise ValueError("Malformed FlatBuffer: out-of-bounds region")

    def number(self, offset: int, fmt: str) -> int:
        self.region(offset, struct.calcsize(fmt))
        return struct.unpack_from(fmt, self.data, offset)[0]

    def u16(self, offset: int) -> int:
        return self.number(offset, "<H")

    def u32(self, offset: int) -> int:
        return self.number(offset, "<I")

    def i32(self, offset: int) -> int:
        return self.number(offset, "<i")

    def field(self, table: int, index: int, width: int = 4) -> int | None:
        vtable = table - self.i32(table)
        vtable_size = self.u16(vtable)
        object_size = self.u16(vtable + 2)
        if vtable_size < 4 or vtable_size % 2 or object_size < 4:
            raise ValueError("Malformed FlatBuffer table header")
        self.region(vtable, vtable_size)
        self.region(table, object_size)
        entry = 4 + index * 2
        if entry + 2 > vtable_size:
            return None
        relative = self.u16(vtable + entry)
        if relative == 0:
            return None
        if relative < 4 or relative + width > object_size:
            raise ValueError("Malformed FlatBuffer table field")
        return table + relative

    def reference(self, table: int, index: int) -> int | None:
        offset = self.field(table, index)
        if offset is None:
            return None
        relative = self.u32(offset)
        if relative < 4:
            raise ValueError("Malformed FlatBuffer reference")
        target = offset + relative
        self.region(target, 4)
        return target

    def vector(self, table: int, index: int, limit: int = 65_536) -> tuple[int, int] | None:
        offset = self.reference(table, index)
        if offset is None:
            return None
        count = self.u32(offset)
        if count > limit:
            raise ValueError("FlatBuffer metadata vector exceeds safety limit")
        self.region(offset + 4, count * 4)
        return offset + 4, count

    def integers(self, table: int, index: int, limit: int = 64) -> list[int]:
        vector = self.vector(table, index, limit)
        if vector is None:
            return []
        start, count = vector
        return [self.i32(start + n * 4) for n in range(count)]

    def tables(self, table: int, index: int) -> list[int]:
        vector = self.vector(table, index)
        if vector is None:
            return []
        start, count = vector
        result = []
        for n in range(count):
            offset = start + n * 4
            relative = self.u32(offset)
            if relative < 4:
                raise ValueError("Malformed FlatBuffer table vector")
            self.region(offset + relative, 4)
            result.append(offset + relative)
        return result

    def string(self, table: int, index: int) -> str | None:
        offset = self.reference(table, index)
        if offset is None:
            return None
        length = self.u32(offset)
        if length > 4096:
            raise ValueError("FlatBuffer metadata string exceeds safety limit")
        self.region(offset + 4, length + 1)
        if self.data[offset + 4 + length] != 0:
            raise ValueError("Malformed FlatBuffer string terminator")
        return self.data[offset + 4:offset + 4 + length].decode("utf-8")

    def tensors(self) -> dict:
        self.region(0, 8)
        if self.data[4:8] != b"TFL3":
            raise ValueError("Expected TFLite TFL3 file identifier")
        root = self.u32(0)
        if root < 8:
            raise ValueError("Malformed FlatBuffer root offset")
        version_field = self.field(root, 0)
        version = self.u32(version_field) if version_field is not None else 0
        if version != 3:
            raise ValueError("Expected TFLite Model schema version 3")
        subgraphs = self.tables(root, 2)
        if len(subgraphs) != 1:
            raise ValueError("Canonical EXP02 must have exactly one subgraph")
        subgraph = subgraphs[0]
        tensors = self.tables(subgraph, 0)
        result = dict(schemaVersion=version, fileIdentifier="TFL3", subgraphCount=1,
                      subgraphName=self.string(subgraph, 4), tensorCount=len(tensors))
        for label, index in (("inputs", 1), ("outputs", 2)):
            observed = []
            for tensor_index in self.integers(subgraph, index):
                if not 0 <= tensor_index < len(tensors):
                    raise ValueError("Subgraph input/output tensor index out of bounds")
                tensor = tensors[tensor_index]
                type_field = self.field(tensor, 1, width=1)
                type_enum = self.data[type_field] if type_field is not None else 0
                observed.append(dict(index=tensor_index, name=self.string(tensor, 3),
                                     shape=self.integers(tensor, 0),
                                     shapeSignature=self.integers(tensor, 7), dtypeEnum=type_enum,
                                     dtype="FLOAT32" if type_enum == 0 else "NON_FLOAT32"))
            result[label] = observed
        return result


def validate_tensor_contract(metadata: dict) -> None:
    for label, expected in (("inputs", EXPECTED_INPUT), ("outputs", EXPECTED_OUTPUT)):
        actual = metadata[label]
        if len(actual) != 1 or actual[0]["shape"] != expected or actual[0]["dtypeEnum"] != 0:
            raise ValueError("Canonical EXP02 input/output shape or FLOAT32 dtype mismatch")


def inspect_model(model: Path) -> dict:
    """Identity and directly observed tensor metadata only, NOT native evidence."""
    model = model.resolve(strict=True)
    if model.name != MODEL_NAME or not model.is_file() or model.stat().st_size != MODEL_BYTES:
        raise ValueError("Canonical EXP02 filename/size mismatch")
    data = model.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    if len(data) != MODEL_BYTES or digest != MODEL_SHA256:
        raise ValueError("Canonical EXP02 SHA256/size mismatch")
    metadata = FlatBufferMetadataReader(data).tensors()
    validate_tensor_contract(metadata)
    return dict(schemaVersion=1, evidenceKind="STATIC_FLATBUFFER_METADATA_ONLY",
                status="STATIC_ONLY_ANDROID_NATIVE_PENDING", contractStatus="NOT_VERIFIED",
                actualModelGoldenExecuted=False, androidNativeInferenceExecuted=False,
                model=dict(path=str(model), filename=model.name, bytes=len(data), sha256=digest),
                expected=dict(inputShape=EXPECTED_INPUT, outputShape=EXPECTED_OUTPUT, dtype="FLOAT32"),
                observed=metadata, metadataMatch=True)


def private_output_path(output: Path, repository: Path) -> Path:
    """No tracked evidence, no traversal via reparse points, no arbitrary overwrite."""
    repository = repository.resolve(strict=True)
    lexical = output.absolute()
    destination = output.resolve()
    allowed = (repository / "build", repository / "local_validation" / "exp02_golden")
    if not any(root in destination.parents for root in allowed):
        raise ValueError("Metadata evidence must stay under ignored build/ or local_validation/exp02_golden/")
    if not re.fullmatch(r"[a-z0-9][a-z0-9_.-]{0,111}\.json", destination.name):
        raise ValueError("Evidence filename must be a flat lowercase .json basename")
    for component in (lexical, *lexical.parents):
        if component == repository:
            break
        if component.exists() or component.is_symlink():
            metadata = component.lstat()
            if component.is_symlink() or getattr(metadata, "st_file_attributes", 0) & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0):
                raise ValueError("Evidence output cannot follow symlink/junction paths")
    relative = destination.relative_to(repository).as_posix()
    ignored = subprocess.run(["git", "-C", str(repository), "check-ignore", "--quiet", "--no-index", "--", relative], capture_output=True, check=False)
    tracked = subprocess.run(["git", "-C", str(repository), "ls-files", "-z", "--", relative], capture_output=True, check=False)
    if ignored.returncode != 0 or tracked.returncode != 0 or tracked.stdout:
        raise ValueError("Metadata evidence must be Git-ignored and untracked")
    return destination


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--output", type=Path, help="Optional private local-only JSON evidence path")
    arguments = parser.parse_args()
    repository = Path(__file__).resolve().parents[1]
    output = private_output_path(arguments.output, repository) if arguments.output else None
    evidence = inspect_model(arguments.model)
    document = json.dumps(evidence, indent=2, ensure_ascii=False) + "\n"
    if output is not None:
        output.parent.mkdir(parents=True, exist_ok=True)
        # Validate again after creating parents; exclusive creation preserves prior evidence.
        output = private_output_path(output, repository)
        with output.open("x", encoding="utf-8") as stream:
            stream.write(document)
    print(document, end="")


if __name__ == "__main__":
    main()
