"""Offline native reference for 1..8 explicitly saved EXP02 candidate PNGs.

Reuses the established actual-Golden guards and exact pinned Ultralytics functions;
never imports the full YOLO package, installs dependencies, uploads media, infers
labels from filenames, changes thresholds, or promotes the production contract.
Every input and generated fixture must stay Git-ignored below exp02_positive/.
"""
from __future__ import annotations

import argparse
import importlib.metadata
import importlib.util
import json
from pathlib import Path
import re
import stat
import subprocess
import sys
import tempfile
from types import SimpleNamespace


def load_actual_helpers():
    path = Path(__file__).with_name("generate-exp02-actual-golden.py")
    spec = importlib.util.spec_from_file_location("exp02_actual_helpers", path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


actual = load_actual_helpers()
MAX_CANDIDATES = 8
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
PURPOSE = "POSITIVE_CANDIDATE"


def require_private_path(path: Path, repository: Path, *, directory: bool = False) -> Path:
    """Resolve/reparse-check and consult Git before any private read or write."""
    repository = repository.resolve()
    root = repository / "local_validation/exp02_positive"
    destination = path.resolve()
    if not (destination == root or root in destination.parents):
        raise ValueError("Positive media/results must stay under local_validation/exp02_positive/")
    if destination == root and not directory:
        raise ValueError("Expected a private file, not the positive root")
    for component in (path.absolute(), *path.absolute().parents):
        if component == repository:
            break
        if component.is_symlink():
            raise ValueError("Symlink/junction private validation paths are forbidden")
        if component.exists():
            metadata = component.lstat()
            if getattr(metadata, "st_file_attributes", 0) & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0):
                raise ValueError("Symlink/junction private validation paths are forbidden")
    relative = destination.relative_to(repository).as_posix()
    ignored = subprocess.run(["git", "-C", str(repository), "check-ignore", "--quiet", "--no-index", "--",
                              relative + ("/.exp02-private-check" if directory else "")], capture_output=True, check=False)
    tracked = subprocess.run(["git", "-C", str(repository), "ls-files", "-z", "--", relative], capture_output=True, check=False)
    if ignored.returncode != 0 or tracked.returncode != 0 or tracked.stdout:
        raise ValueError("Positive validation path must be Git-ignored and untracked")
    return destination


def read_manifest(path: Path, repository: Path) -> list[dict]:
    path = require_private_path(path, repository)
    if not path.is_file() or not 0 < path.stat().st_size <= 131_072:
        raise ValueError("Manifest must be a bounded local JSON file")
    document = json.loads(path.read_text(encoding="utf-8-sig"))
    samples = document.get("samples")
    if document.get("schemaVersion") != 1 or not isinstance(samples, list) or not 1 <= len(samples) <= MAX_CANDIDATES:
        raise ValueError("Expected schemaVersion=1 and 1..8 explicitly saved PNG candidates")
    names, images = set(), set()
    for sample in samples:
        if not isinstance(sample, dict):
            raise ValueError("Each sample must be an object")
        identifier = sample.get("id", "")
        if not isinstance(identifier, str) or not re.fullmatch(r"[a-z0-9][a-z0-9_-]{0,63}", identifier) or identifier in names:
            raise ValueError("Invalid or duplicate sample id")
        names.add(identifier)
        if sample.get("rightsCleared") is not True or not isinstance(sample.get("rightsBasis"), str) or not sample["rightsBasis"].strip():
            raise ValueError("Each candidate requires explicit rightsCleared=true and rightsBasis")
        if sample.get("exercise") not in (None, *actual.CLASSES):
            raise ValueError("Optional exercise must be an explicit existing exercise, not an inferred label")
        if sample.get("cameraView") not in (None, "FRONT", "SIDE"):
            raise ValueError("Optional cameraView must be FRONT or SIDE")
        image = sample.get("image")
        if not isinstance(image, str) or not image.strip() or Path(image).is_absolute() or ":" in image or "\\" in image:
            raise ValueError("Image must be a relative private PNG path")
        image_path = require_private_path(path.parent / image, repository)
        if image_path.suffix.lower() != ".png" or not image_path.is_file() or image_path in images:
            raise ValueError("Each candidate must be a distinct existing private PNG")
        images.add(image_path)
        if not 0 < image_path.stat().st_size <= actual.MAX_ENCODED_IMAGE_BYTES:
            raise ValueError("Candidate encoded size exceeds the fixed image limit")
        with image_path.open("rb") as stream:
            if stream.read(8) != PNG_SIGNATURE:
                raise ValueError("Candidate must contain actual PNG data")
        capture_name = sample.get("androidResult")
        if capture_name is not None:
            if not isinstance(capture_name, str) or not re.fullmatch(r"[a-z0-9][a-z0-9_.-]{0,127}\.android\.json", capture_name):
                raise ValueError("Optional androidResult must be an explicit flat private .android.json basename")
            capture_path = require_private_path(path.parent / capture_name, repository)
            if not capture_path.is_file() or not 0 < capture_path.stat().st_size <= 2_097_152:
                raise ValueError("Missing bounded captured Android JSON")
    return samples


def semantic_observation(expected_exercise: str | None, detections: list[dict]) -> dict:
    """No declared label means unevaluated semantics, not guessed correctness."""
    if expected_exercise is not None:
        return actual.semantic_observation(expected_exercise, detections)
    return dict(expectedExercise=None, topPrediction=detections[0] if detections else None,
                semanticMatch=None, hasExpectedDetection=None, detectionsAfterNms=len(detections),
                classOrdering=[detection["classId"] for detection in detections])


def validate_capture_provenance(document: dict, image: str, image_hash: str, rgb_hash: str, width: int, height: int):
    """Same saved camera PNG must decode to the SAME logical RGB raster on Python."""
    if document.get("schemaVersion") != 1 or document.get("validationPurpose") != "EXPLICIT_DEBUG_POSITIVE_CANDIDATE":
        raise ValueError("Expected an explicitly saved DEBUG candidate result")
    if document.get("modelSha256") != actual.MODEL_SHA256 or document.get("productionContract") != "NOT_VERIFIED":
        raise ValueError("Captured Android model/contract provenance mismatch")
    if document.get("image") != image or document.get("imageSha256") != image_hash or document.get("sourceImageSha256") != image_hash or document.get("decodedRgbSha256") != rgb_hash:
        raise ValueError("Android captured PNG/RGB and Python decoded raster differ")
    if (document.get("width"), document.get("height")) != (width, height) or document.get("thresholds") != dict(confidence=.10, iou=.70, maxDetections=300):
        raise ValueError("Captured Android dimensions/thresholds differ")
    if document.get("predictionUsedForSession") is not False or document.get("contractPromotionPerformed") is not False:
        raise ValueError("Capture must be validation-only, not session prediction or promotion")


def candidate_summary(samples: list[dict]) -> dict:
    return dict(candidateCount=len(samples), positiveCandidateCount=sum(bool(sample["detections"]) for sample in samples),
                emptyCandidateCount=sum(not sample["detections"] for sample in samples),
                positiveCriterion="At least one actual post-NMS detection at unchanged confidence=0.10, iou=0.70; no semantic label gate.",
                positiveEquivalenceStatus="ANDROID_COMPARISON_PENDING", productionContract="NOT_VERIFIED")


def require_empty_output(output: Path):
    """A new run may create evidence; it must never replace an earlier run or partial evidence."""
    if output.exists() and (not output.is_dir() or any(output.iterdir())):
        raise ValueError("Output already contains evidence; select a new empty run directory")


def claim_output_directory(output: Path):
    require_empty_output(output)
    output.mkdir(parents=True, exist_ok=True)
    # Exclusive persistent claim also rejects two simultaneous writers that both saw empty.
    # Failed/partial runs stay immutable; use a new run id instead of clearing the claim.
    with (output / ".validation-run.claim").open("x", encoding="ascii") as claim:
        claim.write("LOCAL_ONLY_IMMUTABLE_VALIDATION_RUN\n")


def normalized_native_snapshot(interpreter, outputs, np):
    # LiteRT2.2.0 get_tensor() explicitly returns a COPY. Ultralytics8.4.176.forward
    # denormalizes its copy only, so this reads untouched normalized native output,
    # not a guessed inverse of the denormalized prediction used by Python NMS.
    snapshot = interpreter.get_tensor(outputs[0]["index"])
    if snapshot.shape != (1, 6, 8400) or snapshot.dtype != np.float32 or not np.isfinite(snapshot).all():
        raise ValueError("Native NMS snapshot violates finite FLOAT32 [1,6,8400]")
    if (snapshot[:, 4:] < 0).any() or (snapshot[:, 4:] > 1).any():
        raise ValueError("Native class scores violate the existing decoder contract")
    return np.ascontiguousarray(snapshot, dtype=np.dtype("<f4")).tobytes()


def generate(args, repository: Path):
    samples = read_manifest(args.manifest, repository)
    output = require_private_path(args.output, repository, directory=True)
    claim_output_directory(output)
    model = args.model.resolve()
    if model.name != actual.MODEL_NAME or model.stat().st_size != actual.MODEL_BYTES or actual.sha256(model.read_bytes()) != actual.MODEL_SHA256:
        raise ValueError("Canonical EXP02 model filename/size/SHA256 mismatch")
    if importlib.util.find_spec("ai_edge_litert") is None:
        raise RuntimeError("ai_edge_litert is missing; no automatic install or substitute interpreter is allowed")
    if importlib.metadata.version("ai-edge-litert") != "2.2.0":
        raise RuntimeError("Use the established LiteRT 2.2.0 environment; do not silently change native runtimes")
    from ai_edge_litert.interpreter import Interpreter
    import cv2
    import numpy as np
    import torch

    loader = actual.load_pinned_loader(repository)
    reference, source_hashes = loader.load_reference(args.source_root, args.wheel)
    torch.set_num_threads(1)
    interpreter = Interpreter(model_path=str(model), num_threads=2)
    interpreter.allocate_tensors()
    inputs, outputs = interpreter.get_input_details(), interpreter.get_output_details()
    actual.validate_tensor_details(inputs, outputs, np)
    predictor = SimpleNamespace(imgsz=(640, 640), scale_fill=False,
                                args=SimpleNamespace(rect=True), device=torch.device("cpu"),
                                model=SimpleNamespace(format="litert", dynamic=False, stride=32, fp16=False))
    predictor.pre_transform = lambda images: reference["pre_transform"](predictor, images)
    backend = SimpleNamespace(nhwc=False, task="detect", end2end=False, head="Detect",
                              names={0: "SQUAT", 1: "SHOULDER_PRESS"}, interpreter=interpreter,
                              input_details=inputs, output_details=outputs)
    output.mkdir(parents=True, exist_ok=True)
    result_samples = []
    # Bounded one-candidate memory; private temporary files disappear on normal completion/failure.
    with tempfile.TemporaryDirectory(prefix=".positive-stage-", dir=output) as temporary:
        stage = Path(temporary).resolve()
        for sample in samples:
            image_path = require_private_path(args.manifest.parent / sample["image"], repository)
            encoded = image_path.read_bytes()
            if not 0 < len(encoded) <= actual.MAX_ENCODED_IMAGE_BYTES or not encoded.startswith(PNG_SIGNATURE):
                raise ValueError("Private input changed or ceased to be a bounded PNG")
            bgr = cv2.imdecode(np.frombuffer(encoded, dtype=np.uint8), cv2.IMREAD_COLOR | cv2.IMREAD_IGNORE_ORIENTATION)
            if bgr is None:
                raise ValueError(f"Cannot decode candidate {sample['id']}")
            height, width = bgr.shape[:2]
            actual.validate_input_dimensions(width, height)
            rgb = np.ascontiguousarray(bgr[..., ::-1]).tobytes()
            capture_name = sample.get("androidResult")
            if capture_name is not None:
                capture_path = require_private_path(args.manifest.parent / capture_name, repository)
                if not capture_path.is_file() or not 0 < capture_path.stat().st_size <= 2_097_152:
                    raise ValueError("Missing bounded captured Android JSON")
                capture = json.loads(capture_path.read_text(encoding="utf-8-sig"))
                validate_capture_provenance(capture, image_path.name, actual.sha256(encoded), actual.sha256(rgb), width, height)
            # These are unchanged original functions, not a second letterbox/normalization implementation.
            tensor = reference["preprocess"](predictor, [bgr])
            tensor_array = tensor.numpy()
            if tensor_array.shape != (1, 3, 640, 640) or tensor_array.dtype != np.float32 or not np.isfinite(tensor_array).all() or tensor_array.min() < 0 or tensor_array.max() > 1:
                raise ValueError("Pinned preprocessing failed canonical finite FLOAT32 BCHW checks")
            tensor_bytes = np.ascontiguousarray(tensor_array, dtype=np.dtype("<f4")).tobytes()
            raw = reference["forward"](backend, tensor)[0]
            if raw.shape != (1, 6, 8400) or raw.dtype != np.float32 or not np.isfinite(raw).all():
                raise ValueError("Actual native output violates canonical finite FLOAT32 shape")
            raw_metadata = {}
            if getattr(args, "export_nms_raw", False):
                native_bytes = normalized_native_snapshot(interpreter, outputs, np)
                raw_name = f"{sample['id']}.output.f32"
                actual.private_child(stage, raw_name).write_bytes(native_bytes)
                raw_metadata = dict(rawOutputFile=raw_name, rawOutputSha256=actual.sha256(native_bytes),
                    rawOutputByteOrder="LITTLE_ENDIAN", rawOutputProvenance="ACTUAL_NATIVE_NORMALIZED_GET_TENSOR_COPY_AFTER_REFERENCE_FORWARD")
            processed = reference["non_max_suppression"](
                torch.from_numpy(raw), conf_thres=.10, iou_thres=.70, nc=2, multi_label=False,
                agnostic=False, max_det=300, rotated=False, end2end=False)[0]
            if len(processed):
                reference["scale_boxes"]((640, 640), processed[:, :4], (height, width))
            detections, clipped = actual.post_nms_detections(processed, width, height)
            rgb_name, tensor_name = f"{sample['id']}.rgb", f"{sample['id']}.input.f32"
            actual.private_child(stage, rgb_name).write_bytes(rgb)
            actual.private_child(stage, tensor_name).write_bytes(tensor_bytes)
            result_samples.append(dict(id=sample["id"], exercise=sample.get("exercise"), cameraView=sample.get("cameraView"),
                image=image_path.name, sourceImageBytes=len(encoded), sourceImageSha256=actual.sha256(encoded),
                sourceAndroidResult=capture_name, capturedAndroidRasterVerified=capture_name is not None,
                rgbFile=rgb_name, rgbSha256=actual.sha256(rgb), width=width, height=height,
                inputTensorFile=tensor_name, inputTensorSha256=actual.sha256(tensor_bytes), inputTensorByteOrder="LITTLE_ENDIAN",
                nmsOutputCount=len(processed), droppedFullyClippedCount=clipped, detections=detections,
                positiveCandidate=bool(detections), **raw_metadata, **semantic_observation(sample.get("exercise"), detections)))
        document = dict(schemaVersion=1, validationPurpose=PURPOSE,
            model=dict(id="yolo26n_exp02_fp32", filename=actual.MODEL_NAME, bytes=actual.MODEL_BYTES, sha256=actual.MODEL_SHA256),
            ultralyticsVersion=loader.VERSION, wheelSha256=loader.WHEEL_SHA256, sourceSha256=source_hashes,
            modelInferenceExecuted=True, androidNativeInferenceExecuted=False, exportContractVerified=False,
            pythonVersion=sys.version.split()[0],
            runtime=dict(name="ai_edge_litert", version=importlib.metadata.version("ai-edge-litert"), pythonExecutable=sys.executable,
                         numpyVersion=np.__version__, opencvVersion=cv2.__version__, torchVersion=torch.__version__,
                         interpreterThreads=2, torchThreads=1, device="CPU"),
            tensorMetadata=dict(input=dict(shape=[1, 3, 640, 640], dtype="FLOAT32"), output=dict(shape=[1, 6, 8400], dtype="FLOAT32")),
            profile="STATIC_LITERT_PREDICT_RGB_FLOAT32_BCHW_BEST_CLASS",
            comparisonOrder="POST_NMS_DESCENDING_CONFIDENCE_PRESERVED",
            thresholds=dict(confidence=.10, iou=.70, maxDetections=300),
            tolerances=dict(confidence=.001, bboxPixels=2.0, detectionCount=0, classMatch="EXACT", status="PROVISIONAL_NOT_MEASURED"),
            semanticSanityCheck=dict(labeled=sum(sample["expectedExercise"] is not None for sample in result_samples),
                matched=sum(sample["semanticMatch"] is True for sample in result_samples),
                definition="Only explicitly declared exercise labels; not dataset accuracy, positive criterion, or runtime equivalence gate."),
            candidateSummary=candidate_summary(result_samples), samples=result_samples,
            privacy="Private explicitly saved candidate PNGs/rasters/tensors only; never uploaded, committed, or packaged.")
        for sample in result_samples:
            for filename in (sample["rgbFile"], sample["inputTensorFile"], *([sample["rawOutputFile"]] if "rawOutputFile" in sample else [])):
                actual.private_child(stage, filename).replace(actual.private_child(output, filename))
        pending = actual.private_child(output, "reference.json.pending")
        pending.write_text(json.dumps(document, ensure_ascii=False, indent=2, allow_nan=False) + "\n", encoding="utf-8")
        pending.replace(actual.private_child(output, "reference.json"))
    summary = document["candidateSummary"]
    print(f"RUN: {len(result_samples)} actual native candidate inferences; {summary['positiveCandidateCount']} positive, {summary['emptyCandidateCount']} empty.")
    print("Python output preserved as-is. Android comparison pending; production contract NOT_VERIFIED.")


def main():
    repository = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--model", type=Path, default=repository / "app/src/main/assets" / actual.MODEL_NAME)
    parser.add_argument("--source-root", type=Path, default=repository / "build/yolo-contract-reference/extracted")
    parser.add_argument("--wheel", type=Path, default=repository / "build/yolo-contract-reference/ultralytics-8.4.176-py3-none-any.whl")
    parser.add_argument("--output", type=Path, default=repository / "local_validation/exp02_positive/bundle")
    parser.add_argument("--export-nms-raw", action="store_true", help="Private actual normalized native output copy for NMS evidence; no extra inference")
    args = parser.parse_args()
    try:
        generate(args, repository)
    except (ValueError, RuntimeError, OSError, ImportError) as error:
        parser.exit(2, f"Positive reference NOT_RUN/INCOMPLETE: {error}\n")


if __name__ == "__main__":
    main()
