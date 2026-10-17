"""Synthetic result-schema guards only, not actual positive equivalence evidence."""
import copy
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("positive_summary", Path(__file__).with_name("summarize-exp02-positive-comparison.py"))
summary = importlib.util.module_from_spec(spec)
spec.loader.exec_module(summary)


class PositiveComparisonTests(unittest.TestCase):
    def evidence(self, positive=True):
        detections = [dict(classId=1, className="SHOULDER_PRESS", confidence=.9, bbox=dict(x1=1., y1=2., x2=20., y2=30.))] if positive else []
        native = [dict(classId=1, confidence=.9, box=dict(left=1., top=2., right=20., bottom=30.))] if positive else []
        reference = dict(schemaVersion=1, validationPurpose="POSITIVE_CANDIDATE", modelInferenceExecuted=True, exportContractVerified=False,
            model=dict(sha256=summary.transfer.positive.actual.MODEL_SHA256),
            thresholds=dict(confidence=.10, iou=.70, maxDetections=300),
            tolerances=dict(confidence=.001, bboxPixels=2., detectionCount=0, classMatch="EXACT"),
            samples=[dict(id="candidate", exercise="SQUAT", rgbSha256="a" * 64, sourceImageSha256="b" * 64, detections=detections)])
        android = dict(schemaVersion=1, validationPurpose="POSITIVE_CANDIDATE", productionContract="NOT_VERIFIED",
            modelSha256=summary.transfer.positive.actual.MODEL_SHA256, contractPromotionPerformed=False, predictionUsedForSession=False,
            androidNativeInferenceExecuted=True, nativeInferenceCount=1, allSamplesPassed=positive, positiveEquivalentPairs=int(positive),
            samples=[dict(id="candidate", rgbSha256="a" * 64, sourceImageSha256="b" * 64, detections=native, passed=True,
                comparison=dict(passed=True, matches=[dict(referenceIndex=0, androidIndex=0, confidenceError=0., maximumBoxErrorPixels=0.)] if positive else [],
                    ordering=dict(status="ORDER_MATCH")),
                preprocessingComparison=dict(passed=True, comparedFloatCount=1_228_800,
                    exactNormalizedTolerance=1e-6, resampledNormalizedTolerance=1 / 255 + 1e-6))])
        return reference, android

    def test_positive_equivalence_and_wrong_semantics_are_separate(self):
        result = summary.summarize(*self.evidence())
        self.assertEqual("PASS", result["positiveEquivalenceStatus"])
        self.assertEqual(1, result["positiveEquivalentPairs"])
        self.assertFalse(result["samples"][0]["semanticCorrectness"]["pythonTopMatchesExpected"])
        self.assertFalse(result["samples"][0]["semanticCorrectness"]["androidTopMatchesExpected"])
        self.assertFalse(result["exportContractVerified"])
        self.assertEqual("NOT_VERIFIED", result["productionContract"])

    def test_negative_only_can_never_be_positive_pass(self):
        result = summary.summarize(*self.evidence(False))
        self.assertEqual("NOT_VERIFIED", result["positiveEquivalenceStatus"])
        self.assertEqual(0, result["positiveEquivalentPairs"])
        self.assertEqual("PASS", result["samples"][0]["runtimeEquivalence"])

    def test_no_declared_label_means_unevaluated_semantics(self):
        reference, android = self.evidence()
        reference["samples"][0]["exercise"] = None
        semantic = summary.summarize(reference, android)["samples"][0]["semanticCorrectness"]
        self.assertFalse(semantic["evaluated"])
        self.assertIsNone(semantic["pythonTopMatchesExpected"])
        self.assertIsNone(semantic["androidTopMatchesExpected"])

    def test_missing_sample_and_duplicate_coverage_fail_closed(self):
        for mutate in (lambda doc: doc.update(samples=[]), lambda doc: doc["samples"].append(copy.deepcopy(doc["samples"][0]))):
            reference, android = self.evidence()
            mutate(android)
            with self.assertRaises(ValueError):
                summary.summarize(reference, android)

    def test_source_png_and_decoded_rgb_hashes_must_match(self):
        for field in ("rgbSha256", "sourceImageSha256"):
            reference, android = self.evidence()
            android["samples"][0][field] = "0" * 64
            with self.subTest(field=field), self.assertRaises(ValueError):
                summary.summarize(reference, android)

    def test_contract_bypass_promotion_or_threshold_widening_rejected(self):
        for key, value in (("contractPromotionPerformed", True), ("predictionUsedForSession", True), ("productionContract", "FORGED")):
            reference, android = self.evidence()
            android[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                summary.summarize(reference, android)
        for key, value in (("confidence", .01), ("bboxPixels", 20.)):
            reference, android = self.evidence()
            reference["tolerances"][key] = value
            with self.subTest(tolerance=key), self.assertRaises(ValueError):
                summary.summarize(reference, android)

    def test_approved_verified_snapshot_remains_verified_without_tool_promotion(self):
        reference, android = self.evidence()
        android["productionContract"] = "VERIFIED"
        result = summary.summarize(reference, android)
        self.assertEqual("VERIFIED", result["productionContract"])
        self.assertFalse(result["contractPromotionPerformed"])
        self.assertFalse(result["exportContractVerified"])

    def test_count_class_confidence_bbox_order_or_preprocess_failure_prevents_pass(self):
        changes = [lambda doc: doc["samples"][0]["detections"].clear(),
            lambda doc: doc["samples"][0]["detections"][0].update(classId=0),
            lambda doc: doc["samples"][0]["comparison"]["matches"][0].update(confidenceError=.002),
            lambda doc: doc["samples"][0]["comparison"]["matches"][0].update(maximumBoxErrorPixels=2.1),
            lambda doc: doc["samples"][0]["comparison"]["ordering"].update(status="NON_TIE_ORDER_MISMATCH"),
            lambda doc: doc["samples"][0]["preprocessingComparison"].update(passed=False),
            lambda doc: doc["samples"][0]["preprocessingComparison"].update(resampledNormalizedTolerance=.1)]
        for change in changes:
            reference, android = self.evidence()
            change(android)
            try:
                result = summary.summarize(reference, android)
                self.assertEqual("NOT_VERIFIED", result["positiveEquivalenceStatus"])
            except ValueError:
                pass # Structurally inconsistent evidence fails before producing a summary.

    def test_match_coverage_and_native_execution_required(self):
        reference, android = self.evidence()
        android["samples"][0]["comparison"]["matches"] = []
        self.assertEqual("NOT_VERIFIED", summary.summarize(reference, android)["positiveEquivalenceStatus"])
        reference, android = self.evidence()
        android["nativeInferenceCount"] = 0
        self.assertEqual("NOT_VERIFIED", summary.summarize(reference, android)["positiveEquivalenceStatus"])

    def test_nonfinite_or_negative_metrics_rejected(self):
        for metric in (float("nan"), float("inf"), -1.):
            reference, android = self.evidence()
            android["samples"][0]["comparison"]["matches"][0]["confidenceError"] = metric
            with self.subTest(metric=metric), self.assertRaises(ValueError):
                summary.summarize(reference, android)

    def test_invalid_model_provenance_rejected(self):
        reference, android = self.evidence()
        android["modelSha256"] = "0" * 64
        with self.assertRaises(ValueError):
            summary.summarize(reference, android)


if __name__ == "__main__":
    unittest.main()
