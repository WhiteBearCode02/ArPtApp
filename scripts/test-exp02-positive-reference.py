"""Pure guard tests: never load a model, private image, or Android device."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock

spec = importlib.util.spec_from_file_location("positive_reference", Path(__file__).with_name("generate-exp02-positive-reference.py"))
positive = importlib.util.module_from_spec(spec)
spec.loader.exec_module(positive)


class PositiveReferenceTests(unittest.TestCase):
    def manifest(self, label=None):
        sample = dict(id="candidate_01", image="candidate.png", rightsCleared=True, rightsBasis="Self-photographed; explicit local validation consent")
        if label is not None:
            sample["exercise"] = label
        return dict(schemaVersion=1, samples=[sample])

    def read(self, document):
        with tempfile.TemporaryDirectory() as temporary:
            repository = Path(temporary).resolve()
            directory = repository / "local_validation/exp02_positive"
            directory.mkdir(parents=True)
            (directory / "candidate.png").write_bytes(positive.PNG_SIGNATURE + b"guard-test-only-not-a-real-image")
            file = directory / "manifest.json"
            file.write_text(json.dumps(document), encoding="utf-8")
            with mock.patch.object(positive.subprocess, "run", return_value=mock.Mock(returncode=0, stdout=b"")):
                return positive.read_manifest(file, repository)

    def test_one_explicitly_saved_candidate_without_labels_is_valid(self):
        result = self.read(self.manifest())
        self.assertEqual(1, len(result))
        self.assertNotIn("exercise", result[0])
        self.assertNotIn("cameraView", result[0])

    def test_semantics_and_view_are_optional_not_four_way_coverage(self):
        document = self.manifest("SQUAT")
        document["samples"][0]["cameraView"] = "SIDE"
        self.assertEqual("SQUAT", self.read(document)[0]["exercise"])

    def test_eight_candidate_bound(self):
        document = self.manifest()
        document["samples"] *= 9
        with self.assertRaisesRegex(ValueError, "1..8"):
            self.read(document)

    def test_empty_manifest_is_not_a_pass(self):
        with self.assertRaises(ValueError):
            self.read(dict(schemaVersion=1, samples=[]))

    def test_bad_schema_and_non_object_samples_fail(self):
        for document in (dict(schemaVersion=2, samples=[{}]), dict(schemaVersion=1, samples=["bad"])):
            with self.subTest(document=document), self.assertRaises(ValueError):
                self.read(document)

    def test_rights_and_basis_not_inferred(self):
        for field, value in (("rightsCleared", False), ("rightsCleared", "true"), ("rightsBasis", " "), ("rightsBasis", None)):
            document = self.manifest()
            document["samples"][0][field] = value
            with self.subTest(field=field, value=value), self.assertRaises(ValueError):
                self.read(document)

    def test_expected_label_must_be_explicit_known_value(self):
        with self.assertRaises(ValueError):
            self.read(self.manifest("PUSH_UP"))

    def test_view_must_not_be_guessed_or_extended(self):
        document = self.manifest()
        document["samples"][0]["cameraView"] = "ANGLE_45"
        with self.assertRaises(ValueError):
            self.read(document)

    def test_id_and_duplicate_id_are_rejected(self):
        for identifier in ("../outside", "UPPERCASE", "", "a" * 65):
            document = self.manifest()
            document["samples"][0]["id"] = identifier
            with self.subTest(identifier=identifier), self.assertRaises(ValueError):
                self.read(document)
        document = self.manifest()
        document["samples"].append(document["samples"][0].copy())
        with self.assertRaises(ValueError):
            self.read(document)

    def test_image_must_be_local_relative_png(self):
        for image in ("/outside.png", "C:/outside.png", "..\\outside.png", "candidate.jpg", "../outside.png", ""):
            document = self.manifest()
            document["samples"][0]["image"] = image
            with self.subTest(image=image), self.assertRaises(ValueError):
                self.read(document)

    def test_optional_capture_result_cannot_leave_private_directory(self):
        for filename in ("../result.android.json", "/result.android.json", "other.json", "missing.android.json", 1):
            document = self.manifest()
            document["samples"][0]["androidResult"] = filename
            with self.subTest(filename=filename), self.assertRaises(ValueError):
                self.read(document)

    def test_output_must_be_ignored_and_untracked(self):
        repository = Path(__file__).resolve().parents[1]
        destination = repository / "local_validation/exp02_positive/bundle"
        self.assertEqual(destination.resolve(), positive.require_private_path(destination, repository, directory=True))
        cases = [mock.Mock(returncode=1, stdout=b""), mock.Mock(returncode=128, stdout=b""), mock.Mock(returncode=0, stdout=b"tracked-file\0")]
        for result in cases:
            with self.subTest(code=result.returncode, output=result.stdout), mock.patch.object(positive.subprocess, "run", return_value=result), self.assertRaises(ValueError):
                positive.require_private_path(destination, repository, directory=True)

    def test_only_private_positive_subtree_allowed(self):
        repository = Path(__file__).resolve().parents[1]
        for relative in ("docs/ml/positive", "local_validation", "local_validation/exp02_golden", "build/positive", "local_validation/exp02_positive/../../outside"):
            with self.subTest(relative=relative), self.assertRaises(ValueError):
                positive.require_private_path(repository / relative, repository, directory=True)

    def test_symlink_rejected_before_git(self):
        repository = Path(__file__).resolve().parents[1]
        destination = repository / "local_validation/exp02_positive"
        with mock.patch.object(Path, "is_symlink", lambda value: value == destination), mock.patch.object(positive.subprocess, "run") as git:
            with self.assertRaisesRegex(ValueError, "Symlink/junction"):
                positive.require_private_path(destination, repository, directory=True)
            git.assert_not_called()

    def test_wrong_class_kept_and_still_positive(self):
        detections, _ = positive.actual.post_nms_detections([[1, 2, 20, 30, .9, 1]], 100, 100)
        observation = positive.semantic_observation("SQUAT", detections)
        self.assertFalse(observation["semanticMatch"])
        self.assertEqual(1, observation["topPrediction"]["classId"])
        summary = positive.candidate_summary([dict(detections=detections)])
        self.assertEqual(1, summary["positiveCandidateCount"])
        self.assertEqual("ANDROID_COMPARISON_PENDING", summary["positiveEquivalenceStatus"])
        self.assertEqual("NOT_INSPECTED", summary["productionContract"])

    def test_empty_result_preserved_and_not_positive(self):
        observation = positive.semantic_observation("SQUAT", [])
        self.assertEqual(0, observation["detectionsAfterNms"])
        self.assertIsNone(observation["topPrediction"])
        self.assertFalse(observation["semanticMatch"])
        summary = positive.candidate_summary([dict(detections=[])])
        self.assertEqual(0, summary["positiveCandidateCount"])
        self.assertEqual(1, summary["emptyCandidateCount"])
        self.assertNotIn("passed", summary)

    def test_unlabeled_sample_semantics_is_not_automatic_pass_or_fail(self):
        detections, _ = positive.actual.post_nms_detections([[1, 2, 20, 30, .9, 1]], 100, 100)
        observation = positive.semantic_observation(None, detections)
        self.assertIsNone(observation["expectedExercise"])
        self.assertIsNone(observation["semanticMatch"])
        self.assertIsNone(observation["hasExpectedDetection"])
        self.assertEqual([1], observation["classOrdering"])
        self.assertEqual(1, observation["topPrediction"]["classId"])

    def test_uses_pinned_production_reference_functions_not_new_letterbox(self):
        source = Path(positive.__file__).read_text(encoding="utf-8")
        for reference_call in ('reference["preprocess"]', 'reference["forward"]', 'reference["non_max_suppression"]', 'reference["scale_boxes"]'):
            self.assertIn(reference_call, source)
        self.assertIn("conf_thres=.10, iou_thres=.70", source)
        self.assertNotIn("cv2.resize(", source)
        self.assertNotIn("SyntheticInterpreter", source)
        self.assertNotIn("pip install", source)

    def test_capture_png_and_decoded_rgb_provenance_match(self):
        capture = dict(schemaVersion=1, validationPurpose="EXPLICIT_DEBUG_POSITIVE_CANDIDATE", modelSha256=positive.actual.MODEL_SHA256,
            productionContract="NOT_VERIFIED", image="candidate.png", imageSha256="a" * 64, sourceImageSha256="a" * 64,
            decodedRgbSha256="b" * 64, width=100, height=100, thresholds=dict(confidence=.10, iou=.70, maxDetections=300),
            predictionUsedForSession=False, contractPromotionPerformed=False)
        positive.validate_capture_provenance(capture, "candidate.png", "a" * 64, "b" * 64, 100, 100)
        for key, value in (("decodedRgbSha256", "c" * 64), ("sourceImageSha256", "c" * 64), ("width", 101),
                           ("modelSha256", "c" * 64), ("predictionUsedForSession", True), ("contractPromotionPerformed", True)):
            changed = dict(capture, **{key: value})
            with self.subTest(key=key), self.assertRaises(ValueError):
                positive.validate_capture_provenance(changed, "candidate.png", "a" * 64, "b" * 64, 100, 100)


if __name__ == "__main__":
    unittest.main()
