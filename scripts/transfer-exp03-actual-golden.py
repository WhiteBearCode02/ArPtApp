"""Explicit EXP03-only private fixture transfer. No model/DB/root/shared-storage/network operation.

Binary-safe transport is reused unchanged from EXP02, NOT its model or inference evidence.
Fresh exclusive run directories and reference-last publication preserve previous validation.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import re
import subprocess

import importlib.util


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


golden = load("exp03_actual_generator", "generate-exp03-actual-golden.py")
transport = load("exp03_existing_binary_transport", "transfer-exp02-positive.py")
BASE = "no_backup/yolo/exp03-actual-golden/runs"
PACKAGE = "com.example.arptapp"


def run_path(identifier):
    if not isinstance(identifier, str) or not re.fullmatch(r"[a-z0-9][a-z0-9_-]{0,63}", identifier):
        raise ValueError("Explicit safe unique run id required")
    return BASE + "/" + identifier


def validate_bundle(bundle: Path, repository: Path):
    bundle = golden.private_path(bundle, repository)
    path = golden.private_path(bundle / "reference.json", repository)
    if not path.is_file() or not 0 < path.stat().st_size <= 2_097_152:
        raise ValueError("Missing bounded EXP03 reference")
    encoded = path.read_bytes()
    document = transport.json_object(encoded)
    expected = dict(id=golden.MODEL_ID, filename=golden.MODEL_NAME, bytes=golden.MODEL_BYTES, sha256=golden.MODEL_SHA256)
    if (document.get("schemaVersion") != 1 or document.get("validationPurpose") != "EXP03_ACTUAL_IMAGE_GOLDEN"
            or document.get("model") != expected or document.get("ultralyticsVersion") != golden.VERSION
            or document.get("wheelSha256") != golden.WHEEL_SHA256
            or document.get("modelInferenceExecuted") is not True or document.get("exportContractVerified") is not False
            or document.get("thresholds") != golden.THRESHOLDS or document.get("tolerances") != golden.TOLERANCES):
        raise ValueError("EXP03 reference identity/source/unchanged threshold/tolerance mismatch")
    samples = document.get("samples")
    if not isinstance(samples, list) or not 4 <= len(samples) <= 64:
        raise ValueError("Four-way actual image coverage required")
    if document.get("nativeInferenceCount") != len(samples):
        raise ValueError("Actual Python native inference count mismatch")
    covered, seen, uploads = set(), set(), []
    for sample in samples:
        identifier = sample.get("id", "")
        if not isinstance(identifier, str) or not re.fullmatch(r"[a-z0-9][a-z0-9_-]{0,63}", identifier) or identifier in seen:
            raise ValueError("Invalid duplicate/path sample id")
        seen.add(identifier)
        pair = sample.get("exercise"), sample.get("cameraView")
        if pair not in golden.shared.REQUIRED or sample.get("rightsCleared") is not True or not sample.get("rightsBasis", "").strip():
            raise ValueError("No inferred rights or coverage")
        covered.add(pair)
        width, height = sample.get("width"), sample.get("height")
        golden.shared.validate_input_dimensions(width, height)
        for suffix, key, hash_key, size in (
                (".rgb", "rgbFile", "rgbSha256", width * height * 3),
                (".input.f32", "inputTensorFile", "inputTensorSha256", golden.TENSOR_BYTES),
                (".output.f32", "nativeOutputFile", "nativeOutputSha256", 4 * 6 * 8400)):
            filename = identifier + suffix
            if sample.get(key) != filename or not re.fullmatch(r"[a-f0-9]{64}", sample.get(hash_key, "")):
                raise ValueError("Invalid fixture basename/hash")
            file = golden.private_path(bundle / filename, repository)
            if not file.is_file() or file.stat().st_size != size or golden.sha256(file.read_bytes()) != sample[hash_key]:
                raise ValueError("Private fixture size/SHA mismatch")
            uploads.append((filename, file, size, sample[hash_key]))
    if covered != golden.shared.REQUIRED:
        raise ValueError("Missing actual four-way image coverage")
    uploads.append(("reference.json", path, len(encoded), golden.sha256(encoded)))
    return uploads


def verify_one_device(adb):
    result = subprocess.run([str(adb.executable), "devices"], capture_output=True, timeout=30, check=True)
    listed = [line.split() for line in result.stdout.decode("ascii").splitlines()[1:] if line.strip()]
    if len(listed) != 1 or listed[0] != [adb.serial, "device"]:
        raise ValueError("Exactly one authorized USB device matching the explicit serial is required")
    return adb.verify_debug_device()


def provision(adb, bundle, run_id, repository):
    uploads = validate_bundle(bundle, repository)
    root = verify_one_device(adb)
    remote = run_path(run_id)
    # Validate EACH parent before creation, not just the final path after mkdir.
    current = ""
    for segment in BASE.split("/"):
        current = current + "/" + segment if current else segment
        resolved = adb.call("exec-out", "run-as", PACKAGE, "readlink", "-f", current).decode("ascii").strip()
        if resolved != root + "/" + current:
            raise ValueError("Private parent symlink/path mismatch")
        adb.call("exec-out", "run-as", PACKAGE, "mkdir", "-p", current)
    if adb.call("exec-out", "run-as", PACKAGE, "readlink", "-f", remote).decode("ascii").strip() != root + "/" + remote:
        raise ValueError("Private run path mismatch")
    adb.call("exec-out", "run-as", PACKAGE, "mkdir", remote)  # exclusive, never overwrite a previous run
    adb.call("exec-out", "run-as", PACKAGE, "chmod", "700", remote)
    # Fixture provisioning never needs to stop the app. Installation/instrumentation is a separate explicit action.
    for filename, path, size, digest in uploads:
        data = golden.private_path(path, repository).read_bytes()
        if len(data) != size or golden.sha256(data) != digest:
            raise ValueError("Private fixture changed after preflight")
        target = remote + "/" + filename
        if adb.call("exec-out", "run-as", PACKAGE, "readlink", "-f", target).decode("ascii").strip() != root + "/" + target:
            raise ValueError("Private target link/path mismatch")
        adb.write_private(target, data)
    print(f"EXP03 private bundle PASS: {len(uploads)} files, device size/SHA verified, reference published last")


def export_result(adb, bundle, run_id, repository):
    verify_one_device(adb)
    bundle = golden.private_path(bundle, repository)
    destination = golden.shared.private_child(bundle, "android-result.json")
    if destination.exists():
        raise ValueError("Preserve previous result: choose a new run")
    data = adb.read_private(run_path(run_id) + "/android-result.json", 2_097_152)
    document = transport.json_object(data)
    reference_hash = golden.sha256((bundle / "reference.json").read_bytes())
    if (document.get("modelId") != golden.MODEL_ID or document.get("modelSha256") != golden.MODEL_SHA256
            or document.get("referenceSha256") != reference_hash):
        raise ValueError("Android result is not bound to this EXP03 run/reference")
    with destination.open("xb") as stream:
        stream.write(data)
    print("Exported this EXP03 run's scalar comparison JSON only; no contract promotion")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True, type=Path)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--bundle", required=True, type=Path)
    parser.add_argument("action", choices=("provision", "export-result"))
    args = parser.parse_args()
    try:
        run_path(args.run_id)
        adb = transport.PrivateAdb(args.adb, args.serial)
        (provision if args.action == "provision" else export_result)(adb, args.bundle, args.run_id, Path(__file__).resolve().parents[1])
    except (ValueError, RuntimeError, OSError, subprocess.SubprocessError) as error:
        parser.exit(2, f"EXP03 private transfer blocked: {error}\n")


if __name__ == "__main__":
    main()
