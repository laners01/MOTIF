"""SAM 2.1 / EfficientTAM / EdgeTAM / MatAnyone 비디오 대상 추적 코어 로직.

CUDA(H200) + Ubuntu Jupyter/터미널 환경 전용. GUI 프레임워크(Gradio)는 쓰지 않는다.
Cloud Run Job에서는 자동 검출과 요청의 person ID로 대상을 선택한다.
"""
from __future__ import annotations

import os

# 이 서버는 GPU 1번만 사용한다. torch import 전에 설정해야 적용된다.
os.environ.setdefault("CUDA_VISIBLE_DEVICES", "1")

import argparse
import contextlib
import json
import shutil
import subprocess
import sys
import time
import gc
import importlib
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Callable

import cv2
import imageio_ffmpeg
import numpy as np
import torch
import yt_dlp
from PIL import Image


class UserError(RuntimeError):
    """사용자 입력/환경 문제로 인한 오류 (스택트레이스 없이 메시지만 보여주면 되는 것들)."""


ProgressFn = Callable[[float, str], None]


ROOT = Path(__file__).resolve().parents[2]
VIDEO_DIR = ROOT / "video"
WORK_DIR = ROOT / "work"
OUTPUT_DIR = ROOT / "output"
MODEL_REGISTRY_PATH = ROOT / "models" / "registry.json"
HAIR_SEGMENTER_PATH = ROOT / "models" / "hair_segmenter.tflite"
OBJECT_ID = 1
MASK_COLOR = np.array([42, 190, 255], dtype=np.float32)
SAM2_ROOT = ROOT / "vendor" / "sam2"
EDGETAM_ROOT = ROOT / "vendor" / "edgetam"
PERSON_BACKENDS = {
    "matanyone_person",
    "efficienttam_matanyone",
    "efficienttam_matanyone_continuous",
    "matanyone2_person",
    "efficienttam_matanyone2",
}
EFFICIENT_BOOTSTRAP_BACKENDS = {
    "efficienttam_matanyone",
    "efficienttam_matanyone_continuous",
    "efficienttam_matanyone2",
}
# 사람 검출을 macOS Apple Vision 대신 SAM2 point prompt로 부트스트랩하는 백엔드.
SAM2_BOOTSTRAP_BACKENDS = PERSON_BACKENDS - EFFICIENT_BOOTSTRAP_BACKENDS
SAM2_BOOTSTRAP_MODEL_ID = "sam2.1_base_plus"
# RF-DETR-S가 사람을 자동 검출해 SAM 2.1에 Box Prompt를 전달하는 백엔드.
SAM2_FAMILY_BACKENDS = {"sam2", "sam2_hair", "rfdetr_s_sam2", "edgetam"}


@dataclass(frozen=True)
class ModelSpec:
    model_id: str
    label: str
    backend: str
    checkpoint: Path
    config: str


def load_model_registry() -> dict[str, ModelSpec]:
    if not MODEL_REGISTRY_PATH.exists():
        raise UserError(f"모델 레지스트리가 없습니다: {MODEL_REGISTRY_PATH}")
    raw_models = json.loads(MODEL_REGISTRY_PATH.read_text(encoding="utf-8"))
    specs: dict[str, ModelSpec] = {}
    for raw in raw_models:
        spec = ModelSpec(
            model_id=raw["id"],
            label=raw["label"],
            backend=raw.get("backend", "sam2"),
            checkpoint=ROOT / raw["checkpoint"],
            config=raw["config"],
        )
        if spec.checkpoint.exists():
            specs[spec.model_id] = spec
    if not specs:
        raise UserError("models/registry.json에 사용 가능한 모델이 없습니다.")
    return specs


MODEL_SPECS = load_model_registry()
DEFAULT_MODEL_ID = "sam2.1_base_plus" if "sam2.1_base_plus" in MODEL_SPECS else next(iter(MODEL_SPECS))


@dataclass
class Session:
    source: Path | None = None
    frame_dir: Path | None = None
    frame_paths: list[Path] = field(default_factory=list)
    fps: float = 30.0
    width: int = 0
    height: int = 0
    predictor: object | None = None
    person_detector: object | None = None
    model_id: str | None = None
    inference_state: dict | None = None
    prompt_frame: int | None = None
    prompt_box: list[float] | None = None
    object_ids: list[int] = field(default_factory=list)  # 자동 검출한 사람 id 전체
    selected_obj_id: int | None = None  # 분리해서 출력할 대상 id
    detections: list[dict] = field(default_factory=list)  # [{obj_id, box, confidence}]
    points: list[list[float]] = field(default_factory=list)
    labels: list[int] = field(default_factory=list)
    preview_masks: dict[int, np.ndarray] = field(default_factory=dict)
    detector_preview: np.ndarray | None = None
    detection_info: dict = field(default_factory=dict)
    result_dir: Path | None = None  # 마지막 track_video 출력 폴더 (인페인팅 입력)
    timings: dict[str, float] = field(default_factory=dict)


SESSION = Session()


def get_device() -> str:
    if torch.cuda.is_available():
        return "cuda"
    return "cpu"


def synchronize_device() -> None:
    if get_device() == "cuda":
        torch.cuda.synchronize()


# A안: 무손실 가속. SAM2는 bf16으로 설계/시연된 모델이라 품질 중립이면서 인코더가 빨라진다.
if torch.cuda.is_available():
    torch.backends.cuda.matmul.allow_tf32 = True
    torch.backends.cudnn.allow_tf32 = True
    torch.backends.cudnn.benchmark = True

# 프레임마다 cuda.synchronize()로 GPU를 멈춰 세우면 정확한 per-frame 타이밍은 얻지만 처리량이 준다.
# 평소엔 끄고, 세밀한 프로파일링이 필요할 때만 TRACK_PROFILE=1 로 켠다.
PROFILE = os.environ.get("TRACK_PROFILE") == "1"


def autocast_ctx():
    """CUDA에서 bf16 autocast, 그 외 장치에서는 아무 것도 하지 않는다."""
    if get_device() == "cuda":
        return torch.autocast("cuda", dtype=torch.bfloat16)
    return contextlib.nullcontext()


def model_choices() -> list[tuple[str, str]]:
    return [(spec.label, spec.model_id) for spec in MODEL_SPECS.values()]


def current_model_spec() -> ModelSpec:
    model_id = SESSION.model_id or DEFAULT_MODEL_ID
    return MODEL_SPECS[model_id]


TIMING_LABELS = {
    "model_build": "모델 로딩",
    "frame_extract": "원본 프레임 추출",
    "state_init": "영상 상태 초기화",
    "person_detection_scan": "RF-DETR-S 사람 검출",
    "auto_box_prompt": "SAM 2.1 박스 프롬프트",
    "video_load_total": "영상 준비 전체",
    "prompt_total": "클릭 프롬프트 처리",
    "model_propagation": "모델 전파",
    "mask_cpu_transfer": "마스크 GPU→CPU 변환",
    "mask_png_write": "PNG 마스크 저장",
    "tracking_loop_total": "추적 루프 전체",
    "result_frames_render": "오버레이·흰 배경 프레임 생성",
    "overlay_video_encode": "오버레이 MP4 인코딩",
    "isolated_video_encode": "흰 배경 MP4 인코딩",
    "tracking_total": "추적·결과 생성 전체",
}


def timing_markdown() -> str:
    spec = current_model_spec()
    lines = [
        "### 단계별 실제 처리 시간",
        f"**모델:** {spec.label} · **장치:** {get_device().upper()}",
        "",
        "| 단계 | 시간 |",
        "|---|---:|",
    ]
    if not SESSION.timings:
        lines.append("| 아직 측정된 단계 없음 | - |")
    else:
        for key, label in TIMING_LABELS.items():
            if key not in SESSION.timings:
                continue
            seconds = SESSION.timings[key]
            display = f"{seconds:.3f}초" if seconds < 60 else f"{seconds:.2f}초 ({seconds / 60:.2f}분)"
            lines.append(f"| {label} | {display} |")

    model_seconds = SESSION.timings.get("model_propagation")
    propagated_frames = int(SESSION.timings.get("propagated_frames", 0))
    if model_seconds and propagated_frames:
        model_fps = propagated_frames / model_seconds
        lines.extend(
            [
                "",
                f"**순수 모델 속도:** {model_fps:.3f} FPS "
                f"({propagated_frames}프레임 / {model_seconds:.2f}초)",
            ]
        )
    correction_count = int(getattr(SESSION.predictor, "correction_count", 0))
    if correction_count:
        lines.extend(["", f"**MatAnyone 연속 교정:** {correction_count}회"])
    hair_frames = int(getattr(SESSION.predictor, "hair_frames_refined", 0))
    if hair_frames:
        added_pixels = int(getattr(SESSION.predictor, "hair_added_pixels_total", 0))
        lines.extend(
            [
                "",
                f"**HairSegmenter 보강:** {hair_frames}프레임 · "
                f"SAM 외곽 머리카락 {added_pixels:,}픽셀 추가",
            ]
        )
    return "\n".join(lines)


def list_videos() -> list[str]:
    extensions = {".mp4", ".mov", ".mkv", ".avi", ".webm", ".m4v"}
    if not VIDEO_DIR.exists():
        return []
    return [
        str(path.relative_to(ROOT))
        for path in sorted(VIDEO_DIR.rglob("*"))
        if path.is_file() and path.suffix.lower() in extensions
    ]


def release_predictor() -> None:
    if SESSION.person_detector is not None and hasattr(SESSION.person_detector, "close"):
        SESSION.person_detector.close()
    SESSION.person_detector = None
    if SESSION.predictor is not None and hasattr(SESSION.predictor, "close"):
        SESSION.predictor.close()
    SESSION.inference_state = None
    SESSION.predictor = None
    gc.collect()
    if get_device() == "cuda":
        torch.cuda.empty_cache()


def activate_sam2_source(backend: str) -> None:
    """Switch between the official SAM 2 and EdgeTAM forks sharing `sam2`."""
    source_root = EDGETAM_ROOT if backend == "edgetam" else SAM2_ROOT
    source = str(source_root)
    other_sources = {str(SAM2_ROOT), str(EDGETAM_ROOT)}
    sys.path[:] = [path for path in sys.path if path not in other_sources]
    sys.path.insert(0, source)
    for name in [name for name in sys.modules if name == "sam2" or name.startswith("sam2.")]:
        del sys.modules[name]
    importlib.invalidate_caches()


def configure_hydra(backend: str) -> None:
    from hydra import initialize_config_module
    from hydra.core.global_hydra import GlobalHydra

    config_module = {
        "sam2": "sam2",
        "sam2_hair": "sam2",
        "rfdetr_s_sam2": "sam2",
        "edgetam": "sam2",
        "efficienttam": "efficient_track_anything",
    }.get(backend)
    if config_module is None:
        raise UserError(f"아직 지원하지 않는 백엔드입니다: {backend}")
    GlobalHydra.instance().clear()
    initialize_config_module(config_module=config_module, version_base="1.2")


def _build_sam2_video_predictor(model_id: str):
    spec = MODEL_SPECS.get(model_id)
    if spec is None:
        raise UserError(f"부트스트랩용 SAM2 모델이 레지스트리에 없습니다: {model_id}")
    activate_sam2_source(spec.backend)
    configure_hydra(spec.backend)
    from sam2.build_sam import build_sam2_video_predictor

    return build_sam2_video_predictor(
        spec.config, str(spec.checkpoint), device=get_device(), apply_postprocessing=False
    )


def load_predictor(model_id: str):
    spec = MODEL_SPECS.get(model_id)
    if spec is None:
        raise UserError("선택한 모델이 레지스트리에 없습니다.")
    if SESSION.predictor is not None and SESSION.model_id == model_id:
        return SESSION.predictor, 0.0, True
    if not spec.checkpoint.exists():
        raise UserError(f"모델이 없습니다: {spec.checkpoint}. setup.sh를 먼저 실행해 주세요.")

    release_predictor()
    if spec.backend in SAM2_FAMILY_BACKENDS:
        activate_sam2_source(spec.backend)
        configure_hydra(spec.backend)
        from sam2.build_sam import build_sam2_video_predictor

        builder = build_sam2_video_predictor
    elif spec.backend == "efficienttam":
        configure_hydra(spec.backend)
        from efficient_track_anything.build_efficienttam import (
            build_efficienttam_video_predictor,
        )

        builder = build_efficienttam_video_predictor
    elif spec.backend in PERSON_BACKENDS:
        builder = None
    else:
        raise UserError(f"아직 지원하지 않는 백엔드입니다: {spec.backend}")

    synchronize_device()
    started = time.perf_counter()
    if spec.backend in SAM2_FAMILY_BACKENDS | {"efficienttam"}:
        built_predictor = builder(
            spec.config,
            str(spec.checkpoint),
            device=get_device(),
            apply_postprocessing=False,
        )
        if spec.backend == "sam2_hair":
            from source.segmentation.hair_segmenter_adapter import build_sam2_hair_predictor

            SESSION.predictor = build_sam2_hair_predictor(built_predictor, HAIR_SEGMENTER_PATH)
        else:
            SESSION.predictor = built_predictor
        if spec.backend == "rfdetr_s_sam2":
            from source.detection.rfdetr_person_detector import RFDETRSmallPersonDetector

            SESSION.person_detector = RFDETRSmallPersonDetector(get_device())
    else:
        bootstrap_predictor = None
        bootstrap_mode = "person"
        if spec.backend in EFFICIENT_BOOTSTRAP_BACKENDS:
            efficient_spec = MODEL_SPECS.get("efficienttam_s")
            if efficient_spec is None:
                raise UserError("EfficientTAM-S 모델이 없어 연결 파이프라인을 시작할 수 없습니다.")
            configure_hydra("efficienttam")
            from efficient_track_anything.build_efficienttam import (
                build_efficienttam_video_predictor,
            )

            bootstrap_predictor = build_efficienttam_video_predictor(
                efficient_spec.config,
                str(efficient_spec.checkpoint),
                device=get_device(),
                apply_postprocessing=False,
            )
            bootstrap_mode = "efficienttam"
        elif spec.backend in SAM2_BOOTSTRAP_BACKENDS:
            # 단독 매팅 모드의 사람 검출: macOS Apple Vision 대신 SAM2 point prompt로 첫 마스크를 만든다.
            bootstrap_predictor = _build_sam2_video_predictor(SAM2_BOOTSTRAP_MODEL_ID)
            bootstrap_mode = "sam2"

        if spec.backend in {"matanyone2_person", "efficienttam_matanyone2"}:
            from source.segmentation.matanyone_adapter import build_matanyone2_predictor

            SESSION.predictor = build_matanyone2_predictor(
                checkpoint=spec.checkpoint,
                device=get_device(),
                bootstrap_mode=bootstrap_mode,
                bootstrap_predictor=bootstrap_predictor,
            )
        else:
            from source.segmentation.matanyone_adapter import build_matanyone_predictor

            SESSION.predictor = build_matanyone_predictor(
                checkpoint=spec.checkpoint,
                device=get_device(),
                bootstrap_mode=bootstrap_mode,
                bootstrap_predictor=bootstrap_predictor,
                continuous_correction=spec.backend == "efficienttam_matanyone_continuous",
            )
    synchronize_device()
    elapsed = time.perf_counter() - started
    SESSION.model_id = model_id
    return SESSION.predictor, elapsed, False


def read_frame(index: int) -> np.ndarray:
    if not SESSION.frame_paths:
        raise UserError("먼저 영상을 불러오세요.")
    index = max(0, min(int(index), len(SESSION.frame_paths) - 1))
    return np.asarray(Image.open(SESSION.frame_paths[index]).convert("RGB"))


def overlay_mask(image: np.ndarray, mask: np.ndarray | None) -> np.ndarray:
    if mask is None:
        return image
    result = image.astype(np.float32).copy()
    binary = mask.astype(bool)
    result[binary] = result[binary] * 0.45 + MASK_COLOR * 0.55
    contours, _ = cv2.findContours(binary.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    bgr = cv2.cvtColor(np.clip(result, 0, 255).astype(np.uint8), cv2.COLOR_RGB2BGR)
    cv2.drawContours(bgr, contours, -1, (0, 255, 255), 2)
    return cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)


def draw_points(image: np.ndarray) -> np.ndarray:
    result = image.copy()
    for (x, y), label in zip(SESSION.points, SESSION.labels):
        color = (40, 220, 40) if label == 1 else (255, 50, 50)
        cv2.circle(result, (round(x), round(y)), 7, color, -1, lineType=cv2.LINE_AA)
        cv2.circle(result, (round(x), round(y)), 9, (255, 255, 255), 2, lineType=cv2.LINE_AA)
    return result


def extract_frames(source: Path, target: Path) -> tuple[list[Path], float, int, int]:
    shutil.rmtree(target, ignore_errors=True)
    target.mkdir(parents=True, exist_ok=True)
    capture = cv2.VideoCapture(str(source))
    if not capture.isOpened():
        raise UserError(f"영상을 열 수 없습니다: {source.name}")

    fps = capture.get(cv2.CAP_PROP_FPS)
    fps = fps if fps and fps > 0 else 30.0
    width = int(capture.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT))
    paths: list[Path] = []
    index = 0
    while True:
        ok, frame = capture.read()
        if not ok:
            break
        path = target / f"{index:06d}.jpg"
        cv2.imwrite(str(path), frame, [cv2.IMWRITE_JPEG_QUALITY, 95])
        paths.append(path)
        index += 1
    capture.release()
    if not paths:
        raise UserError("영상에서 프레임을 읽지 못했습니다.")
    return paths, fps, width, height


def apply_automatic_person_box_prompt() -> str:
    """RF-DETR-S로 사람을 자동 검출해 SAM 2.1에 Box Prompt를 전달한다.

    사람 자동 백엔드(rfdetr_s_sam2)에서만 동작한다. 검출한 박스를 그린 미리보기를
    `SESSION.detector_preview`와 `work/current/detector_box.jpg`에 남긴다.
    """
    detector = SESSION.person_detector
    if detector is None or SESSION.inference_state is None:
        return ""

    synchronize_device()
    detection_started = time.perf_counter()
    detection = detector.find_people(SESSION.frame_paths, SESSION.fps)
    synchronize_device()
    SESSION.timings["person_detection_scan"] = time.perf_counter() - detection_started

    # 검출한 사람 전원을 id 1..N으로 SAM 2.1에 등록한다(겹치는 사람끼리 구분되도록).
    synchronize_device()
    prompt_started = time.perf_counter()
    object_ids: list[int] = []
    logits = None
    with autocast_ctx():
        for obj_id, box in enumerate(
            (np.asarray(b, dtype=np.float32) for b in detection.boxes), start=1
        ):
            _, object_ids, logits = SESSION.predictor.add_new_points_or_box(
                inference_state=SESSION.inference_state,
                frame_idx=detection.frame_index,
                obj_id=obj_id,
                box=box,
            )
    synchronize_device()
    SESSION.timings["auto_box_prompt"] = time.perf_counter() - prompt_started

    # 마지막 호출의 logits에는 등록된 객체 전부가 들어 있다. 각 id의 미리보기 마스크를 저장.
    object_ids = list(object_ids)
    SESSION.object_ids = [i + 1 for i in range(len(detection.boxes))]
    SESSION.detections = [
        {"obj_id": i + 1, "box": np.asarray(box, dtype=np.float32).tolist(),
         "confidence": float(conf)}
        for i, (box, conf) in enumerate(zip(detection.boxes, detection.confidences))
    ]
    SESSION.selected_obj_id = SESSION.object_ids[0]  # 기본 = confidence 최상위(id1)
    SESSION.prompt_frame = detection.frame_index
    SESSION.prompt_box = None
    selected_position = object_ids.index(SESSION.selected_obj_id)
    SESSION.preview_masks[detection.frame_index] = (
        logits[selected_position, 0] > 0.0
    ).detach().cpu().numpy()
    SESSION.detector_preview = detection.annotated_rgb
    SESSION.detection_info = {
        "detector": "RF-DETR-S",
        "runtime": detection.runtime,
        "frame_index": detection.frame_index,
        "people": SESSION.detections,
        "selected_obj_id": SESSION.selected_obj_id,
    }
    preview_path = WORK_DIR / "current" / "detector_box.jpg"
    Image.fromarray(detection.annotated_rgb).save(preview_path, quality=92)
    conf_list = ", ".join(
        f"id{d['obj_id']}={d['confidence']:.2f}" for d in SESSION.detections
    )
    return (
        f" · 자동 검출: frame {detection.frame_index}, 사람 {len(SESSION.detections)}명 "
        f"({conf_list}) · 기본 선택 id{SESSION.selected_obj_id} · {detection.runtime} · "
        f"미리보기 {preview_path.relative_to(ROOT)}"
    )


def select_object(obj_id: int) -> str:
    """자동 검출된 사람 중 분리해서 출력할 대상 id를 고른다."""
    if not SESSION.object_ids:
        raise UserError("먼저 자동 검출 모델로 영상을 로드하세요.")
    if int(obj_id) not in SESSION.object_ids:
        raise UserError(f"검출된 id가 아닙니다: {obj_id} · 가능: {SESSION.object_ids}")
    SESSION.selected_obj_id = int(obj_id)
    SESSION.detection_info["selected_obj_id"] = SESSION.selected_obj_id
    conf = next(
        (d["confidence"] for d in SESSION.detections if d["obj_id"] == int(obj_id)), None
    )
    return f"선택한 대상: id{obj_id}" + (
        f" (confidence {conf:.3f})" if conf is not None else ""
    )


def load_video(video_name: str, model_id: str) -> str:
    """영상을 불러와 세션을 초기화하고 상태 메시지를 반환한다."""
    if not video_name:
        raise UserError("video 폴더에서 영상을 선택하세요.")
    source = (ROOT / video_name).resolve()
    input_roots = tuple((ROOT / p).resolve() for p in
                        ("video", "output/music_sync", "output/background_precomputed", "output/run_post",
                         "output/motif_precompute", "output/motif_compose"))
    input_roots += tuple(p.resolve() for p in ROOT.glob("Result_*") if p.is_dir())
    if (not source.is_relative_to(ROOT) or not any(source.is_relative_to(folder) for folder in input_roots)
            or not source.is_file()):
        raise UserError("허용된 프로젝트 입력/서비스 결과 폴더 안의 유효한 영상이 아닙니다.")

    load_started = time.perf_counter()
    SESSION.timings.clear()
    predictor, model_build_seconds, model_reused = load_predictor(model_id)
    SESSION.timings["model_build"] = model_build_seconds

    frame_dir = WORK_DIR / "current" / "frames"
    extract_started = time.perf_counter()
    paths, fps, width, height = extract_frames(source, frame_dir)
    SESSION.timings["frame_extract"] = time.perf_counter() - extract_started

    synchronize_device()
    init_started = time.perf_counter()
    state = predictor.init_state(
        video_path=str(frame_dir),
        offload_video_to_cpu=True,
        offload_state_to_cpu=False,
        async_loading_frames=False,
    )
    predictor.reset_state(state)
    synchronize_device()
    SESSION.timings["state_init"] = time.perf_counter() - init_started
    SESSION.timings["video_load_total"] = time.perf_counter() - load_started
    SESSION.source = source
    SESSION.frame_dir = frame_dir
    SESSION.frame_paths = paths
    SESSION.fps = fps
    SESSION.width = width
    SESSION.height = height
    SESSION.inference_state = state
    SESSION.prompt_frame = None
    SESSION.prompt_box = None
    SESSION.object_ids = []
    SESSION.selected_obj_id = None
    SESSION.detections = []
    SESSION.points.clear()
    SESSION.labels.clear()
    SESSION.preview_masks.clear()
    SESSION.detector_preview = None
    SESSION.detection_info.clear()
    SESSION.result_dir = None

    auto_detection_note = apply_automatic_person_box_prompt()
    SESSION.timings["video_load_total"] = time.perf_counter() - load_started

    duration = len(paths) / fps
    spec = current_model_spec()
    reuse_text = "· 모델 재사용" if model_reused else "· 모델 새로 로딩"
    prompt_note = " · 첫 프레임(0)에서 클릭" if getattr(predictor, "requires_first_frame", False) else ""
    return (
        f"준비 완료 · {spec.label} {reuse_text} · {len(paths)} 프레임 · "
        f"{fps:.2f} FPS · {width}×{height} · {duration:.1f}초 · "
        f"장치: {get_device().upper()}{prompt_note}{auto_detection_note}"
    )


def download_video(url: str, progress: ProgressFn | None = None) -> str:
    """URL에서 영상을 내려받아 `video/downloads/`에 저장하고 프로젝트 상대 경로를 반환한다."""
    url = (url or "").strip()
    if not url.startswith(("http://", "https://")):
        raise UserError("http:// 또는 https://로 시작하는 영상 URL을 입력하세요.")

    download_dir = VIDEO_DIR / "downloads"
    download_dir.mkdir(parents=True, exist_ok=True)
    if progress:
        progress(0.05, "영상 정보를 확인하는 중")

    ffmpeg_dir = str(Path(imageio_ffmpeg.get_ffmpeg_exe()).parent)
    options = {
        "outtmpl": str(download_dir / "%(title).80s_%(id)s.%(ext)s"),
        "format": "bv*+ba/b",
        "merge_output_format": "mp4",
        "ffmpeg_location": ffmpeg_dir,
        "noplaylist": True,
        "restrictfilenames": True,
        "quiet": True,
        "no_warnings": True,
    }
    try:
        with yt_dlp.YoutubeDL(options) as downloader:
            info = downloader.extract_info(url, download=True)
            downloaded = Path(downloader.prepare_filename(info))
            requested = info.get("requested_downloads") or []
            if requested and requested[0].get("filepath"):
                downloaded = Path(requested[0]["filepath"])
            mp4_candidate = downloaded.with_suffix(".mp4")
            if mp4_candidate.exists():
                downloaded = mp4_candidate
    except Exception as exc:
        raise UserError(f"URL 영상을 가져오지 못했습니다: {exc}") from exc

    if not downloaded.exists():
        candidates = sorted(
            download_dir.glob(f"*{info.get('id', '')}*"), key=lambda path: path.stat().st_mtime
        )
        if not candidates:
            raise UserError("다운로드는 끝났지만 영상 파일을 찾지 못했습니다.")
        downloaded = candidates[-1]

    if progress:
        progress(0.7, "다운로드 완료")
    return str(downloaded.relative_to(ROOT))


def show_frame(frame_index: int) -> np.ndarray:
    index = int(frame_index)
    image = read_frame(index)
    image = overlay_mask(image, SESSION.preview_masks.get(index))
    if SESSION.prompt_frame == index:
        image = draw_points(image)
    return image


def add_point(frame_index: int, x: float, y: float, is_positive: bool) -> tuple[np.ndarray, str]:
    """프레임 위 한 점을 대상(+)/제외(-) 프롬프트로 추가하고 미리보기 마스크를 갱신한다."""
    if SESSION.inference_state is None:
        raise UserError("먼저 영상을 불러오세요.")

    index = int(frame_index)
    if getattr(SESSION.predictor, "requires_first_frame", False) and index != 0:
        raise UserError("MatAnyone 모드는 첫 프레임(0)에서 사람을 클릭해 주세요.")
    if SESSION.prompt_frame is not None and SESSION.prompt_frame != index:
        raise UserError("한 번의 테스트에서는 한 프레임에 클릭을 모아 주세요. 초기화 후 다른 프레임을 선택할 수 있습니다.")
    SESSION.prompt_frame = index
    SESSION.points.append([float(x), float(y)])
    SESSION.labels.append(1 if is_positive else 0)

    points = np.asarray(SESSION.points, dtype=np.float32)
    labels = np.asarray(SESSION.labels, dtype=np.int32)
    synchronize_device()
    prompt_started = time.perf_counter()
    with autocast_ctx():
        _, object_ids, logits = SESSION.predictor.add_new_points_or_box(
            inference_state=SESSION.inference_state,
            frame_idx=index,
            obj_id=OBJECT_ID,
            points=points,
            labels=labels,
        )
    synchronize_device()
    SESSION.timings["prompt_total"] = SESSION.timings.get("prompt_total", 0.0) + (
        time.perf_counter() - prompt_started
    )
    object_ids = list(object_ids)
    object_position = object_ids.index(OBJECT_ID)
    mask = (logits[object_position, 0] > 0.0).detach().cpu().numpy()
    SESSION.preview_masks[index] = mask
    image = draw_points(overlay_mask(read_frame(index), mask))
    status = f"프레임 {index}: 클릭 {len(points)}개 · 초록=대상, 빨강=제외"
    return image, status


def reset_prompts(frame_index: int) -> np.ndarray:
    if SESSION.inference_state is not None:
        SESSION.predictor.reset_state(SESSION.inference_state)
    SESSION.prompt_frame = None
    SESSION.prompt_box = None
    SESSION.object_ids = []
    SESSION.selected_obj_id = None
    SESSION.detections = []
    SESSION.points.clear()
    SESSION.labels.clear()
    SESSION.preview_masks.clear()
    SESSION.detector_preview = None
    SESSION.detection_info.clear()
    SESSION.timings.pop("prompt_total", None)
    for key in (
        "model_propagation",
        "mask_cpu_transfer",
        "mask_png_write",
        "tracking_loop_total",
        "result_frames_render",
        "overlay_video_encode",
        "isolated_video_encode",
        "tracking_total",
        "propagated_frames",
    ):
        SESSION.timings.pop(key, None)
    return show_frame(int(frame_index))


def write_mask(mask_path: Path, mask: np.ndarray) -> None:
    mask_path.parent.mkdir(parents=True, exist_ok=True)
    if mask.dtype == np.bool_:
        encoded = mask.astype(np.uint8) * 255
    else:
        encoded = np.clip(mask, 0.0, 1.0) * 255.0
        encoded = np.rint(encoded).astype(np.uint8)
    cv2.imwrite(str(mask_path), encoded)


def _ffmpeg_rgb_pipe(out_path: Path, width: int, height: int, fps: float,
                     audio_source: Path | None = None, *, frame_count: int) -> subprocess.Popen:
    """RGB 원시 프레임을 stdin으로 받아 mp4로 인코딩하는 ffmpeg 프로세스를 연다."""
    for path in (out_path, audio_source):
        if path is not None and not Path(path).resolve().is_relative_to(ROOT):
            raise ValueError(f"프로젝트 밖 경로: {path}")
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    cmd = [
        ffmpeg, "-y", "-loglevel", "error",
        "-f", "rawvideo", "-pixel_format", "rgb24",
        "-video_size", f"{width}x{height}", "-framerate", f"{fps:.8f}", "-i", "-",
    ]
    if audio_source is not None:
        duration = frame_count / fps
        cmd += ["-i", str(audio_source), "-map", "0:v:0", "-map", "1:a:0?",
                "-c:a", "aac", "-af", f"apad=whole_dur={duration:.9f},atrim=duration={duration:.9f}"]
    cmd += ["-vf", "pad=ceil(iw/2)*2:ceil(ih/2)*2",
            "-c:v", "libx264", "-preset", "medium", "-crf", "18",
            "-pix_fmt", "yuv420p", str(out_path)]
    # A file cannot fill up a stderr pipe while Python is feeding video frames.
    with out_path.with_suffix(".ffmpeg.log").open("wb") as log:
        return subprocess.Popen(cmd, stdin=subprocess.PIPE, stderr=log)


def render_outputs(result_dir: Path, progress: ProgressFn | None = None) -> tuple[Path, Path, float, float, float]:
    """오버레이·흰 배경 영상을 만든다.

    프레임을 디스크 JPEG로 저장하지 않고 ffmpeg stdin으로 직접 파이프한다
    (중간 JPEG 인코딩+재디코딩과 임시 디렉터리 I/O 제거). 인코딩이 렌더에 융합된다.
    """
    masks_dir = result_dir / "masks"
    total = len(SESSION.frame_paths)
    overlay_video = result_dir / "overlay.mp4"
    isolated_video = result_dir / "isolated.mp4"

    render_started = time.perf_counter()
    processes = []
    def encoding_error(proc, path):
        log = path.with_suffix(".ffmpeg.log")
        detail = log.read_text(errors="replace")[-6000:]
        return RuntimeError(f"{path.name} 인코딩 실패 (exit {proc.returncode}) · 입력 {total}프레임\n"
                            f"로그: {log}\n{detail}")
    try:
        for path in (overlay_video, isolated_video):
            proc = _ffmpeg_rgb_pipe(path, SESSION.width, SESSION.height, SESSION.fps,
                                    SESSION.source, frame_count=total)
            processes.append((proc, path))
        for index, frame_path in enumerate(SESSION.frame_paths):
            frame = cv2.imread(str(frame_path))[:, :, ::-1]  # BGR→RGB (뷰)
            mask_path = masks_dir / f"{index:06d}.png"
            if mask_path.exists():
                alpha = cv2.imread(str(mask_path), cv2.IMREAD_GRAYSCALE).astype(np.float32) / 255.0
            else:
                alpha = np.zeros(frame.shape[:2], dtype=np.float32)

            overlay = overlay_mask(frame, alpha > 0.5)
            isolated = frame.astype(np.float32) * alpha[..., None] + 255.0 * (1.0 - alpha[..., None])
            isolated = np.clip(isolated, 0, 255).astype(np.uint8)
            for (proc, path), image in zip(processes, (overlay, isolated)):
                proc.stdin.write(np.ascontiguousarray(image).tobytes())
            if progress:
                progress((index + 1) / total, f"오버레이·흰 배경 영상 생성 {index + 1}/{total}")

        for proc, path in processes:
            proc.stdin.close()
            proc.wait()
            if proc.returncode != 0:
                raise encoding_error(proc, path)
    except BrokenPipeError as exc:
        proc.wait(timeout=10)
        raise encoding_error(proc, path) from exc
    finally:
        for proc, _ in processes:
            if proc.poll() is None:
                proc.terminate()
            with contextlib.suppress(OSError):
                proc.stdin.close()
            try:
                proc.wait(timeout=10)
            except subprocess.TimeoutExpired:
                proc.kill()
                proc.wait()
    for path in (overlay_video, isolated_video):
        cap = cv2.VideoCapture(str(path))
        try:
            actual = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        finally:
            cap.release()
        if actual != total:
            raise RuntimeError(f"{path.name} 저장 프레임 불일치: {actual}/{total}")
    render_seconds = time.perf_counter() - render_started
    # 인코딩이 렌더에 융합됨 → 별도 인코딩 시간은 0으로 보고.
    return overlay_video, isolated_video, render_seconds, 0.0, 0.0


def track_video(progress: ProgressFn | None = None) -> tuple[Path, Path, str]:
    """전체 영상을 추적하고 결과 영상 경로와 상태 메시지를 반환한다."""
    has_prompt = (
        bool(SESSION.points) or SESSION.prompt_box is not None or bool(SESSION.object_ids)
    )
    if SESSION.inference_state is None or SESSION.prompt_frame is None or not has_prompt:
        raise UserError("먼저 영상을 자동 검출하거나 추적할 대상을 클릭하세요.")

    # 자동 검출은 선택 id, 수동 클릭은 단일 OBJECT_ID를 출력 대상으로 쓴다.
    target_id = SESSION.selected_obj_id if SESSION.selected_obj_id is not None else OBJECT_ID

    with torch.inference_mode(), autocast_ctx():
        tracking_started = time.perf_counter()
        for key in (
            "model_propagation",
            "mask_cpu_transfer",
            "mask_png_write",
            "tracking_loop_total",
            "result_frames_render",
            "overlay_video_encode",
            "isolated_video_encode",
            "tracking_total",
            "propagated_frames",
        ):
            SESSION.timings.pop(key, None)
        stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        result_dir = OUTPUT_DIR / f"{SESSION.source.stem}_{stamp}"
        masks_dir = result_dir / "masks"
        masks_dir.mkdir(parents=True, exist_ok=True)
        SESSION.result_dir = result_dir  # 인페인팅(remove_object) 입력으로 재사용
        total = len(SESSION.frame_paths)
        completed: set[int] = set()
        propagated_frames = 0
        model_seconds = 0.0
        transfer_seconds = 0.0
        write_seconds = 0.0
        loop_started = time.perf_counter()

        directions = [(False, "정방향"), (True, "역방향")]
        for reverse, label in directions:
            if reverse and SESSION.prompt_frame == 0:
                continue
            iterator = SESSION.predictor.propagate_in_video(
                SESSION.inference_state,
                start_frame_idx=SESSION.prompt_frame,
                reverse=reverse,
            )
            while True:
                if PROFILE:
                    synchronize_device()
                    model_started = time.perf_counter()
                try:
                    frame_index, object_ids, logits = next(iterator)
                except StopIteration:
                    break
                if PROFILE:
                    synchronize_device()
                    model_seconds += time.perf_counter() - model_started
                propagated_frames += 1

                object_ids = list(object_ids)
                if target_id not in object_ids:
                    continue
                position = object_ids.index(target_id)

                transfer_started = time.perf_counter()
                raw_mask = logits[position, 0].detach().cpu().numpy()
                if getattr(SESSION.predictor, "returns_alpha", False):
                    mask = np.clip(raw_mask, 0.0, 1.0)
                else:
                    mask = raw_mask > 0.0
                transfer_seconds += time.perf_counter() - transfer_started

                write_started = time.perf_counter()
                write_mask(masks_dir / f"{int(frame_index):06d}.png", mask)
                write_seconds += time.perf_counter() - write_started
                completed.add(int(frame_index))
                if progress:
                    elapsed = model_seconds if PROFILE else (time.perf_counter() - loop_started)
                    live_fps = propagated_frames / elapsed if elapsed else 0.0
                    correction_suffix = (
                        f" · 교정 {int(getattr(SESSION.predictor, 'correction_count', 0))}회"
                        if getattr(SESSION.predictor, "continuous_correction", False)
                        else ""
                    )
                    progress(
                        len(completed) / total,
                        f"{label} 추적 {len(completed)}/{total} · "
                        f"모델 {model_seconds:.1f}초 · {live_fps:.2f} FPS{correction_suffix}",
                    )

        loop_wall = time.perf_counter() - loop_started
        if not PROFILE:
            # per-frame 동기화를 생략했으므로 모델 시간을 루프 전체 벽시계로 근사한다.
            model_seconds = loop_wall
        SESSION.timings["model_propagation"] = model_seconds
        SESSION.timings["mask_cpu_transfer"] = transfer_seconds
        SESSION.timings["mask_png_write"] = write_seconds
        SESSION.timings["tracking_loop_total"] = loop_wall
        SESSION.timings["propagated_frames"] = float(propagated_frames)

        overlay_video, isolated_video, render_seconds, overlay_encode_seconds, isolated_encode_seconds = (
            render_outputs(result_dir, progress)
        )
        SESSION.timings["result_frames_render"] = render_seconds
        SESSION.timings["overlay_video_encode"] = overlay_encode_seconds
        SESSION.timings["isolated_video_encode"] = isolated_encode_seconds
        SESSION.timings["tracking_total"] = time.perf_counter() - tracking_started
        spec = current_model_spec()
        metadata = {
            "source": str(SESSION.source.relative_to(ROOT)),
            "model_id": spec.model_id,
            "model": spec.label,
            "checkpoint": spec.checkpoint.name,
            "device": get_device(),
            "frames": total,
            "fps": SESSION.fps,
            "resolution": [SESSION.width, SESSION.height],
            "prompt_frame": SESSION.prompt_frame,
            "points": SESSION.points,
            "labels": SESSION.labels,
            "box_prompt_xyxy": SESSION.prompt_box,
            "automatic_person_detection": SESSION.detection_info,
            "timings_seconds": SESSION.timings,
            "model_propagation_fps": (propagated_frames / model_seconds if model_seconds else None),
            "continuous_corrections": getattr(SESSION.predictor, "correction_events", []),
            "hair_segmenter": {
                "frames_refined": int(getattr(SESSION.predictor, "hair_frames_refined", 0)),
                "added_pixels": int(getattr(SESSION.predictor, "hair_added_pixels_total", 0)),
            },
            "outputs": {
                "overlay": overlay_video.name,
                "isolated_white_background": isolated_video.name,
            },
        }
        (result_dir / "metadata.json").write_text(
            json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        correction_status = (
            f"연속 교정 {int(getattr(SESSION.predictor, 'correction_count', 0))}회 · "
            if getattr(SESSION.predictor, "continuous_correction", False)
            else ""
        )
        status = (
            f"완료 · {spec.label} · {len(completed)}/{total} 프레임 · "
            f"순수 모델 {model_seconds:.2f}초 ({propagated_frames / model_seconds:.3f} FPS) · "
            f"{correction_status}"
            f"전체 {SESSION.timings['tracking_total']:.2f}초 · {result_dir.relative_to(ROOT)}"
        )
        return overlay_video, isolated_video, status


def remove_object(
    result_dir: Path | str | None = None,
    dilate_px: int = 8,
    fp16: bool = True,
    resize_ratio: float = 1.0,
    progress: ProgressFn | None = None,
) -> tuple[Path, str]:
    """추적한 대상을 지우고 그 자리를 ProPainter로 인페인팅해 removed.mp4를 만든다.

    track_video의 masks(선택 id)를 구멍으로, 원본 프레임을 배경 소스로 쓴다.
    dilate_px는 마스크를 넓혀 경계 잔상을 줄인다. 결과 mp4 경로와 상태 메시지를 반환.
    """
    from source.inpainting import inpaint_propainter as ip

    target = Path(result_dir) if result_dir else SESSION.result_dir
    if target is None or not (target / "masks").exists():
        raise UserError("먼저 track_video로 대상을 추적해 masks를 만들어 주세요.")
    if not SESSION.frame_paths or SESSION.frame_dir is None:
        raise UserError("원본 프레임이 없습니다. 영상을 다시 로드하세요.")

    started = time.perf_counter()
    # 프레임마다 마스크 보장(누락=검은 마스크). ProPainter는 프레임/마스크 수가 맞아야 한다.
    masks_full = WORK_DIR / "current" / "inpaint_masks"
    shutil.rmtree(masks_full, ignore_errors=True)
    holes = ip.prepare_masks(SESSION.frame_paths, target / "masks", masks_full)
    if holes == 0:
        raise UserError("마스크에 지울 영역이 없습니다. 대상 선택/추적을 확인하세요.")

    out_tmp = WORK_DIR / "current" / "inpaint_out"
    shutil.rmtree(out_tmp, ignore_errors=True)
    mp4 = ip.run_propainter(
        SESSION.frame_dir,
        masks_full,
        out_tmp,
        dilate_px=dilate_px,
        fp16=fp16,
        fps=SESSION.fps,
        resize_ratio=resize_ratio,
        python_exe=sys.executable,
        progress=(lambda s: progress(0.5, s)) if progress else None,
    )
    removed = target / "removed.mp4"
    shutil.copyfile(mp4, removed)
    SESSION.timings["inpaint_total"] = time.perf_counter() - started

    meta_path = target / "metadata.json"
    if meta_path.exists():
        meta = json.loads(meta_path.read_text(encoding="utf-8"))
        meta["object_removed_inpaint"] = {
            "file": removed.name,
            "backend": "ProPainter",
            "mask_dilation": dilate_px,
            "fp16": fp16,
            "resize_ratio": resize_ratio,
            "seconds": round(SESSION.timings["inpaint_total"], 2),
        }
        meta_path.write_text(
            json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8"
        )

    status = (
        f"인페인팅 완료 · ProPainter · 구멍 {holes}/{len(SESSION.frame_paths)} 프레임 · "
        f"dilation {dilate_px}px · {SESSION.timings['inpaint_total']:.1f}초 · "
        f"{removed.relative_to(ROOT)}"
    )
    return removed, status


def export_isolated_crop(
    result_dir: Path | str | None = None, margin: float = 0.06,
    progress: ProgressFn | None = None,
) -> tuple[Path, dict, str]:
    """추적한 전경을 합집합 bbox로 크롭한 RGBA 시퀀스로 export (합성 자산).

    현재 세션의 원본 프레임 + 해당 result_dir의 masks를 쓰므로, 소스 영상을 로드·추적한
    직후에 호출해야 한다(다른 영상을 로드하면 원본 프레임이 덮어써진다).
    반환: (크롭 디렉터리, bbox dict, 상태 메시지).
    """
    from source.relighting import composite as cp

    target = Path(result_dir) if result_dir else SESSION.result_dir
    if target is None or not (target / "masks").exists():
        raise UserError("먼저 track_video로 전경을 추적해 masks를 만들어 주세요.")
    if not SESSION.frame_paths:
        raise UserError("원본 프레임이 없습니다. 소스 영상을 다시 로드하세요.")

    crop_dir, bbox = cp.export_isolated_crop(
        SESSION.frame_paths, target / "masks", target,
        margin=margin, fps=SESSION.fps, audio_source=SESSION.source, progress=progress,
    )
    status = (
        f"isolated 크롭 완료 · {bbox['w']}×{bbox['h']} · {len(SESSION.frame_paths)} 프레임 · "
        f"{crop_dir.relative_to(ROOT)}"
    )
    return crop_dir, bbox, status


def select_model(model_id: str) -> str:
    if model_id not in MODEL_SPECS:
        raise UserError("선택한 모델을 찾을 수 없습니다.")
    changed = SESSION.model_id != model_id
    if changed:
        release_predictor()
        SESSION.model_id = model_id
        SESSION.prompt_frame = None
        SESSION.prompt_box = None
        SESSION.object_ids = []
        SESSION.selected_obj_id = None
        SESSION.detections = []
        SESSION.points.clear()
        SESSION.labels.clear()
        SESSION.preview_masks.clear()
        SESSION.detector_preview = None
        SESSION.detection_info.clear()
        SESSION.timings.clear()
    spec = MODEL_SPECS[model_id]
    if spec.backend in PERSON_BACKENDS:
        prompt_note = " 사람 전용 매팅 모드는 첫 프레임(0)에서 클릭합니다."
    elif spec.backend == "sam2_hair":
        prompt_note = " HairSegmenter 보강은 선택한 사람이 화면에 크게 보일수록 정확합니다."
    elif spec.backend == "rfdetr_s_sam2":
        prompt_note = (
            " 영상을 불러오면 RF-DETR-S가 사람을 자동 검출해 SAM 2.1 Large에 박스를 "
            "전달합니다. CUDA에서는 TensorRT FP16을 우선 사용합니다."
        )
    else:
        prompt_note = ""
    return f"모델 선택: {spec.label} · 영상 불러오기를 누르면 이 모델로 새 추론 상태를 준비합니다.{prompt_note}"


def _cli() -> None:
    parser = argparse.ArgumentParser(description="터미널에서 영상 대상 추적 실행 (클릭 UI 없이 좌표를 직접 지정)")
    parser.add_argument("--video", required=True, help="video/ 폴더 기준 상대 경로 또는 --url로 받은 영상")
    parser.add_argument("--url", help="video 대신 URL에서 영상을 내려받아 사용")
    parser.add_argument("--model", default=DEFAULT_MODEL_ID, choices=sorted(MODEL_SPECS))
    parser.add_argument("--frame", type=int, default=0, help="클릭을 적용할 프레임 인덱스")
    parser.add_argument(
        "--point",
        action="append",
        default=[],
        metavar="X,Y,LABEL",
        help="예: --point 480,270,1 (label 1=대상, 0=제외). 여러 번 지정 가능. "
        "rfdetr_s_sam2 모델은 생략하면 사람을 자동 검출한다.",
    )
    args = parser.parse_args()

    video_name = download_video(args.url, progress=lambda f, d: print(f"  {f*100:.0f}% {d}")) if args.url else args.video

    print(f"[장치] {get_device().upper()}")
    print(load_video(video_name, args.model))
    for raw_point in args.point:
        x_str, y_str, label_str = raw_point.split(",")
        _, status = add_point(args.frame, float(x_str), float(y_str), label_str.strip() == "1")
        print(status)

    def report(fraction: float, desc: str) -> None:
        print(f"  {fraction * 100:5.1f}% {desc}")

    overlay_video, isolated_video, status = track_video(progress=report)
    print(status)
    print(f"overlay: {overlay_video}")
    print(f"isolated: {isolated_video}")


if __name__ == "__main__":
    _cli()
