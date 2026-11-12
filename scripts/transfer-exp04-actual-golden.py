"""Opt-in EXP04 fixture transport to ONE .exp04 DEBUG private sandbox.

Does not install/stop the app, transfer models, use shared staging or contact a server.
Reference is published last into a NEW run; old EXP02/03 and EXP04 runs are preserved.
"""
from __future__ import annotations
import argparse
import importlib.util
from pathlib import Path
import re
import subprocess


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


golden = load("exp04_reference_tool", "generate-exp04-actual-golden.py")
transport = load("exp04_binary_transport", "transfer-exp02-positive.py")
PACKAGE = "com.example.arptapp.exp04"
BASE = "no_backup/yolo/exp04-actual-golden/runs"
INPUT_BYTES, OUTPUT_BYTES = 4 * 3 * 640 * 640, 4 * 11 * 8400


def run_path(identifier):
    if not isinstance(identifier, str) or not re.fullmatch(r"[a-z0-9][a-z0-9_-]{0,63}", identifier):
        raise ValueError("Explicit safe unique run id required")
    return BASE + "/" + identifier


def validate_bundle(bundle: Path, repository: Path):
    bundle = golden.private_path(bundle, repository)
    path = golden.private_path(bundle / "reference.json", repository)
    if not path.is_file() or not 0 < path.stat().st_size <= 2_097_152:
        raise ValueError("No bounded actual EXP04 reference; Golden NOT_RUN")
    encoded = path.read_bytes()
    document = transport.json_object(encoded)
    model = dict(id=golden.MODEL_ID, filename=golden.MODEL_NAME, bytes=golden.MODEL_BYTES, sha256=golden.MODEL_SHA256)
    if (document.get("schemaVersion") != 1 or document.get("validationPurpose") != "EXP04_ACTUAL_IMAGE_GOLDEN"
            or document.get("model") != model or document.get("ultralyticsVersion") != golden.VERSION
            or document.get("wheelSha256") != golden.WHEEL_SHA256 or document.get("classes") != golden.CLASSES
            or document.get("thresholds") != golden.THRESHOLDS or document.get("tolerances") != golden.TOLERANCES
            or document.get("preprocessingAcceptance") != "EXACT_TENSOR_BYTES_SHA256"
            or document.get("tensorByteOrder") != "LITTLE_ENDIAN"
            or document.get("modelInferenceExecuted") is not True
            or document.get("androidNativeInferenceExecuted") is not False
            or document.get("contractPromotionPerformed") is not False or document.get("exportContractVerified") is not False):
        raise ValueError("EXP04 identity/source/class/threshold/verification boundary mismatch")
    samples = document.get("samples")
    if not isinstance(samples, list) or not 7 <= len(samples) <= 64 or document.get("nativeInferenceCount") != len(samples):
        raise ValueError("Seven-class actual inference records required")
    seen, covered, uploads = set(), set(), []
    for sample in samples:
        identifier = sample.get("id", "")
        if not isinstance(identifier, str) or not re.fullmatch(r"[a-z0-9][a-z0-9_-]{0,63}", identifier) or identifier in seen:
            raise ValueError("Invalid duplicate/path sample id")
        seen.add(identifier)
        if (sample.get("exercise") not in golden.CLASSES or sample.get("cameraView") not in ("FRONT", "SIDE", "DIAGONAL")
                or sample.get("rightsCleared") is not True or not isinstance(sample.get("rightsBasis"), str) or not sample["rightsBasis"].strip()):
            raise ValueError("Explicit actual class/view/rights required")
        covered.add(sample["exercise"])
        width, height = sample.get("width"), sample.get("height")
        golden.validate_dimensions(width, height)
        if not re.fullmatch(r"[a-f0-9]{64}", sample.get("sourceImageSha256", "")):
            raise ValueError("Source image identity missing")
        for suffix, size in ((".rgb", width * height * 3), (".input.f32", INPUT_BYTES), (".output.f32", OUTPUT_BYTES)):
            info = sample.get("payloads", {}).get(suffix, {})
            filename = identifier + suffix
            if info.get("file") != filename or info.get("bytes") != size or not re.fullmatch(r"[a-f0-9]{64}", info.get("sha256", "")):
                raise ValueError("Invalid fixture basename/size/hash")
            file = golden.private_path(bundle / filename, repository)
            if not file.is_file() or file.stat().st_size != size or transport.sha256(file.read_bytes()) != info["sha256"]:
                raise ValueError("Fixture bytes/hash mismatch")
            uploads.append((filename, file, size, info["sha256"]))
    if covered != set(golden.CLASSES):
        raise ValueError("All seven expected exercise classes required, regardless of predictions")
    uploads.append(("reference.json", path, len(encoded), transport.sha256(encoded)))
    return uploads


def verify_one_device(adb):
    result = subprocess.run([str(adb.executable), "devices"], capture_output=True, timeout=30, check=True)
    listed = [line.split() for line in result.stdout.decode("ascii").splitlines()[1:] if line.strip()]
    if len(listed) != 1 or listed[0] != [adb.serial, "device"]:
        raise ValueError("Exactly one authorized device matching the explicit serial required")
    return adb.verify_debug_device()


def provision(adb, bundle, run_id, repository):
    uploads = validate_bundle(bundle, repository)
    root = verify_one_device(adb)
    remote = run_path(run_id)
    current = ""
    for segment in BASE.split("/"):
        current = current + "/" + segment if current else segment
        if adb.call("exec-out", "run-as", PACKAGE, "readlink", "-f", current).decode("ascii").strip() != root + "/" + current:
            raise ValueError("Private parent link/path mismatch")
        adb.call("exec-out", "run-as", PACKAGE, "mkdir", "-p", current)
    if adb.call("exec-out", "run-as", PACKAGE, "readlink", "-f", remote).decode("ascii").strip() != root + "/" + remote:
        raise ValueError("Private run link/path mismatch")
    adb.call("exec-out", "run-as", PACKAGE, "mkdir", remote)  # exclusive; preserve prior runs
    adb.call("exec-out", "run-as", PACKAGE, "chmod", "700", remote)
    for index, (filename, path, size, digest) in enumerate(uploads, 1):
        data = golden.private_path(path, repository).read_bytes()
        if len(data) != size or transport.sha256(data) != digest:
            raise ValueError("Fixture changed after validation")
        target = remote + "/" + filename
        if adb.call("exec-out", "run-as", PACKAGE, "readlink", "-f", target).decode("ascii").strip() != root + "/" + target:
            raise ValueError("Private target link/path mismatch")
        adb.write_private(target, data)
        print(f"EXP04 private fixture size/hash verified: {index}/{len(uploads)}", flush=True)
    print("EXP04 private fixture transfer complete; reference published last; NOT VERIFIED")


def export_result(adb, bundle, run_id, repository):
    validate_bundle(bundle, repository)
    root = verify_one_device(adb)
    remote = run_path(run_id) + "/android-result.json"
    if adb.call("exec-out", "run-as", PACKAGE, "readlink", "-f", remote).decode("ascii").strip() != root + "/" + remote:
        raise ValueError("Private result link/path mismatch")
    destination = golden.private_path(bundle / "android-result.json", repository)
    if destination.exists():
        raise ValueError("Preserve old result; use a new run")
    data = adb.read_private(remote, 2_097_152)
    document = transport.json_object(data)
    if (document.get("modelId") != golden.MODEL_ID or document.get("modelSha256") != golden.MODEL_SHA256
            or document.get("referenceSha256") != transport.sha256((bundle / "reference.json").read_bytes())
            or document.get("contractPromotionPerformed") is not False):
        raise ValueError("Result is not bound to this EXP04 reference")
    with destination.open("xb") as stream:
        stream.write(data)
    print("Exported bounded EXP04 scalar comparison JSON only; no contract promotion")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("check-bundle", "provision", "export-result"))
    parser.add_argument("--bundle", required=True, type=Path)
    parser.add_argument("--run-id")
    parser.add_argument("--adb", type=Path); parser.add_argument("--serial")
    args = parser.parse_args()
    try:
        repository = Path(__file__).resolve().parents[1]
        if args.action == "check-bundle":
            print(f"BUNDLE_PREFLIGHT_PASS: {len(validate_bundle(args.bundle, repository))} files; Android NOT_RUN")
            return
        run_path(args.run_id)
        if args.adb is None or not args.serial:
            raise ValueError("Explicit --adb and --serial required for device operations")
        adb = transport.PrivateAdb(args.adb, args.serial, package=PACKAGE)
        (provision if args.action == "provision" else export_result)(adb, args.bundle, args.run_id, repository)
    except (ValueError, RuntimeError, OSError, KeyError, TypeError, subprocess.SubprocessError) as error:
        parser.exit(2, f"EXP04 fixture transfer blocked: {error}\n")

if __name__ == "__main__": main()
