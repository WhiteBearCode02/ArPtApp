from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from datetime import date
from pathlib import Path
from urllib.parse import parse_qs, urlparse

from .schema import Exercise, SourceMetadata, SourceType, to_dict


def youtube_video_id(url: str) -> str:
    parsed = urlparse(url)
    if parsed.hostname in {"youtu.be", "www.youtu.be"}:
        return parsed.path.lstrip("/").split("/")[0]
    identifier = parse_qs(parsed.query).get("v", [""])[0]
    if not identifier:
        raise ValueError(f"Unable to identify YouTube video_id: {url}")
    return identifier


@dataclass
class SampleIdGenerator:
    registry_path: Path

    def next(self, exercise: Exercise, source_type: SourceType) -> str:
        prefix = {Exercise.SQUAT: "SQ", Exercise.SHOULDER_PRESS: "SP", Exercise.READY: "RD"}[exercise]
        source = {SourceType.YOUTUBE: "YT", SourceType.LOCAL: "LOCAL", SourceType.SELF_RECORDED: "SELF", SourceType.PUBLIC_DATASET: "PUBLIC"}[source_type]
        registry = json.loads(self.registry_path.read_text("utf-8")) if self.registry_path.exists() else {"issued": []}
        issued = set(registry.get("issued", []))
        sequence = 1
        while f"{prefix}_{source}_{sequence:06d}" in issued:
            sequence += 1
        sample_id = f"{prefix}_{source}_{sequence:06d}"
        issued.add(sample_id)
        self.registry_path.parent.mkdir(parents=True, exist_ok=True)
        self.registry_path.write_text(json.dumps({"issued": sorted(issued)}, indent=2), encoding="utf-8")
        return sample_id


def source_metadata(source_type: SourceType, uri: str, **kwargs: str | None) -> SourceMetadata:
    video_id = youtube_video_id(uri) if source_type == SourceType.YOUTUBE else None
    return SourceMetadata(
        type=source_type, uri=uri, video_id=video_id,
        retrieved_at=kwargs.get("retrieved_at") or date.today().isoformat(),
        channel_id=kwargs.get("channel_id"), license=kwargs.get("license"),
        subject_id=kwargs.get("subject_id"),
    )


def source_fingerprint(source: SourceMetadata) -> str:
    return hashlib.sha256(json.dumps(to_dict(source), sort_keys=True).encode()).hexdigest()
