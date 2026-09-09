from __future__ import annotations

from dataclasses import dataclass
from typing import Mapping, Sequence

import numpy as np

from .config import PseudoLabelConfig
from .schema import ErrorLabel, Exercise


@dataclass(frozen=True)
class CandidateError:
    label: ErrorLabel
    confidence: float


class PseudoLabeler:
    """Conservative candidate generator; output always requires human review."""

    def __init__(self, config: PseudoLabelConfig | None = None) -> None:
        self.config = config or PseudoLabelConfig()

    def label(
        self,
        exercise: Exercise,
        poses: np.ndarray,
        features: Sequence[Mapping[str, float]],
    ) -> list[CandidateError]:
        if len(features) == 0:
            return []
        result: list[CandidateError] = []
        cfg = self.config
        if exercise == Exercise.SQUAT:
            knees = [
                min(item.get("left_knee_angle", np.nan), item.get("right_knee_angle", np.nan))
                for item in features
            ]
            if np.nanmin(knees) > cfg.shallow_depth_angle:
                result.append(CandidateError(ErrorLabel.SHALLOW_DEPTH, cfg.confidence))
            if np.nanmax([item.get("torso_angle", np.nan) for item in features]) > cfg.forward_lean_degrees:
                result.append(CandidateError(ErrorLabel.FORWARD_LEAN, cfg.confidence))
        elif exercise == Exercise.SHOULDER_PRESS:
            elbows = [
                max(item.get("left_elbow_angle", np.nan), item.get("right_elbow_angle", np.nan))
                for item in features
            ]
            if np.nanmax(elbows) < cfg.press_rom_angle:
                result.append(CandidateError(ErrorLabel.INCOMPLETE_ROM, cfg.confidence))
            asymmetry = [
                abs(item.get("left_elbow_angle", np.nan) - item.get("right_elbow_angle", np.nan))
                for item in features
            ]
            if np.nanmax(asymmetry) > cfg.press_asymmetry_degrees:
                result.append(CandidateError(ErrorLabel.ASYMMETRIC_PRESS, cfg.confidence))
        return result
