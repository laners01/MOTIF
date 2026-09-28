"""Use the local music matcher before run_depth tracking; preserve each video's geometry."""
import hashlib
import json
import math
import subprocess
import time
from fractions import Fraction

import cv2
import numpy as np
from music_sync import core
from source.util import removal_experiments as ex


def _video_info(path):
    cap = cv2.VideoCapture(str(path))
    try:
        fps = cap.get(cv2.CAP_PROP_FPS)
        frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        size = [int(cap.get(cv2.CAP_PROP_FRAME_WIDTH)), int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))]
    finally:
        cap.release()
    if not math.isfinite(fps) or fps <= 0 or frames < 1 or min(size) < 2:
        raise ValueError(f"영상 정보를 읽을 수 없습니다: {path}")
    return dict(fps=fps, frames=frames, size=size)


def _trim(source, output, start, frames, fps):
    duration = frames / fps
    # Decimal FPS with excessive precision can create near-int32-limit MP4 time
    # bases whose final B-frames disappear in older FFmpeg/OpenCV decoders.
    rate = Fraction(str(fps)).limit_denominator(100000)
    # No common-resolution resize or 30 FPS override from the standalone app.
    video_filter = (f"setpts=PTS-STARTPTS,fps={rate.numerator}/{rate.denominator},"
                    f"tpad=stop_mode=clone:stop_duration={2/fps:.9f},trim=end_frame={frames},"
                    "pad=ceil(iw/2)*2:ceil(ih/2)*2")
    audio_filter = (f"asetpts=PTS-STARTPTS,aresample=48000,"
                    f"apad=whole_dur={duration:.9f},atrim=end_sample={round(duration*48000)}")
    command = [core.FFMPEG, "-nostdin", "-hide_banner", "-loglevel", "error", "-n",
               "-ss", f"{start:.9f}", "-i", str(source), "-t", f"{duration:.9f}",
               "-map", "0:v:0", "-map", "0:a:0", "-vf", video_filter, "-af", audio_filter,
               "-c:v", "libx264", "-preset", "fast", "-crf", "18", "-pix_fmt", "yuv420p",
               "-c:a", "aac", "-ar", "48000", "-movflags", "+faststart", str(output)]
    log_path = output.with_suffix(".ffmpeg.log")
    with log_path.open("wb") as log:
        result = subprocess.run(command, stdout=subprocess.DEVNULL, stderr=log, timeout=600)
    if result.returncode:
        raise RuntimeError(f"음악 정렬 영상 저장 실패: {log_path}\n" + log_path.read_text(errors="replace")[-4000:])
    info = _video_info(output)
    source_size = _video_info(source)["size"]
    expected_size = [n + n % 2 for n in source_size]
    if info["frames"] != frames or abs(info["fps"]-fps) > 1e-4 or info["size"] != expected_size:
        raise RuntimeError(f"음악 정렬 출력의 프레임/FPS/해상도가 다릅니다: {info}")


def frame_times(source):
    """Read presentation times from decoded frames, without an FPS conversion."""
    source = ex.local(source, exists=True)
    cap = cv2.VideoCapture(str(source)); times = []
    try:
        while True:
            ok, _ = cap.read()
            if not ok: break
            times.append(cap.get(cv2.CAP_PROP_POS_MSEC) / 1000.)
    finally:
        cap.release()
    times = np.asarray(times, dtype=float)
    if not len(times) or not np.isfinite(times).all() or np.any(np.diff(times) <= 0):
        raise ValueError(f"원본 프레임 시각을 읽을 수 없습니다: {source.name}")
    return times - times[0]


def _trim_native(source, output, start, duration):
    """Keep actual frames plus interpolation neighbors; record their music-relative times."""
    times = frame_times(source); info = _video_info(source)
    first = max(0, int(np.searchsorted(times, start, side='right')) - 1)
    stop = min(len(times), int(np.searchsorted(times, start + duration)) + 1)
    clip_duration = times[stop-1] - times[first] + 1 / info['fps']
    video_filter = (f"trim=start_frame={first}:end_frame={stop},setpts=PTS-STARTPTS,"
                    "pad=ceil(iw/2)*2:ceil(ih/2)*2")
    audio_filter = (f"atrim=start={times[first]:.9f}:end={times[first]+clip_duration:.9f},"
                    "asetpts=PTS-STARTPTS")
    # Passthrough preserves PTS but some encoders leave the final packet duration
    # at zero, making OpenCV report N/(N-1) times the real FPS. Fill only that tail.
    tail_duration = round(1000000 / info['fps'])
    command = [core.FFMPEG, '-nostdin', '-hide_banner', '-loglevel', 'error', '-n',
               '-i', str(source), '-map', '0:v:0', '-map', '0:a:0?',
               '-vf', video_filter, '-af', audio_filter,
               '-fps_mode', 'passthrough', '-enc_time_base', '1:1000000',
               '-c:v', 'libx264', '-preset', 'fast', '-crf', '0', '-bf', '0', '-pix_fmt', 'yuv420p',
               '-bsf:v', f"setts=duration='if(eq(N,{stop-first-1}),{tail_duration},DURATION)'",
               '-video_track_timescale', '1000000', '-c:a', 'aac', str(output)]
    log_path = output.with_suffix('.ffmpeg.log')
    with log_path.open('wb') as log:
        result = subprocess.run(command, stdout=subprocess.DEVNULL, stderr=log, timeout=600)
    if result.returncode:
        raise RuntimeError('원본 시각 유지 영상 저장 실패:\n' + log_path.read_text(errors='replace')[-4000:])
    actual = frame_times(output)
    expected = times[first:stop] - times[first]
    if len(actual) != len(expected) or not np.allclose(actual, expected, atol=2e-5, rtol=0):
        raise RuntimeError('전경 프레임 수 또는 원본 시각이 변했습니다.')
    return dict(version=1, source=str(source.relative_to(ex.ROOT)),
                source_first_frame=first, source_frames=stop-first,
                music_start_seconds=float(start), times=(times[first:stop]-start).tolist())


def prepare(foreground, background, *, progress=None, preserve_foreground_timing=False):
    """Trim confident common music; keep original inputs when matching is unavailable."""
    started = time.perf_counter()
    paths = [ex.local(p, exists=True) for p in (foreground, background)]
    infos = [_video_info(p) for p in paths]
    inputs = dict(foreground=str(paths[0].relative_to(ex.ROOT)), background=str(paths[1].relative_to(ex.ROOT)))
    hashes = [ex.digest(p) for p in paths]
    signature = dict(inputs=inputs, hashes=hashes, matcher=ex.digest(core.__file__),
                     implementation=ex.digest(__file__))
    if preserve_foreground_timing:
        signature['foreground_timing_version'] = 1
    key = hashlib.sha256(json.dumps(signature, sort_keys=True).encode()).hexdigest()
    cache_dir = ex.local(".cache/music_sync"); cache_dir.mkdir(parents=True, exist_ok=True)
    cache = cache_dir / (key + ".json")
    if cache.exists():
        saved = ex.read_json(cache)
        outputs = [ex.local(saved[k]) for k in ("foreground", "background")]
        if all(p.is_file() for p in outputs) and [ex.digest(p) for p in outputs] == saved["output_hashes"]:
            if progress: progress(1, "음악 정렬 결과 재사용")
            return saved
    if progress: progress(.1, "두 영상의 음악 읽기")
    reason = None
    try:
        a, da = core.read_video(paths[0])
        b, db = core.read_video(paths[1])
        if progress: progress(.4, "같은 음악의 시작점 찾기")
        offset, score = core.find_offset(a, b)
        starts = [max(0., offset), max(0., -offset)]
        fps = infos[1]["fps"]
        frames = math.floor(min(da-starts[0], db-starts[1]) * fps)
        if frames < fps:
            raise core.MatchError("두 영상의 공통 음악 구간이 1초보다 짧습니다.")
    except core.MatchError as exc:
        reason = str(exc)
    folder = ex.unique_dir("output/music_sync", "pair")
    record = dict(inputs=inputs, input_hashes=hashes, manifest=str((folder/"sync.json").relative_to(ex.ROOT)))
    if reason is not None:
        record.update(status="unchanged", reason=reason, foreground=inputs["foreground"], background=inputs["background"])
        outputs = paths
        if preserve_foreground_timing:
            times = frame_times(paths[0])
            record['foreground_timing'] = dict(version=1, source=inputs['foreground'],
                source_first_frame=0, source_frames=len(times), music_start_seconds=0., times=times.tolist())
    else:
        outputs = [folder/"foreground.mp4", folder/"background.mp4"]
        for i, (source, output) in enumerate(zip(paths, outputs)):
            if progress: progress(.5+i*.2, f"{'전경' if i == 0 else '배경'} 공통 구간 저장")
            if preserve_foreground_timing and i == 0:
                record['foreground_timing'] = _trim_native(source, output, starts[i], frames/fps)
            else:
                _trim(source, output, starts[i], frames, fps)
        record.update(status="matched", offset_seconds=offset, score=score,
                      starts=dict(foreground=starts[0], background=starts[1]),
                      frames=frames, fps=fps, seconds=frames/fps,
                      foreground=str(outputs[0].relative_to(ex.ROOT)), background=str(outputs[1].relative_to(ex.ROOT)))
    record.update(output_hashes=[ex.digest(p) for p in outputs], processing_seconds=time.perf_counter()-started)
    ex.write_json(folder/"sync.json", record)
    ex.write_json(cache, record)
    if progress: progress(1, "음악 정렬 완료" if reason is None else "음악 정렬 생략 · 원본 사용")
    return record
