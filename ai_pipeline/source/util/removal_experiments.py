"""Independent human-removal experiments; no model is loaded on import."""
from __future__ import annotations

import hashlib
import importlib.util
import json
import math
import os
from pathlib import Path
import subprocess
import shutil
import sys
import time
import uuid

import cv2
import numpy as np

ROOT = Path(__file__).resolve().parents[2]
PACKAGES = {"propainter": "vendor/propainter", "svor": "vendor/svor", "diffueraser": "vendor/diffueraser", "omnimattezero": "vendor/omnimattezero", "minimax-remover": "vendor/minimax-remover"}


def local(path, *, exists=False):
    # 심링크를 따라가지 않고(venv bin/python은 /opt/uv-python 심링크) lexical 정규화로 containment 검사.
    # 보안 목적인 '..' 탈출·절대경로 차단은 normpath가 그대로 수행한다.
    p = Path(os.path.normpath(ROOT / Path(path)))
    if not p.is_relative_to(ROOT):
        raise ValueError(f"프로젝트 밖 경로: {path}")
    if exists and not p.exists():
        raise FileNotFoundError(p)
    return p


def digest(path):
    h = hashlib.sha256()
    with local(path, exists=True).open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def read_json(path):
    return json.loads(local(path, exists=True).read_text())


def source_fps(video):
    cap = cv2.VideoCapture(str(local(video, exists=True)))
    try:
        fps = cap.get(cv2.CAP_PROP_FPS)
    finally:
        cap.release()
    if not math.isfinite(fps) or fps <= 0:
        raise ValueError("원본 영상의 FPS를 읽을 수 없습니다.")
    return fps


def write_json(path, data):
    local(path).write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")


def unique_dir(parent, label):
    if not label or any(c not in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-" for c in label):
        raise ValueError("실험 이름은 영문·숫자·밑줄·하이픈만 사용하세요.")
    p = local(parent) / f"{label}_{time.strftime('%Y%m%d_%H%M%S')}_{uuid.uuid4().hex[:8]}"
    p.mkdir(parents=True, exist_ok=False)
    return p


def sequence(folder, count=None):
    folder = local(folder, exists=True)
    files = sorted(folder.glob("*.png"))
    if not files:
        files = sorted(folder.glob("*.jpg"))
    n = len(files) if count is None else count
    if not files or len(files) != n or [p.stem for p in files] != [f"{i:06d}" for i in range(n)]:
        raise ValueError(f"0부터 연속된 6자리 파일명과 정확한 프레임 수가 필요합니다: {folder}")
    return [local(p, exists=True) for p in files]


def prepare_video(video="video/JENNIE_background.mp4", fps=None):
    video = local(video, exists=True)
    if not video.is_relative_to(local("video")) or (fps is not None and not 0 < fps <= 240):
        raise ValueError("video/ 안의 영상과 유효한 비교 FPS를 지정하세요.")
    work = unique_dir("work/inpainting_model_test", "session")
    frames = work / "frames"
    frames.mkdir()
    cap = cv2.VideoCapture(str(video))
    if not cap.isOpened():
        raise ValueError(f"영상을 열 수 없습니다: {video}")
    original_fps = cap.get(cv2.CAP_PROP_FPS)
    if fps is None:
        fps = original_fps
    if not math.isfinite(fps) or fps <= 0:
        cap.release()
        raise ValueError("원본 영상의 FPS를 읽을 수 없습니다.")
    count = 0
    size = None
    try:
        while True:
            ok, frame = cap.read()
            if not ok:
                break
            size = [frame.shape[1], frame.shape[0]]
            if not cv2.imwrite(str(frames / f"{count:06d}.jpg"), frame, [cv2.IMWRITE_JPEG_QUALITY, 98]):
                raise OSError("프레임 저장 실패")
            count += 1
    finally:
        cap.release()
    if count == 0:
        raise ValueError("디코딩한 프레임이 없습니다.")
    exp = {"source": str(video.relative_to(ROOT)), "source_sha256": digest(video),
           "frames": count, "size": size, "fps": float(fps), "original_fps": original_fps,
           "frame_dir": str(frames.relative_to(ROOT)), "work": str(work.relative_to(ROOT))}
    write_json(work / "session.json", exp)
    return exp


def validate_masks(folder, exp):
    files = sequence(folder, exp["frames"])
    w, h = exp["size"]
    for p in files:
        mask = cv2.imread(str(p), cv2.IMREAD_GRAYSCALE)
        if mask is None or mask.shape != (h, w):
            raise ValueError(f"마스크 크기 불일치: {p}")
    return files


def import_masks(folder, exp):
    """Accept only masks accompanied by an explicit matching provenance manifest."""
    folder = local(folder, exists=True)
    metadata = read_json(folder / "mask_manifest.json")
    for key in ("source_sha256", "frames", "size", "fps"):
        if metadata.get(key) != exp[key]:
            raise ValueError(f"외부 마스크의 원본/프레임 정렬 정보 불일치: {key}")
    validate_masks(folder, exp)
    return folder


def mask_manifest(exp, **extra):
    return {**{k: exp[k] for k in ("source", "source_sha256", "frames", "size", "fps")}, **extra}


def new_tracker(exp):
    # Load a separate Session without mutating source.util.pipeline used by run.ipynb.
    name = "source.util._human_removal_" + uuid.uuid4().hex
    spec = importlib.util.spec_from_file_location(name, local("source/util/pipeline.py", exists=True))
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    module.WORK_DIR = local(exp["work"]) / "tracker"
    module.OUTPUT_DIR = unique_dir("output/inpainting_model_test", "tracking")
    return module


def initialize_tracker(tracker, exp, model_id="sam2.1_large"):
    if model_id not in {"sam2.1_tiny", "sam2.1_small", "sam2.1_base_plus", "sam2.1_large"}:
        raise ValueError("이 실험의 기본 추적기는 로컬 SAM 2.1 모델을 사용합니다.")
    predictor, _, _ = tracker.load_predictor(model_id)
    state = predictor.init_state(video_path=str(local(exp["frame_dir"])), offload_video_to_cpu=True,
                                 offload_state_to_cpu=False, async_loading_frames=False)
    s = tracker.SESSION
    s.source = local(exp["source"], exists=True)
    s.frame_dir = local(exp["frame_dir"])
    s.frame_paths = sequence(s.frame_dir, exp["frames"])
    s.fps = exp["fps"]
    s.width, s.height = exp["size"]
    s.inference_state = state
    s.prompt_frame = None
    s.points.clear()
    s.labels.clear()
    s.object_ids.clear()
    s.selected_obj_id = None
    s.preview_masks.clear()
    s.detections.clear()
    s.detection_info.clear()
    s.prompt_box = None
    s.result_dir = None


def detect_people(exp, frame_index=0, threshold=0.45):
    """RF-DETR -> SAM2 boxes; this detects people, NOT their shadows."""
    from rfdetr import RFDETRSmall
    from PIL import Image
    import torch
    from source.detection.rfdetr_person_detector import RFDETRSmallPersonDetector
    weight = local("models/rfdetr/rf-detr-small.pth", exists=True)
    detector = RFDETRSmall(pretrain_weights=str(weight), device="cuda" if torch.cuda.is_available() else "cpu")
    frame = cv2.imread(str(sequence(exp["frame_dir"])[frame_index]))
    detections = detector.predict(Image.fromarray(cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)), threshold=threshold)
    candidates = RFDETRSmallPersonDetector._person_candidates(detections)
    return sorted(candidates, key=lambda item: item[1], reverse=True)


def add_boxes(tracker, frame_index, candidates, target_id):
    if not 1 <= target_id <= len(candidates):
        raise ValueError("검출 이미지에서 확인한 사람 ID를 선택하세요.")
    import torch
    with torch.inference_mode(), tracker.autocast_ctx():
        for obj_id, (box, _) in enumerate(candidates, 1):
            tracker.SESSION.predictor.add_new_points_or_box(
                inference_state=tracker.SESSION.inference_state, frame_idx=frame_index, obj_id=obj_id,
                box=np.asarray(box, dtype=np.float32))
    s = tracker.SESSION
    s.object_ids = list(range(1, len(candidates) + 1))
    s.prompt_frame = frame_index
    s.selected_obj_id = target_id
    s.detection_info = {"detector": "RF-DETR-S local PyTorch", "selected_obj_id": target_id,
                        "frame_index": frame_index, "boxes": [b.tolist() for b, _ in candidates]}


def save_tracking(tracker, exp, label="body", progress=None):
    tracker.OUTPUT_DIR = unique_dir("output/inpainting_model_test", label)
    tracker.track_video(progress=progress)
    folder = tracker.SESSION.result_dir / "masks"
    validate_masks(folder, exp)
    write_json(folder / "mask_manifest.json", mask_manifest(exp, kind=label,
               model=tracker.SESSION.model_id, points=tracker.SESSION.points,
               labels=tracker.SESSION.labels, detection=tracker.SESSION.detection_info))
    return folder


def build_variant(exp, body_dir, *, label="body_effects", effects_dir=None, protect_dir=None,
                  polygons=(), dilation=0, temporal_radius=0):
    """Union body + effects, then subtract protected people. Polygon ranges are inclusive."""
    body = validate_masks(body_dir, exp)
    effects = validate_masks(effects_dir, exp) if effects_dir else None
    protect = validate_masks(protect_dir, exp) if protect_dir else None
    for folder in (body_dir, effects_dir, protect_dir):
        if folder:
            import_masks(folder, exp)
    if dilation < 0 or temporal_radius < 0 or int(dilation) != dilation or int(temporal_radius) != temporal_radius:
        raise ValueError("확장 폭·시간 반경은 0 이상의 정수여야 합니다.")
    w, h = exp["size"]
    prepared = []
    for item in polygons:
        start, end = int(item.get("start", 0)), int(item.get("end", len(body)-1))
        pts = np.asarray(item["points"], dtype=np.float64)
        if pts.ndim != 2 or pts.shape[1] != 2 or len(pts) < 3 or not np.isfinite(pts).all():
            raise ValueError("다각형에는 유효한 점이 3개 이상 필요합니다.")
        if not (0 <= start <= end < len(body)) or (pts < 0).any() or (pts[:,0] >= w).any() or (pts[:,1] >= h).any():
            raise ValueError("다각형 좌표 또는 적용 프레임 범위가 영상 밖입니다.")
        operation = item.get("operation", "add")
        if operation not in {"add", "protect"}:
            raise ValueError("다각형 operation은 add 또는 protect입니다.")
        prepared.append((start, end, pts.astype(np.int32), operation))
    out = unique_dir(local(exp["work"]) / "masks", label)
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2*dilation+1, 2*dilation+1))
    for i in range(len(body)):
        mask = np.zeros((h,w), np.uint8)
        for j in range(max(0,i-temporal_radius), min(len(body),i+temporal_radius+1)):
            mask |= (cv2.imread(str(body[j]),0) > 127).astype(np.uint8)*255
            if effects:
                mask |= (cv2.imread(str(effects[j]),0) > 127).astype(np.uint8)*255
            for start,end,pts,operation in prepared:
                if operation == "add" and start <= j <= end:
                    cv2.fillPoly(mask,[pts],255)
        if dilation:
            mask = cv2.dilate(mask,kernel)
        for start,end,pts,operation in prepared:
            if operation == "protect" and start <= i <= end:
                cv2.fillPoly(mask,[pts],0)
        if protect:
            mask[cv2.imread(str(protect[i]),0)>127] = 0
        if not cv2.imwrite(str(out/f"{i:06d}.png"),mask):
            raise OSError("마스크 저장 실패")
    write_json(out/"mask_manifest.json", mask_manifest(exp, kind=label,
        body=str(local(body_dir).relative_to(ROOT)), effects=str(local(effects_dir).relative_to(ROOT)) if effects_dir else None,
        protection=str(local(protect_dir).relative_to(ROOT)) if protect_dir else None,
        polygons=list(polygons), dilation=dilation, temporal_radius=temporal_radius))
    return out


def preview_masks(exp, masks_dir, index=0):
    import matplotlib.pyplot as plt
    frames=sequence(exp["frame_dir"],exp["frames"])
    masks=validate_masks(masks_dir,exp)
    rgb=cv2.cvtColor(cv2.imread(str(frames[index])),cv2.COLOR_BGR2RGB)
    mask=cv2.imread(str(masks[index]),0)>127
    overlay=rgb.copy()
    overlay[mask]=(rgb[mask]*0.45+np.array([255,40,40])*0.55).astype(np.uint8)
    fig,axs=plt.subplots(1,3,figsize=(12,7))
    for ax,im,title in zip(axs,[rgb,mask,overlay],["Original","Removal mask","Red = remove"]):
        ax.imshow(im,cmap="gray"); ax.set_title(f"{title} / frame {index}"); ax.axis("off")
    plt.show()


REQUIRED = {
    "omnimattezero": ["OmnimatteZero.py", "weights/LTX-Video-0.9.7-diffusers/model_index.json"],
    "minimax-remover": ["pipeline_minimax_remover.py", "transformer_minimax_remover.py", "vae/config.json", "transformer/config.json", "scheduler/scheduler_config.json"],
    "propainter": ["inference_propainter.py", "weights/ProPainter.pth", "weights/raft-things.pth", "weights/recurrent_flow_completion.pth"],
    "svor": ["predict_SVOR.py", "config/wan2.1/wan_civitai.yaml", "models/Wan2.1-VACE-1.3B/config.json", "models/Wan2.1-VACE-1.3B/diffusion_pytorch_model.safetensors", "models/Wan2.1-VACE-1.3B/Wan2.1_VAE.pth", "models/Wan2.1-VACE-1.3B/models_t5_umt5-xxl-enc-bf16.pth", "models/Wan2.1-VACE-1.3B/google/umt5-xxl/tokenizer_config.json",
             "models/remove_model_stage1.safetensors", "models/remove_model_stage2.safetensors"],
    "diffueraser": ["run_diffueraser.py", "weights/diffuEraser/brushnet", "weights/diffuEraser/unet_main",
                   "weights/stable-diffusion-v1-5/model_index.json", "weights/sd-vae-ft-mse/config.json",
                   "weights/PCM_Weights", "weights/propainter/ProPainter.pth", "weights/propainter/raft-things.pth",
                   "weights/propainter/recurrent_flow_completion.pth"],
}


def backend_status():
    rows=[]
    for name,base in PACKAGES.items():
        missing=[str(Path(base)/p) for p in REQUIRED[name] if not local(Path(base)/p).exists()]
        # A downloaded configuration alone does not mean weights are complete.
        manifest = local("models/inpainting_assets.json")
        if manifest.exists():
            for asset in read_json(manifest):
                if Path(asset["path"]).is_relative_to(Path(base)):
                    path = local(asset["path"])
                    if not path.is_file() or (asset["size"] is not None and path.stat().st_size != asset["size"]):
                        if asset["path"] not in missing:
                            missing.append(asset["path"])
        rows.append({"model":name, "files_present":not missing, "missing":missing,
                     "note":"필수 파일 검사만 수행; 실행 환경·GPU 추론은 별도 검증 필요"})
    return rows


def encode_sequence(paths, output, fps, *, masks=False, playback=False):
    paths=[local(p,exists=True) for p in paths]
    frames=(cv2.imread(str(p),cv2.IMREAD_GRAYSCALE if masks else cv2.IMREAD_COLOR) for p in paths)
    return encode_frames(frames,output,fps,masks=masks,playback=playback)


def has_audio(path):
    import imageio_ffmpeg
    result=subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(),'-hide_banner','-i',str(local(path,exists=True))],
                          capture_output=True,text=True)
    return any('Stream #' in line and 'Audio:' in line for line in result.stderr.splitlines())


def add_audio(video, audio_source, output):
    """Copy the existing video stream and add one background soundtrack without shortening video."""
    import imageio_ffmpeg
    video=local(video,exists=True);audio_source=local(audio_source,exists=True);output=local(output)
    if output.exists():raise FileExistsError(output)
    cap=cv2.VideoCapture(str(video))
    try:duration=cap.get(cv2.CAP_PROP_FRAME_COUNT)/cap.get(cv2.CAP_PROP_FPS)
    finally:cap.release()
    if not math.isfinite(duration) or duration<=0:raise ValueError('오디오 길이 기준 영상 정보를 읽을 수 없습니다.')
    command=[imageio_ffmpeg.get_ffmpeg_exe(),'-hide_banner','-loglevel','error','-n',
             '-i',str(video),'-i',str(audio_source),'-map','0:v:0','-map','1:a:0?',
             '-c:v','copy','-c:a','aac','-b:a','192k','-af',f'apad=whole_dur={duration:.9f},atrim=duration={duration:.9f}',
             '-movflags','+faststart',str(output)]
    result=subprocess.run(command,capture_output=True,text=True)
    if result.returncode:raise RuntimeError(result.stderr)
    return output


def encode_frames(frames, output, fps, *, masks=False, playback=False, audio_source=None, final=False, fast_preview=False):
    import imageio_ffmpeg
    from itertools import chain
    output=local(output)
    if output.exists():
        raise FileExistsError(output)
    encoded=output
    if audio_source is not None:
        audio_source=local(audio_source,exists=True)
        encoded=unique_dir('work','audio_encode')/'video.mp4'
    frames=iter(frames)
    first=next(frames)
    h,w=first.shape[:2]
    if final:
        if masks: raise ValueError('최종 영상 인코딩에는 RGB 프레임이 필요합니다.')
        preset,crf,pixel_format='slow','14','yuv444p'
    else:
        preset='ultrafast' if fast_preview else 'fast'
        crf='20' if fast_preview else '18' if playback and not masks else '0'
        pixel_format='yuv420p' if playback and not masks else 'yuv444p'
    cmd=[imageio_ffmpeg.get_ffmpeg_exe(),"-hide_banner","-loglevel","error","-n",
         "-f","rawvideo","-pix_fmt","gray" if masks else "bgr24","-s",f"{w}x{h}",
         "-r",str(fps),"-i","pipe:0","-an","-c:v","libx264","-preset",preset,"-crf",crf,
         "-pix_fmt",pixel_format,"-movflags","+faststart",str(encoded)]
    proc=subprocess.Popen(cmd,stdin=subprocess.PIPE,stderr=subprocess.PIPE)
    try:
        for frame in chain((first,),frames):
            if frame is None or frame.shape[:2]!=(h,w):
                raise ValueError(f"프레임 크기 불일치: 예상 {w}x{h}")
            if masks:
                frame=(frame>127).astype(np.uint8)*255
            proc.stdin.write(frame.tobytes())
        proc.stdin.close()
        error=proc.stderr.read().decode(errors="replace")
        if proc.wait()!=0:
            raise RuntimeError(error)
    except BrokenPipeError as exc:
        # ffmpeg가 입력 도중 먼저 죽으면 stdin.write가 BrokenPipe로 터진다. 그대로
        # 올리면 진짜 원인(ffmpeg stderr / OOM 신호)이 삼켜지므로 여기서 드러낸다.
        try:err=proc.stderr.read().decode(errors="replace").strip()
        except Exception:err=""
        proc.kill();rc=proc.wait()
        detail=err or (f"신호 {-rc}로 강제 종료(메모리 부족 가능성)" if rc and rc<0 else f"종료코드 {rc}")
        raise RuntimeError(f"ffmpeg가 입력 중 먼저 종료됨: {detail}") from exc
    except BaseException:
        proc.kill(); proc.wait()
        raise
    finally:
        proc.stderr.close()
    return output if audio_source is None else add_audio(encoded,audio_source,output)


def backend_command(name, exp, masks_dir, inputs, output, *, python=None,
                    neighbor_length=10, ref_stride=10, subvideo_length=80, fp16=True,
                    steps=20, seed=43, memory_mode="model_cpu_offload", attention_backend="default", chunk_frames=0, chunk_overlap=16):
    if name not in PACKAGES:
        raise ValueError(f"알 수 없는 모델: {name}")
    model_python = ROOT / ".model-envs" / name / "bin/python"
    executable=Path(python or (model_python if model_python.exists() else sys.executable)).absolute()
    # The active Python is a system runtime; all other custom runtimes must be local.
    if executable.resolve()!=Path(sys.executable).resolve():
        local(executable,exists=True)
    if not executable.exists():
        raise FileNotFoundError(executable)
    base=local(PACKAGES[name],exists=True)
    inputs,output,masks_dir=local(inputs),local(output),local(masks_dir)
    if neighbor_length<2 or neighbor_length%2 or ref_stride<1 or subvideo_length<ref_stride or steps<1:
        raise ValueError("시간 문맥·스텝 설정이 유효하지 않습니다. neighbor_length는 2 이상의 짝수입니다.")
    common=[str(executable)]
    if name in {"omnimattezero", "minimax-remover"}:
        return common+[str(local("source/inpainting/model_test_runner.py")),
            "--model",name,"--frames",str(local(exp["frame_dir"])),"--masks",str(masks_dir),
            "--output",str(output/"model_result.mp4"),"--fps",str(exp["fps"]),
            "--steps",str(steps),"--seed",str(seed)]+(
                ["--attention-backend",attention_backend] if name=="omnimattezero" else [])
    if name=="propainter":
        return common+[str(base/"inference_propainter.py"),"-i",str(local(exp["frame_dir"])),"-m",str(masks_dir),
            "-o",str(output),"--mask_dilation","0","--save_fps",str(max(1, round(exp["fps"]))),
            "--resize_ratio","1.0","--neighbor_length",str(neighbor_length),"--ref_stride",str(ref_stride),
            "--subvideo_length",str(subvideo_length)]+(["--fp16"] if fp16 else [])
    if name=="svor":
        if chunk_frames:
            from source.inpainting.svor_chunks import windows
            list(windows(exp["frames"], chunk_frames, chunk_overlap))
        padded=math.ceil((exp["frames"]-1)/4)*4+1
        return common+[str(base/"predict_SVOR.py"),"--input_video",str(inputs/"input.mp4"),
            "--input_mask_video",str(inputs/"mask.mp4"),"--save_dir",str(output),
            "--config_path",str(base/"config/wan2.1/wan_civitai.yaml"),
            "--model_name",str(base/"models/Wan2.1-VACE-1.3B"),
            "--lora_path",str(base/"models/remove_model_stage1.safetensors"),str(base/"models/remove_model_stage2.safetensors"),
            "--sample_size",f"{exp['size'][1]},{exp['size'][0]}","--video_length",str(padded),
            "--fps",str(exp["fps"]),"--dilation","0","--seed",str(seed),
            "--num_inference_steps",str(steps),"--gpu_memory_mode",memory_mode,
            "--chunk_frames",str(chunk_frames),"--chunk_overlap",str(chunk_overlap)]
    return common+[str(base/"run_diffueraser.py"),"--input_video",str(inputs/"input.mp4"),
        "--input_mask",str(inputs/"mask.mp4"),"--save_path",str(output),
        "--video_length",str(math.ceil(exp["frames"]/exp["fps"])+1),
        "--mask_dilation_iter","0","--max_img_size",str(max(exp["size"])),
        "--base_model_path",str(base/"weights/stable-diffusion-v1-5"),
        "--vae_path",str(base/"weights/sd-vae-ft-mse"),"--diffueraser_path",str(base/"weights/diffuEraser"),
        "--propainter_model_dir",str(base/"weights/propainter"),"--neighbor_length",str(neighbor_length),
        "--ref_stride",str(ref_stride),"--subvideo_length",str(subvideo_length)]


def normalize_result(raw, exp, work, output, backend):
    cap=cv2.VideoCapture(str(local(raw,exists=True)))
    native_fps=cap.get(cv2.CAP_PROP_FPS)
    if backend=="omnimattezero":
        info={"native_frames":0,"native_fps":native_fps,"native_size":None,
              "trimmed_padding":0,"comparison_fps":exp["fps"]}
        def decoded_frames():
            while True:
                ok,im=cap.read()
                if not ok:
                    break
                size=[im.shape[1],im.shape[0]]
                if size!=exp["size"]:
                    raise ValueError(f"모델 출력 해상도가 다릅니다: {size}; 원본 {exp['size']}")
                info["native_frames"]+=1
                info["native_size"]=size
                yield im
            if info["native_frames"]!=exp["frames"]:
                raise ValueError(f"출력 프레임 수 불일치: {info['native_frames']}; 예상 {exp['frames']}")
        try:
            encode_frames(decoded_frames(),output,exp["fps"],playback=True)
        finally:
            cap.release()
        return info
    frames=local(work)/"result_frames"
    frames.mkdir()
    count=0
    size=None
    try:
        while True:
            ok,im=cap.read()
            if not ok:
                break
            size=[im.shape[1],im.shape[0]]
            if size!=exp["size"]:
                raise ValueError(f"모델 출력 해상도가 다릅니다: {size}; 원본 {exp['size']}. 원본 결과를 확인하세요.")
            if count<exp["frames"]:
                if not cv2.imwrite(str(frames/f"{count:06d}.png"),im):
                    raise OSError("결과 프레임 저장 실패")
            count+=1
    finally:
        cap.release()
    padded=math.ceil((exp["frames"]-1)/4)*4+1
    allowed={exp["frames"],padded} if backend=="svor" else {exp["frames"]}
    if count not in allowed:
        raise ValueError(f"출력 프레임 수 불일치: {count}; 예상 {sorted(allowed)}. 잘린 결과는 성공 처리하지 않습니다.")
    encode_sequence(sequence(frames,exp["frames"]),output,exp["fps"],playback=True)
    return {"native_frames":count,"native_fps":native_fps,"native_size":size,
            "trimmed_padding":count-exp["frames"],"comparison_fps":exp["fps"]}


def run_backend(name, exp, masks_dir, *, label=None, python=None, **options):
    if not math.isfinite(exp["fps"]) or exp["fps"] <= 0:
        raise ValueError("원본 영상의 유효한 FPS가 필요합니다.")
    if digest(exp["source"])!=exp["source_sha256"]:
        raise ValueError("마스크 생성 이후 원본 영상이 변경되었습니다.")
    masks_dir=import_masks(masks_dir,exp)
    mask_files=validate_masks(masks_dir,exp)
    if name == "svor":
        # Validate the existing mask manifest before correcting stale notebook FPS.
        fps = source_fps(exp["source"])
        exp = {**exp, "fps": fps, "original_fps": fps}
    if not any(np.any(cv2.imread(str(p),0)>127) for p in mask_files):
        raise ValueError("제거 영역이 없는 마스크입니다.")
    status=next(row for row in backend_status() if row["model"]==name)
    if status["missing"]:
        raise FileNotFoundError("추가 모델을 실행하려면 다음 로컬 파일이 필요합니다:\n"+"\n".join(status["missing"]))
    out=unique_dir("output/inpainting_model_test",label or name)
    work=unique_dir(local(exp["work"])/"inference",name)
    inputs=work/"inputs"; inputs.mkdir()
    raw_dir=out/"native"; raw_dir.mkdir()
    # Official ProPainter opens every directory entry as an image: exclude the manifest.
    clean_masks=inputs/"masks"
    clean_masks.mkdir()
    for mask in mask_files:
        shutil.copyfile(mask,clean_masks/mask.name)
    command=backend_command(name,exp,clean_masks,inputs,raw_dir,python=python,**options)
    mask_hash=hashlib.sha256("".join(digest(p) for p in mask_files).encode()).hexdigest()
    record={"status":"running","backend":name,"experiment":exp,"mask_dir":str(masks_dir.relative_to(ROOT)),
            "mask_sha256":mask_hash,"mask_settings":read_json(masks_dir/"mask_manifest.json"),
            "command":command,"options":options,"notes":"","scores":{}}
    write_json(out/"experiment.json",record)
    started=time.perf_counter()
    try:
        if name in {"svor", "diffueraser"}:
            encode_sequence(sequence(exp["frame_dir"],exp["frames"]),inputs/"input.mp4",exp["fps"])
            encode_sequence(mask_files,inputs/"mask.mp4",exp["fps"],masks=True)
        env=os.environ.copy()
        for key,relative in {"TMPDIR":"tmp","MPLCONFIGDIR":"matplotlib","XDG_CACHE_HOME":"xdg",
                             "HF_HOME":"huggingface","TORCH_HOME":"torch","NUMBA_CACHE_DIR":"numba","TRITON_CACHE_DIR":"triton"}.items():
            cache=local(".cache/inpainting_model_test")/relative; cache.mkdir(parents=True,exist_ok=True)
            env[key]=str(cache)
        env.update(HF_HUB_OFFLINE="1",TRANSFORMERS_OFFLINE="1",PYTHONDONTWRITEBYTECODE="1")
        # 서브프로세스 출력을 파일과 부모 stdout(=Cloud Run 로그) 양쪽에 흘린다.
        # 기존엔 파일에만 남아 SVOR 로딩/청크 진행이 Cloud Run에서 완전 무음이었다.
        with (out/"inference.log").open("w") as log:
            proc=subprocess.Popen(command,cwd=local(PACKAGES[name]),env=env,
                                  stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
            for chunk in iter(proc.stdout.readline,b""):
                line=chunk.decode(errors="replace")
                log.write(line);sys.stdout.write(line);sys.stdout.flush()
            returncode=proc.wait()
        if returncode:
            raise RuntimeError(f"추론 실패. 로그: {out/'inference.log'}")
        if name=="propainter":
            raw=raw_dir/Path(exp["frame_dir"]).name/"inpaint_out.mp4"
        elif name=="diffueraser":
            raw=raw_dir/"diffueraser_result.mp4"
        else:
            videos=list(raw_dir.glob("*.mp4"))
            if len(videos)!=1:
                raise ValueError(f"모델 결과 파일을 하나로 특정할 수 없습니다: {videos}")
            raw=videos[0]
        record["output_check"]=normalize_result(raw,exp,work,out/"result.mp4",name)
        record.update(status="complete",result=str((out/"result.mp4").relative_to(ROOT)))
    except BaseException as exc:
        record.update(status="failed",error=str(exc))
        raise
    finally:
        record["seconds"]=round(time.perf_counter()-started,3)
        write_json(out/"experiment.json",record)
    return out/"result.mp4"


def results():
    return [(p.parent,read_json(p)) for p in sorted(local("output/inpainting_model_test").glob("*/experiment.json"))]


def compare(exp, result_paths, index=0):
    import matplotlib.pyplot as plt
    reference=sequence(exp["frame_dir"],exp["frames"])[index]
    images=[cv2.cvtColor(cv2.imread(str(reference)),cv2.COLOR_BGR2RGB)]
    titles=[f"Original / {index}"]
    for path in result_paths:
        path=local(path,exists=True)
        record=read_json(path.parent/"experiment.json")
        if record["experiment"]["source_sha256"]!=exp["source_sha256"] or record["experiment"]["fps"]!=exp["fps"]:
            raise ValueError("다른 원본/FPS 실험 결과는 같은 프레임 비교에 넣을 수 없습니다.")
        cap=cv2.VideoCapture(str(path)); frame=None
        try:
            for _ in range(index+1):
                ok,frame=cap.read()
                if not ok:
                    raise ValueError(f"결과 프레임 부족: {path}")
        finally:
            cap.release()
        images.append(cv2.cvtColor(frame,cv2.COLOR_BGR2RGB)); titles.append(path.parent.name)
    fig,axes=plt.subplots(1,len(images),figsize=(4*len(images),7),squeeze=False)
    for ax,im,title in zip(axes[0],images,titles):
        ax.imshow(im); ax.set_title(title,fontsize=9); ax.axis("off")
    plt.show()


