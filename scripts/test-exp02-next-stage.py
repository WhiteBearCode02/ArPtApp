"""Offline next-stage safety tests; never invoke a model or access a phone/private image."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


positive = load("next_positive", "generate-exp02-positive-reference.py")
transfer = load("next_transfer", "transfer-exp02-positive.py")
summary = load("next_summary", "summarize-exp02-positive-comparison.py")
fixtures = load("next_summary_tests", "test-exp02-positive-comparison.py")


class NextStageTests(unittest.TestCase):
    def test_generator_requires_a_new_empty_output(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            positive.require_empty_output(directory)
            saved = directory / "reference.json"
            saved.write_text("old evidence", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "new empty"):
                positive.require_empty_output(directory)
            self.assertEqual("old evidence", saved.read_text(encoding="utf-8"))

    def test_output_file_is_not_treated_as_empty_directory(self):
        with tempfile.TemporaryDirectory() as temporary:
            file = Path(temporary) / "output"
            file.touch()
            with self.assertRaises(ValueError):
                positive.require_empty_output(file)

    def test_a_claimed_or_partial_run_is_never_reused(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary) / "new-run"
            positive.claim_output_directory(directory)
            with self.assertRaises(ValueError):
                positive.claim_output_directory(directory)
            self.assertTrue((directory / ".validation-run.claim").is_file())

    def test_unique_run_paths_do_not_replace_legacy_directory(self):
        self.assertEqual(transfer.REMOTE_BUNDLE, transfer.run_directory(transfer.REMOTE_BUNDLE, None))
        self.assertEqual(transfer.REMOTE_BUNDLE + "/runs/shoulder_01", transfer.run_directory(transfer.REMOTE_BUNDLE, "shoulder_01"))
        for invalid in ("../escape", "", "a/b", "UPPER", "x;id", "a" * 65):
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                transfer.run_directory(transfer.REMOTE_BUNDLE, invalid)

    def test_private_remote_existing_evidence_is_not_deleted(self):
        adb = mock.Mock()
        remote = transfer.run_directory(transfer.REMOTE_BUNDLE, "new_01")
        root = "/data/user/0/com.example.arptapp"
        adb.verify_debug_device.return_value = root
        adb.call.side_effect = [b"", (root + "/" + remote).encode(), b"reference.json\n"]
        with self.assertRaisesRegex(ValueError, "already contains evidence"):
            transfer.create_empty_remote_run(adb, remote)
        self.assertFalse(any("rm" in call.args or "mv" in call.args for call in adb.call.call_args_list))

    def test_remote_symlink_resolution_is_rejected(self):
        adb = mock.Mock()
        adb.verify_debug_device.return_value = "/data/user/0/com.example.arptapp"
        adb.call.side_effect = [b"", b"/data/user/0/com.example.arptapp/elsewhere"]
        with self.assertRaisesRegex(ValueError, "exact path"):
            transfer.create_empty_remote_run(adb, transfer.REMOTE_BUNDLE)

    def test_native_raw_snapshot_uses_get_tensor_without_invoke_or_denormalizing(self):
        import numpy as np
        values = np.zeros((1, 6, 8400), dtype=np.float32)
        values[0, :4, 0] = [.5, .25, .1, .2]
        values[0, 5, 0] = .9
        interpreter = mock.Mock()
        interpreter.get_tensor.return_value = values.copy()
        data = positive.normalized_native_snapshot(interpreter, [{"index": 7}], np)
        self.assertEqual(values.tobytes(), data)
        interpreter.get_tensor.assert_called_once_with(7)
        interpreter.invoke.assert_not_called()
        self.assertEqual(4 * 6 * 8400, len(data))

    def test_raw_snapshot_wrong_shape_dtype_nonfinite_or_scores_fail(self):
        import numpy as np
        values = np.zeros((1, 6, 8400), dtype=np.float32)
        bad_score = values.copy(); bad_score[0, 4, 0] = 1.01
        nonfinite = values.copy(); nonfinite[0, 0, 0] = np.nan
        for invalid in (values[:, :5], values.astype(np.float64), bad_score, nonfinite):
            with self.subTest(shape=invalid.shape), self.assertRaises(ValueError):
                positive.normalized_native_snapshot(mock.Mock(get_tensor=mock.Mock(return_value=invalid)), [{"index": 0}], np)

    def test_required_class_needs_actual_matching_class_coverage(self):
        reference, android = fixtures.PositiveComparisonTests().evidence()
        android.update(requiredPositiveClass="SHOULDER_PRESS", requiredClassPositivePairs=1)
        result = summary.summarize(reference, android)
        self.assertEqual("PASS", result["positiveEquivalenceStatus"])
        android["requiredPositiveClass"] = "SQUAT"
        result = summary.summarize(reference, android)
        self.assertEqual("NOT_VERIFIED", result["positiveEquivalenceStatus"])
        self.assertEqual("PASS", result["samples"][0]["runtimeEquivalence"])

    def test_required_coverage_count_cannot_be_fabricated(self):
        reference, android = fixtures.PositiveComparisonTests().evidence()
        android.update(requiredPositiveClass="SHOULDER_PRESS", requiredClassPositivePairs=9)
        self.assertEqual("NOT_VERIFIED", summary.summarize(reference, android)["positiveEquivalenceStatus"])
        android["requiredPositiveClass"] = "UNKNOWN"
        with self.assertRaises(ValueError):
            summary.summarize(reference, android)

    def nms_fixture(self, bundle):
        payload = bytes(4 * 6 * 8400)  # Synthetic transport fixture only; no actual output claim.
        (bundle / "controlled.output.f32").write_bytes(payload)
        return dict(schemaVersion=1, validationPurpose="EXP02_NMS_CONTRACT",
            modelSha256=transfer.positive.actual.MODEL_SHA256, ultralyticsVersion="8.4.176", wheelSha256="a" * 64,
            productionContract="NOT_VERIFIED", contractPromotionPerformed=False, predictionUsedForSession=False,
            modelInferenceExecutedByThisTool=False,
            normalizedOutput=dict(shape=[1, 6, 8400], dtype="FLOAT32", byteOrder="LITTLE_ENDIAN"),
            thresholds=dict(confidence=.10, iou=.70, maxDetections=300),
            comparison=dict(classMatch="EXACT", ordering="EXACT_ORDER_EXCEPT_EXACT_SCORE_TIES_IN_BOTH_OUTPUTS",
                            confidenceTolerance=1e-6, bboxTolerancePixels=1e-4),
            cases=[dict(id="controlled", evidenceType="CONTROLLED_MODEL_FORMAT_NMS", width=640, height=640,
                        rawOutputFile="controlled.output.f32", rawOutputByteOrder="LITTLE_ENDIAN",
                        rawOutputSha256=transfer.sha256(payload))])

    def test_nms_transfer_bounds_identity_and_no_tolerance_change(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            bundle = root / "local_validation/exp02_positive/test/bundle"
            bundle.mkdir(parents=True)
            source = self.nms_fixture(bundle)
            reference_file = bundle / "reference.json"
            with mock.patch.object(transfer.positive.subprocess, "run", return_value=mock.Mock(returncode=0, stdout=b"")), \
                 mock.patch.object(transfer.positive.actual, "load_pinned_loader", return_value=mock.Mock(WHEEL_SHA256="a" * 64)):
                reference_file.write_text(json.dumps(source), encoding="utf-8")
                uploads = transfer.validate_nms_bundle(bundle, root)
                self.assertEqual(["controlled.output.f32", "reference.json"], [item[0] for item in uploads])
                for mutate in (lambda item: item["thresholds"].update(confidence=.01),
                               lambda item: item["comparison"].update(bboxTolerancePixels=2.),
                               lambda item: item["cases"][0].update(rawOutputFile="../outside"),
                               lambda item: item["cases"][0].update(rawOutputSha256="0" * 64),
                               lambda item: item["cases"][0].update(evidenceType="ACTUAL_IMAGE_NMS")):
                    changed = json.loads(json.dumps(source)); mutate(changed)
                    reference_file.write_text(json.dumps(changed), encoding="utf-8")
                    with self.assertRaises(ValueError):
                        transfer.validate_nms_bundle(bundle, root)


if __name__ == "__main__":
    unittest.main()
