"""Policy/format unit tests only. NO images, real inference or Golden PASS evidence is generated."""
import copy
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


actual = load("exp04_unit_generator", "generate-exp04-actual-golden.py")
transfer = load("exp04_unit_transfer", "transfer-exp04-actual-golden.py")
ROOT = Path(__file__).resolve().parents[1]
LOCAL = ROOT / "local_validation" / "exp04_golden"


class Exp04ToolPolicyTest(unittest.TestCase):
    def manifest(self):
        document = json.loads((ROOT / "docs/ml/exp04-actual-golden.manifest.example.json").read_text(encoding="utf-8"))
        for sample in document["samples"]:
            sample.update(rightsCleared=True, rightsBasis="Unit-test policy metadata ONLY, no real image")
        return document

    def read(self, document):
        # Only the fresh owned metadata directory is removed by TemporaryDirectory, never user fixtures.
        with tempfile.TemporaryDirectory(prefix="policy-test-", dir=LOCAL) as folder:
            path = Path(folder) / "manifest.json"
            path.write_text(json.dumps(document), encoding="utf-8")
            return actual.read_manifest(path, ROOT)

    def test_profile_source_and_unchanged_thresholds(self):
        self.assertEqual(actual.MODEL_BYTES, 9898949)
        self.assertEqual(actual.VERSION, "8.4.202")
        self.assertEqual(len(actual.CLASSES), 7)
        self.assertEqual(actual.THRESHOLDS, dict(confidence=.10, iou=.70, maxDetections=300))
        self.assertEqual(actual.TOLERANCES, dict(confidence=.001, bboxPixels=2., detectionCount=0, classMatch="EXACT"))

    def test_manifest_seven_coverage_and_structured_paths(self):
        self.assertEqual(len(self.read(self.manifest())), 7)

    def test_template_never_grants_rights(self):
        document = json.loads((ROOT / "docs/ml/exp04-actual-golden.manifest.example.json").read_text(encoding="utf-8"))
        self.assertTrue(all(s["rightsCleared"] is False for s in document["samples"]))
        with self.assertRaisesRegex(ValueError, "Rights"): self.read(document)

    def test_rights_and_directions_explicit(self):
        for key, value in (("rightsCleared", False), ("rightsCleared", 1), ("rightsBasis", ""), ("cameraView", None)):
            document = self.manifest(); document["samples"][0][key] = value
            with self.subTest(key=key, value=value), self.assertRaises(ValueError): self.read(document)

    def test_paths_cannot_cross_class_folder_or_use_urls(self):
        for image in ("../a.jpg", "https://example.com/a.jpg", "inputs/0_SQUAT/../../a.jpg", "inputs/0_SQUAT/a/evil.jpg", "inputs/1_SHOULDER_PRESS/a.jpg", "inputs\\0_SQUAT\\a.jpg"):
            document = self.manifest(); document["samples"][0]["image"] = image
            with self.subTest(image=image), self.assertRaises(ValueError): self.read(document)

    def test_missing_class_duplicate_id_and_nonobject_rejected(self):
        document = self.manifest(); document["samples"][6] = copy.deepcopy(document["samples"][0]); document["samples"][6]["id"] = "second_squat"
        with self.assertRaises(ValueError): self.read(document)
        document = self.manifest(); document["samples"][1]["id"] = document["samples"][0]["id"]
        with self.assertRaises(ValueError): self.read(document)
        document = self.manifest(); document["samples"][0] = None
        with self.assertRaises(ValueError): self.read(document)

    def test_private_path_rejects_outside_and_tracked(self):
        for name in ("docs/ml/reference.json", "local_validation/exp03_golden/reference.json", "app/src/main/assets/output.json"):
            with self.subTest(name=name), self.assertRaises(ValueError): actual.private_path(ROOT / name, ROOT)
        answers = [mock.Mock(returncode=0, stdout=b""), mock.Mock(returncode=0, stdout=b"tracked\0")]
        with mock.patch.object(actual.subprocess, "run", side_effect=answers), self.assertRaises(ValueError):
            actual.private_path(LOCAL / "tracked.json", ROOT)

    def test_links_rejected_before_git(self):
        path = LOCAL / "never-created-link"
        with mock.patch.object(Path, "is_symlink", lambda p: p == path), mock.patch.object(actual.subprocess, "run") as git:
            with self.assertRaises(ValueError): actual.private_path(path, ROOT)
            git.assert_not_called()

    def test_seven_classes_wrong_labels_and_empty_are_not_filtered(self):
        for cls in range(7):
            result = actual.post_nms_detections([[1., 2., 10., 20., .8, float(cls)]], 100, 100)
            self.assertEqual(result[0]["classId"], cls)
        # The API intentionally accepts no expected label, so it cannot replace/filter predictions.
        self.assertEqual(actual.post_nms_detections([], 100, 100), [])
        self.assertEqual(actual.post_nms_detections([[0., 0., 0., 5., .8, 6.]], 100, 100), [])

    def test_invalid_detections_fail_not_disappear(self):
        for index, value in ((4, float("nan")), (4, float("inf")), (4, 1.1), (5, 7.), (5, .5), (0, -1.)):
            row = [1., 2., 10., 20., .8, 0.]; row[index] = value
            with self.subTest(index=index, value=value), self.assertRaises(ValueError): actual.post_nms_detections([row], 100, 100)

    def test_dimension_bounds_and_bool(self):
        for dimensions in ((True, 1), (8193, 1), (8192, 8192), (0, 640)):
            with self.subTest(dimensions=dimensions), self.assertRaises(ValueError): actual.validate_dimensions(*dimensions)

    def test_immutable_previous_output(self):
        with tempfile.TemporaryDirectory(prefix="existing-run-", dir=LOCAL) as folder:
            args = mock.Mock(manifest=LOCAL / "manifest.json", output=Path(folder))
            with mock.patch.object(actual, "read_manifest", return_value=[]), mock.patch.object(actual, "load_reference_helpers") as native:
                with self.assertRaisesRegex(ValueError, "never overwrite"): actual.generate(args, ROOT)
                native.assert_not_called()

    def test_remote_fixed_private_sandbox_and_safe_ids(self):
        self.assertEqual(transfer.PACKAGE, "com.example.arptapp.exp04")
        self.assertEqual(transfer.OUTPUT_BYTES, 369600)
        self.assertEqual(transfer.run_path("test_01"), "no_backup/yolo/exp04-actual-golden/runs/test_01")
        for name in (None, "../x", "/data/a", "a b", "a;rm", ""):
            with self.subTest(name=name), self.assertRaises(ValueError): transfer.run_path(name)

    def test_transport_default_unchanged_and_exp04_explicit(self):
        with tempfile.TemporaryDirectory(prefix="transport-policy-", dir=LOCAL) as folder:
            executable = Path(folder) / "adb.exe"; executable.touch()  # Never execute this placeholder.
            self.assertEqual(transfer.transport.PrivateAdb(executable, "test").package, "com.example.arptapp")
            self.assertEqual(transfer.transport.PrivateAdb(executable, "test", package=transfer.PACKAGE).package, transfer.PACKAGE)
            with self.assertRaises(ValueError): transfer.transport.PrivateAdb(executable, "test", package="other.app")

    def test_no_actual_reference_no_bundle_no_device_operation(self):
        with tempfile.TemporaryDirectory(prefix="empty-bundle-", dir=LOCAL) as folder:
            with self.assertRaisesRegex(ValueError, "NOT_RUN"): transfer.validate_bundle(Path(folder), ROOT)

if __name__ == "__main__": unittest.main()
