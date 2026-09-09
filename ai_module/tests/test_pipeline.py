from __future__ import annotations

import json
import tempfile
import unittest
import warnings
from pathlib import Path

import numpy as np

from ai_module.pipeline.dataset_builder import DatasetBuilder
from ai_module.pipeline.metadata_collector import SampleIdGenerator
from ai_module.pipeline.pose_normalizer import normalize_pose
from ai_module.pipeline.rep_segmenter import ThresholdRepSegmenter
from ai_module.pipeline.schema import (
    Annotation, ErrorLabel, Exercise, FormQuality, SourceType,
    validate_annotation,
)
from ai_module.training.dataset_split import split_samples


def mock_pose() -> np.ndarray:
    pose = np.zeros((33, 4), dtype=np.float32)
    pose[:, 3] = 1.0
    pose[11, :3], pose[12, :3] = (-0.2, -1.0, 0), (0.2, -1.0, 0)
    pose[23, :3], pose[24, :3] = (-0.15, 0, 0), (0.15, 0, 0)
    return pose


class PipelineTests(unittest.TestCase):
    def test_pose_shape_and_normalization(self) -> None:
        result = normalize_pose(mock_pose())
        self.assertEqual(result.shape, (33, 4))
        np.testing.assert_allclose((result[23, :3] + result[24, :3]) / 2, 0, atol=1e-6)

    def test_metadata_serialization(self) -> None:
        annotation = Annotation(Exercise.SQUAT, FormQuality.INCORRECT, [ErrorLabel.KNEE_VALGUS])
        self.assertEqual(annotation.exercise.value, "SQUAT")
        self.assertEqual(validate_annotation(annotation), [])

    def test_annotation_validation(self) -> None:
        annotation = Annotation(Exercise.SQUAT, FormQuality.INCORRECT, [ErrorLabel.INCOMPLETE_ROM])
        self.assertTrue(validate_annotation(annotation))

    def test_sample_id_uniqueness(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            generator = SampleIdGenerator(Path(directory) / "ids.json")
            first = generator.next(Exercise.SQUAT, SourceType.LOCAL)
            second = generator.next(Exercise.SQUAT, SourceType.LOCAL)
            self.assertNotEqual(first, second)

    def test_rep_segmentation(self) -> None:
        segmenter = ThresholdRepSegmenter(("angle",), 100, 155, 3)
        outputs = [segmenter.update({"angle": value}, index) for index, value in enumerate([170, 150, 90, 120, 160])]
        boundary = next(item for item in outputs if item is not None)
        self.assertEqual((boundary.start_frame, boundary.end_frame), (1, 4))

    def test_manifest_generation(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            samples = root / "metadata" / "samples"
            samples.mkdir(parents=True)
            (samples / "one.json").write_text(json.dumps({
                "sample_id": "SQ_LOCAL_000001", "exercise": "SQUAT", "view": "SIDE",
                "source": {"type": "LOCAL"}, "annotation": {"form_quality": "CORRECT"},
            }), "utf-8")
            manifest = json.loads(DatasetBuilder(root).update_manifest().read_text("utf-8"))
            self.assertEqual(manifest["samples"], 1)
            self.assertEqual(manifest["statistics"]["by_exercise"]["SQUAT"], 1)

    def test_subject_independent_split(self) -> None:
        samples = [
            {"sample_id": f"S{i}", "source": {"subject_id": f"person_{i // 2}"}}
            for i in range(8)
        ]
        split = split_samples(samples)
        membership = {sample: name for name, values in split.items() for sample in values}
        for index in range(0, 8, 2):
            self.assertEqual(membership[f"S{index}"], membership[f"S{index + 1}"])

    def test_random_split_fallback_warning(self) -> None:
        with warnings.catch_warnings(record=True) as caught:
            warnings.simplefilter("always")
            split_samples([{"sample_id": "S1", "source": {}}])
            self.assertTrue(caught)


if __name__ == "__main__":
    unittest.main()
