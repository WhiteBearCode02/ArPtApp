"""Legacy-compatible frame CSV exporter backed by the reusable PoseExtractor."""

from __future__ import annotations

import argparse
import csv
from pathlib import Path

from ai_module.pipeline.pose_extractor import PoseExtractor
from ai_module.pipeline.schema import Exercise


def process_videos(base_path: str | Path = "data", output: str | Path = "raw_data.csv") -> int:
    """Preserve the former folder-to-CSV workflow for classification experiments."""
    base = Path(base_path)
    labels = [Exercise.READY, Exercise.SQUAT, Exercise.SHOULDER_PRESS]
    header = ["label", "source_video", "frame_index", "timestamp_sec"]
    for index in range(33):
        header.extend([f"landmark_{index}_{axis}" for axis in ("x", "y", "z", "visibility")])
    count = 0
    with Path(output).open("w", newline="", encoding="utf-8") as stream, PoseExtractor() as extractor:
        writer = csv.writer(stream)
        writer.writerow(header)
        for label in labels:
            folder = base / label.value
            if not folder.exists():
                continue
            for video in sorted(folder.iterdir()):
                if video.suffix.lower() not in {".mp4", ".avi", ".mov", ".mkv"}:
                    continue
                print(f"[{label.value}] processing: {video.name}")
                for frame in extractor.iter_video(video):
                    if frame.landmarks is None:
                        continue
                    writer.writerow([
                        label.value, str(video), frame.frame_index, frame.timestamp_sec,
                        *frame.landmarks.reshape(-1).tolist(),
                    ])
                    count += 1
    print(f"Exported {count} frames to {output}")
    return count


def main() -> None:
    parser = argparse.ArgumentParser(description="Legacy frame-level classification CSV exporter")
    parser.add_argument("--data", default="data")
    parser.add_argument("--output", default="raw_data.csv")
    args = parser.parse_args()
    process_videos(args.data, args.output)


if __name__ == "__main__":
    main()
