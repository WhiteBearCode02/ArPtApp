"""Offline unit/source-reference tests. No device/model/image access or promotion.

Tiny in-memory tensors are test fixtures ONLY, never actual model evidence. Execute
with the existing local validation venv; no package installation takes place.
"""
from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import numpy as np
import torch


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("exp02_nms_reference_under_test", ROOT / "scripts/generate-exp02-nms-reference.py")
nms = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(nms)


class RawFormatGuards(unittest.TestCase):
    def test_all_controlled_inputs_are_explicitly_synthetic_and_canonical(self):
        cases = nms.controlled_cases(np)
        self.assertEqual(6, len(cases))
        self.assertEqual(6, len({case["id"] for case in cases}))
        for case in cases:
            nms.validate_raw(case["raw"], np)
            self.assertEqual(nms.CONTROLLED, case["evidenceType"])
            self.assertFalse(case["sourceModelInferenceExecuted"])
            self.assertEqual(nms.RAW_BYTES, case["raw"].nbytes)

    def test_strict_threshold_inputs_are_adjacent_float32_values(self):
        raw = next(case for case in nms.controlled_cases(np) if case["id"] == "strict-confidence-boundary")["raw"]
        values = raw[0, 4, :3]
        self.assertEqual(np.float32(.10), values[1])
        self.assertEqual(np.nextafter(values[1], np.float32(0)), values[0])
        self.assertEqual(np.nextafter(values[1], np.float32(1)), values[2])

    def test_dtype_and_shape_are_never_guessed_or_coerced(self):
        for raw in (np.zeros((1, 8400, 6), np.float32), np.zeros(nms.SHAPE, np.float64)):
            with self.subTest(shape=raw.shape, dtype=raw.dtype), self.assertRaises(ValueError):
                nms.validate_raw(raw, np)

    def test_nonfinite_coordinates_and_scores_rejected(self):
        for channel, value in ((0, np.nan), (2, np.inf), (4, np.nan), (5, np.inf)):
            raw = np.zeros(nms.SHAPE, np.float32)
            raw[0, channel, 0] = value
            with self.subTest(channel=channel), self.assertRaises(ValueError):
                nms.validate_raw(raw, np)

    def test_class_probabilities_cannot_be_logits_or_objectness(self):
        for value in (-.01, 1.01):
            raw = np.zeros(nms.SHAPE, np.float32)
            raw[0, 4, 0] = value
            with self.assertRaises(ValueError):
                nms.validate_raw(raw, np)


class PinnedSourceNmsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        torch.set_num_threads(1)
        cls.loader = nms.actual.load_pinned_loader(ROOT)
        cls.reference, _ = cls.loader.load_reference(
            ROOT / "build/yolo-contract-reference/extracted",
            ROOT / "build/yolo-contract-reference/ultralytics-8.4.176-py3-none-any.whl")
        box_iou, _, _ = nms.load_source_box_iou(ROOT / "build/yolo-contract-reference/extracted",
            ROOT / "build/yolo-contract-reference/ultralytics-8.4.176-py3-none-any.whl", torch)
        cls.box_iou = staticmethod(box_iou)
        cls.results = {case["id"]: nms.evaluate_case(case, cls.reference, cls.loader, np, torch, cls.box_iou)
                       for case in nms.controlled_cases(np)}

    def test_same_class_keeps_highest_and_suppresses_two_lower_scores(self):
        case = self.results["same-class-overlap"]
        self.assertEqual([0], case["retainedAnchorIndicesBeforeClip"])
        self.assertEqual(3, case["inputAboveConfidenceCount"])
        self.assertEqual(1, len(case["expected"]))
        self.assertEqual(2, case["suppressedAnchorWitnessCount"])
        self.assertTrue(case["hasSameClassOverlapSuppressionWitness"])

    def test_iou_below_point_seven_keeps_pair(self):
        case = self.results["iou-below-threshold"]
        self.assertLess(case["measuredPairIoU"], .70)
        self.assertLess(abs(case["measuredPairIoU"] - .6999), 1e-6)
        self.assertEqual([0, 1], case["retainedAnchorIndicesBeforeClip"])

    def test_iou_above_point_seven_suppresses_pair(self):
        case = self.results["iou-above-threshold"]
        self.assertGreater(case["measuredPairIoU"], .70)
        self.assertLess(abs(case["measuredPairIoU"] - .7001), 1e-6)
        self.assertEqual([0], case["retainedAnchorIndicesBeforeClip"])

    def test_identical_different_class_boxes_both_survive(self):
        case = self.results["different-class-overlap"]
        self.assertEqual([0, 1], case["classOrdering"])
        self.assertEqual(case["expected"][0]["bbox"], case["expected"][1]["bbox"])
        self.assertFalse(case["hasSameClassOverlapSuppressionWitness"])

    def test_ordering_uses_descending_score_not_anchor_or_class(self):
        case = self.results["descending-order"]
        self.assertEqual([22, 2, 0, 7], case["retainedAnchorIndicesBeforeClip"])
        self.assertEqual([1, 0, 1, 0], case["classOrdering"])
        scores = [item["confidence"] for item in case["expected"]]
        self.assertEqual(sorted(scores, reverse=True), scores)

    def test_strict_point_one_retains_only_next_float_above(self):
        case = self.results["strict-confidence-boundary"]
        self.assertEqual([2], case["retainedAnchorIndicesBeforeClip"])
        self.assertEqual(1, case["inputAboveConfidenceCount"])

    def test_no_controlled_case_is_relabelled_actual(self):
        for case in self.results.values():
            self.assertEqual(nms.CONTROLLED, case["evidenceType"])
            self.assertFalse(case["sourceModelInferenceExecuted"])
            self.assertNotIn("sourceImageSha256", case)

    def test_actual_without_overlap_is_not_reported_as_actual_overlap_pass(self):
        case = next(case for case in nms.controlled_cases(np) if case["id"] == "descending-order")
        case["evidenceType"] = nms.ACTUAL  # isolated test-only status fixture, never published evidence
        result = nms.evaluate_case(case, self.reference, self.loader, np, torch, self.box_iou)
        self.assertEqual("NOT_OBSERVED", result["actualImageOverlapReferenceStatus"])
        self.assertFalse(result["hasSameClassOverlapSuppressionWitness"])

    def test_actual_with_overlap_requires_both_reduction_and_same_class_witness(self):
        case = next(case for case in nms.controlled_cases(np) if case["id"] == "same-class-overlap")
        case["evidenceType"] = nms.ACTUAL  # isolated test-only status fixture, never published evidence
        result = nms.evaluate_case(case, self.reference, self.loader, np, torch, self.box_iou)
        self.assertGreater(result["inputAboveConfidenceCount"], result["nmsOutputCount"])
        self.assertTrue(result["hasSameClassOverlapSuppressionWitness"])
        self.assertEqual("OBSERVED_REFERENCE_SUPPRESSION", result["actualImageOverlapReferenceStatus"])


class ActualRawProvenanceGuards(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="exp02-nms-unit-")
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.raw = np.zeros(nms.SHAPE, np.float32).tobytes()
        self.raw_path = self.directory / "candidate-1.output.f32"
        self.raw_path.write_bytes(self.raw)
        self.path = self.directory / "reference.json"
        self.document = dict(schemaVersion=1, validationPurpose="POSITIVE_CANDIDATE",
            modelInferenceExecuted=True, exportContractVerified=False,
            model=dict(id="yolo26n_exp02_fp32", filename=nms.actual.MODEL_NAME,
                       bytes=nms.actual.MODEL_BYTES, sha256=nms.actual.MODEL_SHA256),
            ultralyticsVersion="8.4.176", wheelSha256="15f4bbac5c69fd9a5a9395f5957a7ce561e3eb4620699a9c2ff11ed38ec7d34c",
            thresholds=dict(confidence=.10, iou=.70, maxDetections=300),
            tensorMetadata=dict(output=dict(shape=[1, 6, 8400], dtype="FLOAT32")),
            samples=[dict(id="candidate-1", width=480, height=640,
                sourceImageSha256="b" * 64, rawOutputFile=self.raw_path.name,
                rawOutputByteOrder="LITTLE_ENDIAN", rawOutputSha256=nms.actual.sha256(self.raw),
                rawOutputProvenance="ACTUAL_NATIVE_NORMALIZED_GET_TENSOR_COPY_AFTER_REFERENCE_FORWARD")])
        self.private = patch.object(nms.positive, "require_private_path", side_effect=lambda path, repository: path.resolve())
        self.private.start()
        self.addCleanup(self.private.stop)

    def read(self):
        # Test-only fixture provenance: this is NEVER a generated/published actual evidence bundle.
        self.path.write_text(json.dumps(self.document), encoding="utf-8")
        return nms.actual_cases(self.path, ROOT, np)

    def test_loader_preserves_raw_bytes_and_provenance_without_decoding_or_nms(self):
        cases = self.read()
        self.assertEqual(1, len(cases))
        self.assertEqual(self.raw, cases[0]["raw"].tobytes())
        self.assertEqual("actual-candidate-1", cases[0]["id"])
        self.assertEqual("b" * 64, cases[0]["sourceImageSha256"])

    def test_missing_raw_is_not_reconstructed_from_forward_result(self):
        self.document["samples"][0].pop("rawOutputFile")
        with self.assertRaises(ValueError): self.read()

    def test_raw_bytes_require_exact_size_and_sha(self):
        self.raw_path.write_bytes(self.raw[:-4])
        with self.assertRaises(ValueError): self.read()
        self.raw_path.write_bytes(self.raw)
        self.document["samples"][0]["rawOutputSha256"] = "0" * 64
        with self.assertRaises(ValueError): self.read()

    def test_inference_provenance_required(self):
        self.document["modelInferenceExecuted"] = False
        with self.assertRaises(ValueError): self.read()

    def test_model_identity_and_thresholds_are_frozen(self):
        self.document["model"]["sha256"] = "0" * 64
        with self.assertRaises(ValueError): self.read()
        self.document["model"]["sha256"] = nms.actual.MODEL_SHA256
        self.document["thresholds"]["confidence"] = .09
        with self.assertRaises(ValueError): self.read()

    def test_tensor_shape_and_byte_order_are_not_inferred(self):
        self.document["tensorMetadata"]["output"]["shape"] = [1, 8400, 6]
        with self.assertRaises(ValueError): self.read()
        self.document["tensorMetadata"]["output"]["shape"] = [1, 6, 8400]
        self.document["samples"][0]["rawOutputByteOrder"] = "NATIVE"
        with self.assertRaises(ValueError): self.read()

    def test_duplicate_ids_and_missing_image_hash_rejected(self):
        self.document["samples"].append(dict(self.document["samples"][0]))
        with self.assertRaises(ValueError): self.read()
        self.document["samples"].pop()
        self.document["samples"][0].pop("sourceImageSha256")
        with self.assertRaises(ValueError): self.read()

    def test_direct_normalized_get_tensor_provenance_required(self):
        self.document["samples"][0]["rawOutputProvenance"] = "RECONSTRUCTED_FROM_DENORMALIZED_FORWARD"
        with self.assertRaises(ValueError): self.read()


class EvidenceRetention(unittest.TestCase):
    def test_existing_reference_or_payload_is_not_overwritten(self):
        with tempfile.TemporaryDirectory(prefix="exp02-nms-retention-") as temporary:
            output = Path(temporary)
            prior = output / "reference.json"
            prior.write_text("prior evidence", encoding="utf-8")
            with patch.object(nms.positive, "require_private_path", return_value=output):
                with self.assertRaises(ValueError):
                    nms.generate(SimpleNamespace(output=output), ROOT)
            self.assertEqual("prior evidence", prior.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main(verbosity=2)
