"""gcsfuse 랜덤 mmap 로딩 병목 회피 — 가중치를 순차 RAM read 후 bytes에서 역직렬화.

서브프로세스 진입점(main 시작)에서 vendor 모델 import·load보다 먼저 install(tag)을 부른다.
읽는 방식(mmap→순차)만 바뀌고 텐서는 바이트 동일 → 품질·핵심 로직 불변.
RelightVid(variant_worker)/light_worker의 인라인 safetensors 패치와 같은 트릭에 torch.load(.pth/.ckpt)를 더한 것.
"""
import io
import os
import time


def _slurp(f):
    """f가 실제 파일 경로면 통째로 순차 read한 bytes, 아니면 None(파일류·버퍼·부재 경로는 원본에 위임)."""
    if isinstance(f, (str, os.PathLike)) and os.path.isfile(f):
        with open(f, 'rb') as fh:
            return fh.read()
    return None


def install(tag):
    """torch.load / safetensors.load_file(경로 인자)를 순차-read 판으로 갈아끼우고 로딩 경과를 stdout에 찍는다. 재호출 안전."""
    import torch

    def _log(kind, data, t0):
        print(f'[{tag} 로딩] {kind} 순차 read {len(data)/2**30:.2f}GiB · {time.perf_counter()-t0:.1f}s', flush=True)

    if not getattr(torch.load, '_seq_patched', False):
        _torch_load = torch.load

        def seq_torch_load(f, *args, **kwargs):
            t0 = time.perf_counter(); data = _slurp(f)
            if data is None:
                return _torch_load(f, *args, **kwargs)
            _log('torch', data, t0)
            kwargs.pop('mmap', None)   # 이미 RAM에 있으므로 무의미(BytesIO는 mmap 불가)
            return _torch_load(io.BytesIO(data), *args, **kwargs)
        seq_torch_load._seq_patched = True
        torch.load = seq_torch_load

    try:
        import safetensors.torch as st
    except ImportError:   # 이 env엔 safetensors가 없다 → 쓸 일도 없다
        return
    if not getattr(st.load_file, '_seq_patched', False):
        _load_file = st.load_file

        def seq_load_file(path, device='cpu'):
            t0 = time.perf_counter(); data = _slurp(path)
            if data is None:
                return _load_file(path, device)
            _log('safetensors', data, t0)
            sd = st.load(data)
            return sd if device in (None, 'cpu') else {k: v.to(device) for k, v in sd.items()}
        seq_load_file._seq_patched = True
        st.load_file = seq_load_file
