"""Single-person extraction and existing RelightVid lighting on an untouched background."""
from pathlib import Path
import os
import shutil
import signal
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import cv2
import numpy as np
from source.util import removal_experiments as ex
from source.service import prepost
from source.relighting import automatic, composite as cp, model_test as rel, variants

PLACEMENT_VERSION = 'full-frame-contain-v1'


def contain_transform(source_size, target_size):
    """CSS object-fit: contain, object-position: center; full-frame edge coordinates."""
    fw, fh = source_size
    w, h = target_size
    scale = min(w/fw, h/fh)
    return dict(x=(w-fw*scale)/2, y=(h-fh*scale)/2, scale=scale, subpixel=True)


def contain_frame(rgba, target_size):
    """Transform RGB and alpha together once; never crop to a person or mirror."""
    h, w = rgba.shape[:2]
    transform = contain_transform((w, h), target_size)
    # OpenCV maps pixel centers, whereas CSS sizes/offsets describe pixel edges.
    shift = (transform['scale']-1)/2
    affine = dict(transform, x=transform['x']+shift, y=transform['y']+shift)
    placed = cp._affine_foreground(rgba, (target_size[1], target_size[0], 3), affine)
    yy, xx = np.ogrid[:target_size[1], :target_size[0]]
    inside = ((xx+.5 >= transform['x']) & (xx+.5 < transform['x']+w*transform['scale'])
              & (yy+.5 >= transform['y']) & (yy+.5 < transform['y']+h*transform['scale']))
    placed[~inside] = 0  # Letterbox space is transparent, including resampling support.
    return placed


def check_inputs(background, foreground):
    paths = [ex.local(p, exists=True) for p in (background, foreground)]
    variants.require_backend('RelightVid')
    for name in ('models/rfdetr/rf-detr-small.pth', 'models/sam2.1_hiera_large.pt'):
        ex.local(name, exists=True)
    infos = [rel.video_info(p)[0] for p in paths]
    if any(i['frames'] < 1 or not np.isfinite(i['fps']) or i['fps'] <= 0 for i in infos):
        raise ValueError('입력 영상의 프레임 수와 FPS를 확인하세요.')
    if any(n % 2 for n in infos[0]['size']):
        raise ValueError('H.264 호환 출력을 위해 배경 영상의 가로·세로는 짝수여야 합니다.')
    return dict(background=infos[0], foreground=infos[1])


def launch(mode, cfg):
    work = ex.local(cfg['work'], exists=True)
    request, log_path = work / f'{mode}_input.json', work / f'{mode}.log'
    ex.write_json(request, cfg)
    print('실행 로그:', log_path, flush=True)
    with log_path.open('w') as log:
        proc = subprocess.Popen([prepost.PYTHON, '-m', 'source.util.motif_composite', mode, str(request)],
            cwd=ROOT, env=prepost.environment(cfg['gpu'], work), stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, text=True, bufsize=1, start_new_session=True)
        try:
            for line in proc.stdout:
                log.write(line); log.flush()
                print(line.rstrip(), flush=True)
            if proc.wait():
                raise RuntimeError(f'처리 실패: {log_path}\n' + log_path.read_text(errors='replace')[-5000:])
        except BaseException:
            if proc.poll() is None:
                os.killpg(proc.pid, signal.SIGTERM)
                proc.wait()
            raise
        finally:
            proc.stdout.close()


def prepare(background, foreground, *, gpu=1):
    started = time.perf_counter()
    info = check_inputs(background, foreground)
    work = ex.unique_dir('work/run_motif', 'session')
    output = ex.unique_dir('output/run_motif', 'session')
    cfg = dict(background=prepost.relative(background), foreground=prepost.relative(foreground),
        background_sha256=ex.digest(background), foreground_sha256=ex.digest(foreground),
        work=prepost.relative(work), output=prepost.relative(output), gpu=int(gpu), info=info)
    launch('prepare', cfg)
    session = ex.read_json(work / 'session.json')
    session['prepare_seconds'] = time.perf_counter()-started
    ex.write_json(work / 'session.json', session)
    return session


def finish(session):
    if session.get('placement_version') != PLACEMENT_VERSION:
        raise ValueError('기존 픽셀 배치 결과입니다. 가이드 contain 배치를 적용하려면 [2]부터 다시 실행하세요.')
    started = time.perf_counter()
    launch('light', session)
    path = ex.local(session['output']) / 'result.json'
    result = ex.read_json(path)
    result['total_seconds'] = session['prepare_seconds']+time.perf_counter()-started
    result['timer_scope'] = 'prepare + finish 호출 시간 합계; 셀 사이 대기와 플레이어 표시 제외'
    ex.write_json(path, result)
    return result


def validate_sources(cfg):
    for key in ('background', 'foreground'):
        if ex.digest(cfg[key]) != cfg[key + '_sha256']:
            raise ValueError('입력 영상이 바뀌었습니다. [2] 유저 추출 셀부터 다시 실행하세요.')


def nearest_frames(source_times, target_times):
    """Use real presentation timestamps; hold the last frame at a shortfall."""
    times = np.asarray(source_times, dtype=float)
    if not len(times) or not np.isfinite(times).all() or np.any(np.diff(times) <= 0):
        raise ValueError('유저 영상의 프레임 시각이 유효하지 않습니다.')
    right = np.searchsorted(times, target_times).clip(0, len(times)-1)
    left = np.maximum(right-1, 0)
    return np.where(np.abs(times[left]-target_times) <= np.abs(times[right]-target_times), left, right)


def render_object(cfg, masks):
    from source.util.music_sync import frame_times
    from source.enhancement import edge_matte
    bg = cfg['info']['background']
    times = frame_times(cfg['foreground'])
    paths = ex.sequence(masks, len(times))
    indices = nearest_frames(times, np.arange(bg['frames']) / bg['fps'])
    w, h = bg['size']
    transform = contain_transform(cfg['info']['foreground']['size'], bg['size'])
    folder = ex.local(cfg['output']) / 'object'
    rgb, alpha_dir = folder / 'rgb', folder / 'alpha'
    rgb.mkdir(parents=True); alpha_dir.mkdir()
    capture = cv2.VideoCapture(str(ex.local(cfg['foreground'])))
    def frames():
        position = last = -1
        previous = None
        try:
            for index, selected in enumerate(indices):
                if selected != last:
                    while position < selected:
                        ok, frame = capture.read()
                        if not ok: raise ValueError(f'유저 영상 프레임 누락: {selected}')
                        position += 1
                    mask = cv2.imread(str(paths[selected]), 0)
                    if mask is None or mask.shape != frame.shape[:2]:
                        raise ValueError('유저 마스크와 원본 프레임 크기가 다릅니다.')
                    rgba, previous, _ = edge_matte.refine(np.dstack([frame, mask]), previous)
                    placed = contain_frame(rgba, bg['size'])
                    last = selected
                alpha = placed[..., 3]
                canvas = np.full((h, w, 3), 128, np.uint8)
                canvas[alpha > 0] = placed[..., :3][alpha > 0]
                for directory, image in ((rgb, canvas), (alpha_dir, alpha)):
                    if not cv2.imwrite(str(directory / f'{index:06d}.png'), image, [cv2.IMWRITE_PNG_COMPRESSION, 1]):
                        raise OSError('객체 RGB·알파 저장 실패')
                yield canvas
        finally:
            capture.release()
    video = folder / 'object.mp4'
    ex.encode_frames(frames(), video, bg['fps'], playback=True, fast_preview=True)
    ex.write_json(video.with_suffix('.composition.json'), dict(source_sha256=ex.digest(video),
        rgb_dir=prepost.relative(rgb), rgb_sha256=automatic.sequence_hash(rgb, bg['frames']),
        alpha_dir=prepost.relative(alpha_dir), frames=bg['frames'], fps=bg['fps'], transform=transform,
        stage='object_before_composite', neutral_background=128,
        placement_version=PLACEMENT_VERSION, coordinate_space='background_full_frame', mirror=False,
        scene_harmonization=dict(version='disabled', keyframes=[])))
    mapping = dict(transform=transform, placement_version=PLACEMENT_VERSION,
        source_size=cfg['info']['foreground']['size'], target_size=bg['size'],
        fit='contain', object_position='center', mirror=False, person_crop=False,
        coordinate_space='background_full_frame', transform_applied=1,
        foreground_times=times.tolist(), output_times=(np.arange(bg['frames'])/bg['fps']).tolist(),
        source_indices=indices.tolist(),
        shortfall='hold', time_mapping='nearest original presentation timestamp; no motion retiming')
    ex.write_json(folder / 'placement.json', mapping)
    return video


def prepare_worker(cfg):
    from source.util import pipeline
    started = time.perf_counter(); timings = {}
    validate_sources(cfg)
    work, output = ex.local(cfg['work']), ex.local(cfg['output'])
    with prepost.stage(timings, '유저 인물 자동 검출·추적'):
        # Existing tracking accepts video/ inputs. Copy bytes without re-encoding.
        inputs = ex.unique_dir('video/downloads', 'motif_input')
        foreground = inputs / Path(cfg['foreground']).name
        shutil.copyfile(ex.local(cfg['foreground']), foreground)
        pipeline.WORK_DIR = work / 'tracking'
        pipeline.OUTPUT_DIR = output / 'user_tracking'
        try:
            print(pipeline.load_video(prepost.relative(foreground), prepost.MODEL), flush=True)
            if not pipeline.SESSION.object_ids:
                raise ValueError('유저 영상에서 사람을 찾지 못했습니다.')
            person = int(pipeline.SESSION.object_ids[0])
            print('유저 인물 자동 선택:', person, flush=True)
            pipeline.select_object(person)
            pipeline.track_video()
            masks = pipeline.SESSION.result_dir / 'masks'
            detections = pipeline.SESSION.detections
        finally:
            pipeline.release_predictor()
    with prepost.stage(timings, '원래 위치의 객체 RGB·알파 준비'):
        video = render_object(cfg, masks)
    with prepost.stage(timings, 'RelightVid 입력 준비'):
        record = dict(cfg['info']['background'], source=cfg['background'], source_sha256=cfg['background_sha256'])
        plan = variants.prepare(video, cfg['background'], None, record, 'RelightVid',
                                conditioning_background=cfg['background'])
    cfg.update(object_video=prepost.relative(video), user_id=person, detections=detections,
        placement_version=PLACEMENT_VERSION,
        alpha_dir=plan['alpha_dir'], relight_plan=prepost.relative(ex.local(plan['work']) / 'plan.json'),
        prepare_seconds=time.perf_counter()-started, timings_seconds=timings)
    ex.write_json(work / 'session.json', cfg)
    print('조명 입력 준비 완료:', video, flush=True)


def composite_frames(background, foreground, alpha_dir, count):
    bg_cap = cv2.VideoCapture(str(ex.local(background, exists=True)))
    fg_cap = cv2.VideoCapture(str(ex.local(foreground, exists=True)))
    masks = ex.sequence(alpha_dir, count)
    try:
        for index, path in enumerate(masks):
            ok, bg = bg_cap.read(); ok2, fg = fg_cap.read()
            if not ok or not ok2: raise ValueError(f'최종 합성 프레임 누락: {index}')
            alpha = cv2.imread(str(path), 0)
            yield variants.compose_frame(fg, bg, alpha, index,
                contact_shadow=False, cast_shadow=False, grain_sigma=0.)
    finally:
        bg_cap.release(); fg_cap.release()


def light_worker(cfg):
    started = time.perf_counter(); timings = dict(cfg['timings_seconds'])
    validate_sources(cfg)
    plan = ex.read_json(cfg['relight_plan'])
    with prepost.stage(timings, 'RelightVid 20 step·조명 강도 평가'):
        plan = variants.choose(plan, cfg['gpu'])
    with prepost.stage(timings, '기존 조명 보정 적용·원본 배경 합성'):
        # Reuse the existing illumination transfer exactly, without its optional scene effects.
        adjusted = variants.finish(plan, output_path=ex.local(cfg['work']) / 'relit_object.mp4',
                                   compose=False, contact_shadow=False, cast_shadow=False)
        destination = ex.unique_dir(cfg['output'], 'result')
        final = destination / 'final.mp4'
        bg = cfg['info']['background']
        stream = composite_frames(cfg['background'], adjusted, plan['alpha_dir'], bg['frames'])
        try:
            ex.encode_frames(stream, final, bg['fps'], playback=True, audio_source=cfg['background'])
        finally:
            stream.close()
        info, _ = rel.video_info(final)
        if info['frames'] != bg['frames'] or info['size'] != bg['size'] or abs(info['fps']-bg['fps']) > 1e-4:
            raise ValueError('최종 영상의 길이·FPS·해상도가 배경과 다릅니다.')
    validate_sources(cfg)
    result = dict(status='complete', final_video=prepost.relative(final), video=info,
        inputs=cfg, timings_seconds=timings, total_seconds=cfg['prepare_seconds']+time.perf_counter()-started,
        placement=ex.read_json(ex.local(cfg['object_video']).parent/'placement.json'),
        lighting_choice=plan['choice'], relight_plan=cfg['relight_plan'],
        background_processing='none; read-only lighting condition and alpha composition',
        audio_source=cfg['background'], encoding='H.264 / yuv420p / AAC / faststart')
    ex.write_json(ex.local(cfg['output']) / 'result.json', result)
    print('완료:', final, flush=True)


if __name__ == '__main__':
    {'prepare': prepare_worker, 'light': light_worker}[sys.argv[1]](ex.read_json(sys.argv[2]))
