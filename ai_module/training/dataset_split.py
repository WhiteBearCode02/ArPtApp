from __future__ import annotations

import argparse
import json
import random
import warnings
from collections import defaultdict
from pathlib import Path


def split_samples(
    samples: list[dict],
    ratios: tuple[float, float, float] = (0.7, 0.15, 0.15),
    seed: int = 42,
) -> dict[str, list[str]]:
    if len(ratios) != 3 or abs(sum(ratios) - 1.0) > 1e-6 or any(value < 0 for value in ratios):
        raise ValueError("ratios must be three non-negative values summing to 1")
    rng = random.Random(seed)
    subject_groups: dict[str, list[str]] = defaultdict(list)
    missing_subject = False
    for sample in samples:
        sample_id = sample["sample_id"]
        subject = sample.get("source", {}).get("subject_id")
        if not subject:
            missing_subject = True
            subject = f"__sample__{sample_id}"
        subject_groups[subject].append(sample_id)
    if missing_subject:
        warnings.warn("subject_id is missing; affected samples use random sample-level fallback", stacklevel=2)
    groups = list(subject_groups.values())
    rng.shuffle(groups)
    targets = [len(samples) * value for value in ratios]
    output = {"train": [], "validation": [], "test": []}
    names = list(output)
    for group in groups:
        index = min(range(3), key=lambda i: len(output[names[i]]) / max(targets[i], 1e-9))
        output[names[index]].extend(group)
    return output


def main() -> None:
    parser = argparse.ArgumentParser(description="Create subject-independent dataset splits")
    parser.add_argument("--dataset-root", default="ai_module/dataset")
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()
    root = Path(args.dataset_root)
    samples = [json.loads(path.read_text("utf-8")) for path in (root / "metadata" / "samples").glob("*.json")]
    output = split_samples(samples, seed=args.seed)
    destination = root / "metadata" / "dataset_split.json"
    destination.write_text(json.dumps(output, indent=2), "utf-8")
    print(destination)


if __name__ == "__main__":
    main()
