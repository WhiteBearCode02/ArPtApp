from __future__ import annotations

import numpy as np

from .schema import Exercise


def angle(a: np.ndarray, vertex: np.ndarray, c: np.ndarray) -> float:
    first, second = a[:3] - vertex[:3], c[:3] - vertex[:3]
    denominator = np.linalg.norm(first) * np.linalg.norm(second)
    if denominator <= 1e-8 or np.isnan(denominator):
        return float("nan")
    cosine = np.clip(np.dot(first, second) / denominator, -1.0, 1.0)
    return float(np.degrees(np.arccos(cosine)))


def torso_angle(pose: np.ndarray) -> float:
    shoulder = (pose[11, :3] + pose[12, :3]) / 2.0
    hip = (pose[23, :3] + pose[24, :3]) / 2.0
    delta = shoulder - hip
    return float(np.degrees(np.arctan2(abs(delta[0]), abs(delta[1]))))


def extract_features(pose: np.ndarray, exercise: Exercise) -> dict[str, float]:
    if pose.shape != (33, 4):
        raise ValueError(f"Expected pose shape (33, 4), got {pose.shape}")
    if exercise == Exercise.SQUAT:
        return {
            "left_knee_angle": angle(pose[23], pose[25], pose[27]),
            "right_knee_angle": angle(pose[24], pose[26], pose[28]),
            "left_hip_angle": angle(pose[11], pose[23], pose[25]),
            "right_hip_angle": angle(pose[12], pose[24], pose[26]),
            "torso_angle": torso_angle(pose),
        }
    if exercise == Exercise.SHOULDER_PRESS:
        return {
            "left_elbow_angle": angle(pose[11], pose[13], pose[15]),
            "right_elbow_angle": angle(pose[12], pose[14], pose[16]),
            "left_shoulder_angle": angle(pose[13], pose[11], pose[23]),
            "right_shoulder_angle": angle(pose[14], pose[12], pose[24]),
            "torso_angle": torso_angle(pose),
            "wrist_height_difference": float(abs(pose[15, 1] - pose[16, 1])),
        }
    return {}
