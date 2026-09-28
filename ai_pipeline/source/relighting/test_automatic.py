import unittest

import numpy as np

from source.relighting import automatic


class GainMapTest(unittest.TestCase):
    def test_neutral_exposure_uses_equal_channel_gains(self):
        original = np.full((128, 128, 3), 80, np.uint8)
        reference = np.full((128, 128, 3), 110, np.uint8)
        alpha = np.full((128, 128), 255, np.uint8)

        gain = automatic.gain_map(original, reference, alpha)

        self.assertTrue(np.allclose(gain[..., 0], gain[..., 1], atol=1e-5))
        self.assertTrue(np.allclose(gain[..., 1], gain[..., 2], atol=1e-5))

    def test_reference_tint_produces_channel_specific_gain(self):
        original = np.full((128, 128, 3), 100, np.uint8)
        reference = original.copy()
        reference[..., 0] = 135
        reference[..., 2] = 80
        alpha = np.full((128, 128), 255, np.uint8)

        gain = automatic.gain_map(original, reference, alpha)
        center = gain[64, 64]

        self.assertGreater(center[0], center[1])
        self.assertGreater(center[1], center[2])
        self.assertGreaterEqual(float(gain.min()), .65)
        self.assertLessEqual(float(gain.max()), 1.55)

    def test_apply_gain_preserves_pixels_outside_alpha(self):
        original = np.full((32, 32, 3), 100, np.uint8)
        alpha = np.zeros((32, 32), np.uint8)
        alpha[8:24, 8:24] = 255
        gain = np.empty((32, 32, 3), np.float32)
        gain[:] = (1.2, 1.0, .8)

        result = automatic.apply_gain(original, alpha, gain, .75)

        self.assertTrue(np.array_equal(result[alpha == 0], original[alpha == 0]))
        self.assertFalse(np.array_equal(result[alpha > 0], original[alpha > 0]))


    def test_background_neutral_pixels_influence_tint(self):
        original = np.full((128, 128, 3), 100, np.uint8)
        reference = original.copy()
        background = np.full((128, 128, 3), (108, 100, 94), np.uint8)
        alpha = np.zeros((128, 128), np.uint8)
        alpha[32:96, 32:96] = 255
        without_scene = automatic.gain_map(original, reference, alpha)
        with_scene = automatic.gain_map(original, reference, alpha, background)
        self.assertGreater(with_scene[64, 64, 0], without_scene[64, 64, 0])
        self.assertLess(with_scene[64, 64, 2], without_scene[64, 64, 2])

    def test_soft_alpha_does_not_attenuate_gain_before_composite(self):
        original = np.full((8, 8, 3), 100, np.uint8)
        alpha = np.full((8, 8), 64, np.uint8)
        gain = np.full((8, 8, 3), 1.3, np.float32)
        soft = automatic.apply_gain(original, alpha, gain, .75)
        opaque = automatic.apply_gain(original, np.full_like(alpha, 255), gain, .75)
        self.assertTrue(np.array_equal(soft, opaque))

    def test_trusted_reference_relaxes_dark_pixel_protection(self):
        original = np.full((8, 8, 3), 20, np.uint8)
        alpha = np.full((8, 8), 255, np.uint8)
        gain = np.full((8, 8, 3), 1.5, np.float32)
        guarded = automatic.apply_gain(original, alpha, gain, 1., reference_confidence=0.)
        trusted = automatic.apply_gain(original, alpha, gain, 1., reference_confidence=1.)
        self.assertGreater(trusted.mean(), guarded.mean())

    def test_neutral_tint_reports_missing_evidence(self):
        image = np.full((32, 32, 3), (0, 0, 255), np.uint8)
        tint, confidence = automatic._neutral_tint(image, np.full((32, 32), 255, np.uint8))
        self.assertEqual(confidence, 0.)
        self.assertTrue(np.array_equal(tint, np.ones(3, np.float32)))

    def test_skin_colour_is_not_semantically_special_cased(self):
        original = np.empty((32, 64, 3), np.uint8)
        original[:, :32] = (90, 130, 180)
        original[:, 32:] = (130, 130, 130)
        alpha = np.full((32, 64), 255, np.uint8)
        gain = np.full((32, 64, 3), 1.2, np.float32)
        result = automatic.apply_gain(original, alpha, gain, 1., reference_confidence=1.)
        skin_ratio = automatic.linear(result[:, :32]).mean()/automatic.linear(original[:, :32]).mean()
        neutral_ratio = automatic.linear(result[:, 32:]).mean()/automatic.linear(original[:, 32:]).mean()
        self.assertAlmostEqual(float(skin_ratio), float(neutral_ratio), delta=.03)


if __name__ == '__main__':
    unittest.main()
