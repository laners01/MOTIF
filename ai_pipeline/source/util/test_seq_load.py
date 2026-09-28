"""seq_load 분기(경로/파일류/부재) 자체 점검. torch 왕복은 torch가 있는 env(이미지)에서만 실행된다."""
import importlib.util
import io
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import seq_load  # noqa: E402

HAS_TORCH = importlib.util.find_spec('torch') is not None


class SlurpTest(unittest.TestCase):
    def test_reads_real_path_and_delegates_everything_else(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / 'w.bin'; p.write_bytes(b'abc')
            self.assertEqual(seq_load._slurp(str(p)), b'abc')
            self.assertEqual(seq_load._slurp(p), b'abc')          # os.PathLike
            self.assertIsNone(seq_load._slurp(io.BytesIO(b'x')))  # 파일류는 원본에 위임
            self.assertIsNone(seq_load._slurp(str(p) + '.missing'))  # 부재 경로도 원본이 원래 오류를 낸다


@unittest.skipUnless(HAS_TORCH, 'torch 없는 env(로컬)에서는 건너뜀 — 이미지 env에서 실행')
class TorchLoadPatchTest(unittest.TestCase):
    def test_install_is_idempotent_and_roundtrips_bytes_identical(self):
        import torch
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / 't.pth'; want = {'w': torch.arange(6.).reshape(2, 3)}
            torch.save(want, p)
            seq_load.install('test'); seq_load.install('test')       # 재호출 안전
            self.assertTrue(getattr(torch.load, '_seq_patched', False))
            got = torch.load(str(p), map_location='cpu', weights_only=True, mmap=True)  # mmap는 무시돼야 한다
            self.assertTrue(torch.equal(got['w'], want['w']))
            with self.assertRaises(Exception):                         # 부재 경로는 원본 오류 그대로
                torch.load(str(p) + '.missing')


if __name__ == '__main__':
    unittest.main()
