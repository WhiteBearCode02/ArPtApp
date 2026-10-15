"""Generate synthetic contract fixtures by executing exact Ultralytics 8.4.176 source.

No model, dataset, image file, cloud service or full Ultralytics installation is used.
The downloaded wheel stays in ignored build/. AST-selected original functions are
executed unchanged with NumPy/OpenCV/CPU Torch; unrelated package side effects,
requirements auto-installation and weight downloads are deliberately bypassed.
This checks the explicit static LiteRT PREDICT path, NOT historical dataset .val.
Prints JSON to stdout; --check compares an existing committed fixture without writes.
"""
from __future__ import annotations

import argparse
import ast
import hashlib
import importlib.metadata
import json
import logging
from pathlib import Path
import sys
import time
from types import SimpleNamespace
from typing import Any
import zipfile

import cv2
import numpy as np
import torch


VERSION = "8.4.176"
WHEEL_SHA256 = "15f4bbac5c69fd9a5a9395f5957a7ce561e3eb4620699a9c2ff11ed38ec7d34c"
FILES = [
    "ultralytics/data/augment.py",
    "ultralytics/engine/predictor.py",
    "ultralytics/nn/backends/litert.py",
    "ultralytics/utils/ops.py",
    "ultralytics/utils/nms.py",
]


def load_reference(source_root: Path, wheel: Path):
    """Compile original AST nodes, not a rewritten Python approximation."""
    if hashlib.sha256(wheel.read_bytes()).hexdigest() != WHEEL_SHA256:
        raise ValueError("Exact Ultralytics wheel SHA-256 mismatch")
    sources = {}
    with zipfile.ZipFile(wheel) as archive:
        for relative in FILES:
            content = (source_root / relative).read_bytes()
            if content != archive.read(relative):
                raise ValueError(f"Extracted source differs from pinned wheel: {relative}")
            sources[relative] = content
    namespace = dict(np=np, cv2=cv2, torch=torch, Any=Any, sys=sys, time=time,
                     LOGGER=logging.getLogger("exp02-golden"), NOT_MACOS14=True)
    # Rotated-box paths are outside EXP02 detect scope and must never execute.
    def unsupported(*args, **kwargs):
        raise AssertionError("Out-of-scope rotated-box function called")
    namespace.update(batch_probiou=unsupported, box_iou=unsupported)

    def execute(relative, names, owner=None):
        parsed = ast.parse(sources[relative].decode("utf-8"))
        available = parsed.body
        if owner:
            available = next(n for n in available if isinstance(n, ast.ClassDef) and n.name == owner).body
        nodes = [next(n for n in available if isinstance(n, (ast.FunctionDef, ast.ClassDef)) and n.name == name)
                 for name in names]
        module = ast.Module(body=[ast.ImportFrom(module="__future__", names=[ast.alias(name="annotations")], level=0),
                                  *nodes], type_ignores=[])
        ast.fix_missing_locations(module)
        exec(compile(module, str(source_root / relative), "exec"), namespace)

    execute(FILES[0], ["BaseTransform", "LetterBox"])
    execute(FILES[1], ["preprocess", "pre_transform"], owner="BasePredictor")
    execute(FILES[2], ["forward"], owner="LiteRTBackend")
    execute(FILES[3], ["empty_like", "xywh2xyxy", "clip_boxes", "scale_boxes"])
    execute(FILES[4], ["TorchNMS", "non_max_suppression"])
    return namespace, {name: hashlib.sha256(data).hexdigest() for name, data in sources.items()}


def rgb_image(width: int, height: int):
    y, x = np.indices((height, width), dtype=np.uint32)
    return np.stack(((37*x + 17*y + 11) % 256,
                     (13*x + 29*y + 53) % 256,
                     (7*x + 43*y + 101) % 256), axis=-1).astype(np.uint8)


def preprocess_fixture(reference, name, width, height):
    # Source array follows OpenCV logical BGR; expected tensor must be RGB BCHW.
    bgr = rgb_image(width, height)[..., ::-1].copy()
    predictor = SimpleNamespace(imgsz=(640, 640), scale_fill=False,
                                args=SimpleNamespace(rect=True), device=torch.device("cpu"),
                                model=SimpleNamespace(format="litert", dynamic=False, stride=32, fp16=False))
    predictor.pre_transform = lambda images: reference["pre_transform"](predictor, images)
    tensor = reference["preprocess"](predictor, [bgr]).numpy()
    assert tensor.shape == (1, 3, 640, 640) and tensor.dtype == np.float32
    params = reference["LetterBox"]((640, 640), auto=False).get_params({"img": bgr})
    new_w, new_h = params["new_unpad"]
    left, top = params["left"], params["top"]
    coordinates = {
        (0, 0), (639, 639), (0, 639), (639, 0), (320, 320),
        (left, top), (left+new_w-1, top+new_h-1),
        (left, min(639, top+new_h//2)), (min(639, left+new_w//2), top),
        (max(0, left-1), max(0, top-1)),
    }
    # Non-uniform interpolation samples, not only constant colours/padding.
    for i in range(12):
        coordinates.add((left + (i*73+17) % new_w, top + (i*97+23) % new_h))
    samples = []
    for x, y in sorted(coordinates, key=lambda p: (p[1], p[0])):
        is_padding = not (left <= x < left+new_w and top <= y < top+new_h)
        exact = is_padding or (new_w == width and new_h == height)
        for channel in range(3):
            samples.append([channel*640*640+y*640+x, float(tensor[0, channel, y, x]), exact])
    return dict(name=name, width=width, height=height, resizedWidth=new_w, resizedHeight=new_h,
                padLeft=left, padTop=top, padRight=params["right"], padBottom=params["bottom"],
                gainX=new_w/width, gainY=new_h/height,
                tensorSha256=hashlib.sha256(tensor.tobytes()).hexdigest(), samples=samples)


class SyntheticInterpreter:
    """Only supplies synthetic output for original LiteRTBackend.forward; NOT native inference."""
    def __init__(self, output):
        self.output = output
        self.input = None
        self.calls = 0
    def set_tensor(self, index, value):
        self.input = value
    def invoke(self):
        self.calls += 1
    def get_tensor(self, index):
        return self.output.copy()


def backend_output(reference, columns):
    raw = np.zeros((1, 6, 8400), dtype=np.float32)
    for column in columns:
        raw[0, :, column["index"]] = np.asarray(column["values"], dtype=np.float32)
    interpreter = SyntheticInterpreter(raw)
    backend = SimpleNamespace(nhwc=False, task="detect", end2end=False, head="Detect",
                              names={0: "SQUAT", 1: "SHOULDER_PRESS"}, interpreter=interpreter,
                              input_details=[dict(index=0, dtype=np.float32)],
                              output_details=[dict(index=1, dtype=np.float32)])
    result = reference["forward"](backend, torch.zeros((1, 3, 640, 640), dtype=torch.float32))[0]
    assert interpreter.calls == 1 and interpreter.input.shape == (1, 3, 640, 640)
    return torch.from_numpy(result)


def detection_rows(reference, columns, conf=.25, iou=.7, width=640, height=640, max_det=300):
    prediction = backend_output(reference, columns)
    outputs, indices = reference["non_max_suppression"](
        prediction, conf_thres=conf, iou_thres=iou, nc=2, multi_label=False,
        agnostic=False, max_det=max_det, rotated=False, end2end=False, return_idxs=True)
    model_boxes = outputs[0][:, :4].clone()
    source_boxes = reference["scale_boxes"]((640, 640), model_boxes.clone(), (height, width))
    rows = []
    for i, row in enumerate(outputs[0]):
        rows.append(dict(index=int(indices[0][i]), classId=int(row[5]), confidence=float(row[4]),
                         modelBox=[float(x) for x in model_boxes[i]],
                         sourceBox=[float(x) for x in source_boxes[i]]))
    return rows


def generate(reference, hashes):
    def column(index, cx, cy, width, height, squat, press):
        return dict(index=index, values=[cx, cy, width, height, squat, press])
    decode = [column(0, .5, .5, .25, .5, .91, .13),
              column(37, .253, .709, .132, .211, .22, .83),
              column(8399, .125, .25, .15, .2, .67, .67)]
    nms_cases = [
        dict(name="same-class-overlap-and-different-class", width=640, height=640,
             columns=[column(0, .5, .5, .4, .4, .95, .02),
                      column(1, .51, .5, .4, .4, .85, .03),
                      column(2, .5, .5, .4, .4, .02, .9),
                      column(3, .1, .1, .1, .1, .7, .03)]),
        dict(name="strict-confidence-threshold", width=640, height=640,
             columns=[column(0, .2, .2, .1, .1, .25, .1),
                      column(1, .8, .8, .1, .1, .2501, .1),
                      column(2, .5, .5, .1, .1, .2, .2499)]),
        # On landscape input these boxes overlap strongly AFTER clipping to the
        # original image but not in model space: NMS must precede mapping/clip.
        dict(name="nms-before-source-clipping", width=1000, height=333,
             columns=[column(0, .5, .285, .4, .13, .92, .1),
                      column(1, .5, .335, .4, .13, .81, .1)]),
        dict(name="odd-padding-rescale-and-clip", width=7, height=13,
             columns=[column(0, .2, .3, .25, .4, .89, .1),
                      column(1, .78, .79, .4, .55, .1, .79)]),
        dict(name="no-detection", width=640, height=640,
             columns=[column(0, .5, .5, .2, .2, .2, .1)]),
    ]
    for case in nms_cases:
        case.update(confidenceThreshold=.25, iouThreshold=.7,
                    expected=detection_rows(reference, case["columns"], width=case["width"], height=case["height"]))
    packages = {name: importlib.metadata.version(name)
                for name in ["numpy", "opencv-python-headless", "torch"]}
    return dict(
        schemaVersion=1, ultralyticsVersion=VERSION, wheelSha256=WHEEL_SHA256, sourceSha256=hashes,
        pythonVersion=sys.version.split()[0], packages=packages,
        execution="AST-selected original functions; no full YOLO package or native/model inference",
        referenceFunctions=["BasePredictor.pre_transform", "BasePredictor.preprocess", "LetterBox",
                            "LiteRTBackend.forward (synthetic interpreter)", "non_max_suppression",
                            "TorchNMS.nms", "xywh2xyxy", "scale_boxes", "clip_boxes"],
        profile="STATIC_LITERT_PREDICT_RGB_FLOAT32_BCHW_BEST_CLASS",
        modelInferenceExecuted=False, originalDatasetMediaUsed=False,
        rgbFormula=["(37*x+17*y+11)%256", "(13*x+29*y+53)%256", "(7*x+43*y+101)%256"],
        tolerances=dict(exactTensor=1e-6, resampledTensor=1/255+1e-6, confidence=1e-6, bboxPixels=1e-4,
                        rationale="Resampled uint8 OpenCV INTER_LINEAR vs half-pixel bilinear may differ by at most ONE source byte; no tolerance for colour/layout/padding mistakes. Hash records Python output only."),
        preprocess=[preprocess_fixture(reference, *args) for args in [
            ("landscape", 5, 3), ("portrait", 3, 5), ("square", 7, 7),
            ("odd-padding", 7, 13), ("downscale", 961, 721), ("native", 640, 640)]],
        decode=dict(columns=decode, expected=detection_rows(reference, decode, conf=0, iou=1)),
        postprocess=nms_cases)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", type=Path, default=Path("build/yolo-contract-reference/extracted"))
    parser.add_argument("--wheel", type=Path, default=Path("build/yolo-contract-reference/ultralytics-8.4.176-py3-none-any.whl"))
    parser.add_argument("--check", type=Path, help="Compare existing fixture; never overwrite it")
    args = parser.parse_args()
    torch.set_num_threads(1)
    reference, hashes = load_reference(args.source_root, args.wheel)
    result = generate(reference, hashes)
    if args.check:
        existing = json.loads(args.check.read_text(encoding="utf-8"))
        if result != existing:
            raise SystemExit("Golden fixture mismatch: investigate source/dependency drift; do not auto-approve")
        print("Exact-source synthetic golden fixture reproduced; no model inference executed")
    else:
        print(json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False))


if __name__ == "__main__":
    main()
