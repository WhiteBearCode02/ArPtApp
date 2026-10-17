"""Offline, exact-source EXP02 NMS evidence; never model accuracy or contract promotion.

Controlled normalized [1,6,8400] tensors are explicitly labelled synthetic. Optional
actual tensors MUST be frozen direct Interpreter.get_tensor output from an actual
positive reference. Both paths call the existing pinned LiteRTBackend.forward and
Ultralytics 8.4.176 non_max_suppression; there is no second NMS implementation here.
No models/images/network/package installation/Android/session access is performed.
"""
from __future__ import annotations

import argparse
import ast
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys
import tempfile
from types import SimpleNamespace
import zipfile


def load_positive_helpers():
    path = Path(__file__).with_name("generate-exp02-positive-reference.py")
    spec = importlib.util.spec_from_file_location("exp02_positive_nms_helpers", path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


positive = load_positive_helpers()
actual = positive.actual
SHAPE = (1, 6, 8400)
RAW_BYTES = 1 * 6 * 8400 * 4
CONFIDENCE, IOU, MAX_DETECTIONS = .10, .70, 300
CONTROLLED = "CONTROLLED_MODEL_FORMAT_NMS"
ACTUAL = "ACTUAL_IMAGE_NMS"
PURPOSE = "EXP02_NMS_CONTRACT"


def validate_raw(raw, np):
    if raw.shape != SHAPE or raw.dtype != np.float32 or not np.isfinite(raw).all():
        raise ValueError("NMS source must be finite normalized FLOAT32 [1,6,8400]")
    if not ((raw[:, 4:6] >= 0).all() and (raw[:, 4:6] <= 1).all()):
        raise ValueError("Class probabilities must be in [0,1]")


def load_source_box_iou(source_root: Path, wheel: Path, torch):
    """Original pinned IoU measurement only; does not implement/replace NMS."""
    relative = "ultralytics/utils/metrics.py"
    source = (source_root / relative).read_bytes()
    with zipfile.ZipFile(wheel) as archive:
        if source != archive.read(relative):
            raise ValueError("Pinned original IoU source differs from the verified wheel")
    parsed = ast.parse(source.decode("utf-8"))
    function = next(node for node in parsed.body if isinstance(node, ast.FunctionDef) and node.name == "box_iou")
    tree = ast.Module(body=[ast.ImportFrom(module="__future__", names=[ast.alias(name="annotations")], level=0), function], type_ignores=[])
    ast.fix_missing_locations(tree)
    namespace = {"torch": torch}
    exec(compile(tree, str(source_root / relative), "exec"), namespace)
    return namespace["box_iou"], relative, hashlib.sha256(source).hexdigest()


def controlled_cases(np):
    """Only input construction, not a decoder/NMS or an actual model result."""
    def make(identifier, entries, description, expected_count, *, pair_iou_target=None):
        raw = np.zeros(SHAPE, dtype=np.float32)
        for index, values in entries:
            raw[0, :, index] = values
        return dict(id=identifier, evidenceType=CONTROLLED, raw=raw, width=640, height=640,
                    description=description, expectedCountAssertion=expected_count,
                    targetPairIoU=pair_iou_target, sourceModelInferenceExecuted=False)

    common = [.5, .5, .4, .4]
    cases = [make("same-class-overlap", [(0, [*common, .95, .01]),
                  (17, [.505, .5, .4, .4, .85, .01]), (21, [.51, .5, .4, .4, .75, .01])],
                  "Three overlapping SQUAT boxes; retain highest confidence, suppress the other two.", 1)]
    # For equal squares shifted horizontally: IoU=(width-shift)/(width+shift).
    # 0.0001 either side is far above float32 rounding, while close enough to
    # establish the UNCHANGED .70 decision boundary without broadening tolerance.
    for identifier, target, expected in (("iou-below-threshold", .6999, 2), ("iou-above-threshold", .7001, 1)):
        shift = .4 * (1 - target) / (1 + target)
        cases.append(make(identifier, [(0, [*common, .95, .01]),
                     (1, [.5 + shift, .5, .4, .4, .85, .01])],
                     "Same-class pair around unchanged IoU=.70; actual float32 pair IoU is reported.", expected,
                     pair_iou_target=target))
    cases.append(make("different-class-overlap", [(0, [*common, .95, .01]), (1, [*common, .01, .92])],
                      "Identical boxes of SQUAT and SHOULDER_PRESS both survive class-aware NMS.", 2))
    cases.append(make("descending-order", [(7, [.15, .15, .1, .1, .40, .01]),
                      (22, [.8, .8, .1, .1, .01, .97]), (2, [.2, .8, .1, .1, .89, .01]),
                      (0, [.8, .2, .1, .1, .01, .55])],
                      "Disjoint boxes and unique scores; final ordering must descend by confidence, not anchor/class.", 4))
    threshold = np.float32(CONFIDENCE)
    cases.append(make("strict-confidence-boundary", [(0, [.15, .15, .1, .1, np.nextafter(threshold, np.float32(0)), 0]),
                      (1, [.5, .5, .1, .1, threshold, 0]),
                      (2, [.8, .8, .1, .1, np.nextafter(threshold, np.float32(1)), 0])],
                      "Float32 values immediately below/equal/above .10; strict > retains only above.", 1))
    return cases


def actual_cases(path: Path, repository: Path, np):
    path = positive.require_private_path(path, repository)
    if not path.is_file() or not 0 < path.stat().st_size <= 2_097_152:
        raise ValueError("Actual source reference must be a bounded private JSON file")
    document = json.loads(path.read_text(encoding="utf-8-sig"))
    if (document.get("schemaVersion") != 1 or document.get("validationPurpose") != "POSITIVE_CANDIDATE"
            or document.get("modelInferenceExecuted") is not True or document.get("exportContractVerified") is not False):
        raise ValueError("Actual raw source must explicitly originate from actual native positive-reference inference")
    if document.get("model") != dict(id="yolo26n_exp02_fp32", filename=actual.MODEL_NAME, bytes=actual.MODEL_BYTES, sha256=actual.MODEL_SHA256):
        raise ValueError("Actual raw model identity mismatch")
    if (document.get("ultralyticsVersion") != "8.4.176" or document.get("wheelSha256") != actual.load_pinned_loader(repository).WHEEL_SHA256
            or document.get("thresholds") != dict(confidence=CONFIDENCE, iou=IOU, maxDetections=MAX_DETECTIONS)
            or document.get("tensorMetadata", {}).get("output") != dict(shape=list(SHAPE), dtype="FLOAT32")):
        raise ValueError("Actual source contract/thresholds/tensor metadata mismatch")
    samples = document.get("samples")
    if not isinstance(samples, list) or not 1 <= len(samples) <= 8:
        raise ValueError("Actual source must contain 1..8 samples")
    cases, identifiers = [], set()
    for sample in samples:
        identifier = sample.get("id", "")
        if not isinstance(identifier, str) or not re.fullmatch(r"[a-z0-9][a-z0-9_-]{0,63}", identifier) or identifier in identifiers:
            raise ValueError("Invalid or duplicate actual sample id")
        identifiers.add(identifier)
        name = sample.get("rawOutputFile")
        if (name != f"{identifier}.output.f32" or sample.get("rawOutputByteOrder") != "LITTLE_ENDIAN"
                or sample.get("rawOutputProvenance") != "ACTUAL_NATIVE_NORMALIZED_GET_TENSOR_COPY_AFTER_REFERENCE_FORWARD"):
            raise ValueError("Missing direct normalized raw output; never reconstruct it from forward's denormalized result")
        raw_path = positive.require_private_path(path.parent / name, repository)
        payload = raw_path.read_bytes()
        if len(payload) != RAW_BYTES or actual.sha256(payload) != sample.get("rawOutputSha256"):
            raise ValueError("Actual raw output bytes/SHA256 mismatch")
        raw = np.frombuffer(payload, dtype="<f4").reshape(SHAPE).copy()
        validate_raw(raw, np)
        actual.validate_input_dimensions(sample.get("width"), sample.get("height"))
        image_hash = sample.get("sourceImageSha256", "")
        if not isinstance(image_hash, str) or not re.fullmatch(r"[a-f0-9]{64}", image_hash):
            raise ValueError("Actual raw must retain source-image SHA256, not an image upload")
        cases.append(dict(id=f"actual-{identifier}", evidenceType=ACTUAL, raw=raw,
                          width=sample["width"], height=sample["height"], sourceImageSha256=image_hash,
                          sourceReferenceSha256=actual.sha256(path.read_bytes()),
                          sourceSampleId=identifier, sourceModelInferenceExecuted=True,
                          rawOutputProvenance=sample["rawOutputProvenance"],
                          description="Direct actual normalized Interpreter.get_tensor output; no raw-score/box changes."))
    return cases


def evaluate_case(case, reference, loader, np, torch, source_box_iou=None):
    """Run original pinned backend and NMS, reporting rather than implementing their decisions."""
    raw = case["raw"]
    validate_raw(raw, np)
    interpreter = loader.SyntheticInterpreter(raw)
    backend = SimpleNamespace(nhwc=False, task="detect", end2end=False, head="Detect",
                              names={0: "SQUAT", 1: "SHOULDER_PRESS"}, interpreter=interpreter,
                              input_details=[dict(index=0, dtype=np.float32)], output_details=[dict(index=1, dtype=np.float32)])
    prediction = torch.from_numpy(reference["forward"](backend, torch.zeros((1, 3, 640, 640), dtype=torch.float32))[0])
    # SyntheticInterpreter supplies already captured/model-format raw output; this
    # forward call is NOT an Interpreter/native/model invocation.
    processed, retained_indices = reference["non_max_suppression"](
        prediction, conf_thres=CONFIDENCE, iou_thres=IOU, nc=2, multi_label=False,
        agnostic=False, max_det=MAX_DETECTIONS, rotated=False, end2end=False, return_idxs=True)
    rows = processed[0]
    retained = [int(index) for index in retained_indices[0]]
    if len(rows):
        reference["scale_boxes"]((640, 640), rows[:, :4], (case["height"], case["width"]))
    detections, clipped = actual.post_nms_detections(rows, case["width"], case["height"])
    if "expectedCountAssertion" in case and len(detections) != case["expectedCountAssertion"]:
        raise ValueError(f"Pinned NMS did not meet controlled boundary expectation for {case['id']}; investigate, never tune thresholds")
    # Input diagnostics only. All NMS decisions above came from the pinned source.
    nonzero = np.flatnonzero(np.max(raw[0, 4:6], axis=0) > 0)
    above = np.flatnonzero(np.max(raw[0, 4:6], axis=0) > np.float32(CONFIDENCE))
    reported = nonzero if case["evidenceType"] == CONTROLLED else above
    columns = [dict(index=int(index), values=[float(value) for value in raw[0, :, index]]) for index in reported[:128]]
    details = {key: value for key, value in case.items() if key != "raw"}
    details.update(inputNonzeroClassScoreCount=len(nonzero), inputAboveConfidenceCount=len(above),
                   inputNormalizedColumns=columns, inputColumnsTruncated=len(reported) > 128,
                   retainedAnchorIndicesBeforeClip=retained, expected=detections,
                   nmsOutputCount=len(rows), droppedFullyClippedCount=clipped,
                   classOrdering=[detection["classId"] for detection in detections])
    witnesses = []
    witness_count = 0
    if source_box_iou is not None and retained and len(above) > len(retained):
        rejected = [int(index) for index in above if int(index) not in set(retained)]
        if rejected:
            boxes = reference["xywh2xyxy"](prediction[0, :4].transpose(0, 1))
            # Only a diagnostic matrix using original box_iou, eps=0 to match the
            # original TorchNMS.nms denominator. No greedy/suppression algorithm.
            overlaps = source_box_iou(boxes[retained], boxes[rejected], eps=0)
            scores, classes = prediction[0, 4:6].max(dim=0)
            eligible = ((overlaps > IOU) & (classes[retained, None] == classes[rejected][None, :]) &
                        (scores[retained, None] >= scores[rejected][None, :]))
            witness_count = int(eligible.any(dim=0).sum())
            for kept_position, rejected_position in eligible.nonzero()[:16]:
                kept_index, rejected_index = retained[int(kept_position)], rejected[int(rejected_position)]
                witnesses.append(dict(keptAnchor=kept_index, suppressedAnchor=rejected_index,
                    classId=int(classes[kept_index]), keptConfidence=float(scores[kept_index]),
                    suppressedConfidence=float(scores[rejected_index]),
                    modelBoxIou=float(overlaps[kept_position, rejected_position])))
    details.update(sameClassSuppressionWitnesses=witnesses, suppressedAnchorWitnessCount=witness_count,
                   suppressionWitnessListLimitedTo=16, hasSameClassOverlapSuppressionWitness=bool(witnesses),
                   actualImageOverlapReferenceStatus=("OBSERVED_REFERENCE_SUPPRESSION" if
                       case["evidenceType"] == ACTUAL and len(above) > len(rows) and witnesses else
                       "NOT_OBSERVED" if case["evidenceType"] == ACTUAL else "NOT_APPLICABLE_CONTROLLED"))
    if case.get("targetPairIoU") is not None:
        boxes = reference["xywh2xyxy"](prediction[0, :4, :2].transpose(0, 1))
        # Measurement of the two input boxes, not an independent NMS algorithm.
        a, b = boxes[0], boxes[1]
        intersection = (torch.minimum(a[2:], b[2:]) - torch.maximum(a[:2], b[:2])).clamp(min=0).prod()
        union = (a[2:] - a[:2]).prod() + (b[2:] - b[:2]).prod() - intersection
        measured = float(intersection / union)
        if not (5e-5 <= abs(measured - IOU) <= 1.5e-4 and (measured < IOU) == (case["targetPairIoU"] < IOU)):
            raise ValueError("Float32 pair IoU does not safely straddle unchanged .70 boundary")
        details["measuredPairIoU"] = measured
    return details


def generate(args, repository: Path):
    output = positive.require_private_path(args.output, repository, directory=True)
    positive.claim_output_directory(output)
    import numpy as np
    import torch
    torch.set_num_threads(1)
    loader = actual.load_pinned_loader(repository)
    reference, hashes = loader.load_reference(args.source_root, args.wheel)
    source_box_iou, iou_relative, iou_hash = load_source_box_iou(args.source_root, args.wheel, torch)
    hashes[iou_relative] = iou_hash
    cases = controlled_cases(np)
    if args.actual_reference is not None:
        cases.extend(actual_cases(args.actual_reference, repository, np))
    output.mkdir(parents=True, exist_ok=True)
    results = []
    with tempfile.TemporaryDirectory(prefix=".nms-stage-", dir=output) as temporary:
        stage = Path(temporary).resolve()
        for case in cases:
            result = evaluate_case(case, reference, loader, np, torch, source_box_iou)
            name = f"{case['id']}.output.f32"
            payload = np.ascontiguousarray(case["raw"], dtype="<f4").tobytes()
            actual.private_child(stage, name).write_bytes(payload)
            result.update(rawOutputFile=name, rawOutputSha256=actual.sha256(payload), rawOutputByteOrder="LITTLE_ENDIAN")
            results.append(result)
        document = dict(schemaVersion=1, validationPurpose=PURPOSE, ultralyticsVersion=loader.VERSION,
                        wheelSha256=loader.WHEEL_SHA256, sourceSha256=hashes,
                        normalizedOutput=dict(shape=list(SHAPE), dtype="FLOAT32", byteOrder="LITTLE_ENDIAN"),
                        modelSha256=actual.MODEL_SHA256, productionContract="NOT_VERIFIED",
                        contractPromotionPerformed=False, predictionUsedForSession=False,
                        thresholds=dict(confidence=CONFIDENCE, iou=IOU, maxDetections=MAX_DETECTIONS),
                        comparison=dict(classMatch="EXACT", ordering="EXACT_ORDER_EXCEPT_EXACT_SCORE_TIES_IN_BOTH_OUTPUTS",
                                        confidenceTolerance=1e-6, bboxTolerancePixels=1e-4,
                                        rationale="Identical frozen float32 raw inputs, no image/interpolation/native-runtime variation; retain existing synthetic-golden limits."),
                        execution="Pinned LiteRTBackend.forward with recorded/synthetic raw, then original non_max_suppression; no model load or native inference in this tool.",
                        pythonVersion=sys.version.split()[0], modelInferenceExecutedByThisTool=False,
                        androidNativeInferenceExecutedByThisTool=False, cases=results)
        for case in results:
            name = case["rawOutputFile"]
            actual.private_child(stage, name).replace(actual.private_child(output, name))
        pending = actual.private_child(output, "reference.json.pending")
        pending.write_text(json.dumps(document, indent=2, allow_nan=False) + "\n", encoding="utf-8")
        pending.replace(actual.private_child(output, "reference.json"))
    print(f"NMS reference RUN: {len(results)} cases ({sum(case['evidenceType'] == CONTROLLED for case in results)} controlled, {sum(case['evidenceType'] == ACTUAL for case in results)} captured actual outputs).")
    print("Android decoder/NMS comparison pending; no model inference or VERIFIED promotion performed by this tool.")


def main():
    repository = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=repository / "local_validation/exp02_positive/nms_20261017_01/bundle")
    parser.add_argument("--actual-reference", type=Path)
    parser.add_argument("--source-root", type=Path, default=repository / "build/yolo-contract-reference/extracted")
    parser.add_argument("--wheel", type=Path, default=repository / "build/yolo-contract-reference/ultralytics-8.4.176-py3-none-any.whl")
    args = parser.parse_args()
    try:
        generate(args, repository)
    except (ValueError, RuntimeError, OSError, ImportError) as error:
        parser.exit(2, f"NMS reference NOT_RUN/INCOMPLETE: {error}\n")


if __name__ == "__main__":
    main()
