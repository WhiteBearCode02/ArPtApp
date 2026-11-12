"""Offline, opt-in EXP04 reference. No download/install/upload, no semantic label filtering.

Reuse only the reviewed hash-pinned 8.4.202 AST loader and image bounds, never EXP03 results.
All images, RGB, tensors and predictions must remain in ignored local_validation/exp04_golden/.
Running this tool does NOT promote the Android contract or reproduce the 9,031-image holdout.
"""
from __future__ import annotations
import argparse
import importlib.util
import io
import json
import math
from pathlib import Path
import re
import stat
import subprocess
import sys
from types import SimpleNamespace

MODEL_ID = "yolo26n_exp04_7class_fp32"
MODEL_NAME = "airptcoach_yolo26n_exp04_7class_fp32.tflite"
MODEL_BYTES = 9898949
MODEL_SHA256 = "3e613e94fc1ef3aebbeba309d1b31d7ba6fc71a19274c9b2946793fe493eb84d"
CLASSES = ["SQUAT", "SHOULDER_PRESS", "BICEP_CURL", "PUSH_UP", "BENT_OVER_ROW", "TRICEPS_EXTENSION", "SIDE_BEND"]
VERSION = "8.4.202"
WHEEL_SHA256 = "ae71dc5bb1024010fbdef5bd9281984ddd8c70885bddcfdd6aff0801b7015fb4"
THRESHOLDS = dict(confidence=.10, iou=.70, maxDetections=300)
TOLERANCES = dict(confidence=.001, bboxPixels=2.0, detectionCount=0, classMatch="EXACT")

def validate_dimensions(width, height):
    if type(width) is not int or type(height) is not int or not (1 <= width <= 8192 and 1 <= height <= 8192) or width * height > 16_777_216:
        raise ValueError("RGB dimensions exceed Android preprocessor allocation bounds")

def post_nms_detections(rows, width, height):
    """Preserve wrong/empty predictions; omit ONLY boxes collapsed by the source's clipping."""
    validate_dimensions(width, height)
    detections = []
    if len(rows) > 300:
        raise ValueError("NMS detection bound exceeded")
    for row in rows:
        if len(row) != 6 or not all(math.isfinite(float(v)) for v in row):
            raise ValueError("Non-finite/invalid actual detection")
        x1, y1, x2, y2, confidence, cls = row
        if int(cls) not in range(7) or cls != int(cls) or not 0 <= confidence <= 1:
            raise ValueError("Invalid actual class/confidence")
        if not (0 <= x1 <= x2 <= width and 0 <= y1 <= y2 <= height):
            raise ValueError("Source-rescaled box outside the actual image")
        if x2 > x1 and y2 > y1:
            detections.append(dict(classId=int(cls), className=CLASSES[int(cls)], confidence=confidence, bbox=dict(x1=x1, y1=y1, x2=x2, y2=y2)))
    return detections

def load_reference_helpers():
    spec = importlib.util.spec_from_file_location("exp04_reviewed_source_loader", Path(__file__).with_name("generate-exp03-actual-golden.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

def private_path(path: Path, repository: Path) -> Path:
    lexical, root = path.absolute(), repository.resolve()
    resolved = path.resolve()
    base = root / "local_validation" / "exp04_golden"
    if resolved != base and base not in resolved.parents:
        raise ValueError("Use ignored local_validation/exp04_golden/ only")
    for item in (lexical, *lexical.parents):
        if item == root:
            break
        if item.is_symlink() or item.exists() and getattr(item.lstat(), "st_file_attributes", 0) & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0):
            raise ValueError("Private validation path cannot contain links/junctions")
    relative = resolved.relative_to(root).as_posix()
    ignored = subprocess.run(["git", "-C", str(root), "check-ignore", "--quiet", "--no-index", "--", relative], capture_output=True, timeout=30)
    tracked = subprocess.run(["git", "-C", str(root), "ls-files", "-z", "--", relative], capture_output=True, timeout=30)
    if ignored.returncode != 0 or tracked.returncode != 0 or tracked.stdout:
        raise ValueError("Validation inputs/outputs must be ignored and untracked")
    return resolved

def read_manifest(path: Path, repository: Path):
    path = private_path(path, repository)
    if not path.is_file() or not 0 < path.stat().st_size <= 262144:
        raise ValueError("Missing/bounded manifest: supply real rights-cleared images")
    document = json.loads(path.read_text(encoding="utf-8"), parse_constant=lambda _: (_ for _ in ()).throw(ValueError("Non-finite manifest")))
    if not isinstance(document, dict):
        raise ValueError("Manifest must be an object")
    samples = document["samples"]
    if not isinstance(samples, list) or not 7 <= len(samples) <= 64:
        raise ValueError("Require 7..64 actual images with all seven classes")
    seen, covered = set(), set()
    for s in samples:
        if not isinstance(s, dict):
            raise ValueError("Each manifest sample must be an object")
        identifier = s.get("id", "")
        if not isinstance(identifier, str) or not re.fullmatch(r"[a-z0-9][a-z0-9_-]{0,63}", identifier) or identifier in seen:
            raise ValueError("Unsafe/duplicate sample id")
        seen.add(identifier)
        if s.get("exercise") not in CLASSES or s.get("cameraView") not in ("FRONT", "SIDE", "DIAGONAL"):
            raise ValueError("Explicit expected exercise and camera view required")
        if s.get("rightsCleared") is not True or not isinstance(s.get("rightsBasis"), str) or not s["rightsBasis"].strip():
            raise ValueError("Rights must be explicitly confirmed, not inferred")
        image = s.get("image", "")
        folder = f"inputs/{CLASSES.index(s['exercise'])}_{s['exercise']}/"
        if not isinstance(image, str) or not image.startswith(folder) or not re.fullmatch(
                r"[A-Za-z0-9][A-Za-z0-9_.-]*\.(?:jpg|jpeg|png)", image[len(folder):], re.I):
            raise ValueError("Use inputs/<class index>_<EXERCISE>/<safe filename>, never a path escape/URL")
        private_path(path.parent / image, repository)
        covered.add(s["exercise"])
    if covered != set(CLASSES):
        raise ValueError("Missing exercise classes: " + ",".join(sorted(set(CLASSES) - covered)))
    return samples

def generate(args, repository: Path):
    samples = read_manifest(args.manifest, repository)
    output = private_path(args.output, repository)
    if output.exists():
        raise ValueError("Choose a NEW output directory; never overwrite evidence")
    old = load_reference_helpers()
    model = args.model.resolve()
    if model.name != MODEL_NAME or model.stat().st_size != MODEL_BYTES or old.sha256(model.read_bytes()) != MODEL_SHA256:
        raise ValueError("Exact EXP04 identity mismatch")
    reference, hashes, functions = old.load_reference(args.wheel)
    import cv2
    import numpy as np
    import torch
    from PIL import Image
    from ai_edge_litert.interpreter import Interpreter
    if cv2.__version__ != "4.13.0":
        raise ValueError("Require the reviewed OpenCV 4.13.0, no auto-install/version substitution")
    torch.set_num_threads(1)
    runtime = Interpreter(model_path=str(model), num_threads=2)
    runtime.allocate_tensors()
    inputs, outputs = runtime.get_input_details(), runtime.get_output_details()
    if len(inputs) != 1 or len(outputs) != 1 or inputs[0]["shape"].tolist() != [1, 3, 640, 640] or outputs[0]["shape"].tolist() != [1, 11, 8400] or any(v["dtype"] != np.float32 for v in inputs + outputs):
        raise ValueError("EXP04 actual FLOAT32 tensor metadata mismatch")
    predictor = SimpleNamespace(imgsz=(640, 640), scale_fill=False, args=SimpleNamespace(rect=True), device=torch.device("cpu"),
        model=SimpleNamespace(format="litert", dynamic=False, stride=32, fp16=False))
    predictor.pre_transform = lambda images: reference["pre_transform"](predictor, images)
    backend = SimpleNamespace(nhwc=False, intel=False, task="detect", end2end=False, head="Detect", names=dict(enumerate(CLASSES)),
        interpreter=runtime, input_details=inputs, output_details=outputs)
    # A failure may leave a partial NEW run. Never relabel it as complete or overwrite an old run.
    output.mkdir(parents=True, exist_ok=False)
    rows = []
    for sample_index, sample in enumerate(samples, 1):
        image = private_path(args.manifest.parent / sample["image"], repository)
        if not 0 < image.stat().st_size <= old.shared.MAX_ENCODED_IMAGE_BYTES:
            raise ValueError("Image exceeds encoded input bound")
        encoded = image.read_bytes()
        with Image.open(io.BytesIO(encoded)) as header:
            validate_dimensions(*header.size)  # before OpenCV raster allocation
        bgr = cv2.imdecode(np.frombuffer(encoded, np.uint8), cv2.IMREAD_COLOR | cv2.IMREAD_IGNORE_ORIENTATION)
        if bgr is None:
            raise ValueError("Actual image decode failed")
        h, w = bgr.shape[:2]; validate_dimensions(w, h)
        tensor = reference["preprocess"](predictor, [bgr])
        array = tensor.numpy()
        if array.shape != (1, 3, 640, 640) or array.dtype != np.float32 or not np.isfinite(array).all() or array.min() < 0 or array.max() > 1:
            raise ValueError("Invalid real preprocessing tensor")
        prediction = reference["forward"](backend, tensor)[0]  # actual native invoke; no expected-label filtering
        native = runtime.get_tensor(outputs[0]["index"])
        if native.shape != (1, 11, 8400) or native.dtype != np.float32 or not np.isfinite(native).all():
            raise ValueError("Invalid real output tensor")
        result = reference["non_max_suppression"](torch.from_numpy(prediction), conf_thres=.10, iou_thres=.70,
            nc=7, multi_label=False, agnostic=False, max_det=300, rotated=False, end2end=False)[0]
        if len(result): reference["scale_boxes"]((640, 640), result[:, :4], (h, w))
        detections = post_nms_detections(result.tolist(), w, h)
        geometry = reference["LetterBox"]((640, 640), auto=False).get_params({"img": bgr})
        identifier = sample["id"]; payloads = {}
        for suffix, data in ((".rgb", np.ascontiguousarray(bgr[..., ::-1]).tobytes()),
                             (".input.f32", np.ascontiguousarray(array, dtype="<f4").tobytes()),
                             (".output.f32", np.ascontiguousarray(native, dtype="<f4").tobytes())):
            path = private_path(output / (identifier + suffix), repository)
            with path.open("xb") as stream: stream.write(data)
            payloads[suffix] = dict(file=path.name, sha256=old.sha256(data), bytes=len(data))
        rows.append(dict(id=identifier, exercise=sample["exercise"], cameraView=sample["cameraView"], rightsCleared=True,
            rightsBasis=sample["rightsBasis"], width=w, height=h, sourceImageSha256=old.sha256(encoded),
            payloads=payloads, detections=detections, classOrdering=[d["classId"] for d in detections],
            semanticMatch=bool(detections and detections[0]["className"] == sample["exercise"]),
            preprocessing=dict(logicalColor="RGB", layout="BCHW", dtype="FLOAT32", normalizationDivisor=255,
                paddingValue=114, centered=True, auto=False,
                resizedWidth=geometry["new_unpad"][0], resizedHeight=geometry["new_unpad"][1],
                padLeft=geometry["left"], padTop=geometry["top"], padRight=geometry["right"], padBottom=geometry["bottom"])))
        print(f"EXP04 actual Python inference completed: {sample_index}/{len(samples)}; Android NOT_RUN", flush=True)
    document = dict(schemaVersion=1, validationPurpose="EXP04_ACTUAL_IMAGE_GOLDEN", model=dict(id=MODEL_ID, filename=MODEL_NAME, bytes=MODEL_BYTES, sha256=MODEL_SHA256),
        ultralyticsVersion=VERSION, wheelSha256=WHEEL_SHA256, sourceSha256=hashes, referenceFunctions=functions,
        classes=CLASSES, thresholds=THRESHOLDS, tolerances=TOLERANCES, preprocessingAcceptance="EXACT_TENSOR_BYTES_SHA256", tensorByteOrder="LITTLE_ENDIAN",
        modelInferenceExecuted=True, nativeInferenceCount=len(rows), androidNativeInferenceExecuted=False,
        exportContractVerified=False, contractPromotionPerformed=False, samples=rows)
    encoded_reference = json.dumps(document, ensure_ascii=False, indent=2, allow_nan=False).encode("utf-8")
    if len(encoded_reference) > 2_097_152:
        raise ValueError("Reference exceeds Android JSON bound; incomplete run, never a Golden PASS")
    with private_path(output / "reference.json", repository).open("xb") as stream:
        stream.write(encoded_reference)
    print(json.dumps(dict(status="PYTHON_REFERENCE_CREATED_NOT_ANDROID_VERIFIED", samples=len(rows),
        referenceSha256=old.sha256((output / "reference.json").read_bytes())), ensure_ascii=False))

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True); parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--model", type=Path, default=Path("app/src/main/assets") / MODEL_NAME)
    parser.add_argument("--wheel", type=Path, default=Path("build/exp03-contract-reference/ultralytics-8.4.202-py3-none-any.whl"))
    parser.add_argument("--check-inputs", action="store_true", help="Validate rights, coverage and existing files only; never invoke the model")
    args = parser.parse_args()
    try:
        repository = Path(__file__).resolve().parents[1]
        if args.check_inputs:
            samples = read_manifest(args.manifest, repository)
            for sample in samples:
                image = private_path(args.manifest.parent / sample["image"], repository)
                if not image.is_file() or not 0 < image.stat().st_size <= 32 * 1024 * 1024:
                    raise ValueError("Missing/bounded real image: " + sample["id"])
            print("INPUT_PREFLIGHT_PASS; Golden NOT_RUN; no model inference or contract promotion")
        else:
            generate(args, repository)
    except (ValueError, OSError, KeyError, TypeError, ImportError, subprocess.SubprocessError) as error: parser.exit(2, f"EXP04 Golden NOT_RUN/INCOMPLETE: {error}\n")
if __name__ == "__main__": main()
