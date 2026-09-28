"""Overlapping temporal windows and streaming blends for SVOR."""
import numpy as np


def windows(count, size, overlap):
    if count < 1 or size < 5 or (size - 1) % 4 or overlap < 1 or 2 * overlap >= size:
        raise ValueError("SVOR 구간: frames>0, chunk_frames=4n+1 (>=5), 0<overlap<chunk_frames/2 필요")
    start = 0
    while start < count:
        end = min(start + size, count)
        yield start, end
        if end == count:
            break
        start = end - overlap


def blended_frames(count, size, overlap, predict):
    """predict(start,end) returns float RGB [T,H,W,C] for real (unpadded) frames."""
    pending = None
    spatial_shape = None
    for start, end in windows(count, size, overlap):
        frames = np.asarray(predict(start, end), dtype=np.float32).copy()
        if frames.ndim != 4 or frames.shape[0] != end - start or frames.shape[-1] != 3:
            raise ValueError("SVOR 구간 출력 프레임 수/형식 불일치")
        if spatial_shape is not None and frames.shape[1:] != spatial_shape:
            raise ValueError("SVOR 구간 출력 해상도 불일치")
        spatial_shape = frames.shape[1:]
        if pending is not None:
            n = len(pending)
            alpha = (np.arange(1, n + 1, dtype=np.float32) / (n + 1))[:, None, None, None]
            frames[:n] = pending * (1 - alpha) + frames[:n] * alpha
        emit = len(frames) if end == count else len(frames) - overlap
        yield from frames[:emit]
        pending = frames[emit:].copy()
