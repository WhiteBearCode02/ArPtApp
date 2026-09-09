from __future__ import annotations

import numpy as np

LEFT_SHOULDER, RIGHT_SHOULDER = 11, 12
LEFT_HIP, RIGHT_HIP = 23, 24


def normalize_pose(
    pose: np.ndarray,
    *,
    visibility_threshold: float = 0.5,
    scale_epsilon: float = 1e-6,
    mirror_x: bool = False,
) -> np.ndarray:
    """Hip-center translation and shoulder-width scaling; preserves visibility."""
    if pose.shape != (33, 4):
        raise ValueError(f"Expected pose shape (33, 4), got {pose.shape}")
    output = pose.astype(np.float32, copy=True)
    if mirror_x:
        output[:, 0] = 1.0 - output[:, 0]
    hip_center = (output[LEFT_HIP, :3] + output[RIGHT_HIP, :3]) / 2.0
    shoulder_width = np.linalg.norm(output[LEFT_SHOULDER, :3] - output[RIGHT_SHOULDER, :3])
    torso_length = np.linalg.norm(
        ((output[LEFT_SHOULDER, :3] + output[RIGHT_SHOULDER, :3]) / 2.0) - hip_center
    )
    scale = float(max(shoulder_width, torso_length, scale_epsilon))
    output[:, :3] = (output[:, :3] - hip_center) / scale
    output[output[:, 3] < visibility_threshold, :3] = np.nan
    return output


def normalize_sequence(sequence: np.ndarray, **kwargs: object) -> np.ndarray:
    if sequence.ndim != 3 or sequence.shape[1:] != (33, 4):
        raise ValueError(f"Expected sequence shape (T, 33, 4), got {sequence.shape}")
    return np.stack([normalize_pose(frame, **kwargs) for frame in sequence])
