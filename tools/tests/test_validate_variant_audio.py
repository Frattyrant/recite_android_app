import unittest

from tools.validate_variant_audio import pause_tolerance


class VariantAudioValidationTest(unittest.TestCase):
    def test_pause_tolerance_scales_for_opus_padding_per_segment(self):
        self.assertEqual(0.12, pause_tolerance(1))
        self.assertGreaterEqual(pause_tolerance(20), 0.20)


if __name__ == "__main__":
    unittest.main()
