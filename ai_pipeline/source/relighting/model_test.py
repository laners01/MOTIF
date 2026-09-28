"""Local HDR editing and the official UniRelight CLI adapter (no model import)."""
from pathlib import Path
import os
import subprocess
import time

import cv2
import numpy as np

from source.util import removal_experiments as ex
from source.inpainting.svor_chunks import windows, blended_frames

ROOT = Path(__file__).resolve().parents[2]
VENDOR = 'vendor/unirelight'
PYTHON = '.model-envs/unirelight/bin/python'
CONFIG = 'cosmos_predict1/diffusion/training/config/config_relight.py'
TOKENIZER = 'checkpoints/Cosmos-Tokenize1-CV8x8x8-720p'


def missing_backend():
    required = [PYTHON, f'{VENDOR}/cosmos_predict1/diffusion/inference/single_gpu_relight.py',
                f'{VENDOR}/{CONFIG}', f'{VENDOR}/checkpoints/UniRelight/model.pt',
                f'{VENDOR}/{TOKENIZER}/mean_std.pt', f'{VENDOR}/{TOKENIZER}/image_mean_std.pt',
                f'{VENDOR}/{TOKENIZER}/encoder.jit', f'{VENDOR}/{TOKENIZER}/decoder.jit']
    missing = [p for p in required if not ex.local(p).is_file()]
    return missing


def video_info(path):
    cap = cv2.VideoCapture(str(ex.local(path, exists=True)))
    try:
        fps = cap.get(cv2.CAP_PROP_FPS)
        count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        w, h = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH)), int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
        ok, first = cap.read()
    finally:
        cap.release()
    if not ok or not np.isfinite(fps) or fps <= 0 or count < 1:
        raise ValueError('영상 프레임/FPS를 읽을 수 없습니다.')
    return {'fps': fps, 'frames': count, 'size': [w, h]}, first


def studio_map():
    """Procedural linear HDR: ambient light plus a broad softbox, not a photo."""
    y, x = np.mgrid[0:256, 0:512]
    light = np.exp(-0.5 * (((x - 160) / 24) ** 2 + ((y - 75) / 20) ** 2))
    return np.repeat((0.08 + 12 * light)[..., None], 3, axis=2).astype(np.float32)


def read_hdr(path):
    path = ex.local(path, exists=True)
    if path.suffix.lower() not in {'.hdr', '.exr'}:
        raise ValueError('선형 HDR 환경맵(.hdr 또는 .exr)이 필요합니다.')
    data = cv2.imread(str(path), cv2.IMREAD_UNCHANGED)
    if data is None or data.ndim != 3 or data.shape[2] < 3:
        raise ValueError('HDR을 읽을 수 없습니다. EXR 코덱이 없으면 .hdr 파일을 사용하세요.')
    data = data[..., :3][..., ::-1].astype(np.float32)
    if not np.isfinite(data).all() or np.any(data < 0):
        raise ValueError('환경맵은 유한한 0 이상 값이어야 합니다.')
    if data.shape[1] != 2 * data.shape[0]:
        raise ValueError('가로:세로 = 2:1인 위경도 파노라마 환경맵을 사용하세요.')
    return data


def edit_hdr(data, yaw=0, exposure=0, tint=(1, 1, 1)):
    tint = np.asarray(tint, dtype=np.float32)
    if tint.shape != (3,) or not np.isfinite(tint).all() or np.any(tint < 0):
        raise ValueError('RGB 배율이 유효하지 않습니다.')
    if not np.isfinite([yaw, exposure]).all() or not -8 <= exposure <= 8:
        raise ValueError('회전/노출 값이 유효하지 않습니다.')
    shift = round(float(yaw) / 360 * data.shape[1])
    result = np.roll(data, shift, axis=1) * (2.0 ** exposure) * tint
    if not np.isfinite(result).all():
        raise ValueError('HDR 값이 표현 범위를 넘었습니다.')
    return result.astype(np.float32)


def tonemap(data):
    return np.clip(data / (1 + data), 0, 1) ** (1 / 2.2)


def letterbox(frame):
    h, w = frame.shape[:2]
    scale = min(848 / w, 480 / h)
    nw, nh = max(1, round(w * scale)), max(1, round(h * scale))
    x, y = (848 - nw) // 2, (480 - nh) // 2
    canvas = np.zeros((480, 848, 3), np.uint8)
    canvas[y:y+nh, x:x+nw] = cv2.resize(frame, (nw, nh), interpolation=cv2.INTER_AREA)
    return canvas, [x, y, nw, nh]


def prepare(video, hdr, settings):
    video = ex.local(video, exists=True)
    info, _ = video_info(video)
    work = ex.unique_dir('work/relighting_model_test', 'session')
    originals = work / 'frames'; originals.mkdir()
    cap = cv2.VideoCapture(str(video))
    count = 0
    try:
        while True:
            ok, frame = cap.read()
            if not ok:
                break
            frame, crop = letterbox(frame)
            if not cv2.imwrite(str(originals / f'{count:06d}.rgb.png'), frame,
                               [cv2.IMWRITE_PNG_COMPRESSION, 1]):
                raise OSError('프레임 저장 실패')
            count += 1
    finally:
        cap.release()
    if count != info['frames']:
        raise ValueError(f'영상 프레임 수 불일치: {count} / {info["frames"]}')
    inputs = work / 'inputs'; inputs.mkdir()
    chunks = list(windows(count, 57, 8))
    for i, (start, end) in enumerate(chunks):
        folder = inputs / f'chunk_{i:06d}'; folder.mkdir()
        for j, index in enumerate(range(start, end)):
            os.link(originals / f'{index:06d}.rgb.png', folder / f'{j:06d}.rgb.png')
    out = ex.unique_dir('output/relighting_model_test', 'unirelight')
    envmap = out / 'environment.hdr'
    if not cv2.imwrite(str(envmap), hdr[..., ::-1]):
        raise OSError('HDR 저장 실패')
    record = {**info, 'source': str(video.relative_to(ROOT)), 'source_sha256': ex.digest(video),
              'work': str(work.relative_to(ROOT)), 'output': str(out.relative_to(ROOT)),
              'crop': crop, 'chunks': chunks, 'lighting': settings, 'status': 'prepared',
              'chunk_frames': 57, 'chunk_overlap': 8, 'model_size': [848, 480]}
    ex.write_json(out / 'experiment.json', record)
    return record


def command(record, steps=20, seed=43):
    if not isinstance(steps, int) or steps < 1:
        raise ValueError('step은 1 이상의 정수여야 합니다.')
    out, work = ex.local(record['output']), ex.local(record['work'])
    # Validate the resolved target, but invoke the venv link so Python finds pyvenv.cfg.
    ex.local(PYTHON, exists=True)
    return [str(ROOT / PYTHON), '-m', 'cosmos_predict1.diffusion.inference.single_gpu_relight',
            '--config_file', CONFIG, '--config', 'unirelight_cosmos_f57_480p',
            '--ckpt_path', str(ex.local(f'{VENDOR}/checkpoints/UniRelight/model.pt')),
            '--dataset_name', str(work / 'inputs'), '--output_path', str(out / 'native'),
            '--sample_n_frames', '57', '--resolution', '480', '848', '--resize_resolution', '480', '848',
            '--env_light_path', str(ex.local(record.get('environment_directory', out / 'environment.hdr'))), '--rotate_light', 'false',
            '--save_condition', 'false', '--save_images', 'true', '--overwrite_fps', str(record['fps']),
            '--num_steps', str(steps), '--seed', str(seed),
            '--relit_only', str(record.get('relit_only', False)).lower()]


def assemble(record, light_index=0, target_name='relit'):
    """Official saved panels: input, relit, albedo. Use PNGs to avoid native 24 FPS."""
    out = ex.local(record['output'])
    x, y, w, h = record['crop']
    folders = sorted((out / 'native').glob(f'*_light-{light_index:04d}'))
    if len(folders) != len(record['chunks']):
        raise ValueError('공식 출력 구간 수가 다릅니다. inference.log를 확인하세요.')
    for i, folder in enumerate(folders):
        ex.local(folder, exists=True)
        if not folder.name.startswith(f'chunk_{i:06d}_light-'):
            raise ValueError(f'예상과 다른 공식 출력 이름: {folder.name}')
    panels = [(1, 'relit')] if record.get('relit_only') else [(1, 'relit'), (2, 'albedo')]
    for panel, name in panels:
        def predict(start, end):
            index = next(i for i, c in enumerate(record['chunks']) if c[0] == start)
            if record.get('relit_only'):
                array = np.load(ex.local(folders[index] / 'relit.npy', exists=True),
                                mmap_mode='r', allow_pickle=False)
                if array.shape != (57, 480, 848, 3) or array.dtype != np.uint8:
                    raise ValueError('Relit NPY 형식 불일치')
                return array[:end-start, y:y+h, x:x+w].astype(np.float32) / 255
            paths = sorted(folders[index].glob('*.png'))
            if len(paths) != 57:
                raise ValueError('각 구간에서 패딩을 포함한 57프레임 출력이 필요합니다.')
            frames = []
            for p in paths[:end-start]:
                image = cv2.imread(str(ex.local(p, exists=True)))
                if image is None or image.shape != (480, 848 * 3, 3):
                    raise ValueError('공식 출력 패널 형식이 바뀌었습니다. input/relit/albedo 순서를 확인하세요.')
                image = image[y:y+h, panel*848+x:panel*848+x+w]
                frames.append(image[..., ::-1].astype(np.float32) / 255)
            return np.stack(frames)
        blended = blended_frames(record['frames'], 57, 8, predict)
        frames = (cv2.resize((f.clip(0, 1)*255).round().astype(np.uint8)[..., ::-1],
                            tuple(record['size'])) for f in blended)
        target = out / f'{target_name if name == "relit" else name}.mp4'
        ex.encode_frames(frames, target, record['fps'], playback=all(v % 2 == 0 for v in record['size']))
        actual, _ = video_info(target)
        if actual['frames'] != record['frames'] or abs(actual['fps']-record['fps']) > 1e-4:
            raise ValueError('출력 프레임 수/FPS 보존 검사 실패')


def run(video, hdr, settings, steps=20, seed=43, gpu=0, candidate_lights=None):
    missing = missing_backend()
    if missing:
        raise FileNotFoundError('UniRelight 실행 준비 필요:\n' + '\n'.join(missing))
    if not isinstance(gpu, int) or gpu < 0:
        raise ValueError('GPU 번호는 0 이상의 정수여야 합니다.')
    # Reject vendor/model links that escape the project before launching third-party code.
    for path in ex.local(VENDOR).rglob('*'):
        if path.is_symlink():
            ex.local(path)
    if steps < 1:
        raise ValueError('step은 1 이상이어야 합니다.')
    started = time.perf_counter()
    record = prepare(video, hdr, settings)
    record['relit_only'] = True
    record['timings'] = {'prepare': round(time.perf_counter() - started, 3)}
    out = ex.local(record['output'])
    if candidate_lights is not None:
        env_dir = out / 'environments'; env_dir.mkdir()
        for i, (candidate_hdr, _) in enumerate(candidate_lights):
            if not cv2.imwrite(str(env_dir / f'{i:04d}.hdr'), candidate_hdr[..., ::-1]):
                raise OSError('후보 환경맵 저장 실패')
        record['environment_directory'] = str(env_dir.relative_to(ROOT))
        record['candidates'] = [item[1] for item in candidate_lights]
    cmd = command(record, steps, seed)
    record.update(status='running', command=cmd, steps=steps, seed=seed, gpu=gpu)
    ex.write_json(out / 'experiment.json', record)
    env = os.environ.copy()
    for key in ['TMPDIR', 'XDG_CACHE_HOME', 'HF_HOME', 'TORCH_HOME', 'MPLCONFIGDIR',
                'NUMBA_CACHE_DIR', 'TRITON_CACHE_DIR', 'TORCH_EXTENSIONS_DIR', 'CUDA_CACHE_PATH']:
        cache = ex.local(f'.cache/relighting_model_test/{key.lower()}'); cache.mkdir(parents=True, exist_ok=True)
        env[key] = str(cache)
    env.update(HF_HUB_OFFLINE='1', TRANSFORMERS_OFFLINE='1', PYTHONDONTWRITEBYTECODE='1',
               CUDA_VISIBLE_DEVICES=str(gpu), CUDA_DEVICE_ORDER='PCI_BUS_ID',
               PYTHONPATH=str(ex.local(VENDOR)), OPENCV_IO_ENABLE_OPENEXR='1')
    print(f'처리: {record["frames"]} frames / {record["fps"]:.6f} FPS / {len(record["chunks"])}구간', flush=True)
    print(f'로그: {out.relative_to(ROOT)}/inference.log', flush=True)
    try:
        with (out / 'inference.log').open('w') as log:
            stage_started = time.perf_counter()
            subprocess.run(cmd, cwd=ex.local(VENDOR), env=env, stdout=log, stderr=subprocess.STDOUT, check=True)
        record['timings']['model_process'] = round(time.perf_counter() - stage_started, 3)
        stage_started = time.perf_counter()
        if candidate_lights is None:
            assemble(record)
        else:
            for i in range(len(candidate_lights)):
                assemble(record, light_index=i, target_name=f'relit_{i:04d}')
        record['timings']['assemble'] = round(time.perf_counter() - stage_started, 3)
        record['status'] = 'complete'
    except BaseException as error:
        record.update(status='failed', error=str(error))
        raise
    finally:
        record['seconds'] = round(time.perf_counter() - started, 3)
        ex.write_json(out / 'experiment.json', record)
    return out
