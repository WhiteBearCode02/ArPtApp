"""Pure offline guard tests. These are NOT actual-model or Android Golden execution."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("actual_golden", Path(__file__).with_name("generate-exp02-actual-golden.py"))
actual = importlib.util.module_from_spec(spec)
spec.loader.exec_module(actual)


class ActualGoldenInputTests(unittest.TestCase):
    def manifest(self):
        return dict(schemaVersion=1, samples=[dict(id=f"{exercise.lower()}_{view.lower()}", exercise=exercise,
                    cameraView=view, image="local.png", rightsCleared=True, rightsBasis="Self-created local test; consent recorded privately")
                    for exercise in actual.CLASSES for view in ("FRONT", "SIDE")])

    def read(self, document):
        with tempfile.TemporaryDirectory() as temporary:
            file = Path(temporary) / "manifest.json"
            file.write_text(json.dumps(document), encoding="utf-8")
            return actual.read_manifest(file)

    def test_four_explicit_rights_cleared_directions(self):
        self.assertEqual(4, len(self.read(self.manifest())))

    def test_missing_rights_is_not_inferred(self):
        document = self.manifest()
        document["samples"][0]["rightsCleared"] = False
        with self.assertRaises(ValueError): self.read(document)

    def test_missing_rights_basis_fails(self):
        document = self.manifest()
        document["samples"][0]["rightsBasis"] = " "
        with self.assertRaises(ValueError): self.read(document)

    def test_missing_front_side_coverage_fails(self):
        document = self.manifest()
        document["samples"][0]["cameraView"] = "SIDE"
        with self.assertRaises(ValueError): self.read(document)

    def test_no_invented_45_degree_enum(self):
        document = self.manifest()
        document["samples"][0]["cameraView"] = "ANGLE_45"
        with self.assertRaises(ValueError): self.read(document)

    def test_unsafe_id_fails(self):
        document = self.manifest()
        document["samples"][0]["id"] = "../outside"
        with self.assertRaises(ValueError): self.read(document)

    def test_duplicate_id_fails(self):
        document = self.manifest()
        document["samples"][0]["id"] = document["samples"][1]["id"]
        with self.assertRaises(ValueError): self.read(document)

    def test_schema_mismatch_fails(self):
        document = self.manifest()
        document["schemaVersion"] = 2
        with self.assertRaises(ValueError): self.read(document)

    def test_real_media_output_must_stay_in_ignored_build(self):
        repository = Path(__file__).resolve().parents[1]
        with self.assertRaises(ValueError): actual.require_private_output(repository / "docs/ml/actual", repository)
        self.assertEqual((repository / "build/actual").resolve(), actual.require_private_output(repository / "build/actual", repository))

    def test_cannot_use_entire_build_root(self):
        repository = Path(__file__).resolve().parents[1]
        with self.assertRaises(ValueError): actual.require_private_output(repository / "build", repository)


if __name__ == "__main__":
    unittest.main()
