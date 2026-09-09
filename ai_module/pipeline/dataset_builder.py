from __future__ import annotations

import argparse
import json
import math
from collections import Counter, deque
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

import numpy as np

from .config import PipelineConfig
from .joint_features import extract_features
from .metadata_collector import SampleIdGenerator, source_metadata
from .pose_extractor import PoseExtractor
from .pose_normalizer import normalize_pose
from .pseudo_labeler import PseudoLabeler
from .rep_segmenter import create_segmenter
from .schema import (
    Annotation, AnnotationSource, Exercise, FormQuality, Repetition,
    SampleMetadata, SegmentMetadata, Severity, SourceType, ViewAngle, to_dict,
)


@dataclass(frozen=True)
class BuildResult:
    sample_id: str
    repetitions: int
    metadata_file: Path


class DatasetBuilder:
    def __init__(self, dataset_root: str | Path, config: PipelineConfig | None = None) -> None:
        self.root = Path(dataset_root)
        self.config = config or PipelineConfig()

    def build_video(
        self,
        video: str | Path,
        exercise: Exercise,
        source_type: SourceType = SourceType.LOCAL,
        view: ViewAngle = ViewAngle.UNKNOWN,
        subject_id: str | None = None,
        store_raw: bool = True,
        store_normalized: bool = True,
    ) -> BuildResult:
        if exercise == Exercise.READY:
            raise ValueError("READY is a classification label, not a form repetition exercise")
        video_path = Path(video)
        if source_type == SourceType.LOCAL and not video_path.is_file():
            raise FileNotFoundError(video_path)
        generator = SampleIdGenerator(self.root / "metadata" / "sample_ids.json")
        sample_id = generator.next(exercise, source_type)
        source = source_metadata(source_type, str(video_path), subject_id=subject_id)
        segmenter = create_segmenter(exercise, self.config.segmentation)
        labeler = PseudoLabeler(self.config.pseudo_label)
        # Bounded buffer: only the current candidate repetition is retained.
        buffer: deque[tuple[int, float, np.ndarray, np.ndarray, dict[str, float]]] = deque(maxlen=600)
        repetitions: list[Repetition] = []
        candidate_labels = []
        last_timestamp = 0.0

        with PoseExtractor(self.config.pose) as extractor:
            for frame in extractor.iter_video(video_path):
                last_timestamp = frame.timestamp_sec
                if frame.landmarks is None:
                    continue
                normalized = normalize_pose(
                    frame.landmarks,
                    visibility_threshold=self.config.pose.visibility_threshold,
                    scale_epsilon=self.config.pose.scale_epsilon,
                )
                features = extract_features(normalized, exercise)
                buffer.append((frame.frame_index, frame.timestamp_sec, frame.landmarks, normalized, features))
                boundary = segmenter.update(features, frame.frame_index)
                if boundary is None:
                    continue
                rows = [row for row in buffer if boundary.start_frame <= row[0] <= boundary.end_frame]
                if not rows:
                    continue
                rep_index = len(repetitions) + 1
                rep_id = f"{sample_id}_REP_{rep_index:02d}"
                pose_dir = self.root / "pose" / exercise.value.lower()
                pose_dir.mkdir(parents=True, exist_ok=True)
                pose_file = pose_dir / f"{rep_id}.npz"
                arrays: dict[str, np.ndarray] = {
                    "frame_indices": np.asarray([row[0] for row in rows], dtype=np.int32),
                    "timestamps_sec": np.asarray([row[1] for row in rows], dtype=np.float64),
                }
                if store_raw:
                    arrays["raw_pose"] = np.stack([row[2] for row in rows])
                if store_normalized:
                    arrays["normalized_pose"] = np.stack([row[3] for row in rows])
                np.savez_compressed(pose_file, **arrays)
                feature_file = pose_dir / f"{rep_id}_features.json"
                feature_values = [row[4] for row in rows]
                serializable_features = [
                    {key: value if math.isfinite(value) else None for key, value in item.items()}
                    for item in feature_values
                ]
                feature_file.write_text(json.dumps(serializable_features, indent=2, allow_nan=False), "utf-8")
                candidate_labels.extend(labeler.label(exercise, arrays.get("normalized_pose", np.stack([row[3] for row in rows])), feature_values))
                repetitions.append(Repetition(
                    rep_id, sample_id, rep_index, rows[0][0], rows[-1][0], rows[0][1], rows[-1][1], exercise,
                    str(pose_file.relative_to(self.root)), str(feature_file.relative_to(self.root)),
                ))
                buffer.clear()

        unique_candidates = {item.label: item.confidence for item in candidate_labels}
        annotation = Annotation(
            exercise=exercise,
            form_quality=FormQuality.INCORRECT if unique_candidates else FormQuality.UNKNOWN,
            error_labels=list(unique_candidates),
            annotation_source=AnnotationSource.AUTO,
            severity=Severity.MODERATE if unique_candidates else Severity.NONE,
            confidence=min(unique_candidates.values()) if unique_candidates else None,
            notes="Pseudo label only; human verification required.",
            view=view,
        )
        sample = SampleMetadata(
            sample_id=sample_id, source=source, segment=SegmentMetadata(0.0, last_timestamp),
            exercise=exercise, view=view, annotation=annotation, repetitions=repetitions,
        )
        metadata_dir = self.root / "metadata" / "samples"
        metadata_dir.mkdir(parents=True, exist_ok=True)
        metadata_file = metadata_dir / f"{sample_id}.json"
        metadata_file.write_text(json.dumps(to_dict(sample), indent=2), "utf-8")
        self.update_manifest()
        return BuildResult(sample_id, len(repetitions), metadata_file)

    def update_manifest(self) -> Path:
        samples_dir = self.root / "metadata" / "samples"
        samples = [json.loads(path.read_text("utf-8")) for path in samples_dir.glob("*.json")] if samples_dir.exists() else []
        def count(path: tuple[str, ...]) -> dict[str, int]:
            values = []
            for sample in samples:
                value: object = sample
                for key in path:
                    value = value.get(key) if isinstance(value, dict) else None
                if value is not None:
                    values.append(str(value))
            return dict(sorted(Counter(values).items()))
        manifest = {
            "dataset_name": "ArPt Exercise Form Dataset", "version": "0.1.0",
            "updated_at": datetime.now(timezone.utc).isoformat(),
            "pose_extractor": {"type": "mediapipe", "landmark_count": 33, "values": ["x", "y", "z", "visibility"]},
            "exercise_classes": [item.value for item in Exercise], "samples": len(samples),
            "statistics": {
                "by_exercise": count(("exercise",)), "by_form_quality": count(("annotation", "form_quality")),
                "by_view": count(("view",)), "by_source": count(("source", "type")),
            },
        }
        path = self.root / "metadata" / "dataset_manifest.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(manifest, indent=2), "utf-8")
        return path


def main() -> None:
    parser = argparse.ArgumentParser(description="Build an ArPt repetition-level pose dataset")
    parser.add_argument("--video", required=True)
    parser.add_argument("--exercise", required=True, choices=[Exercise.SQUAT.value, Exercise.SHOULDER_PRESS.value])
    parser.add_argument("--source-type", default="LOCAL", choices=[item.value for item in SourceType])
    parser.add_argument("--view", default="UNKNOWN", choices=[item.value for item in ViewAngle])
    parser.add_argument("--subject-id")
    parser.add_argument("--dataset-root", default="ai_module/dataset")
    args = parser.parse_args()
    result = DatasetBuilder(args.dataset_root).build_video(
        args.video, Exercise(args.exercise), SourceType(args.source_type), ViewAngle(args.view), args.subject_id,
    )
    print(f"Created {result.sample_id}: {result.repetitions} repetitions -> {result.metadata_file}")


if __name__ == "__main__":
    main()
