"""_render_all: 직렬(workers=1) vs 병렬 출력 PNG 바이트 동일성 자체 점검 (cv2/scipy/numpy 필요 — uv run --with 로 실행)."""
import hashlib
import os
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
import cv2  # noqa: E402
import numpy as np  # noqa: E402
from source.enhancement import correction as c  # noqa: E402


def _frames(folder, n, h=240, w=160):
    """구조가 있는 RGBA 프레임(수평 이동)이라 Farneback이 실제 흐름을 계산한다."""
    rng = np.random.default_rng(1); paths = []
    base = (rng.random((h, w, 4)) * 255).astype(np.uint8); base[..., 3] = 255
    xx = np.mgrid[:h, :w][1]
    for i in range(n):
        im = np.roll(base, i * 2, axis=1).copy()
        im[..., :3] = (im[..., :3] * .5 + (((xx + i * 3) % w) * (255 / w) * .5)[..., None]).astype(np.uint8)
        p = folder / f'{i:06d}.png'; assert cv2.imwrite(str(p), im); paths.append(p)
    return paths


class ParallelRenderTest(unittest.TestCase):
    def test_parallel_matches_serial_bytes(self):
        n = 12
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp); src = tmp / 'src'; src.mkdir(); render = _frames(src, n)
            pose = np.zeros((18, 3), np.float32); pose[:, 2] = .9
            pose[:, :2] = [(80, 20), (80, 40), (60, 40), (55, 80), (50, 120), (100, 40), (105, 80), (110, 120), (70, 120),
                           (68, 170), (66, 220), (90, 120), (92, 170), (94, 220), (76, 16), (84, 16), (70, 20), (90, 20)]
            sampled = np.stack([pose] * n); sampled[:, :, :2] += np.random.default_rng(2).normal(0, 1.5, (n, 18, 2)).astype(np.float32)
            offsets = np.random.default_rng(3).normal(0, 2, (n, 18, 2)).astype(np.float32)
            base = np.arange(n, dtype=float)
            selected = np.clip(np.arange(n) + .37, 0, n - 1)   # 소수 시각 → Farneback 경로를 탄다
            digests = {}
            for workers in ('1', '4'):   # 4 워커 × 3프레임 청크
                work = tmp / f'w{workers}'; (work / 'baseline').mkdir(parents=True); (work / 'corrected').mkdir()
                os.environ['MOTIF_CORRECTION_WORKERS'] = workers
                c._render_all(render, work, base, selected, sampled, offsets, n)
                digests[workers] = {str(p.relative_to(work)): hashlib.sha256(p.read_bytes()).hexdigest()
                                    for p in sorted(work.rglob('*.png'))}
            self.assertEqual(len(digests['1']), 2 * n)
            self.assertEqual(digests['1'], digests['4'])


if __name__ == '__main__':
    unittest.main()
