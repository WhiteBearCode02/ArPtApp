"""Offline byte transport/provenance guards only; never contacts a real device."""
import importlib.util
import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest import mock

spec = importlib.util.spec_from_file_location("positive_transfer", Path(__file__).with_name("transfer-exp02-positive.py"))
transfer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(transfer)

NAME = "positive_candidate_20261017_123456_123_abcdef01.png"


class PositiveTransferTests(unittest.TestCase):
    def image(self):
        # Header-only fixture; no decoded/private media or actual-model result is generated.
        return transfer.positive.PNG_SIGNATURE + b"\x00\x00\x00\x0dIHDR" + struct.pack(">II", 100, 100) + b"\x00\x1a\xffbinary-tail"

    def result(self, image, detections=None, executed=True):
        detections = [] if detections is None else detections
        return dict(schemaVersion=1, modelId="yolo26n_exp02_fp32", modelSha256=transfer.positive.actual.MODEL_SHA256,
                    image=NAME, imageSha256=transfer.sha256(image), sourceImageSha256=transfer.sha256(image), decodedRgbSha256="a" * 64, width=100, height=100,
                    productionContract="NOT_VERIFIED", thresholds=dict(confidence=.10, iou=.70, maxDetections=300),
                    androidNativeInferenceExecuted=executed, nativeInferenceCount=1 if executed else 0,
                    detections=detections, detectionsAfterNms=len(detections), classOrdering=[item["classId"] for item in detections])

    def detection(self):
        return dict(classId=1, className="SHOULDER_PRESS", confidence=.9, bbox=dict(x1=1., y1=2., x2=20., y2=30.))

    def test_candidate_name_exactly_allowlisted(self):
        self.assertEqual(NAME, transfer.validate_candidate_name(NAME))
        for name in ("candidate.png", "../" + NAME, NAME + ";id", "/" + NAME, NAME.replace("abcdef01", "ABCDEFGH")):
            with self.subTest(name=name), self.assertRaises(ValueError):
                transfer.validate_candidate_name(name)

    def test_empty_and_wrong_label_results_preserved(self):
        image = self.image()
        transfer.validate_android_candidate(self.result(image), NAME, image)
        result = self.result(image, [self.detection()])
        # There is intentionally no expected-label field or correction in transfer validation.
        transfer.validate_android_candidate(result, NAME, image)
        self.assertEqual("SHOULDER_PRESS", result["detections"][0]["className"])

    def test_failure_result_exportable_but_fake_detection_without_inference_rejected(self):
        image = self.image()
        transfer.validate_android_candidate(self.result(image, executed=False), NAME, image)
        with self.assertRaises(ValueError):
            transfer.validate_android_candidate(self.result(image, [self.detection()], executed=False), NAME, image)

    def test_native_run_count_must_be_one(self):
        image = self.image()
        result = self.result(image)
        result["nativeInferenceCount"] = 0
        with self.assertRaises(ValueError):
            transfer.validate_android_candidate(result, NAME, image)

    def test_image_identity_dimensions_thresholds_contract_and_model_are_not_mutable(self):
        image = self.image()
        mutations = [("imageSha256", "0" * 64), ("image", "other.png"), ("width", 101), ("modelId", "wrong"),
                     ("modelSha256", "0" * 64), ("productionContract", "VERIFIED"),
                     ("thresholds", dict(confidence=.01, iou=.70, maxDetections=300))]
        for key, value in mutations:
            result = self.result(image)
            result[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                transfer.validate_android_candidate(result, NAME, image)

    def test_inconsistent_class_name_count_order_and_clipped_box_rejected(self):
        image = self.image()
        result = self.result(image, [self.detection()])
        mutations = [lambda item: item.update(detectionsAfterNms=0), lambda item: item.update(classOrdering=[0]),
                     lambda item: item["detections"][0].update(className="SQUAT"),
                     lambda item: item["detections"][0]["bbox"].update(x2=1.),
                     lambda item: item["detections"][0].update(confidence=2.)]
        for mutate in mutations:
            changed = json.loads(json.dumps(result))
            mutate(changed)
            with self.assertRaises(ValueError):
                transfer.validate_android_candidate(changed, NAME, image)

    def test_json_nonfinite_oversize_or_nonobject_rejected(self):
        for data in (b'{"confidence":NaN}', b"[]", b"", b"x" * (transfer.MAX_JSON_BYTES + 1)):
            with self.assertRaises(ValueError):
                transfer.json_object(data)

    def test_png_wrong_signature_and_missing_dimensions_rejected(self):
        for image in (b"bad", transfer.positive.PNG_SIGNATURE):
            with self.assertRaises(ValueError):
                transfer.validate_android_candidate(self.result(image), NAME, image)

    def test_read_binary_keeps_nul_ctrl_z_high_bytes_and_final_tail(self):
        data = bytes(range(256)) * 256 + b"\x00\x1a\xfflast142" * 14
        adb = object.__new__(transfer.PrivateAdb)
        adb.remote_identity = mock.Mock(return_value=(len(data), transfer.sha256(data)))
        adb.call = mock.Mock(return_value=data)
        self.assertEqual(data, adb.read_private(transfer.REMOTE_CANDIDATES + "/" + NAME, transfer.MAX_PNG_BYTES))
        self.assertIn("count=" + str(len(data)), adb.call.call_args.args)
        self.assertIn("iflag=count_bytes", adb.call.call_args.args)

    def test_truncated_changed_or_oversized_export_fails(self):
        data = b"\x00\x1a\xfftail"
        for returned in (data[:-1], data + b"extra", b"corrupt"):
            adb = object.__new__(transfer.PrivateAdb)
            adb.remote_identity = mock.Mock(return_value=(len(data), transfer.sha256(data)))
            adb.call = mock.Mock(return_value=returned)
            with self.subTest(returned=returned), self.assertRaises(ValueError):
                adb.read_private(transfer.REMOTE_CANDIDATES + "/" + NAME, 100)
        adb = object.__new__(transfer.PrivateAdb)
        adb.remote_identity = mock.Mock(side_effect=[(len(data), transfer.sha256(data)), (len(data) + 1, transfer.sha256(data))])
        adb.call = mock.Mock(return_value=data)
        with self.assertRaises(ValueError):
            adb.read_private(transfer.REMOTE_CANDIDATES + "/" + NAME, 100)

    def test_private_upload_exact_count_and_atomic_hash_checked_rename(self):
        data = bytes(range(256)) + b"\x1a\x00tail"
        adb = object.__new__(transfer.PrivateAdb)
        adb.call = mock.Mock(return_value=b"")
        adb.remote_identity = mock.Mock(return_value=(len(data), transfer.sha256(data)))
        remote = transfer.REMOTE_BUNDLE + "/reference.json"
        adb.write_private(remote, data)
        calls = adb.call.call_args_list
        self.assertEqual(data, calls[0].kwargs["data"])
        self.assertIn("count=" + str(len(data)), calls[0].args)
        self.assertIn("conv=fsync", calls[0].args)
        self.assertIn("mv", calls[2].args)
        self.assertEqual(remote, calls[2].args[-1])
        self.assertIn("rm", calls[-1].args)
        self.assertIn(".upload-", calls[-1].args[-1])

    def test_failed_private_upload_never_renames_or_deletes_original(self):
        adb = object.__new__(transfer.PrivateAdb)
        adb.call = mock.Mock(return_value=b"")
        adb.remote_identity = mock.Mock(return_value=(1, "0" * 64))
        remote = transfer.REMOTE_BUNDLE + "/reference.json"
        with self.assertRaises(ValueError):
            adb.write_private(remote, b"expected")
        self.assertFalse(any("mv" in call.args for call in adb.call.call_args_list))
        self.assertNotEqual(remote, adb.call.call_args_list[-1].args[-1])

    def test_explicit_serial_online_and_exact_debug_private_root_required(self):
        adb = object.__new__(transfer.PrivateAdb)
        adb.call = mock.Mock(side_effect=[b"device\n", b"/data/user/0/com.example.arptapp\n"])
        self.assertEqual("/data/user/0/com.example.arptapp", adb.verify_debug_device())
        for state, root in ((b"offline", b""), (b"device", b"/sdcard/public"), (b"device", b"/data/user/0/other.package")):
            adb.call = mock.Mock(side_effect=[state, root])
            with self.assertRaises(RuntimeError):
                adb.verify_debug_device()

    def test_remote_metadata_limit_and_hash_are_checked(self):
        adb = object.__new__(transfer.PrivateAdb)
        for size, hashed in ((b"999999", b"0" * 64), (b"0", b"0" * 64), (b"not-size", b"0" * 64), (b"10", b"not-hash")):
            adb.call = mock.Mock(side_effect=[size, hashed])
            with self.assertRaises(ValueError):
                adb.remote_identity(transfer.REMOTE_CANDIDATES + "/" + NAME, 100)

    def test_adb_timeout_and_remote_stderr_not_exposed(self):
        adb = object.__new__(transfer.PrivateAdb)
        adb.executable, adb.serial = Path("adb.exe"), "approved-serial"
        with mock.patch.object(transfer.subprocess, "run", return_value=mock.Mock(returncode=1, stdout=b"", stderr=b"private remote diagnostic")):
            with self.assertRaisesRegex(RuntimeError, "native text suppressed") as error:
                adb.call("get-state")
            self.assertNotIn("private remote diagnostic", str(error.exception))
        with mock.patch.object(transfer.subprocess, "run", side_effect=transfer.subprocess.TimeoutExpired("adb", 30)):
            with self.assertRaisesRegex(RuntimeError, "30-second"):
                adb.call("get-state")
        with self.assertRaises(ValueError):
            adb.call("get-state;id")

    def test_no_public_storage_pull_network_or_model_copy_workflow(self):
        source = Path(transfer.__file__).read_text(encoding="utf-8")
        for forbidden in ('"/sdcard', '"/storage', '"pull"', '"push"', '"adb root"', '"curl"', '"wget"', '"assets/'):
            self.assertNotIn(forbidden, source)
        self.assertIn('"exec-out"', source)
        self.assertIn('"exec-in"', source)
        self.assertIn('"run-as"', source)

    def test_bundle_hash_length_and_byte_order_validated_manifest_published_last(self):
        with tempfile.TemporaryDirectory() as temporary:
            repository = Path(temporary).resolve()
            bundle = repository / "local_validation/exp02_positive/bundle"
            bundle.mkdir(parents=True)
            rgb, tensor = b"\x00\x1a\xff", b"\x00\x1a\xffTENSOR"
            (bundle / "candidate.rgb").write_bytes(rgb)
            (bundle / "candidate.input.f32").write_bytes(tensor)
            reference = dict(schemaVersion=1, validationPurpose="POSITIVE_CANDIDATE", modelInferenceExecuted=True, exportContractVerified=False,
                model=dict(id="yolo26n_exp02_fp32", filename=transfer.positive.actual.MODEL_NAME, bytes=transfer.positive.actual.MODEL_BYTES, sha256=transfer.positive.actual.MODEL_SHA256),
                ultralyticsVersion="8.4.176", wheelSha256="15f4bbac5c69fd9a5a9395f5957a7ce561e3eb4620699a9c2ff11ed38ec7d34c",
                thresholds=dict(confidence=.10, iou=.70, maxDetections=300), tolerances=dict(confidence=.001, bboxPixels=2., detectionCount=0, classMatch="EXACT"),
                samples=[dict(id="candidate", width=1, height=1, rgbFile="candidate.rgb", rgbSha256=transfer.sha256(rgb),
                    inputTensorFile="candidate.input.f32", inputTensorSha256=transfer.sha256(tensor), inputTensorByteOrder="LITTLE_ENDIAN")])
            file = bundle / "reference.json"
            file.write_text(json.dumps(reference), encoding="utf-8")
            with mock.patch.object(transfer.positive.subprocess, "run", return_value=mock.Mock(returncode=0, stdout=b"")), mock.patch.object(transfer, "TENSOR_BYTES", len(tensor)):
                uploads = transfer.validate_bundle(bundle, repository)
                self.assertEqual(["candidate.rgb", "candidate.input.f32", "reference.json"], [item[0] for item in uploads])
                for field, value in (("inputTensorByteOrder", "BIG_ENDIAN"), ("rgbSha256", "0" * 64), ("rgbFile", "../outside.rgb")):
                    changed = json.loads(json.dumps(reference))
                    changed["samples"][0][field] = value
                    file.write_text(json.dumps(changed), encoding="utf-8")
                    with self.subTest(field=field), self.assertRaises(ValueError):
                        transfer.validate_bundle(bundle, repository)


if __name__ == "__main__":
    unittest.main()
