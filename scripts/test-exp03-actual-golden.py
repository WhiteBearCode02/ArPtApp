"""Offline tool guard regressions, NOT actual-image/native equivalence evidence."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock
from types import SimpleNamespace


def load(name, file):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(file))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


actual = load("exp03_test_generator", "generate-exp03-actual-golden.py")
transfer = load("exp03_test_transfer", "transfer-exp03-actual-golden.py")
ROOT = Path(__file__).resolve().parents[1]


class Exp03ActualGoldenToolsTest(unittest.TestCase):
    def manifest(self):
        return dict(schemaVersion=1, samples=[dict(id=f"{exercise.lower()}_{view.lower()}", exercise=exercise,
            cameraView=view, image="not_a_real_fixture.png", rightsCleared=True, rightsBasis="Unit-test input policy only")
            for exercise, view in sorted(actual.shared.REQUIRED)])

    def read_manifest(self, document):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "manifest.json"
            path.write_text(json.dumps(document), encoding="utf-8")
            return actual.shared.read_manifest(path)

    def test_canonical_exp03_not_exp02(self):
        self.assertEqual(actual.MODEL_BYTES, 9894032)
        self.assertEqual(actual.VERSION, "8.4.202")
        self.assertNotEqual(actual.MODEL_SHA256, actual.shared.MODEL_SHA256)
        self.assertEqual(actual.THRESHOLDS, dict(confidence=.10, iou=.70, maxDetections=300))
        self.assertEqual(actual.TOLERANCES, dict(confidence=.001, bboxPixels=2., detectionCount=0, classMatch="EXACT"))

    def test_manifest_four_explicit_rights_and_directions(self):
        self.assertEqual(len(self.read_manifest(self.manifest())), 4)

    def test_no_rights_inference(self):
        for key, value in (("rightsCleared", False), ("rightsBasis", ""), ("cameraView", None)):
            document = self.manifest()
            document["samples"][0][key] = value
            with self.subTest(key=key), self.assertRaises(ValueError): self.read_manifest(document)

    def test_missing_coverage_rejected(self):
        document = self.manifest()
        document["samples"][0]["cameraView"] = document["samples"][1]["cameraView"]
        with self.assertRaises(ValueError): self.read_manifest(document)

    def test_semantic_mismatch_is_recorded_not_rejected(self):
        observation = actual.shared.semantic_observation("SHOULDER_PRESS", [dict(classId=0)])
        self.assertFalse(observation["semanticMatch"])
        self.assertEqual(observation["classOrdering"], [0])
        self.assertFalse(actual.shared.semantic_observation("SQUAT", [])["semanticMatch"])

    def test_private_paths_reject_public_and_root_directories(self):
        for path in ("docs/ml/reference.json", "build", "local_validation", "local_validation/exp02_golden/out"):
            with self.subTest(path=path), self.assertRaises(ValueError): actual.private_path(ROOT / path, ROOT)

    def test_exp03_private_child_is_allowed(self):
        path = ROOT / "local_validation/exp03_golden/unit-test-run/reference.json"
        self.assertEqual(actual.private_path(path, ROOT), path.resolve())

    def test_symlink_or_junction_rejected_before_git(self):
        path = ROOT / "build/exp03-unit-test-output"
        with mock.patch.object(Path, "is_symlink", lambda value: value == path), mock.patch.object(actual.subprocess, "run") as git:
            with self.assertRaisesRegex(ValueError, "symlink/junction"): actual.private_path(path, ROOT)
            git.assert_not_called()

    def test_tracked_private_output_rejected(self):
        answers = [mock.Mock(returncode=0, stdout=b""), mock.Mock(returncode=0, stdout=b"tracked\0")]
        with mock.patch.object(actual.subprocess, "run", side_effect=answers), self.assertRaises(ValueError):
            actual.private_path(ROOT / "build/exp03-unit-test-output", ROOT)

    def test_wrong_source_wheel_rejected_without_import_or_native_execution(self):
        with tempfile.TemporaryDirectory() as folder:
            wheel = Path(folder) / "ultralytics-8.4.202-py3-none-any.whl"
            wheel.write_bytes(b"not the canonical wheel")
            with self.assertRaisesRegex(ValueError, "EXP03_EXPORT_SOURCE_NOT_REPRODUCED"): actual.load_reference(wheel)

    def test_generator_preserves_existing_output(self):
        with tempfile.TemporaryDirectory(dir=ROOT / "build") as folder:
            args = SimpleNamespace(manifest=Path("unused"), output=Path(folder))
            with mock.patch.object(actual.shared, "read_manifest", return_value=[]), mock.patch.object(actual, "load_reference") as source:
                with self.assertRaisesRegex(ValueError, "Preserve existing evidence"): actual.generate(args, ROOT)
                source.assert_not_called()

    def test_safe_run_id_has_fixed_private_namespace(self):
        self.assertEqual(transfer.run_path("actual_01"), "no_backup/yolo/exp03-actual-golden/runs/actual_01")
        for value in ("../escape", "", "a/b", "a;rm", "C:\\outside", None):
            with self.subTest(value=value), self.assertRaises(ValueError): transfer.run_path(value)

    def test_only_one_authorized_selected_device(self):
        adb = mock.Mock(serial="one", executable="adb.exe")
        for devices in (b"List of devices attached\n", b"List of devices attached\none unauthorized\n",
                        b"List of devices attached\none device\ntwo device\n", b"List of devices attached\ntwo device\n"):
            with self.subTest(devices=devices), mock.patch.object(transfer.subprocess, "run", return_value=mock.Mock(stdout=devices)):
                with self.assertRaises(ValueError): transfer.verify_one_device(adb)
        adb.verify_debug_device.assert_not_called()

    def test_missing_or_exp02_bundle_rejected_without_adb(self):
        with tempfile.TemporaryDirectory(dir=ROOT / "build") as folder:
            bundle = Path(folder)
            (bundle / "reference.json").write_text(json.dumps(dict(schemaVersion=1, model=dict(id="yolo26n_exp02_fp32"))))
            with self.assertRaisesRegex(ValueError, "EXP03 reference"): transfer.validate_bundle(bundle, ROOT)


if __name__ == "__main__":
    unittest.main()
