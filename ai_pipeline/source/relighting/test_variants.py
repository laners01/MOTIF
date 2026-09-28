import unittest

import numpy as np

from source.relighting import variants


class FinishConfigurationTest(unittest.TestCase):
    def test_render_settings_change_cache_key(self):
        first = variants.finish_configuration(
            compose=True, contact_shadow=True, cast_shadow=True, light_angle=35)
        second = variants.finish_configuration(
            compose=True, contact_shadow=True, cast_shadow=False, light_angle=35)
        third = variants.finish_configuration(
            compose=True, contact_shadow=True, cast_shadow=True, light_angle=20)
        self.assertNotEqual(first, second)
        self.assertNotEqual(first, third)
        self.assertEqual(first['encoding'], 'h264-crf14-slow-yuv444p')

    def test_invalid_settings_are_rejected(self):
        with self.assertRaises(TypeError):
            variants.finish_configuration(
                compose=1, contact_shadow=True, cast_shadow=True, light_angle=35)
        with self.assertRaises(ValueError):
            variants.finish_configuration(
                compose=True, contact_shadow=True, cast_shadow=True, light_angle=181)


class TemporalStrengthTest(unittest.TestCase):
    def test_interpolates_regularized_strength_keyframes(self):
        choice = {'selected': {'strength': .25}, 'strength_keyframes': [
            {'frame': 0, 'strength': 0.}, {'frame': 10, 'strength': .5}]}
        self.assertAlmostEqual(variants.strength_at(choice, 5), .25)
        self.assertAlmostEqual(variants.strength_at(choice, 20), .5)


class ComposeFrameTest(unittest.TestCase):
    def test_alpha_blend_preserves_background_outside_object(self):
        background = np.full((32, 32, 3), 80, np.uint8)
        adjusted = np.full((32, 32, 3), 160, np.uint8)
        alpha = np.zeros((32, 32), np.uint8)
        alpha[8:24, 8:24] = 255

        result = variants.compose_frame(
            adjusted, background, alpha, 0, contact_shadow=False, cast_shadow=False)

        self.assertTrue(np.array_equal(result[alpha == 0], background[alpha == 0]))
        self.assertTrue(np.array_equal(result[alpha > 0], adjusted[alpha > 0]))

    def test_shape_mismatch_is_rejected(self):
        image = np.zeros((16, 16, 3), np.uint8)
        with self.assertRaises(ValueError):
            variants.compose_frame(image, image, np.zeros((8, 8), np.uint8), 0)


if __name__ == '__main__':
    unittest.main()
