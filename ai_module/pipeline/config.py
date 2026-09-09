from __future__ import annotations

from dataclasses import dataclass, field


@dataclass(frozen=True)
class PoseConfig:
    min_detection_confidence: float = 0.5
    min_tracking_confidence: float = 0.5
    visibility_threshold: float = 0.5
    scale_epsilon: float = 1e-6


@dataclass(frozen=True)
class SegmentationConfig:
    squat_down_angle: float = 100.0
    squat_up_angle: float = 155.0
    press_bottom_angle: float = 100.0
    press_top_angle: float = 155.0
    min_rep_frames: int = 6


@dataclass(frozen=True)
class PseudoLabelConfig:
    shallow_depth_angle: float = 100.0
    knee_valgus_ratio: float = 0.08
    forward_lean_degrees: float = 25.0
    press_rom_angle: float = 150.0
    press_asymmetry_degrees: float = 20.0
    confidence: float = 0.75


@dataclass(frozen=True)
class PipelineConfig:
    pose: PoseConfig = field(default_factory=PoseConfig)
    segmentation: SegmentationConfig = field(default_factory=SegmentationConfig)
    pseudo_label: PseudoLabelConfig = field(default_factory=PseudoLabelConfig)
