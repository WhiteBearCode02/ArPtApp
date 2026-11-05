"""Offline EXP03 actual-image reference from hash-pinned Ultralytics 8.4.202 source.

Only original source IMAGE bytes may be reused from EXP02, never its predictions.
No full ultralytics import, install, autoupdate, model download or network access.
AST-selected original functions run unchanged against the actual LiteRT interpreter.
All generated media/tensors/results must remain in a NEW Git-ignored private run.
"""
from __future__ import annotations

import argparse
import ast
import hashlib
import importlib.metadata
import importlib.util
import json
import logging
from pathlib import Path
import re
import stat
import subprocess
import sys
import time
from types import SimpleNamespace
from typing import Any
import zipfile

MODEL_ID = "yolo26n_exp03_final_fp32"
MODEL_NAME = "airptcoach_yolo26n_exp03_final_fp32.tflite"
MODEL_BYTES = 9894032
MODEL_SHA256 = "3d2b6bc537d8aef5a19b671226d73c98b49411574a193be72e225086202d1167"
VERSION = "8.4.202"
# Official PyPI version-specific JSON, independently checked before download.
WHEEL_SHA256 = "ae71dc5bb1024010fbdef5bd9281984ddd8c70885bddcfdd6aff0801b7015fb4"
FILES = ("ultralytics/data/augment.py", "ultralytics/engine/predictor.py",
         "ultralytics/nn/backends/litert.py", "ultralytics/utils/ops.py", "ultralytics/utils/nms.py")
THRESHOLDS = dict(confidence=.10, iou=.70, maxDetections=300)
TOLERANCES = dict(confidence=.001, bboxPixels=2.0, detectionCount=0, classMatch="EXACT")
TENSOR_BYTES = 4 * 3 * 640 * 640


def load_module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


# Reuse ONLY bounds, rights validation, semantic observation and file helpers.
# Do not call EXP02 generate/load_reference or change any EXP02 constants.
shared = load_module("exp03_shared_actual_bounds", "generate-exp02-actual-golden.py")
sha256 = shared.sha256


def private_path(path: Path, repository: Path) -> Path:
    """Before any read/write, reject links and require an ignored, untracked private path."""
    lexical, root = path.absolute(), repository.resolve()
    resolved = path.resolve()
    allowed = (root / "build", root / "local_validation" / "exp03_golden")
    if not any(base in resolved.parents for base in allowed):
        raise ValueError("Use a dedicated ignored build/ or local_validation/exp03_golden/ run")
    for component in (lexical, *lexical.parents):
        if component == root:
            break
        if component.is_symlink():
            raise ValueError("Private validation paths must not contain symlink/junction components")
        if component.exists():
            metadata = component.lstat()
            if getattr(metadata, "st_file_attributes", 0) & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0):
                raise ValueError("Private validation paths must not contain symlink/junction components")
    relative = resolved.relative_to(root).as_posix()
    ignored = subprocess.run(["git", "-C", str(root), "check-ignore", "--quiet", "--no-index", "--", relative], capture_output=True)
    tracked = subprocess.run(["git", "-C", str(root), "ls-files", "-z", "--", relative], capture_output=True)
    if ignored.returncode != 0 or tracked.returncode != 0 or tracked.stdout:
        raise ValueError("Private validation path must be ignored and untracked")
    return resolved


def load_reference(wheel: Path):
    """Execute only reviewed original AST nodes, never module startup or requirements checks."""
    if wheel.stat().st_size > 8 * 1024 * 1024 or sha256(wheel.read_bytes()) != WHEEL_SHA256:
        raise ValueError("EXP03_EXPORT_SOURCE_NOT_REPRODUCED: exact 8.4.202 wheel SHA mismatch")
    import cv2
    import numpy as np
    import torch
    with zipfile.ZipFile(wheel) as archive:
        sources = {name: archive.read(name) for name in FILES}
    namespace = dict(np=np, cv2=cv2, torch=torch, Any=Any, sys=sys, time=time,
                     LOGGER=logging.getLogger("exp03-golden"), NOT_MACOS14=True)

    def unsupported(*args, **kwargs):
        raise AssertionError("Out-of-scope rotated-box branch executed")
    namespace.update(batch_probiou=unsupported, box_iou=unsupported)
    provenance = {}

    def execute(relative, names, owner=None):
        nodes = ast.parse(sources[relative].decode("utf-8")).body
        if owner:
            nodes = next(node for node in nodes if isinstance(node, ast.ClassDef) and node.name == owner).body
        selected = [next(node for node in nodes if isinstance(node, (ast.FunctionDef, ast.ClassDef)) and node.name == name)
                    for name in names]
        for node in selected:
            provenance[f"{relative}:{owner + '.' if owner else ''}{node.name}"] = dict(
                startLine=node.lineno, endLine=node.end_lineno)
        module = ast.Module(body=[ast.ImportFrom(module="__future__", names=[ast.alias(name="annotations")], level=0),
                                  *selected], type_ignores=[])
        ast.fix_missing_locations(module)
        exec(compile(module, f"{wheel.name}/{relative}", "exec"), namespace)

    execute(FILES[0], ["BaseTransform", "LetterBox"])
    execute(FILES[1], ["preprocess", "pre_transform"], "BasePredictor")
    execute(FILES[2], ["forward"], "LiteRTBackend")
    execute(FILES[3], ["empty_like", "xywh2xyxy", "clip_boxes", "scale_boxes"])
    execute(FILES[4], ["TorchNMS", "non_max_suppression"])
    return namespace, {name: sha256(data) for name, data in sources.items()}, provenance


def generate(args, repository: Path):
    samples = shared.read_manifest(args.manifest)
    output = private_path(args.output, repository)
    if output.exists():
        raise ValueError("Preserve existing evidence: choose a NEW output run directory")
    model = args.model.resolve()
    if model.name != MODEL_NAME or model.stat().st_size != MODEL_BYTES or sha256(model.read_bytes()) != MODEL_SHA256:
        raise ValueError("Canonical EXP03 filename/size/SHA mismatch")
    reference, source_hashes, functions = load_reference(args.wheel)
    from ai_edge_litert.interpreter import Interpreter
    import cv2
    import numpy as np
    import torch

    torch.set_num_threads(1)
    runtime = Interpreter(model_path=str(model), num_threads=2)
    runtime.allocate_tensors()
    inputs, outputs = runtime.get_input_details(), runtime.get_output_details()
    shared.validate_tensor_details(inputs, outputs, np)
    predictor = SimpleNamespace(imgsz=(640, 640), scale_fill=False, args=SimpleNamespace(rect=True),
                                device=torch.device("cpu"),
                                model=SimpleNamespace(format="litert", dynamic=False, stride=32, fp16=False))
    predictor.pre_transform = lambda images: reference["pre_transform"](predictor, images)
    # 8.4.202 introduces the Intel compiled-model branch. Use the actual existing CPU Interpreter.
    backend = SimpleNamespace(nhwc=False, intel=False, task="detect", end2end=False, head="Detect",
                              names={0: "SQUAT", 1: "SHOULDER_PRESS"}, interpreter=runtime,
                              input_details=inputs, output_details=outputs)
    rows, payloads = [], []
    for sample in samples:
        image = (args.manifest.parent / sample["image"]).resolve()
        if not 0 < image.stat().st_size <= shared.MAX_ENCODED_IMAGE_BYTES:
            raise ValueError("Actual input exceeds encoded image safety bounds")
        encoded = image.read_bytes()
        bgr = cv2.imdecode(np.frombuffer(encoded, np.uint8), cv2.IMREAD_COLOR | cv2.IMREAD_IGNORE_ORIENTATION)
        if bgr is None:
            raise ValueError("Actual input could not be decoded")
        height, width = bgr.shape[:2]
        shared.validate_input_dimensions(width, height)
        rgb = np.ascontiguousarray(bgr[..., ::-1]).tobytes()
        tensor = reference["preprocess"](predictor, [bgr])
        array = tensor.numpy()
        if array.shape != (1, 3, 640, 640) or array.dtype != np.float32 or not np.isfinite(array).all() or array.min() < 0 or array.max() > 1:
            raise ValueError("Exact-version preprocessing tensor mismatch")
        input_bytes = np.ascontiguousarray(array, dtype="<f4").tobytes()
        prediction = reference["forward"](backend, tensor)[0]  # ACTUAL model invoke, not a synthetic interpreter
        # forward denormalizes a get_tensor COPY. Capture a fresh original native output independently.
        native = runtime.get_tensor(outputs[0]["index"])
        if native.shape != (1, 6, 8400) or native.dtype != np.float32 or not np.isfinite(native).all():
            raise ValueError("Actual raw FLOAT32 output mismatch")
        raw_bytes = np.ascontiguousarray(native, dtype="<f4").tobytes()
        processed = reference["non_max_suppression"](torch.from_numpy(prediction), conf_thres=.10, iou_thres=.70,
            nc=2, multi_label=False, agnostic=False, max_det=300, rotated=False, end2end=False)[0]
        if len(processed):
            reference["scale_boxes"]((640, 640), processed[:, :4], (height, width))
        detections, clipped = shared.post_nms_detections(processed, width, height)
        params = reference["LetterBox"]((640, 640), auto=False).get_params({"img": bgr})
        new_w, new_h = params["new_unpad"]
        identifier = sample["id"]
        payloads.extend(((identifier + ".rgb", rgb), (identifier + ".input.f32", input_bytes),
                         (identifier + ".output.f32", raw_bytes)))
        rows.append(dict(id=identifier, exercise=sample["exercise"], cameraView=sample["cameraView"],
            rightsCleared=True, rightsBasis=sample["rightsBasis"], image=image.name,
            sourceImageBytes=len(encoded), sourceImageSha256=sha256(encoded), width=width, height=height,
            rgbFile=identifier + ".rgb", rgbSha256=sha256(rgb), inputTensorFile=identifier + ".input.f32",
            inputTensorSha256=sha256(input_bytes), inputTensorByteOrder="LITTLE_ENDIAN",
            nativeOutputFile=identifier + ".output.f32", nativeOutputSha256=sha256(raw_bytes),
            nativeOutputByteOrder="LITTLE_ENDIAN", modelInferenceExecuted=True,
            preprocessing=dict(logicalColor="RGB", layout="BCHW", dtype="FLOAT32", normalizationDivisor=255,
                resize="OpenCV.INTER_LINEAR", paddingValue=114, centered=True, auto=False,
                resizedWidth=new_w, resizedHeight=new_h, padLeft=params["left"], padTop=params["top"],
                padRight=params["right"], padBottom=params["bottom"], gainX=new_w/width, gainY=new_h/height),
            thresholdCandidateCount=int((native[0, 4:6].max(axis=0) > .10).sum()),
            nmsOutputCount=len(processed), droppedFullyClippedCount=clipped,
            detections=detections, **shared.semantic_observation(sample["exercise"], detections)))
    document = dict(schemaVersion=1, validationPurpose="EXP03_ACTUAL_IMAGE_GOLDEN",
        model=dict(id=MODEL_ID, filename=MODEL_NAME, bytes=MODEL_BYTES, sha256=MODEL_SHA256),
        ultralyticsVersion=VERSION, installedUltralyticsDistribution=importlib.metadata.version("ultralytics"),
        sourceExecution="EXACT_HASH_PINNED_WHEEL_AST_NOT_INSTALLED_PACKAGE", wheelSha256=WHEEL_SHA256,
        sourceSha256=source_hashes, referenceFunctions=functions, pythonVersion=sys.version.split()[0],
        runtime=dict(name="ai_edge_litert", version=importlib.metadata.version("ai-edge-litert"),
                     numpyVersion=np.__version__, opencvVersion=cv2.__version__, torchVersion=torch.__version__,
                     interpreterThreads=2, torchThreads=1, device="CPU"),
        tensorMetadata=dict(input=dict(shape=[1, 3, 640, 640], dtype="FLOAT32"),
                            output=dict(shape=[1, 6, 8400], dtype="FLOAT32")),
        modelInferenceExecuted=True, nativeInferenceCount=len(rows), androidNativeInferenceExecuted=False,
        exportContractVerified=False, contractPromotionPerformed=False, cameraPerformance="NOT_MEASURED",
        thresholds=THRESHOLDS, tolerances=TOLERANCES,
        preprocessingTolerances=dict(exact=1e-6, resampled=1/255+1e-6, source="UNCHANGED_EXP02_PIXEL_BOUND"),
        semanticSanityCheck=dict(matched=sum(row["semanticMatch"] for row in rows), total=len(rows),
                                 policy="SANITY_ONLY_NEVER_AN_EQUIVALENCE_GATE_OR_DATASET_ACCURACY"), samples=rows)
    output.mkdir(parents=True, exist_ok=False)
    for filename, data in payloads:
        with shared.private_child(output, filename).open("xb") as stream:
            stream.write(data)
    with shared.private_child(output, "reference.json").open("x", encoding="utf-8") as stream:
        json.dump(document, stream, ensure_ascii=False, indent=2, allow_nan=False)
        stream.write("\n")
    print(json.dumps(dict(status="PYTHON_ACTUAL_REFERENCE_CREATED", samples=len(rows),
        actualNativeInferenceCount=len(rows), sourceVersion=VERSION, contractPromotionPerformed=False)))


def main():
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--model", type=Path, default=root / "app/src/main/assets" / MODEL_NAME)
    parser.add_argument("--wheel", type=Path, default=root / "build/exp03-contract-reference/ultralytics-8.4.202-py3-none-any.whl")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        generate(args, root)
    except (ValueError, RuntimeError, OSError, ImportError, StopIteration) as error:
        parser.exit(2, f"EXP03 Actual Golden blocked: {error}\n")


if __name__ == "__main__":
    main()
