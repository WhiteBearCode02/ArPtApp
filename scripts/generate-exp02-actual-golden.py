"""Offline actual EXP02 FP32 reference, using exact pinned Ultralytics source functions.

Requires user-provided rights-cleared images and an ALREADY installed ai_edge_litert
runtime. Never installs/downloads anything. Outputs private RGB media/reference only
under ignored build/. This does not verify or modify the Android production gate.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import re
from pathlib import Path
import sys
from types import SimpleNamespace

MODEL_NAME = "airptcoach_yolo26n_exp02_fp32.tflite"
MODEL_BYTES = 9894030
MODEL_SHA256 = "464cafe87c3d6e04ba71951e73efab8690367f77fb224105d4b1e50ab3d33749"
CLASSES = {"SQUAT": 0, "SHOULDER_PRESS": 1}
REQUIRED = {(exercise, view) for exercise in CLASSES for view in ("FRONT", "SIDE")}


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def read_manifest(path: Path) -> list[dict]:
    """No inferred exercise/view/rights: explicit user-approved four-way coverage is mandatory."""
    document = json.loads(path.read_text(encoding="utf-8-sig"))
    if document.get("schemaVersion") != 1:
        raise ValueError("Expected input manifest schemaVersion=1")
    samples = document.get("samples")
    if not isinstance(samples, list) or not 4 <= len(samples) <= 64:
        raise ValueError("Provide 4..64 rights-cleared samples")
    names, covered = set(), set()
    for sample in samples:
        name = sample.get("id", "")
        if not re.fullmatch(r"[a-z0-9][a-z0-9_-]{0,63}", name) or name in names:
            raise ValueError("Invalid or duplicate sample id")
        names.add(name)
        pair = (sample.get("exercise"), sample.get("cameraView"))
        if pair not in REQUIRED:
            raise ValueError("Only existing SQUAT/SHOULDER_PRESS and FRONT/SIDE types are supported")
        covered.add(pair)
        if sample.get("rightsCleared") is not True or not isinstance(sample.get("rightsBasis"), str) or not sample["rightsBasis"].strip():
            raise ValueError("Each image needs explicit rightsCleared=true and rightsBasis; do not infer AIHub redistribution permission")
        if not isinstance(sample.get("image"), str) or not sample["image"].strip():
            raise ValueError("Missing local image path")
    if covered != REQUIRED:
        raise ValueError("Missing required SQUAT FRONT/SIDE or SHOULDER_PRESS FRONT/SIDE sample")
    return samples


def require_private_output(output: Path, repository: Path) -> Path:
    """Prevent accidentally creating trackable camera media / real model output."""
    destination = output.resolve()
    build = (repository / "build").resolve()
    if destination == build or build not in destination.parents:
        raise ValueError("Actual Golden output must be a dedicated subdirectory under ignored repository build/")
    return destination


def load_pinned_loader(repository: Path):
    script = repository / "scripts/generate-exp02-golden.py"
    spec = importlib.util.spec_from_file_location("exp02_pinned_reference", script)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


def validate_tensor_details(inputs, outputs, np):
    for actual, expected in ((inputs, [1, 3, 640, 640]), (outputs, [1, 6, 8400])):
        if len(actual) != 1 or actual[0]["shape"].tolist() != expected or actual[0]["dtype"] != np.float32:
            raise ValueError("Canonical input/output FLOAT32 tensor contract mismatch; native inference blocked")


def generate(args, repository: Path):
    # Imports only after manifest and model validation, with no full YOLO import/autoinstall side effects.
    samples = read_manifest(args.manifest)
    output = require_private_output(args.output, repository)
    model = args.model.resolve()
    if model.name != MODEL_NAME or model.stat().st_size != MODEL_BYTES or sha256(model.read_bytes()) != MODEL_SHA256:
        raise ValueError("Canonical EXP02 model filename/size/SHA256 mismatch")
    if importlib.util.find_spec("ai_edge_litert") is None:
        raise RuntimeError("ai_edge_litert is not installed in this interpreter. No auto-install is allowed; use the established export/test environment")
    from ai_edge_litert.interpreter import Interpreter
    import cv2
    import numpy as np
    import torch
    import importlib.metadata

    loader = load_pinned_loader(repository)
    reference, source_hashes = loader.load_reference(args.source_root, args.wheel)
    torch.set_num_threads(1)
    interpreter = Interpreter(model_path=str(model), num_threads=2)
    interpreter.allocate_tensors()
    inputs, outputs = interpreter.get_input_details(), interpreter.get_output_details()
    validate_tensor_details(inputs, outputs, np)
    predictor = SimpleNamespace(imgsz=(640, 640), scale_fill=False,
                                args=SimpleNamespace(rect=True), device=torch.device("cpu"),
                                model=SimpleNamespace(format="litert", dynamic=False, stride=32, fp16=False))
    predictor.pre_transform = lambda images: reference["pre_transform"](predictor, images)
    backend = SimpleNamespace(nhwc=False, task="detect", end2end=False, head="Detect",
                              names={0: "SQUAT", 1: "SHOULDER_PRESS"}, interpreter=interpreter,
                              input_details=inputs, output_details=outputs)
    result_samples, rgb_outputs = [], []
    for sample in samples:
        image_path = (args.manifest.parent / sample["image"]).resolve()
        encoded = image_path.read_bytes()
        # Ignore EXIF in both paths. Android receives this same decoded RGB raster, not a JPEG re-decode.
        bgr = cv2.imdecode(np.frombuffer(encoded, dtype=np.uint8), cv2.IMREAD_COLOR | cv2.IMREAD_IGNORE_ORIENTATION)
        if bgr is None:
            raise ValueError(f"Cannot decode sample {sample['id']}")
        height, width = bgr.shape[:2]
        if not 1 <= width <= 8192 or not 1 <= height <= 8192 or width * height > 16_777_216:
            raise ValueError("Input bounds exceed Android preprocessor safety limits")
        rgb = np.ascontiguousarray(bgr[..., ::-1]).tobytes()
        tensor = reference["preprocess"](predictor, [bgr])
        # This is the REAL native interpreter, not SyntheticInterpreter from the synthetic fixture tool.
        raw = reference["forward"](backend, tensor)[0]
        prediction = torch.from_numpy(raw)
        processed = reference["non_max_suppression"](
            prediction, conf_thres=.10, iou_thres=.70, nc=2, multi_label=False,
            agnostic=False, max_det=300, rotated=False, end2end=False)[0]
        if len(processed):
            reference["scale_boxes"]((640, 640), processed[:, :4], (height, width))
        detections = []
        for row in processed:
            box = [float(value) for value in row[:4]]
            if box[2] <= box[0] or box[3] <= box[1]:
                continue  # Same documented Android fully-clipped-box safety filter after NMS.
            detections.append(dict(classId=int(row[5]), className=backend.names[int(row[5])],
                                   confidence=float(row[4]), bbox=dict(zip(("x1", "y1", "x2", "y2"), box))))
        if not detections or not any(d["classId"] == CLASSES[sample["exercise"]] for d in detections):
            raise ValueError(f"Sample {sample['id']} has no expected-exercise post-NMS prediction; cannot use as a positive correctness Golden")
        rgb_name = f"{sample['id']}.rgb"
        rgb_outputs.append((rgb_name, rgb))
        result_samples.append(dict(id=sample["id"], exercise=sample["exercise"], cameraView=sample["cameraView"],
                                   sourceImageSha256=sha256(encoded), rgbFile=rgb_name, rgbSha256=sha256(rgb),
                                   width=width, height=height, inputTensorSha256=sha256(tensor.numpy().tobytes()),
                                   detections=detections))
    document = dict(schemaVersion=1, model=dict(id="yolo26n_exp02_fp32", filename=MODEL_NAME, bytes=MODEL_BYTES, sha256=MODEL_SHA256),
                    ultralyticsVersion=loader.VERSION, wheelSha256=loader.WHEEL_SHA256, sourceSha256=source_hashes,
                    modelInferenceExecuted=True, androidNativeInferenceExecuted=False,
                    exportContractVerified=False, pythonVersion=sys.version.split()[0],
                    runtime=dict(name="ai_edge_litert", version=importlib.metadata.version("ai-edge-litert")),
                    tensorMetadata=dict(input=dict(shape=[1, 3, 640, 640], dtype="FLOAT32"),
                                        output=dict(shape=[1, 6, 8400], dtype="FLOAT32")),
                    profile="STATIC_LITERT_PREDICT_RGB_FLOAT32_BCHW_BEST_CLASS",
                    thresholds=dict(confidence=.10, iou=.70, maxDetections=300),
                    tolerances=dict(confidence=.001, bboxPixels=2.0, detectionCount=0, classMatch="EXACT",
                                    status="PROVISIONAL_NOT_MEASURED",
                                    rationale="Conservative initial acceptance limits for final detections, not empirical evidence. No automatic widening. Exact post-NMS count/class; order-independent one-to-one same-class matches."),
                    privacy="Local rights-cleared validation rasters and results only; no images or detections are uploaded or committed.",
                    samples=result_samples)
    # Publish reference LAST. A failed run does not replace a previous valid manifest.
    output.mkdir(parents=True, exist_ok=True)
    for filename, data in rgb_outputs:
        (output / filename).write_bytes(data)
    staged = output / "reference.json.pending"
    staged.write_text(json.dumps(document, ensure_ascii=False, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    staged.replace(output / "reference.json")
    print(f"Actual-model Python reference generated: {len(samples)} samples in ignored build output.")
    print("Android native comparison NOT executed. Production export contract remains NOT_VERIFIED.")


def main():
    repository = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--model", type=Path, default=repository / "app/src/main/assets" / MODEL_NAME)
    parser.add_argument("--source-root", type=Path, default=repository / "build/yolo-contract-reference/extracted")
    parser.add_argument("--wheel", type=Path, default=repository / "build/yolo-contract-reference/ultralytics-8.4.176-py3-none-any.whl")
    parser.add_argument("--output", type=Path, default=repository / "build/exp02-actual-golden")
    args = parser.parse_args()
    try:
        generate(args, repository)
    except (ValueError, RuntimeError, OSError, ImportError) as error:
        parser.exit(2, f"Actual Golden blocked: {error}\n")


if __name__ == "__main__":
    main()
