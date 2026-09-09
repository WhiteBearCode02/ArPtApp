from __future__ import annotations

from collections.abc import Iterator
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import numpy as np

from .config import PoseConfig


@dataclass(frozen=True)
class PoseFrame:
    frame_index: int
    timestamp_sec: float
    landmarks: np.ndarray | None


class PoseExtractor:
    """Streaming MediaPipe extractor. A frame pose has shape (33, 4)."""

    def __init__(self, config: PoseConfig | None = None, pose_backend: Any | None = None) -> None:
        self.config = config or PoseConfig()
        self._backend = pose_backend
        self._owns_backend = pose_backend is None

    def __enter__(self) -> "PoseExtractor":
        if self._backend is None:
            try:
                import mediapipe as mp
            except ImportError as exc:
                raise RuntimeError("mediapipe is required for pose extraction") from exc
            self._backend = mp.solutions.pose.Pose(
                static_image_mode=False,
                min_detection_confidence=self.config.min_detection_confidence,
                min_tracking_confidence=self.config.min_tracking_confidence,
            )
        return self

    def __exit__(self, *_: object) -> None:
        if self._owns_backend and self._backend is not None:
            self._backend.close()
            self._backend = None

    def extract_frame(self, bgr_frame: np.ndarray) -> np.ndarray | None:
        if self._backend is None:
            raise RuntimeError("Use PoseExtractor as a context manager")
        import cv2

        result = self._backend.process(cv2.cvtColor(bgr_frame, cv2.COLOR_BGR2RGB))
        if result.pose_landmarks is None:
            return None
        pose = np.asarray(
            [[p.x, p.y, p.z, p.visibility] for p in result.pose_landmarks.landmark],
            dtype=np.float32,
        )
        if pose.shape != (33, 4):
            raise ValueError(f"Expected pose shape (33, 4), got {pose.shape}")
        return pose

    def iter_video(self, video_path: str | Path) -> Iterator[PoseFrame]:
        import cv2

        path = Path(video_path)
        capture = cv2.VideoCapture(str(path))
        if not capture.isOpened():
            raise FileNotFoundError(f"Unable to open video: {path}")
        fps = capture.get(cv2.CAP_PROP_FPS)
        fps = fps if fps > 0 else 30.0
        try:
            frame_index = 0
            while True:
                ok, frame = capture.read()
                if not ok:
                    break
                yield PoseFrame(frame_index, frame_index / fps, self.extract_frame(frame))
                frame_index += 1
        finally:
            capture.release()
