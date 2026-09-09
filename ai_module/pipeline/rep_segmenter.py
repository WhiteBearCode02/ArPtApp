from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass
from enum import Enum
from typing import Mapping

from .config import SegmentationConfig
from .schema import Exercise


@dataclass(frozen=True)
class RepBoundary:
    start_frame: int
    end_frame: int


class RepSegmenter(ABC):
    @abstractmethod
    def update(self, features: Mapping[str, float], frame_index: int) -> RepBoundary | None:
        """Consume one frame and return a completed repetition boundary."""


class _State(Enum):
    READY = "READY"
    ACTIVE = "ACTIVE"


class ThresholdRepSegmenter(RepSegmenter):
    def __init__(self, feature_names: tuple[str, ...], active_below: float, ready_above: float, min_frames: int) -> None:
        self.feature_names = feature_names
        self.active_below = active_below
        self.ready_above = ready_above
        self.min_frames = min_frames
        self.state = _State.READY
        self._ready_frame = 0
        self._start_frame: int | None = None

    def update(self, features: Mapping[str, float], frame_index: int) -> RepBoundary | None:
        values = [features.get(name, float("nan")) for name in self.feature_names]
        finite = [value for value in values if value == value]
        if not finite:
            return None
        representative = sum(finite) / len(finite)
        if self.state == _State.READY:
            if representative <= self.active_below:
                self.state = _State.ACTIVE
                self._start_frame = self._ready_frame
            else:
                self._ready_frame = frame_index
            return None
        if representative >= self.ready_above and self._start_frame is not None:
            boundary = RepBoundary(self._start_frame, frame_index)
            self.state, self._ready_frame, self._start_frame = _State.READY, frame_index, None
            return boundary if boundary.end_frame - boundary.start_frame + 1 >= self.min_frames else None
        return None


def create_segmenter(exercise: Exercise, config: SegmentationConfig | None = None) -> RepSegmenter:
    cfg = config or SegmentationConfig()
    if exercise == Exercise.SQUAT:
        return ThresholdRepSegmenter(
            ("left_knee_angle", "right_knee_angle"),
            cfg.squat_down_angle, cfg.squat_up_angle, cfg.min_rep_frames,
        )
    if exercise == Exercise.SHOULDER_PRESS:
        return ThresholdRepSegmenter(
            ("left_elbow_angle", "right_elbow_angle"),
            cfg.press_bottom_angle, cfg.press_top_angle, cfg.min_rep_frames,
        )
    raise ValueError(f"Repetition segmentation is unsupported for {exercise}")
