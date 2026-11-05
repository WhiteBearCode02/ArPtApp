"""Small synthetic generator checks; NOT model/native/actual-image Golden proof."""
import importlib.util
from pathlib import Path
import unittest
from unittest import mock

spec = importlib.util.spec_from_file_location("exp03_interpolation_vectors", Path(__file__).with_name("generate-exp03-interpolation-vectors.py"))
vectors = importlib.util.module_from_spec(spec)
spec.loader.exec_module(vectors)


class InterpolationVectorTests(unittest.TestCase):
    def test_shapes_cover_native_borders_upscale_downscale_both_orientations(self):
        self.assertEqual(len(vectors.SHAPES), 16)
        for shape in ((1,1), (2,2), (640,640), (1280,720), (720,1280), (1,640), (640,1)):
            self.assertIn(shape, vectors.SHAPES)

    def test_counter_random_is_fixed_integer_formula_not_numpy_rng(self):
        first = vectors.rgb_image(5, 7, "counter_random")
        self.assertTrue((first == vectors.rgb_image(5, 7, "counter_random")).all())
        self.assertFalse((first == vectors.rgb_image(5, 7, "gradient")).all())

    def test_no_silent_opencv_version_substitution(self):
        with mock.patch.object(vectors.cv2, "__version__", "4.12.0"):
            with self.assertRaises(ValueError): vectors.generate()

    def test_optimization_diagnostic_restores_original_settings(self):
        before = vectors.cv2.useOptimized(), vectors.cv2.ipp.useIPP()
        with mock.patch.object(vectors, "SHAPES", [(3, 2)]):
            document = vectors.generate()
        self.assertEqual(before, (vectors.cv2.useOptimized(), vectors.cv2.ipp.useIPP()))
        self.assertEqual(4, len(document["cases"]))
        self.assertTrue(all(c["defaultVsUnoptimizedMismatches"] == 0 for c in document["cases"]))


if __name__ == "__main__":
    unittest.main()
