"""Pure stdlib metadata parser guards; these tests never run model inference."""
import importlib.util
from pathlib import Path
import struct
import unittest


spec = importlib.util.spec_from_file_location("exp02_metadata", Path(__file__).with_name("inspect-exp02-model.py"))
metadata = importlib.util.module_from_spec(spec)
spec.loader.exec_module(metadata)


class FlatBufferMetadataTests(unittest.TestCase):
    def test_truncated_header_rejected(self):
        with self.assertRaises(ValueError):
            metadata.FlatBufferMetadataReader(b"TFL3").tensors()

    def test_wrong_identifier_rejected(self):
        with self.assertRaises(ValueError):
            metadata.FlatBufferMetadataReader(b"\x08\x00\x00\x00BAD!").tensors()

    def test_out_of_bounds_root_rejected(self):
        with self.assertRaises(ValueError):
            metadata.FlatBufferMetadataReader(struct.pack("<I", 1024) + b"TFL3").tensors()

    def test_negative_region_rejected(self):
        with self.assertRaises(ValueError):
            metadata.FlatBufferMetadataReader(b"1234").region(-1, 1)

    def test_oversized_table_rejected(self):
        reader = metadata.FlatBufferMetadataReader(struct.pack("<HHI", 6, 65535, 4))
        with self.assertRaises(ValueError):
            reader.field(4, 0)

    def test_canonical_shapes_and_dtype_accepted(self):
        metadata.validate_tensor_contract(dict(inputs=[dict(shape=[1, 3, 640, 640], dtypeEnum=0)],
                                              outputs=[dict(shape=[1, 6, 8400], dtypeEnum=0)]))

    def test_shape_mismatch_rejected(self):
        with self.assertRaises(ValueError):
            metadata.validate_tensor_contract(dict(inputs=[dict(shape=[1, 640, 640, 3], dtypeEnum=0)],
                                                  outputs=[dict(shape=[1, 6, 8400], dtypeEnum=0)]))

    def test_non_float_dtype_rejected(self):
        with self.assertRaises(ValueError):
            metadata.validate_tensor_contract(dict(inputs=[dict(shape=[1, 3, 640, 640], dtypeEnum=9)],
                                                  outputs=[dict(shape=[1, 6, 8400], dtypeEnum=0)]))

    def test_missing_output_rejected(self):
        with self.assertRaises(ValueError):
            metadata.validate_tensor_contract(dict(inputs=[dict(shape=[1, 3, 640, 640], dtypeEnum=0)], outputs=[]))

    def test_wrong_model_filename_rejected_without_reading_weights(self):
        with self.assertRaises(ValueError):
            metadata.inspect_model(Path(__file__))

    def test_public_output_rejected(self):
        repository = Path(__file__).resolve().parents[1]
        with self.assertRaises(ValueError):
            metadata.private_output_path(repository / "docs/ml/metadata.json", repository)

    def test_ignored_local_output_accepted(self):
        repository = Path(__file__).resolve().parents[1]
        output = repository / "local_validation/exp02_golden/metadata-test.json"
        self.assertEqual(output.resolve(), metadata.private_output_path(output, repository))

    def test_non_json_output_rejected(self):
        repository = Path(__file__).resolve().parents[1]
        with self.assertRaises(ValueError):
            metadata.private_output_path(repository / "local_validation/exp02_golden/metadata.jpg", repository)


if __name__ == "__main__":
    unittest.main()
