"""Local audio matching and frame-exact video trimming."""
from pathlib import Path
import math
import subprocess

import cv2
import imageio_ffmpeg
import numpy as np
from scipy import signal

FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()
SR = 8000
HOP = 160
MAX_SECONDS = 180


class MatchError(ValueError):
    pass


def run(args):
    result = subprocess.run([FFMPEG, '-nostdin', '-hide_banner', '-loglevel', 'error', *args],
                            capture_output=True, timeout=600)
    if result.returncode:
        raise MatchError('영상을 처리할 수 없습니다. 오디오가 있는 정상적인 동영상인지 확인해 주세요.')
    return result.stdout


def read_video(path):
    cap = cv2.VideoCapture(str(path))
    fps = cap.get(cv2.CAP_PROP_FPS)
    frames = cap.get(cv2.CAP_PROP_FRAME_COUNT)
    cap.release()
    if fps <= 0 or frames <= 0:
        raise MatchError('읽을 수 없는 동영상입니다.')
    duration = frames / fps
    if duration > MAX_SECONDS:
        raise MatchError('현재 버전은 영상당 최대 3분까지 지원합니다.')
    raw = run(['-i', str(path), '-map', '0:a:0', '-vn', '-ac', '1', '-ar', str(SR),
               '-t', str(MAX_SECONDS), '-f', 'f32le', 'pipe:1'])
    audio = np.frombuffer(raw, dtype='<f4').copy()
    if len(audio) < SR * 4 or np.std(audio) < 0.0001:
        raise MatchError('음악이 없거나 너무 짧고 조용해서 비교할 수 없습니다. 4초 이상의 음악이 필요합니다.')
    return audio, min(duration, len(audio) / SR)


def features(audio):
    _, _, spectrum = signal.stft(audio, fs=SR, nperseg=512, noverlap=512-HOP,
                                 boundary=None, padded=False)
    power = abs(spectrum) ** 2
    edges = np.unique(np.geomspace(4, 250, 49).astype(int))
    bands = np.stack([power[a:b].mean(axis=0) for a, b in zip(edges[:-1], edges[1:])])
    bands = np.log(bands + max(float(bands.max()) * 1e-7, 1e-12))
    bands -= np.mean(bands, axis=1, keepdims=True)
    bands /= np.maximum(np.std(bands, axis=1, keepdims=True), 0.5)
    bands -= np.mean(bands, axis=0, keepdims=True)
    bands /= np.maximum(np.linalg.norm(bands, axis=0, keepdims=True), 1e-6)
    return bands


def find_offset(a, b, *, background_features=None):
    """Positive offset means B starts later in the recording than A."""
    if min(len(a), len(b)) < SR * 4:
        raise MatchError('비교 가능한 음악이 4초 이상 필요합니다.')
    fa = features(a)
    fb = features(b) if background_features is None else np.asarray(background_features)
    if fb.ndim != 2 or fb.shape[0] != fa.shape[0] or not np.isfinite(fb).all():
        raise ValueError('저장된 배경 음악 특징이 올바르지 않습니다.')
    na, nb = fa.shape[1], fb.shape[1]
    lags = signal.correlation_lags(na, nb)
    count = np.maximum(0, np.minimum(na, lags + nb) - np.maximum(0, lags))
    correlation = sum(signal.correlate(x, y, mode='full', method='fft') for x, y in zip(fa, fb))
    scores = correlation / np.maximum(count, 1)
    scores[count < 175] = -1  # At least 3.5 seconds of shared music.
    best = int(np.argmax(scores))
    lag = int(lags[best])
    score = float(scores[best])
    ia, ib = max(0, lag), max(0, -lag)
    length = int(count[best])
    local = np.sum(fa[:, ia:ia+length] * fb[:, ib:ib+length], axis=0)
    windows = [float(np.mean(x)) for x in np.array_split(local, 3)]
    if score < 0.48 or min(windows) < 0.30:
        raise MatchError('같은 노래가 아니거나, 일치하는 음악 구간을 찾지 못했습니다. 소음·리믹스·배속 차이 또는 겹치는 구간이 없는 경우에도 이 안내가 표시됩니다.')
    # Reject repeated sections when multiple offsets are equally plausible.
    alternatives = scores[np.abs(lags-lag) > 50]
    if alternatives.size and float(alternatives.max()) > score - 0.035:
        raise MatchError('반복되는 음악 구간 때문에 시작 지점을 확정할 수 없습니다. 더 긴 영상으로 시도해 주세요.')
    offset = lag * HOP / SR
    # Refine to sample precision when the original waveform is preserved.
    start_a, start_b = max(0, lag*HOP), max(0, -lag*HOP)
    size = min(len(a)-start_a, len(b)-start_b, 20*SR)
    xa, xb = a[start_a:start_a+size], b[start_b:start_b+size]
    corr = signal.correlate(xa, xb, mode='full', method='fft')
    center = size - 1
    radius = HOP * 2
    segment = abs(corr[center-radius:center+radius+1])
    delta = int(np.argmax(segment)) - radius
    quality = float(segment.max() / max(np.linalg.norm(xa)*np.linalg.norm(xb), 1e-12))
    if quality > 0.25:
        offset += delta / SR
    return offset, score


def video_size(path):
    """Display dimensions, including rotation and non-square pixels."""
    cap = cv2.VideoCapture(str(path))
    width = cap.get(cv2.CAP_PROP_FRAME_WIDTH)
    height = cap.get(cv2.CAP_PROP_FRAME_HEIGHT)
    sar_num = cap.get(cv2.CAP_PROP_SAR_NUM)
    sar_den = cap.get(cv2.CAP_PROP_SAR_DEN)
    rotation = cap.get(cv2.CAP_PROP_ORIENTATION_META)
    cap.release()
    if width < 2 or height < 2:
        raise MatchError('영상의 해상도를 읽을 수 없습니다.')
    sar = sar_num / sar_den if sar_num > 0 and sar_den > 0 else 1
    if int(rotation) % 180:
        height *= sar
    else:
        width *= sar
    return max(2, round(width)), max(2, round(height))


def common_resolution(paths):
    sizes = [video_size(path) for path in paths]
    width, height = min(sizes, key=lambda size: (size[0]*size[1], size))
    return width // 2 * 2, height // 2 * 2


def trim(path, output, start, frames, target):
    duration = frames / 30
    width, height = video_size(path)
    target_w, target_h = target
    ratio = min(1, target_w / width, target_h / height)
    scaled_w = max(2, math.floor(width * ratio / 2) * 2)
    scaled_h = max(2, math.floor(height * ratio / 2) * 2)
    geometry = (f'scale={scaled_w}:{scaled_h}:flags=lanczos,setsar=1,'
                f'pad={target_w}:{target_h}:(ow-iw)/2:(oh-ih)/2:color=black')
    run(['-ss', f'{start:.6f}', '-i', str(path), '-t', f'{duration:.9f}',
         '-map', '0:v:0', '-map', '0:a:0',
         '-vf', f'setpts=PTS-STARTPTS,fps=30,{geometry},tpad=stop_mode=clone:stop_duration=0.1,trim=end_frame={frames}',
         '-af', f'asetpts=PTS-STARTPTS,aresample=48000,apad,atrim=end_sample={frames*1600}',
         '-c:v', 'libx264', '-preset', 'fast', '-crf', '18', '-pix_fmt', 'yuv420p',
         '-c:a', 'aac', '-ar', '48000', '-movflags', '+faststart', '-n', str(output)])
    cap = cv2.VideoCapture(str(output))
    actual = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    cap.release()
    if actual != frames or video_size(output) != target:
        raise MatchError('출력 영상의 길이 또는 해상도를 검증하지 못했습니다. 다른 영상으로 시도해 주세요.')


def synchronize(paths, directory, progress=lambda *args, **kwargs: None):
    progress(0.05, desc='첫 번째 영상의 음악 읽는 중')
    a, da = read_video(paths[0])
    progress(0.20, desc='두 번째 영상의 음악 읽는 중')
    b, db = read_video(paths[1])
    progress(0.40, desc='같은 음악과 시작 지점 비교 중')
    offset, score = find_offset(a, b)
    starts = [max(0, offset), max(0, -offset)]
    frames = math.floor(min(da-starts[0], db-starts[1]) * 30)
    if frames < 30:
        raise MatchError('두 영상에서 겹치는 구간이 너무 짧습니다.')
    target = common_resolution(paths)
    outputs = []
    for i, (path, start) in enumerate(zip(paths, starts)):
        progress(0.55 + i*0.20, desc=f'{i+1}번째 영상 길이·해상도 맞추는 중')
        output = Path(directory) / f'{i+1}_synced.mp4'
        trim(path, output, start, frames, target)
        outputs.append(str(output))
    return outputs, starts, frames / 30
