"""Explicit, binary-safe private DEBUG candidate export/reference provisioning.

No adb pull, root, shared/public staging, network upload, model copy, or arbitrary
remote command is offered. A user-saved candidate must be selected by filename.
Reference provisioning force-stops only the already run-as-validated DEBUG app;
never invoke it during an exercise session. All local outputs stay Git-ignored.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import struct
import subprocess
import uuid

PACKAGE = "com.example.arptapp"
REMOTE_CANDIDATES = "no_backup/exp02_positive"
REMOTE_BUNDLE = "no_backup/yolo/positive-golden"
REMOTE_NMS = "no_backup/yolo/nms-contract"
CANDIDATE_PATTERN = r"positive_candidate_[0-9]{8}_[0-9]{6}_[0-9]{3}_[a-f0-9]{8}\.png"
MAX_PNG_BYTES = 32 * 1024 * 1024
MAX_JSON_BYTES = 2 * 1024 * 1024
TENSOR_BYTES = 4 * 3 * 640 * 640
TIMEOUT_SECONDS = 30


def load_positive_helpers():
    path = Path(__file__).with_name("generate-exp02-positive-reference.py")
    spec = importlib.util.spec_from_file_location("exp02_positive_helpers", path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


positive = load_positive_helpers()


def run_directory(base: str, run_id: str | None) -> str:
    if run_id is None:
        return base
    if not isinstance(run_id, str) or not re.fullmatch(r"[a-z0-9][a-z0-9_-]{0,63}", run_id):
        raise ValueError("Use a unique lowercase safe run id")
    return base + "/runs/" + run_id


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def json_object(data: bytes, maximum: int = MAX_JSON_BYTES) -> dict:
    if not 0 < len(data) <= maximum:
        raise ValueError("Private result JSON exceeds the fixed size limit")
    document = json.loads(data.decode("utf-8-sig"), parse_constant=lambda value: (_ for _ in ()).throw(ValueError("Non-finite JSON value")))
    if not isinstance(document, dict):
        raise ValueError("Expected a private result JSON object")
    return document


def validate_candidate_name(filename: str) -> str:
    if not re.fullmatch(CANDIDATE_PATTERN, filename):
        raise ValueError("Select one explicit positive_candidate_<UTC date>_<time>_<millisecond>_<8hex>.png basename")
    return filename


class PrivateAdb:
    """Small fixed-command transport; no remote data appears in exception text."""
    def __init__(self, executable: Path, serial: str):
        executable = executable.resolve()
        if not executable.is_file() or executable.name.lower() != "adb.exe":
            raise ValueError("Pass the actual platform-tools adb.exe path, not a shell wrapper")
        if not re.fullmatch(r"[A-Za-z0-9_.:-]+", serial):
            raise ValueError("Invalid explicit device serial")
        self.executable, self.serial = executable, serial

    def call(self, *arguments: str, data: bytes | None = None) -> bytes:
        if any(not re.fullmatch(r"[A-Za-z0-9_./:=+-]+", argument) for argument in arguments):
            raise ValueError("Unsafe ADB argument")
        try:
            result = subprocess.run([str(self.executable), "-s", self.serial, *arguments], input=data,
                                    stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=TIMEOUT_SECONDS, check=False)
        except subprocess.TimeoutExpired as error:
            raise RuntimeError("Private ADB command exceeded the 30-second limit") from error
        if result.returncode != 0:
            raise RuntimeError("Private ADB command failed; verify online authorization and DEBUG run-as permission (native text suppressed)")
        return result.stdout

    def verify_debug_device(self):
        if self.call("get-state").strip() != b"device":
            raise RuntimeError("Selected device is not online and authorized")
        root = self.call("exec-out", "run-as", PACKAGE, "pwd").decode("ascii", errors="strict").strip()
        if not re.fullmatch(r"/data/(?:user/[0-9]+|data)/" + re.escape(PACKAGE), root):
            raise RuntimeError("Unexpected DEBUG private directory; refusing transfer")
        return root

    def remote_identity(self, remote: str, maximum: int) -> tuple[int, str]:
        size_output = self.call("exec-out", "run-as", PACKAGE, "wc", "-c", remote).split()
        hash_output = self.call("exec-out", "run-as", PACKAGE, "sha256sum", remote).split()
        if not size_output or not re.fullmatch(rb"[0-9]+", size_output[0]) or not hash_output or not re.fullmatch(rb"[a-f0-9]{64}", hash_output[0]):
            raise ValueError("Invalid remote size/hash metadata")
        size = int(size_output[0])
        if not 0 < size <= maximum:
            raise ValueError("Selected private file exceeds the fixed size limit")
        return size, hash_output[0].decode("ascii")

    def read_private(self, remote: str, maximum: int) -> bytes:
        expected_size, expected_hash = self.remote_identity(remote, maximum)
        # Exact count bounds the result even if a concurrent writer grows the file.
        data = self.call("exec-out", "run-as", PACKAGE, "dd", "if=" + remote, "bs=65536", "iflag=count_bytes", "count=" + str(expected_size), "status=none")
        if len(data) != expected_size or sha256(data) != expected_hash or self.remote_identity(remote, maximum) != (expected_size, expected_hash):
            raise ValueError("Private binary export changed/truncated; size/SHA256 mismatch")
        return data

    def write_private(self, remote: str, data: bytes):
        staging = remote + ".upload-" + uuid.uuid4().hex
        try:
            # count_bytes avoids the raw transport's final-block EOF ambiguity.
            self.call("exec-in", "run-as", PACKAGE, "dd", "of=" + staging, "bs=65536", "iflag=count_bytes",
                      "count=" + str(len(data)), "conv=fsync", "status=none", data=data)
            self.call("exec-out", "run-as", PACKAGE, "chmod", "600", staging)
            if self.remote_identity(staging, len(data)) != (len(data), sha256(data)):
                raise ValueError("Staged private fixture failed size/SHA256 validation")
            self.call("exec-out", "run-as", PACKAGE, "mv", staging, remote)
            if self.remote_identity(remote, len(data)) != (len(data), sha256(data)):
                raise ValueError("Final private fixture failed size/SHA256 validation")
        finally:
            # Only this generated exact temporary file is removed, never candidates/models.
            self.call("exec-out", "run-as", PACKAGE, "rm", "-f", staging)


def validate_android_candidate(document: dict, filename: str, image: bytes):
    if document.get("schemaVersion") != 1 or document.get("modelId") != "yolo26n_exp02_fp32" or document.get("modelSha256") != positive.actual.MODEL_SHA256:
        raise ValueError("Android candidate model provenance mismatch")
    if document.get("image") != filename or document.get("imageSha256") != sha256(image):
        raise ValueError("Android result is not bound to the selected PNG bytes")
    if document.get("thresholds") != dict(confidence=.10, iou=.70, maxDetections=300):
        raise ValueError("Android candidate thresholds differ; do not tune validation")
    if not image.startswith(positive.PNG_SIGNATURE) or len(image) < 24 or image[12:16] != b"IHDR":
        raise ValueError("Selected candidate is not a PNG with dimensions")
    width, height = struct.unpack(">II", image[16:24])
    positive.actual.validate_input_dimensions(width, height)
    if (document.get("width"), document.get("height")) != (width, height):
        raise ValueError("PNG dimensions differ from Android result")
    positive.validate_isolated_contract(document)
    detections = document.get("detections")
    if not isinstance(detections, list) or len(detections) > 300:
        raise ValueError("Invalid Android detection list")
    rows = []
    for detection in detections:
        if not isinstance(detection, dict) or not isinstance(detection.get("bbox"), dict):
            raise ValueError("Invalid Android detection")
        box = detection["bbox"]
        rows.append([box.get("x1"), box.get("y1"), box.get("x2"), box.get("y2"), detection.get("confidence"), detection.get("classId")])
    try:
        validated, clipped = positive.actual.post_nms_detections(rows, width, height)
    except (TypeError, ValueError) as error:
        raise ValueError("Invalid Android detections; no correction is allowed") from error
    if clipped or validated != detections or document.get("detectionsAfterNms") != len(detections) or document.get("classOrdering") != [item["classId"] for item in detections]:
        raise ValueError("Android post-NMS result metadata is inconsistent")
    # Failed native attempts remain exportable diagnostic evidence, but cannot invent detections.
    executed = document.get("androidNativeInferenceExecuted") is True
    if document.get("sourceImageSha256") is not None and document["sourceImageSha256"] != sha256(image):
        raise ValueError("Captured source image identity mismatch")
    if executed and not re.fullmatch(r"[a-f0-9]{64}", document.get("decodedRgbSha256", "")):
        raise ValueError("Missing captured logical RGB provenance")
    if not executed and detections:
        raise ValueError("Detections without actual native inference are invalid")
    if executed and document.get("nativeInferenceCount") != 1:
        raise ValueError("Expected one native candidate inference")


def export_candidate(adb: PrivateAdb, args, repository: Path):
    filename = validate_candidate_name(args.candidate)
    result_name = filename[:-4] + ".android.json"
    output = positive.require_private_path(args.output, repository, directory=True)
    image = adb.read_private(REMOTE_CANDIDATES + "/" + filename, MAX_PNG_BYTES)
    result = adb.read_private(REMOTE_CANDIDATES + "/" + result_name, MAX_JSON_BYTES)
    validate_android_candidate(json_object(result), filename, image)
    output.mkdir(parents=True, exist_ok=True)
    targets = [positive.actual.private_child(output, name) for name in (filename, result_name)]
    if any(path.exists() for path in targets):
        raise ValueError("Local candidate/result already exists; refusing to overwrite private evidence")
    for path, data in zip(targets, (image, result)):
        with path.open("xb") as stream:
            stream.write(data)
    print(f"Exported selected private candidate only: {filename}; {len(image)} bytes; SHA256={sha256(image)}")


def validate_bundle(bundle: Path, repository: Path) -> list[tuple[str, Path, int, str]]:
    bundle = positive.require_private_path(bundle, repository, directory=True)
    reference_file = positive.require_private_path(bundle / "reference.json", repository)
    if not reference_file.is_file() or not 0 < reference_file.stat().st_size <= MAX_JSON_BYTES:
        raise ValueError("Missing bounded private reference.json")
    reference_bytes = reference_file.read_bytes()
    reference = json_object(reference_bytes)
    if reference.get("schemaVersion") != 1 or reference.get("validationPurpose") != positive.PURPOSE or reference.get("modelInferenceExecuted") is not True or reference.get("exportContractVerified") is not False:
        raise ValueError("Actual positive reference provenance mismatch")
    model = reference.get("model", {})
    if model != dict(id="yolo26n_exp02_fp32", filename=positive.actual.MODEL_NAME, bytes=positive.actual.MODEL_BYTES, sha256=positive.actual.MODEL_SHA256):
        raise ValueError("Canonical positive reference model mismatch")
    if reference.get("ultralyticsVersion") != "8.4.176" or reference.get("wheelSha256") != "15f4bbac5c69fd9a5a9395f5957a7ce561e3eb4620699a9c2ff11ed38ec7d34c":
        raise ValueError("Pinned Python reference version/wheel mismatch")
    if reference.get("thresholds") != dict(confidence=.10, iou=.70, maxDetections=300):
        raise ValueError("Unchanged threshold policy mismatch")
    tolerance = reference.get("tolerances", {})
    if any(tolerance.get(key) != value for key, value in dict(confidence=.001, bboxPixels=2.0, detectionCount=0, classMatch="EXACT").items()):
        raise ValueError("Existing comparison tolerances must not widen")
    samples = reference.get("samples")
    if not isinstance(samples, list) or not 1 <= len(samples) <= positive.MAX_CANDIDATES:
        raise ValueError("Expected 1..8 candidate samples")
    uploads, seen = [], set()
    for sample in samples:
        identifier = sample.get("id", "")
        if not isinstance(identifier, str) or not re.fullmatch(r"[a-z0-9][a-z0-9_-]{0,63}", identifier) or identifier in seen:
            raise ValueError("Invalid duplicate/path sample id")
        seen.add(identifier)
        width, height = sample.get("width"), sample.get("height")
        positive.actual.validate_input_dimensions(width, height)
        for suffix, hash_key, expected_size in ((".rgb", "rgbSha256", width * height * 3), (".input.f32", "inputTensorSha256", TENSOR_BYTES)):
            filename = identifier + suffix
            field = "rgbFile" if suffix == ".rgb" else "inputTensorFile"
            if sample.get(field) != filename or (suffix == ".input.f32" and sample.get("inputTensorByteOrder") != "LITTLE_ENDIAN"):
                raise ValueError("Unsafe or missing private fixture filename/byte order")
            path = positive.require_private_path(bundle / filename, repository)
            if not path.is_file() or path.stat().st_size != expected_size or not re.fullmatch(r"[a-f0-9]{64}", sample.get(hash_key, "")):
                raise ValueError("Private fixture size/hash metadata mismatch")
            data = path.read_bytes()
            if len(data) != expected_size or sha256(data) != sample[hash_key]:
                raise ValueError("Private fixture changed or failed SHA256")
            uploads.append((filename, path, expected_size, sample[hash_key]))
    uploads.append(("reference.json", reference_file, len(reference_bytes), sha256(reference_bytes))) # publish manifest last
    return uploads


def provision_reference(adb: PrivateAdb, args, repository: Path):
    uploads = validate_bundle(args.bundle, repository)
    remote_bundle = run_directory(REMOTE_BUNDLE, getattr(args, "run_id", None))
    # Explicit command requires no active exercise session; not an automatic side effect of capture.
    adb.call("exec-out", "am", "force-stop", PACKAGE)
    create_empty_remote_run(adb, remote_bundle)
    for filename, path, expected_size, expected_hash in uploads:
        # Freeze the validated identity and hold at most one raster/tensor in memory.
        positive.require_private_path(path, repository)
        if path.stat().st_size != expected_size:
            raise ValueError("Private fixture changed after preflight")
        data = path.read_bytes()
        if len(data) != expected_size or sha256(data) != expected_hash:
            raise ValueError("Private fixture changed after preflight")
        adb.write_private(remote_bundle + "/" + filename, data)
    print(f"Provisioned {len(uploads)} validated private candidate fixtures; production contract unchanged.")


def export_comparison(adb: PrivateAdb, args, repository: Path):
    output = positive.require_private_path(args.output, repository, directory=True)
    remote_bundle = run_directory(REMOTE_BUNDLE, getattr(args, "run_id", None))
    data = adb.read_private(remote_bundle + "/android-positive-result.json", MAX_JSON_BYTES)
    result = json_object(data)
    positive.validate_isolated_contract(result)
    if result.get("schemaVersion") != 1 or result.get("modelSha256") != positive.actual.MODEL_SHA256:
        raise ValueError("Private Android comparison provenance mismatch")
    output.mkdir(parents=True, exist_ok=True)
    path = positive.actual.private_child(output, "android-positive-result.json")
    if path.exists():
        raise ValueError("Comparison result already exists; preserve previous private evidence")
    with path.open("xb") as stream:
        stream.write(data)
    print("Exported private Android comparison JSON only; no contract promotion.")


def create_empty_remote_run(adb: PrivateAdb, remote: str):
    root = adb.verify_debug_device()
    adb.call("exec-out", "run-as", PACKAGE, "mkdir", "-p", remote)
    resolved = adb.call("exec-out", "run-as", PACKAGE, "readlink", "-f", remote).decode("ascii").strip()
    if resolved != root + "/" + remote:
        raise ValueError("Private run directory resolves outside its exact path")
    if adb.call("exec-out", "run-as", PACKAGE, "ls", "-A", remote).strip():
        raise ValueError("Private run already contains evidence; choose a new run id")
    adb.call("exec-out", "run-as", PACKAGE, "mkdir", remote + "/.evidence-claim")
    adb.call("exec-out", "run-as", PACKAGE, "chmod", "700", remote)


def validate_nms_bundle(bundle: Path, repository: Path):
    """Only transport provenance/bounds; production NMS decisions stay in the reference/test."""
    bundle = positive.require_private_path(bundle, repository, directory=True)
    reference_file = positive.require_private_path(bundle / "reference.json", repository)
    if not reference_file.is_file() or not 0 < reference_file.stat().st_size <= MAX_JSON_BYTES:
        raise ValueError("Missing bounded NMS reference")
    encoded = reference_file.read_bytes()
    reference = json_object(encoded)
    if (reference.get("schemaVersion") != 1 or reference.get("validationPurpose") != "EXP02_NMS_CONTRACT"
            or reference.get("modelSha256") != positive.actual.MODEL_SHA256
            or reference.get("ultralyticsVersion") != "8.4.176"
            or reference.get("wheelSha256") != positive.actual.load_pinned_loader(repository).WHEEL_SHA256
            or reference.get("productionContract") not in ("NOT_VERIFIED", "NOT_INSPECTED")
            or reference.get("contractPromotionPerformed") is not False
            or reference.get("predictionUsedForSession") is not False
            or reference.get("modelInferenceExecutedByThisTool") is not False):
        raise ValueError("NMS reference provenance mismatch")
    if reference.get("normalizedOutput") != dict(shape=[1, 6, 8400], dtype="FLOAT32", byteOrder="LITTLE_ENDIAN"):
        raise ValueError("NMS raw tensor contract mismatch")
    if reference.get("thresholds") != dict(confidence=.10, iou=.70, maxDetections=300):
        raise ValueError("NMS thresholds must remain unchanged")
    expected_comparison = dict(classMatch="EXACT", ordering="EXACT_ORDER_EXCEPT_EXACT_SCORE_TIES_IN_BOTH_OUTPUTS",
                               confidenceTolerance=1e-6, bboxTolerancePixels=1e-4)
    if any(reference.get("comparison", {}).get(key) != value for key, value in expected_comparison.items()):
        raise ValueError("Frozen raw NMS tolerances may not widen")
    cases = reference.get("cases")
    if not isinstance(cases, list) or not 1 <= len(cases) <= 14:
        raise ValueError("NMS reference requires 1..14 bounded cases")
    uploads, seen = [], set()
    for case in cases:
        identifier = case.get("id", "")
        if not isinstance(identifier, str) or not re.fullmatch(r"[a-z0-9][a-z0-9_-]{0,70}", identifier) or identifier in seen:
            raise ValueError("Unsafe duplicate NMS case id")
        seen.add(identifier)
        if case.get("evidenceType") not in ("ACTUAL_IMAGE_NMS", "CONTROLLED_MODEL_FORMAT_NMS"):
            raise ValueError("NMS evidence type must distinguish actual and controlled")
        if case["evidenceType"] == "ACTUAL_IMAGE_NMS" and (case.get("sourceModelInferenceExecuted") is not True or
                any(not re.fullmatch(r"[a-f0-9]{64}", case.get(key, "")) for key in ("sourceImageSha256", "sourceReferenceSha256"))):
            raise ValueError("Actual NMS needs recorded actual output provenance")
        positive.actual.validate_input_dimensions(case.get("width"), case.get("height"))
        filename = identifier + ".output.f32"
        if case.get("rawOutputFile") != filename or case.get("rawOutputByteOrder") != "LITTLE_ENDIAN":
            raise ValueError("Unsafe NMS tensor filename/order")
        path = positive.require_private_path(bundle / filename, repository)
        if not path.is_file() or path.stat().st_size != 4 * 6 * 8400:
            raise ValueError("NMS tensor size mismatch")
        data = path.read_bytes()
        if sha256(data) != case.get("rawOutputSha256"):
            raise ValueError("NMS raw tensor SHA256 mismatch")
        uploads.append((filename, path, len(data), sha256(data)))
    uploads.append(("reference.json", reference_file, len(encoded), sha256(encoded)))
    return uploads


def provision_nms(adb: PrivateAdb, args, repository: Path):
    uploads = validate_nms_bundle(args.bundle, repository)
    remote = run_directory(REMOTE_NMS, args.run_id)
    adb.call("exec-out", "am", "force-stop", PACKAGE)
    create_empty_remote_run(adb, remote)
    for filename, path, expected_size, expected_hash in uploads:
        positive.require_private_path(path, repository)
        data = path.read_bytes()
        if len(data) != expected_size or sha256(data) != expected_hash:
            raise ValueError("NMS fixture changed after preflight")
        adb.write_private(remote + "/" + filename, data)
    print(f"Provisioned {len(uploads)} frozen raw NMS fixtures privately; no model or image transfer.")


def export_nms_comparison(adb: PrivateAdb, args, repository: Path):
    output = positive.require_private_path(args.output, repository, directory=True)
    data = adb.read_private(run_directory(REMOTE_NMS, args.run_id) + "/android-nms-result.json", MAX_JSON_BYTES)
    result = json_object(data)
    positive.validate_isolated_contract(result)
    if (result.get("modelSha256") != positive.actual.MODEL_SHA256
            or result.get("contractPromotionPerformed") is not False or result.get("predictionUsedForSession") is not False):
        raise ValueError("NMS result provenance mismatch")
    output.mkdir(parents=True, exist_ok=True)
    with positive.actual.private_child(output, "android-nms-result.json").open("xb") as stream:
        stream.write(data)
    print("Exported scalar NMS comparison JSON only; actual/controlled evidence types remain separate.")


def main():
    repository = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", type=Path, required=True)
    parser.add_argument("--serial", required=True, help="Explicit online authorized DEBUG device")
    actions = parser.add_subparsers(dest="action", required=True)
    export = actions.add_parser("export-candidate", help="Only after the user explicitly pressed save")
    export.add_argument("--candidate", required=True)
    export.add_argument("--output", type=Path, default=repository / "local_validation/exp02_positive")
    provision = actions.add_parser("provision-reference", help="Force-stops DEBUG app; do not run during exercise")
    provision.add_argument("--bundle", type=Path, default=repository / "local_validation/exp02_positive/bundle")
    provision.add_argument("--run-id", help="Unique private device run; retains previous evidence")
    result = actions.add_parser("export-comparison")
    result.add_argument("--output", type=Path, default=repository / "local_validation/exp02_positive/bundle")
    result.add_argument("--run-id")
    nms = actions.add_parser("provision-nms", help="Frozen raw output only, not images/models; force-stops DEBUG app")
    nms.add_argument("--bundle", type=Path, required=True)
    nms.add_argument("--run-id", required=True)
    nms_result = actions.add_parser("export-nms-comparison")
    nms_result.add_argument("--output", type=Path, required=True)
    nms_result.add_argument("--run-id", required=True)
    args = parser.parse_args()
    try:
        adb = PrivateAdb(args.adb, args.serial)
        adb.verify_debug_device()
        {"export-candidate": export_candidate, "provision-reference": provision_reference, "export-comparison": export_comparison,
         "provision-nms": provision_nms, "export-nms-comparison": export_nms_comparison}[args.action](adb, args, repository)
    except (ValueError, OSError, RuntimeError) as error:
        parser.exit(2, f"Private positive transfer blocked: {error}\n")


if __name__ == "__main__":
    main()
