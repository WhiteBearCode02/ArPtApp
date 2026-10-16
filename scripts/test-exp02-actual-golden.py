"""Pure offline guard tests. These are NOT actual-model or Android Golden execution."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock
from types import SimpleNamespace

spec = importlib.util.spec_from_file_location("actual_golden", Path(__file__).with_name("generate-exp02-actual-golden.py"))
actual = importlib.util.module_from_spec(spec)
spec.loader.exec_module(actual)


class ActualGoldenInputTests(unittest.TestCase):
    def manifest(self):
        return dict(schemaVersion=1, samples=[dict(id=f"{exercise.lower()}_{view.lower()}", exercise=exercise,
                    cameraView=view, image="local.png", rightsCleared=True, rightsBasis="Self-created local test; consent recorded privately")
                    for exercise in actual.CLASSES for view in ("FRONT", "SIDE")])

    def read(self, document):
        with tempfile.TemporaryDirectory() as temporary:
            file = Path(temporary) / "manifest.json"
            file.write_text(json.dumps(document), encoding="utf-8")
            return actual.read_manifest(file)

    def test_four_explicit_rights_cleared_directions(self):
        self.assertEqual(4, len(self.read(self.manifest())))

    def test_missing_rights_is_not_inferred(self):
        document = self.manifest()
        document["samples"][0]["rightsCleared"] = False
        with self.assertRaises(ValueError): self.read(document)

    def test_missing_rights_basis_fails(self):
        document = self.manifest()
        document["samples"][0]["rightsBasis"] = " "
        with self.assertRaises(ValueError): self.read(document)

    def test_missing_front_side_coverage_fails(self):
        document = self.manifest()
        document["samples"][0]["cameraView"] = "SIDE"
        with self.assertRaises(ValueError): self.read(document)

    def test_no_invented_45_degree_enum(self):
        document = self.manifest()
        document["samples"][0]["cameraView"] = "ANGLE_45"
        with self.assertRaises(ValueError): self.read(document)

    def test_unsafe_id_fails(self):
        document = self.manifest()
        document["samples"][0]["id"] = "../outside"
        with self.assertRaises(ValueError): self.read(document)

    def test_duplicate_id_fails(self):
        document = self.manifest()
        document["samples"][0]["id"] = document["samples"][1]["id"]
        with self.assertRaises(ValueError): self.read(document)

    def test_schema_mismatch_fails(self):
        document = self.manifest()
        document["schemaVersion"] = 2
        with self.assertRaises(ValueError): self.read(document)

    def test_real_media_output_must_stay_in_ignored_build(self):
        repository = Path(__file__).resolve().parents[1]
        with self.assertRaises(ValueError): actual.require_private_output(repository / "docs/ml/actual", repository)
        self.assertEqual((repository / "build/actual").resolve(), actual.require_private_output(repository / "build/actual", repository))

    def test_cannot_use_entire_build_root(self):
        repository = Path(__file__).resolve().parents[1]
        with self.assertRaises(ValueError): actual.require_private_output(repository / "build", repository)

    def test_private_local_validation_output_is_supported(self):
        repository = Path(__file__).resolve().parents[1]
        destination = repository / "local_validation/exp02_golden"
        self.assertEqual(destination.resolve(), actual.require_private_output(destination, repository))

    def test_other_local_validation_directory_is_not_allowed(self):
        repository = Path(__file__).resolve().parents[1]
        for relative in ("local_validation", "local_validation/other", "build/../../outside"):
            with self.subTest(relative=relative), self.assertRaises(ValueError):
                actual.require_private_output(repository / relative, repository)

    def test_ignored_but_already_tracked_output_fails(self):
        repository = Path(__file__).resolve().parents[1]
        results = [mock.Mock(returncode=0, stdout=b""), mock.Mock(returncode=0, stdout=b"build/private/reference.json\0")]
        with mock.patch.object(actual.subprocess, "run", side_effect=results), self.assertRaises(ValueError):
            actual.require_private_output(repository / "build/private", repository)

    def test_unignored_output_and_git_check_failure_fail_closed(self):
        repository = Path(__file__).resolve().parents[1]
        for return_code in (1, 128):
            with mock.patch.object(actual.subprocess, "run", return_value=mock.Mock(returncode=return_code, stdout=b"")), self.assertRaises(ValueError):
                actual.require_private_output(repository / "build/private", repository)

    def test_private_child_rejects_path_traversal_and_keeps_safe_basename(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary).resolve()
            self.assertEqual(output / "reference_results.json", actual.private_child(output, "reference_results.json"))
            for filename in ("../image.png", "/outside.json", "sub/image.json", "C:\\outside.json", ""):
                with self.subTest(filename=filename), self.assertRaises(ValueError): actual.private_child(output, filename)

    def test_output_symlink_component_fails_before_git_or_writes(self):
        repository = Path(__file__).resolve().parents[1]
        with tempfile.TemporaryDirectory(dir=repository / "build") as temporary:
            destination = Path(temporary).resolve()
            with mock.patch.object(Path, "is_symlink", lambda value: value == destination), mock.patch.object(actual.subprocess, "run") as git:
                with self.assertRaisesRegex(ValueError, "Symlink/junction"):
                    actual.require_private_output(destination, repository)
                git.assert_not_called()

    def test_generated_child_symlink_fails_before_write(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary).resolve()
            with mock.patch.object(Path, "is_symlink", return_value=True), self.assertRaises(ValueError):
                actual.private_child(output, "reference_results.json")

    def test_wrong_class_is_honest_reference_not_rejected(self):
        detections, clipped = actual.post_nms_detections([[1, 2, 30, 40, .9, 1]], 100, 100)
        observed = actual.semantic_observation("SQUAT", detections)
        self.assertEqual(0, clipped)
        self.assertFalse(observed["semanticMatch"])
        self.assertFalse(observed["hasExpectedDetection"])
        self.assertEqual(1, observed["topPrediction"]["classId"])
        self.assertEqual([1], observed["classOrdering"])
        self.assertNotIn("runtimeEquivalencePassed", observed)

    def test_no_detection_is_preserved_without_fake_positive(self):
        detections, clipped = actual.post_nms_detections([], 100, 100)
        observed = actual.semantic_observation("SQUAT", detections)
        self.assertEqual([], detections)
        self.assertEqual(0, clipped)
        self.assertEqual(0, observed["detectionsAfterNms"])
        self.assertIsNone(observed["topPrediction"])
        self.assertFalse(observed["semanticMatch"])
        self.assertEqual([], observed["classOrdering"])

    def test_semantic_top_one_is_separate_from_any_expected_detection(self):
        detections, _ = actual.post_nms_detections([[1, 2, 30, 40, .9, 1], [50, 60, 90, 99, .8, 0]], 100, 100)
        observed = actual.semantic_observation("SQUAT", detections)
        self.assertFalse(observed["semanticMatch"])
        self.assertTrue(observed["hasExpectedDetection"])
        self.assertEqual([1, 0], observed["classOrdering"])
        self.assertEqual([.9, .8], [item["confidence"] for item in detections])

    def test_matching_semantic_prediction_does_not_grant_runtime_equivalence(self):
        detections, _ = actual.post_nms_detections([[1, 2, 30, 40, .9, 0]], 100, 100)
        observed = actual.semantic_observation("SQUAT", detections)
        self.assertTrue(observed["semanticMatch"])
        self.assertNotIn("exportContractVerified", observed)

    def test_only_fully_clipped_boxes_are_dropped(self):
        detections, clipped = actual.post_nms_detections([[1, 2, 1, 40, .9, 0], [1, 2, 30, 40, .8, 1]], 100, 100)
        self.assertEqual(1, clipped)
        self.assertEqual([1], [item["classId"] for item in detections])

    def test_invalid_detection_values_fail_instead_of_becoming_fake_positive(self):
        invalid = [
            [1, 2, 30, 40, float("nan"), 0], [1, 2, float("inf"), 40, .9, 0],
            [1, 2, 30, 40, .9, 2], [1, 2, 30, 40, .9, .5],
            [1, 2, 30, 40, 1.1, 0], [1, 2, 30, 40, -.1, 0],
            [-1, 2, 30, 40, .9, 0], [1, 2, 101, 40, .9, 0], [1, 2, 30, 101, .9, 0],
            [1, 2, 30, 40, .9],
        ]
        for row in invalid:
            with self.subTest(row=row), self.assertRaises(ValueError): actual.post_nms_detections([row], 100, 100)

    def test_post_nms_fixed_count_limit_is_not_tuned(self):
        with self.assertRaises(ValueError): actual.post_nms_detections([[1, 2, 30, 40, .9, 0]] * 301, 100, 100)

    def test_input_dimensions_match_android_safety_bounds(self):
        actual.validate_input_dimensions(4096, 4096)
        actual.validate_input_dimensions(8192, 1)
        for width, height in ((0, 1), (1, 0), (8193, 1), (1, 8193), (8192, 8192), (True, 10), (10, 1.5)):
            with self.subTest(width=width, height=height), self.assertRaises(ValueError): actual.validate_input_dimensions(width, height)

    def test_missing_native_runtime_generate_fails_cleanly_without_output(self):
        # Exercise the real generate preflight, not merely an isolated find_spec call.
        # Fixtures mock identity only; no model or inference output is manufactured.
        with tempfile.TemporaryDirectory() as temporary:
            repository = Path(temporary).resolve()
            output = repository / "private-output"
            model = repository / actual.MODEL_NAME
            original_stat = Path.stat
            def model_stat(path, *stat_args, **stat_kwargs):
                return SimpleNamespace(st_size=actual.MODEL_BYTES) if path == model else original_stat(path, *stat_args, **stat_kwargs)
            args = SimpleNamespace(manifest=repository / "manifest.json", model=model, output=output,
                                   reference_filename="reference_results.json")
            with mock.patch.object(actual, "read_manifest", return_value=self.manifest()["samples"]), \
                 mock.patch.object(actual, "require_private_output", return_value=output), \
                 mock.patch.object(Path, "stat", model_stat), \
                 mock.patch.object(Path, "read_bytes", return_value=b"identity-only-unit-test"), \
                 mock.patch.object(actual, "sha256", return_value=actual.MODEL_SHA256), \
                 mock.patch.object(actual.importlib.util, "find_spec", return_value=None):
                with self.assertRaisesRegex(RuntimeError, "ai_edge_litert is not installed"):
                    actual.generate(args, repository)
            self.assertFalse(output.exists())
            self.assertEqual([], list(repository.iterdir()))


if __name__ == "__main__":
    unittest.main()
