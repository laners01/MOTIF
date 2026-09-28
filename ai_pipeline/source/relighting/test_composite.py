import unittest

import numpy as np

from source.relighting import composite


class ForegroundFrameTimeTest(unittest.TestCase):
    def test_preserves_seconds_across_different_frame_rates(self):
        self.assertEqual(composite.foreground_frame_time(15, 0, 60, 30, 100, 'hold'), 30.0)
        self.assertEqual(composite.foreground_frame_time(15, 0, 24, 30, 100, 'hold'), 12.0)
        self.assertEqual(composite.foreground_frame_time(20, 5, 60, 30, 100, 'hold'), 30.0)

    def test_fractional_time_is_retained_for_motion_interpolation(self):
        self.assertAlmostEqual(
            composite.foreground_frame_time(1, 0, 24, 30, 100, 'hold'), .8)

    def test_shortfall_modes_use_time_mapped_position(self):
        self.assertEqual(composite.foreground_frame_time(6, 0, 60, 30, 10, 'loop'), 2.0)
        self.assertEqual(composite.foreground_frame_time(6, 0, 60, 30, 10, 'hold'), 9.0)
        self.assertIsNone(composite.foreground_frame_time(6, 0, 60, 30, 10, 'disappear'))
        self.assertIsNone(composite.foreground_frame_time(4, 5, 60, 30, 10, 'loop'))

    def test_motion_blur_requires_three_pixel_displacement(self):
        foreground = np.zeros((24, 24, 4), np.uint8)
        foreground[8:16, 8:16] = [40, 80, 120, 255]
        unchanged = composite._motion_blur(foreground, 2.9, 0.)
        blurred = composite._motion_blur(foreground, 4., 0.)
        self.assertTrue(np.array_equal(unchanged, foreground))
        self.assertFalse(np.array_equal(blurred, foreground))

    def test_invalid_rate_is_rejected(self):
        with self.assertRaises(ValueError):
            composite.foreground_frame_time(0, 0, 0, 30, 10, 'loop')


if __name__ == '__main__':
    unittest.main()
