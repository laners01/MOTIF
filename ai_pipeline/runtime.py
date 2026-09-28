"""GPU 파이프라인 모듈과 실행 환경을 한 번 준비한다."""

import importlib
import os
import sys
from pathlib import Path
from types import SimpleNamespace


PIPELINE_ROOT = Path(__file__).resolve().parent


def inside_pipeline(path: Path) -> Path:
    """생성 경로가 서버 폴더 내부인지 확인한다."""
    resolved = path.resolve()
    if not resolved.is_relative_to(PIPELINE_ROOT):
        raise ValueError(f"서버 폴더 밖 경로는 사용할 수 없습니다: {resolved}")
    return resolved


def _configure_environment() -> None:
    """단일 GPU, 오프라인 모델 캐시와 작업 경로를 설정한다."""
    for required in ("models", "vendor", "source", "music_sync"):
        path = PIPELINE_ROOT / required
        if not path.is_dir():
            raise RuntimeError(f"필수 로컬 경로가 없습니다: {path}")

    # Cloud Run에서는 이 세 경로에 ephemeral disk를 직접 마운트한다.
    for name in ("work", "output", ".cache"):
        inside_pipeline(PIPELINE_ROOT / name).mkdir(parents=True, exist_ok=True)

    # 서버에는 GPU가 하나이므로 탐색 없이 논리 GPU 0만 사용한다.
    os.environ["CUDA_DEVICE_ORDER"] = "PCI_BUS_ID"
    os.environ["CUDA_VISIBLE_DEVICES"] = "0"
    for key in (
        "TMPDIR", "XDG_CACHE_HOME", "HF_HOME", "TORCH_HOME", "MPLCONFIGDIR",
        "NUMBA_CACHE_DIR", "TRITON_CACHE_DIR", "TORCH_EXTENSIONS_DIR", "CUDA_CACHE_PATH",
        "RF_HOME", "INFERENCE_HOME", "HF_HUB_CACHE",
    ):
        cache = inside_pipeline(PIPELINE_ROOT / ".cache/run_final_job" / key.lower())
        cache.mkdir(parents=True, exist_ok=True)
        os.environ[key] = str(cache)

    os.environ.update(
        HF_HUB_OFFLINE="1",
        TRANSFORMERS_OFFLINE="1",
        PYTHONDONTWRITEBYTECODE="1",
        OPENCV_IO_ENABLE_OPENEXR="1",
    )
    os.chdir(PIPELINE_ROOT)


def load_runtime() -> SimpleNamespace:
    """노트북에서 사용한 로컬 모듈과 S3 클라이언트를 준비한다."""
    _configure_environment()
    if str(PIPELINE_ROOT) not in sys.path:
        sys.path.insert(0, str(PIPELINE_ROOT))

    cv2 = importlib.import_module("cv2")
    np = importlib.import_module("numpy")
    torch = importlib.import_module("torch")
    ndimage = importlib.import_module("scipy.ndimage")
    pipeline = importlib.import_module("source.util.pipeline")
    ex = importlib.import_module("source.util.removal_experiments")
    music = importlib.import_module("source.util.music_sync")
    cp = importlib.import_module("source.relighting.composite")
    relightvid = importlib.import_module("source.relighting.variants")
    dance = importlib.import_module("source.enhancement.correction")
    placement = importlib.import_module("source.enhancement.geometry_placement")
    preservation = importlib.import_module("source.enhancement.preservation")
    occlusion = importlib.import_module("source.enhancement.depth_occlusion")
    edge_matte = importlib.import_module("source.enhancement.edge_matte")
    prepost = importlib.import_module("source.service.prepost")
    prepost.PYTHON = sys.executable
    # portable precomputed 인코딩/복원은 GPU 모델을 로드하지 않는 CPU 모듈이다(§5).
    packing = importlib.import_module("source.util.precomputed_export")
    # MOTIF 합성(job_type == MOTIF)용 모듈. 위 모듈들에만 의존해 추가 인프라가 없다.
    motif = importlib.import_module("source.util.motif_composite")

    missing = relightvid.missing_backend("RelightVid")
    missing += occlusion.missing_backend() + dance.missing() + placement.missing_backend()
    missing += next(row["missing"] for row in ex.backend_status() if row["model"] == "svor")
    for path in ("models/rfdetr/rf-detr-small.pth", "models/sam2.1_hiera_large.pt"):
        if not ex.local(path).is_file():
            missing.append(path)
    if not ex.local(".model-envs/svor/bin/python").is_file():
        missing.append(".model-envs/svor/bin/python")
    if missing:
        raise FileNotFoundError("필수 로컬 환경/모델 없음:\n" + "\n".join(map(str, missing)))
    if not torch.cuda.is_available():
        raise RuntimeError("PyTorch에서 CUDA를 사용할 수 없습니다.")

    return SimpleNamespace(
        gpu=0,
        cv2=cv2,
        np=np,
        torch=torch,
        binary_dilation=ndimage.binary_dilation,
        pipeline=pipeline,
        ex=ex,
        music=music,
        cp=cp,
        relightvid=relightvid,
        dance=dance,
        placement=placement,
        preservation=preservation,
        occlusion=occlusion,
        edge_matte=edge_matte,
        prepost=prepost,
        packing=packing,
        motif=motif,
        s3=importlib.import_module("boto3").client("s3"),
    )
