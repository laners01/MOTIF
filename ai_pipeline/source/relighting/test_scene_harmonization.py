import unittest

import numpy as np

from source.relighting import automatic
from source.relighting import scene_harmonization as scene


class SceneHarmonizationTest(unittest.TestCase):
    def test_transform_is_confidence_weighted_and_bounded(self):
        source = {'luma': .05, 'log_iqr': .8, 'white': [1., 1., 1.], 'confidence': 1.}
        target = {'luma': .8, 'log_iqr': .2, 'white': [2., 1., .5], 'confidence': 1.}
        result = scene.transform(source, target)
        self.assertLessEqual(abs(result['exposure']), .35)
        self.assertTrue(.92 <= min(result['white']) <= max(result['white']) <= 1.08)
        self.assertTrue(.95 <= result['gamma'] <= 1.05)
        self.assertEqual(result['look'], [1., 1., 1.])
        self.assertEqual(result['saturation'], 1.)

    def test_contrast_moves_in_target_direction(self):
        source = {'luma': .2, 'log_iqr': .8, 'white': [1., 1., 1.], 'confidence': 1.}
        target = {'luma': .2, 'log_iqr': .2, 'white': [1., 1., 1.], 'confidence': 1.}
        result = scene.transform(source, target)
        self.assertLess(result['gamma'], 1.)

    def test_identity_tone_curve_preserves_rgb(self):
        image = np.full((8, 8, 4), 128, np.uint8)
        image[..., 3] = 255
        record = {'exposure': 0., 'white': [1., 1., 1.], 'look': [1., 1., 1.],
                  'saturation': 1., 'tone': [[.05, .05], [.18, .18], [.7, .7]], 'gamma': 1.}
        result = scene.apply(image, record)
        self.assertTrue(np.allclose(result[..., :3], image[..., :3], atol=1))

    def test_zero_confidence_produces_identity(self):
        source = {'luma': .1, 'log_iqr': .5, 'white': [1., 1., 1.], 'confidence': 0.}
        target = {'luma': .8, 'log_iqr': .2, 'white': [2., 1., .5], 'confidence': 1.}
        result = scene.transform(source, target)
        self.assertEqual(result['exposure'], 0.)
        self.assertTrue(np.allclose(result['white'], 1.))
        self.assertEqual(result['gamma'], 1.)

    def test_apply_preserves_alpha_and_changes_all_foreground_rgb(self):
        image = np.full((8, 8, 4), 128, np.uint8)
        image[..., 3] = np.arange(8, dtype=np.uint8)[:, None] * 32
        result = scene.apply(image, {'exposure': .5, 'white': [1., 1., 1.], 'gamma': 1.})
        self.assertTrue(np.array_equal(result[..., 3], image[..., 3]))
        self.assertGreater(result[..., :3].mean(), image[..., :3].mean())

    def test_apply_reduces_new_highlight_clipping(self):
        image = np.full((16, 16, 4), 220, np.uint8)
        image[..., 3] = 255
        result = scene.apply(image, {'exposure': 1., 'white': [1., 1., 1.], 'gamma': 1.})
        self.assertGreater(result[..., :3].mean(), image[..., :3].mean())
        self.assertLess(result[..., :3].max(), 253)

    def test_directional_residual_removes_uniform_global_gain(self):
        original = np.full((64, 64, 3), 80, np.uint8)
        reference = np.full((64, 64, 3), 120, np.uint8)
        alpha = np.full((64, 64), 255, np.uint8)
        residual = automatic.directional_gain_map(original, reference, alpha)
        self.assertTrue(np.allclose(residual, 1., atol=1e-4))


if __name__ == '__main__':
    unittest.main()
