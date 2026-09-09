from __future__ import annotations

import argparse
import json
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np

from .schema import ERRORS_BY_EXERCISE, Exercise


@dataclass
class ValidationReport:
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)

    @property
    def valid(self) -> bool:
        return not self.errors


def validate_dataset(dataset_root: str | Path) -> ValidationReport:
    root = Path(dataset_root)
    report = ValidationReport()
    samples_dir = root / "metadata" / "samples"
    seen: set[str] = set()
    referenced_pose_files: set[Path] = set()
    for metadata_path in sorted(samples_dir.glob("*.json")) if samples_dir.exists() else []:
        try:
            sample = json.loads(metadata_path.read_text("utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            report.errors.append(f"{metadata_path}: unreadable metadata: {exc}")
            continue
        sample_id = sample.get("sample_id")
        if not sample_id:
            report.errors.append(f"{metadata_path}: missing sample_id")
            continue
        if sample_id in seen:
            report.errors.append(f"duplicate sample_id: {sample_id}")
        seen.add(sample_id)
        try:
            exercise = Exercise(sample.get("exercise"))
        except ValueError:
            report.errors.append(f"{sample_id}: unknown exercise {sample.get('exercise')}")
            continue
        segment = sample.get("segment", {})
        if segment.get("start_sec", 0) >= segment.get("end_sec", 0):
            report.errors.append(f"{sample_id}: start_sec must be less than end_sec")
        annotation = sample.get("annotation")
        if not annotation:
            report.errors.append(f"{sample_id}: missing annotation")
        else:
            valid_labels = {item.value for item in ERRORS_BY_EXERCISE[exercise]}
            invalid = set(annotation.get("error_labels", [])) - valid_labels
            if invalid:
                report.errors.append(f"{sample_id}: invalid error labels {sorted(invalid)}")
        repetitions = sample.get("repetitions", [])
        if not repetitions:
            report.errors.append(f"{sample_id}: no repetition or pose sequence")
        for repetition in repetitions:
            pose_path = root / repetition.get("pose_file", "")
            referenced_pose_files.add(pose_path.resolve())
            if not pose_path.is_file():
                report.errors.append(f"{sample_id}: missing pose file {pose_path}")
                continue
            try:
                with np.load(pose_path) as data:
                    pose_keys = set(data.files) & {"raw_pose", "normalized_pose"}
                    if not pose_keys:
                        report.errors.append(f"{sample_id}: no pose array in {pose_path}")
                    for key in pose_keys:
                        pose = data[key]
                        if pose.ndim != 3 or pose.shape[1:] != (33, 4):
                            report.errors.append(f"{sample_id}: invalid {key} shape {pose.shape}")
                            continue
                        nan_ratio = float(np.isnan(pose[:, :, :3]).mean())
                        if nan_ratio > 0.5:
                            report.warnings.append(f"{sample_id}: {key} NaN ratio is {nan_ratio:.1%}")
                        visibility = pose[:, :, 3]
                        if float(np.nanmean(visibility)) < 0.4:
                            report.warnings.append(f"{sample_id}: low mean visibility")
            except (OSError, ValueError) as exc:
                report.errors.append(f"{sample_id}: invalid pose file {pose_path}: {exc}")
    pose_root = root / "pose"
    for pose_path in pose_root.rglob("*.npz") if pose_root.exists() else []:
        if pose_path.resolve() not in referenced_pose_files:
            report.warnings.append(f"unreferenced pose file: {pose_path}")
    if not seen:
        report.warnings.append("dataset contains no sample metadata")
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description="Validate an ArPt dataset")
    parser.add_argument("--dataset-root", default="ai_module/dataset")
    args = parser.parse_args()
    report = validate_dataset(args.dataset_root)
    for item in report.errors:
        print(f"ERROR: {item}")
    for item in report.warnings:
        print(f"WARNING: {item}")
    print(f"Validation {'passed' if report.valid else 'failed'}: {len(report.errors)} errors, {len(report.warnings)} warnings")
    raise SystemExit(0 if report.valid else 1)


if __name__ == "__main__":
    main()
