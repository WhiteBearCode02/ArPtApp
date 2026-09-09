from __future__ import annotations

from dataclasses import asdict, dataclass, field
from enum import Enum
from typing import Any

SCHEMA_VERSION = "0.1.0"


class StringEnum(str, Enum):
    def __str__(self) -> str:
        return self.value


class Exercise(StringEnum):
    READY = "READY"
    SQUAT = "SQUAT"
    SHOULDER_PRESS = "SHOULDER_PRESS"


class SourceType(StringEnum):
    YOUTUBE = "YOUTUBE"
    LOCAL = "LOCAL"
    SELF_RECORDED = "SELF_RECORDED"
    PUBLIC_DATASET = "PUBLIC_DATASET"


class ViewAngle(StringEnum):
    FRONT = "FRONT"
    SIDE = "SIDE"
    REAR = "REAR"
    FRONT_45 = "FRONT_45"
    REAR_45 = "REAR_45"
    UNKNOWN = "UNKNOWN"


class FormQuality(StringEnum):
    CORRECT = "CORRECT"
    INCORRECT = "INCORRECT"
    UNKNOWN = "UNKNOWN"


class Severity(StringEnum):
    NONE = "NONE"
    MILD = "MILD"
    MODERATE = "MODERATE"
    SEVERE = "SEVERE"


class AnnotationSource(StringEnum):
    AUTO = "AUTO"
    HUMAN = "HUMAN"
    AUTO_VERIFIED = "AUTO_VERIFIED"


class ErrorLabel(StringEnum):
    KNEE_VALGUS = "KNEE_VALGUS"
    SHALLOW_DEPTH = "SHALLOW_DEPTH"
    FORWARD_LEAN = "FORWARD_LEAN"
    HEEL_LIFT = "HEEL_LIFT"
    INCOMPLETE_ROM = "INCOMPLETE_ROM"
    ASYMMETRIC_PRESS = "ASYMMETRIC_PRESS"
    EXCESSIVE_BACK_EXTENSION = "EXCESSIVE_BACK_EXTENSION"
    ELBOW_ALIGNMENT_ERROR = "ELBOW_ALIGNMENT_ERROR"


ERRORS_BY_EXERCISE: dict[Exercise, frozenset[ErrorLabel]] = {
    Exercise.READY: frozenset(),
    Exercise.SQUAT: frozenset({
        ErrorLabel.KNEE_VALGUS, ErrorLabel.SHALLOW_DEPTH,
        ErrorLabel.FORWARD_LEAN, ErrorLabel.HEEL_LIFT,
    }),
    Exercise.SHOULDER_PRESS: frozenset({
        ErrorLabel.INCOMPLETE_ROM, ErrorLabel.ASYMMETRIC_PRESS,
        ErrorLabel.EXCESSIVE_BACK_EXTENSION, ErrorLabel.ELBOW_ALIGNMENT_ERROR,
    }),
}


@dataclass(frozen=True)
class SourceMetadata:
    type: SourceType
    uri: str
    video_id: str | None = None
    channel_id: str | None = None
    retrieved_at: str | None = None
    license: str | None = None
    subject_id: str | None = None


@dataclass(frozen=True)
class SegmentMetadata:
    start_sec: float
    end_sec: float


@dataclass
class Annotation:
    exercise: Exercise
    form_quality: FormQuality = FormQuality.UNKNOWN
    error_labels: list[ErrorLabel] = field(default_factory=list)
    severity: Severity = Severity.NONE
    view: ViewAngle = ViewAngle.UNKNOWN
    annotation_source: AnnotationSource = AnnotationSource.AUTO
    annotator: str | None = None
    confidence: float | None = None
    notes: str | None = None


@dataclass(frozen=True)
class Repetition:
    rep_id: str
    sample_id: str
    rep_index: int
    start_frame: int
    end_frame: int
    start_sec: float
    end_sec: float
    exercise: Exercise
    pose_file: str
    feature_file: str


@dataclass
class SampleMetadata:
    sample_id: str
    source: SourceMetadata
    segment: SegmentMetadata
    exercise: Exercise
    view: ViewAngle
    annotation: Annotation | None = None
    repetitions: list[Repetition] = field(default_factory=list)
    schema_version: str = SCHEMA_VERSION


def to_dict(value: Any) -> Any:
    if isinstance(value, Enum):
        return value.value
    if hasattr(value, "__dataclass_fields__"):
        return {key: to_dict(item) for key, item in asdict(value).items()}
    if isinstance(value, dict):
        return {str(key): to_dict(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [to_dict(item) for item in value]
    return value


def validate_annotation(annotation: Annotation) -> list[str]:
    errors: list[str] = []
    invalid = set(annotation.error_labels) - ERRORS_BY_EXERCISE[annotation.exercise]
    if invalid:
        errors.append(f"Invalid labels for {annotation.exercise}: {sorted(map(str, invalid))}")
    if annotation.form_quality == FormQuality.CORRECT and annotation.error_labels:
        errors.append("CORRECT annotation cannot contain error_labels")
    if annotation.confidence is not None and not 0.0 <= annotation.confidence <= 1.0:
        errors.append("confidence must be between 0 and 1")
    return errors
