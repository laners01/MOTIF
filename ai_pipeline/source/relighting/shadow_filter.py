"""Extract broad darkening from a relit reference; composite onto original pixels."""
import time
import hashlib
import os

import cv2
import numpy as np

from source.relighting import model_test as rel


_values = np.arange(256, dtype=np.float32) / 255
_LINEAR = np.where(_values <= 0.04045, _values / 12.92, ((_values + 0.055) / 1.055) ** 2.4)
_values = np.linspace(0, 1, 65536, dtype=np.float32)
_SRGB = (np.where(_values <= 0.0031308, _values * 12.92,
                  1.055 * _values ** (1 / 2.4) - 0.055) * 255).round().astype(np.uint8)


def linear(image):
    return _LINEAR[image]


def shadow_frame(original, reference, strength, previous=None):
    if not np.isfinite(strength) or not 0 <= strength <= 1:
        raise ValueError('그림자 강도는 0~1이어야 합니다.')
    if original.shape != reference.shape:
        raise ValueError('원본과 참고 프레임 크기가 다릅니다.')
    h, w = original.shape[:2]
    scale = min(1, 256 / max(h, w))
    size = (max(1, round(w * scale)), max(1, round(h * scale)))
    luminance = []
    for frame in (original, reference):
        small = cv2.resize(frame, size, interpolation=cv2.INTER_AREA)
        gray = linear(small) @ np.array([0.0722, 0.7152, 0.2126], np.float32)
        luminance.append(cv2.GaussianBlur(gray, (0, 0), 8))
    source, target = luminance
    gain = np.clip((target + 1e-4) / (source + 1e-4), 0, 1)
    if previous is not None:
        gain = 0.25 * previous + 0.75 * gain
    return apply_gain(original, gain, strength), gain


def apply_gain(original, gain, strength):
    if strength == 0:
        return original.copy()
    h, w = original.shape[:2]
    full_gain = cv2.resize(gain, (w, h), interpolation=cv2.INTER_LINEAR)
    shaded = linear(original) * (1 - strength + strength * full_gain[..., None])
    # 16-bit lookup bounds the added conversion error to one 8-bit code value.
    result = _SRGB[(shaded.clip(0, 1) * 65535).round().astype(np.uint16)]
    return np.minimum(original, result)


def apply(result, strength=0.6):
    """CPU streaming export. Saved reference must match the unchanged source video."""
    if not np.isfinite(strength) or not 0 <= strength <= 1:
        raise ValueError('그림자 강도는 0~1이어야 합니다.')
    result = rel.ex.local(result, exists=True)
    record = rel.ex.read_json(rel.ex.local(result / 'experiment.json', exists=True))
    source = rel.ex.local(record['source'], exists=True)
    reference = rel.ex.local(result / 'relit.mp4', exists=True)
    if rel.ex.digest(source) != record['source_sha256']:
        raise ValueError('추론 이후 원본 영상이 변경되었습니다. 해당 영상으로 새 추론이 필요합니다.')
    for path in (source, reference):
        info, _ = rel.video_info(path)
        if (info['frames'] != record['frames'] or info['size'] != record['size']
                or abs(info['fps'] - record['fps']) > 1e-4):
            raise ValueError('원본/참고 영상의 프레임 수·FPS·해상도가 추론 기록과 다릅니다.')
    reference_hash = rel.ex.digest(reference)
    key = hashlib.sha256((record['source_sha256'] + reference_hash + 'gain-v1-256-8-0.25').encode()).hexdigest()
    cache_dir = rel.ex.local('work/relighting_model_test/shadow_cache') / key
    cache_dir.mkdir(parents=True, exist_ok=True)
    cache_file = rel.ex.local(cache_dir / 'gains.npy')
    w, h = record['size']
    scale = min(1, 256 / max(h, w))
    cache_shape = (record['frames'], max(1, round(h * scale)), max(1, round(w * scale)))
    cached = np.load(cache_file, mmap_mode='r', allow_pickle=False) if cache_file.exists() else None
    if cached is not None and (cached.shape != cache_shape or cached.dtype != np.float32):
        raise ValueError('그림자 마스크 캐시 형식 불일치')
    output_cache = rel.ex.local(cache_dir / f'output_lut16_{float(strength).hex()}.json')
    if output_cache.exists():
        saved = rel.ex.read_json(output_cache)
        ready = rel.ex.local(saved['output'])
        if rel.ex.local(ready / 'shadow.mp4').is_file():
            info, _ = rel.video_info(ready / 'shadow.mp4')
            if (info['frames'] == record['frames'] and info['size'] == record['size']
                    and abs(info['fps'] - record['fps']) < 1e-4):
                print('동일한 그림자 결과 재사용:', ready)
                return ready
    out = rel.ex.unique_dir('output/relighting_model_test', 'shadow')
    metadata = {**{k: record[k] for k in ('source', 'source_sha256', 'frames', 'fps', 'size', 'lighting')},
                'reference': str(reference.relative_to(rel.ROOT)), 'strength': float(strength),
                'method': 'linear_luminance_ratio_darkening', 'map_max_side': 256,
                'blur_sigma': 8, 'previous_weight': 0.25, 'status': 'running',
                'encoding': 'H264 CRF 0 yuv444p', 'audio': False}
    metadata.update(gain_cache=str(cache_file.relative_to(rel.ROOT)), cache_hit=cached is not None,
                    reference_sha256=reference_hash, transfer='16bit_lookup_max_1_code_error')
    started = time.perf_counter()

    def frames():
        original_cap = cv2.VideoCapture(str(source))
        reference_cap = cv2.VideoCapture(str(reference)) if cached is None else None
        pending = None
        if cached is None:
            pending = rel.ex.unique_dir(cache_dir, 'build') / 'gains.npy'
            gains = np.lib.format.open_memmap(pending, mode='w+', dtype=np.float32, shape=cache_shape)
        previous = None
        try:
            for index in range(record['frames']):
                ok, original = original_cap.read()
                if not ok:
                    raise ValueError(f'{index}번 프레임 디코딩 실패')
                if cached is None:
                    ref_ok, ref = reference_cap.read()
                    if not ref_ok:
                        raise ValueError(f'{index}번 참고 프레임 디코딩 실패')
                    frame, previous = shadow_frame(original, ref, strength, previous)
                    gains[index] = previous
                else:
                    frame = apply_gain(original, cached[index], strength)
                yield frame
            if original_cap.read()[0] or (reference_cap is not None and reference_cap.read()[0]):
                raise ValueError('기록보다 많은 프레임이 있습니다.')
            if pending is not None:
                gains.flush()
                del gains
                os.replace(pending, cache_file)
        finally:
            original_cap.release()
            if reference_cap is not None:
                reference_cap.release()

    stream = frames()
    try:
        rel.ex.encode_frames(stream, out / 'shadow.mp4', record['fps'])
        info, _ = rel.video_info(out / 'shadow.mp4')
        if (info['frames'] != record['frames'] or info['size'] != record['size']
                or abs(info['fps'] - record['fps']) > 1e-4):
            raise ValueError('그림자 영상의 프레임 수·FPS·해상도 보존 검사 실패')
        metadata['status'] = 'complete'
    except BaseException as error:
        metadata.update(status='failed', error=str(error))
        raise
    finally:
        stream.close()
        metadata['seconds'] = round(time.perf_counter() - started, 3)
        rel.ex.write_json(out / 'experiment.json', metadata)
    rel.ex.write_json(output_cache, {'output': str(out.relative_to(rel.ROOT))})
    return out
