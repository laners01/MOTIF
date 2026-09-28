"""Docker 빌드 중 필요한 공개 가중치를 병렬로 내려받는다."""

from __future__ import annotations

import argparse
import os
import shutil
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
GVHMR_DRIVE_URL = "https://drive.google.com/drive/folders/1eebJ13FUEXrKBawHpJroW0sNSxLjh9xD"
DIRECT_DOWNLOADS = {
    "models/sam2.1_hiera_large.pt": (
        "https://dl.fbaipublicfiles.com/segment_anything_2/092824/sam2.1_hiera_large.pt"
    ),
    "vendor/Video-Depth-Anything/checkpoints/video_depth_anything_vitl.pth": (
        "https://huggingface.co/depth-anything/Video-Depth-Anything-Large/resolve/main/"
        "video_depth_anything_vitl.pth?download=true"
    ),
    "vendor/MimicMotion/models/DWPose/yolox_l.onnx": (
        "https://huggingface.co/yzd-v/DWPose/resolve/main/yolox_l.onnx?download=true"
    ),
    "vendor/MimicMotion/models/DWPose/dw-ll_ucoco_384.onnx": (
        "https://huggingface.co/yzd-v/DWPose/resolve/main/dw-ll_ucoco_384.onnx?download=true"
    ),
}
GVHMR_SUPPORT = (
    "smplx2smpl_sparse.pt",
    "smpl_coco17_J_regressor.pt",
    "smplx_verts437.pt",
)
REQUIRED = (
    "models/rfdetr/rf-detr-small.pth",
    "models/sam2.1_hiera_large.pt",
    "vendor/Video-Depth-Anything/checkpoints/video_depth_anything_vitl.pth",
    "vendor/MimicMotion/models/DWPose/yolox_l.onnx",
    "vendor/MimicMotion/models/DWPose/dw-ll_ucoco_384.onnx",
    "vendor/GVHMR/inputs/checkpoints/gvhmr/gvhmr_siga24_release.ckpt",
    "vendor/GVHMR/inputs/checkpoints/hmr2/epoch=10-step=25000.ckpt",
    "vendor/GVHMR/inputs/checkpoints/vitpose/vitpose-h-multi-coco.pth",
    "vendor/GVHMR/inputs/checkpoints/body_models/smplx/SMPLX_NEUTRAL.npz",
    *(f"vendor/GVHMR/hmr4d/utils/body_model/{name}" for name in GVHMR_SUPPORT),
    "vendor/svor/models/Wan2.1-VACE-1.3B/config.json",
    "vendor/svor/models/Wan2.1-VACE-1.3B/diffusion_pytorch_model.safetensors",
    "vendor/svor/models/Wan2.1-VACE-1.3B/Wan2.1_VAE.pth",
    "vendor/svor/models/Wan2.1-VACE-1.3B/models_t5_umt5-xxl-enc-bf16.pth",
    "vendor/svor/models/Wan2.1-VACE-1.3B/google/umt5-xxl/tokenizer_config.json",
    "vendor/svor/models/remove_model_stage1.safetensors",
    "vendor/svor/models/remove_model_stage2.safetensors",
    "vendor/RelightVid/models/realistic-vision-v51/unet/diffusion_pytorch_model.safetensors",
    "vendor/RelightVid/models/realistic-vision-v51/vae/config.json",
    "vendor/RelightVid/models/realistic-vision-v51/vae/diffusion_pytorch_model.safetensors",
    "vendor/RelightVid/models/realistic-vision-v51/text_encoder/config.json",
    "vendor/RelightVid/models/realistic-vision-v51/text_encoder/model.safetensors",
    "vendor/RelightVid/models/realistic-vision-v51/tokenizer/tokenizer_config.json",
    "vendor/RelightVid/models/iclight_sd15_fbc.safetensors",
    "vendor/RelightVid/models/relvid_mm_sd15_fbc.pth",
)


def _download(url: str, relative: str, token: str | None = None) -> None:
    """파일을 임시 경로에 받은 뒤 원자적으로 교체한다."""
    output = ROOT / relative
    if output.is_file() and output.stat().st_size:
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_suffix(output.suffix + ".part")
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    request = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(request) as response, temporary.open("wb") as target:
        shutil.copyfileobj(response, target)
    temporary.replace(output)


def _snapshot(repo: str, relative: str, token: str | None) -> None:
    """Hugging Face 저장소를 실행 코드가 기대하는 위치에 받는다."""
    from huggingface_hub import snapshot_download

    snapshot_download(repo_id=repo, local_dir=ROOT / relative, token=token)


def _download_rfdetr() -> None:
    """RF-DETR 공식 로더로 Small 가중치를 준비한다."""
    target = ROOT / "models/rfdetr"
    if (target / "rf-detr-small.pth").is_file():
        return
    target.mkdir(parents=True, exist_ok=True)
    os.environ["RF_HOME"] = str(target)
    from rfdetr import RFDETRSmall

    RFDETRSmall(device="cpu")


def _download_gvhmr(token: str | None = None) -> None:
    """GVHMR 체크포인트를 받는다.

    공식 배포처는 Google Drive(GVHMR_DRIVE_URL)뿐이지만, 대용량 공개 파일이라
    gdown이 Drive 전역 다운로드 쿼터("too many users")에 자주 막힌다. 그래서
    파일명·디렉터리 구조가 동일한 HF 미러(camenduru/GVHMR)를 1차 소스로 쓴다.
    추론에 불필요한 preproc_data(학습용 21GB+)는 제외하고 체크포인트만 받는다.
    """
    from huggingface_hub import snapshot_download

    target = ROOT / "vendor/GVHMR/inputs/checkpoints"
    expected = (
        "gvhmr/gvhmr_siga24_release.ckpt",
        "hmr2/epoch=10-step=25000.ckpt",
        "vitpose/vitpose-h-multi-coco.pth",
    )
    if all((target / path).is_file() for path in expected):
        return
    target.mkdir(parents=True, exist_ok=True)
    snapshot_download(
        repo_id="camenduru/GVHMR",
        local_dir=target,
        allow_patterns=["dpvo/*", "gvhmr/*", "hmr2/*", "vitpose/*", "yolo/*"],
        token=token,
    )


def _install_smplx(source: Path) -> None:
    """사용자가 제공한 라이선스 SMPL-X 모델을 이미지에 설치한다."""
    if not source.is_file() or not source.stat().st_size:
        raise FileNotFoundError("SMPLX_NEUTRAL.npz BuildKit secret이 필요합니다.")
    target = ROOT / "vendor/GVHMR/inputs/checkpoints/body_models/smplx/SMPLX_NEUTRAL.npz"
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, target)


def check_models() -> None:
    """불완전한 이미지가 생성되지 않도록 필수 파일을 검증한다."""
    missing = [path for path in REQUIRED if not (ROOT / path).is_file() or not (ROOT / path).stat().st_size]
    if missing:
        raise FileNotFoundError("필수 모델 다운로드 실패:\n" + "\n".join(missing))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--smplx", type=Path)
    parser.add_argument("--hf-token-file", type=Path)
    parser.add_argument("--check-only", action="store_true")
    args = parser.parse_args()
    if args.check_only:
        check_models()
        return
    if args.smplx is None:
        parser.error("--smplx is required unless --check-only is used")

    token = None
    if args.hf_token_file and args.hf_token_file.is_file():
        token = args.hf_token_file.read_text(encoding="utf-8").strip() or None
    tasks = [
        _download_rfdetr,
        lambda: _download_gvhmr(token),
        lambda: _snapshot("Wan-AI/Wan2.1-VACE-1.3B", "vendor/svor/models/Wan2.1-VACE-1.3B", token),
        lambda: _snapshot("HigherHu/SVOR", "vendor/svor/models", token),
        lambda: _snapshot("aleafy/relightvid_models", "vendor/RelightVid/models", token),
        *(lambda path=path, url=url: _download(url, path, token) for path, url in DIRECT_DOWNLOADS.items()),
        *(
            lambda name=name: _download(
                f"https://raw.githubusercontent.com/zju3dv/GVHMR/main/hmr4d/utils/body_model/{name}",
                f"vendor/GVHMR/hmr4d/utils/body_model/{name}",
            )
            for name in GVHMR_SUPPORT
        ),
    ]
    # 네트워크와 디스크를 과포화하지 않는 범위에서 독립 다운로드만 병렬화한다.
    with ThreadPoolExecutor(max_workers=4, thread_name_prefix="model-download") as pool:
        list(pool.map(lambda task: task(), tasks))
    _install_smplx(args.smplx)
    check_models()


if __name__ == "__main__":
    main()
