"""Summarize a private Android positive Golden result without rerunning/overriding it.

The Kotlin comparator remains the executable acceptance implementation. This tool
checks provenance/matching coverage and reports its exact confidence/bbox/order/
preprocessing evidence. A label mismatch never changes runtime equivalence. Empty
outputs never count as positive evidence. Production NOT_VERIFIED is untouched.
"""
from __future__ import annotations

import argparse
import importlib.util
import json
import math
from pathlib import Path
import re
import struct


def load_transfer():
    spec = importlib.util.spec_from_file_location("exp02_positive_transfer_helpers", Path(__file__).with_name("transfer-exp02-positive.py"))
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


transfer = load_transfer()


def summarize(reference: dict, android: dict) -> dict:
    if reference.get("schemaVersion") != 1 or reference.get("validationPurpose") != "POSITIVE_CANDIDATE" or reference.get("modelInferenceExecuted") is not True or reference.get("exportContractVerified") is not False:
        raise ValueError("Expected an actual, unpromoted private Python positive reference")
    if reference.get("model", {}).get("sha256") != transfer.positive.actual.MODEL_SHA256 or android.get("modelSha256") != transfer.positive.actual.MODEL_SHA256:
        raise ValueError("Python/Android model identity mismatch")
    if android.get("schemaVersion") != 1 or android.get("validationPurpose") != "POSITIVE_CANDIDATE" or android.get("productionContract") != "NOT_VERIFIED":
        raise ValueError("Expected an isolated Android positive comparison result")
    if android.get("contractPromotionPerformed") is not False or android.get("predictionUsedForSession") is not False:
        raise ValueError("Validation evidence must not bypass/promote or control a session")
    if reference.get("thresholds") != dict(confidence=.10, iou=.70, maxDetections=300):
        raise ValueError("Reference threshold policy changed")
    tolerances = reference.get("tolerances", {})
    if any(tolerances.get(key) != value for key, value in dict(confidence=.001, bboxPixels=2., detectionCount=0, classMatch="EXACT").items()):
        raise ValueError("Reference tolerances changed")
    references, actuals = reference.get("samples"), android.get("samples")
    if not isinstance(references, list) or not isinstance(actuals, list) or not 1 <= len(references) <= 8:
        raise ValueError("Expected bounded candidate samples")
    reference_ids = [sample.get("id") for sample in references]
    actual_ids = [sample.get("id") for sample in actuals]
    if len(set(reference_ids)) != len(reference_ids) or len(set(actual_ids)) != len(actual_ids) or set(reference_ids) != set(actual_ids):
        raise ValueError("Android/Python sample coverage differs; incomplete evidence cannot pass")
    native_executed = android.get("androidNativeInferenceExecuted") is True and android.get("nativeInferenceCount") == len(references)
    results, positive_pairs = [], 0
    for expected in references:
        actual = next(sample for sample in actuals if sample["id"] == expected["id"])
        if any(not isinstance(expected.get(field), str) or not re.fullmatch(r"[a-f0-9]{64}", expected[field]) for field in ("rgbSha256", "sourceImageSha256")):
            raise ValueError("Missing candidate raster/original SHA256 provenance")
        if actual.get("rgbSha256") != expected.get("rgbSha256") or actual.get("sourceImageSha256") != expected.get("sourceImageSha256"):
            raise ValueError("Python/Android candidate raster or original image identity differs")
        expected_detections, android_detections = expected.get("detections"), actual.get("detections")
        if not isinstance(expected_detections, list) or not isinstance(android_detections, list) or max(len(expected_detections), len(android_detections)) > 300:
            raise ValueError("Invalid bounded final detections")
        comparison = actual.get("comparison", {})
        preprocessing = actual.get("preprocessingComparison", {})
        positive = bool(expected_detections) and bool(android_detections)
        matches = comparison.get("matches", [])
        count_match = len(expected_detections) == len(android_detections)
        if not isinstance(matches, list):
            raise ValueError("Invalid recorded Kotlin match evidence")
        confidence_errors, bbox_errors = [], []
        class_match = True
        for match in matches:
            reference_index, android_index = match.get("referenceIndex"), match.get("androidIndex")
            if isinstance(reference_index, bool) or not isinstance(reference_index, int) or not 0 <= reference_index < len(expected_detections) or isinstance(android_index, bool) or not isinstance(android_index, int) or not 0 <= android_index < len(android_detections):
                raise ValueError("Invalid Kotlin match indices")
            class_match = class_match and expected_detections[reference_index].get("classId") == android_detections[android_index].get("classId")
            for field, errors in (("confidenceError", confidence_errors), ("maximumBoxErrorPixels", bbox_errors)):
                value = match.get(field)
                if not isinstance(value, (int, float)) or isinstance(value, bool) or not math.isfinite(value) or value < 0:
                    raise ValueError("Invalid recorded Kotlin difference metric")
                errors.append(value)
        matched_all = count_match and sorted(match.get("referenceIndex") for match in matches) == list(range(len(expected_detections))) and sorted(match.get("androidIndex") for match in matches) == list(range(len(android_detections)))
        ordering = comparison.get("ordering", {})
        ordering_ok = ordering.get("status") in ("ORDER_MATCH", "EQUIVALENT_EXACT_SCORE_TIE_REORDER")
        prep_ok = preprocessing.get("passed") is True and preprocessing.get("comparedFloatCount") == 3 * 640 * 640 and preprocessing.get("exactNormalizedTolerance") == 1e-6 and math.isclose(preprocessing.get("resampledNormalizedTolerance", -1), 1 / 255 + 1e-6, rel_tol=0, abs_tol=1e-12)
        # Exact representation of the unchanged Kotlin .001f, not a widened tolerance.
        kotlin_confidence_limit = struct.unpack("<f", struct.pack("<f", .001))[0]
        metrics_ok = all(value <= kotlin_confidence_limit for value in confidence_errors) and all(value <= 2. for value in bbox_errors)
        equivalent = native_executed and count_match and class_match and matched_all and ordering_ok and prep_ok and metrics_ok and comparison.get("passed") is True and actual.get("passed") is True
        positive_pairs += int(positive and equivalent)
        label = expected.get("exercise")
        if label is not None and label not in transfer.positive.actual.CLASSES:
            raise ValueError("Unexpected semantic label; do not infer or rewrite it")
        expected_class = transfer.positive.actual.CLASSES.get(label)
        python_top = expected_detections[0].get("classId") if expected_detections else None
        android_top = android_detections[0].get("classId") if android_detections else None
        results.append(dict(id=expected["id"], pythonDetections=expected_detections, androidDetections=android_detections,
            countMatch=count_match, classMatch=class_match, pythonDetectionCount=len(expected_detections), androidDetectionCount=len(android_detections),
            confidenceDifferences=confidence_errors, bboxMaximumPixelDifferences=bbox_errors,
            ordering=ordering, preprocessingComparison=preprocessing,
            runtimeEquivalence="PASS" if equivalent else "FAIL", positiveEvidence=positive,
            semanticCorrectness=dict(expectedExercise=label, evaluated=label is not None,
                pythonTopMatchesExpected=None if label is None else python_top == expected_class,
                androidTopMatchesExpected=None if label is None else android_top == expected_class)))
    positive_pass = native_executed and all(sample["runtimeEquivalence"] == "PASS" for sample in results) and positive_pairs > 0 and android.get("allSamplesPassed") is True and android.get("positiveEquivalentPairs") == positive_pairs
    return dict(schemaVersion=1, validationPurpose="POSITIVE_CANDIDATE_SUMMARY", positiveEquivalentPairs=positive_pairs,
        positiveEquivalenceStatus="PASS" if positive_pass else "NOT_VERIFIED", androidNativeInferenceExecuted=native_executed,
        exportContractVerified=False, productionContract="NOT_VERIFIED", contractPromotionPerformed=False,
        policy="Summarizes recorded Kotlin comparison; no new tolerance, no label gate, no automatic VERIFIED promotion.", samples=results)


def main():
    repository = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", type=Path, required=True)
    parser.add_argument("--android", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=repository / "local_validation/exp02_positive/comparison_summary.json")
    args = parser.parse_args()
    try:
        paths = [transfer.positive.require_private_path(path, repository) for path in (args.reference, args.android, args.output)]
        documents = []
        for path in paths[:2]:
            if not path.is_file() or not 0 < path.stat().st_size <= transfer.MAX_JSON_BYTES:
                raise ValueError("Missing or oversized private JSON evidence")
            documents.append(transfer.json_object(path.read_bytes()))
        result = summarize(*documents)
        paths[2].parent.mkdir(parents=True, exist_ok=True)
        with paths[2].open("x", encoding="utf-8") as stream:
            stream.write(json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False) + "\n")
        print(f"Recorded positive equivalence: {result['positiveEquivalenceStatus']}; production NOT_VERIFIED unchanged.")
    except (ValueError, OSError, TypeError) as error:
        parser.exit(2, f"Positive comparison summary blocked: {error}\n")


if __name__ == "__main__":
    main()
