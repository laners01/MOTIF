"""ProPainter 비디오 인페인팅 러너 (subprocess 격리).

우리 파이프라인이 만든 프레임 폴더 + 마스크 폴더를 그대로 ProPainter CLI에 넘긴다.
ProPainter의 전역 상태·가중치·CUDA 컨텍스트를 별도 프로세스로 격리해 SAM2 세션과 분리한다.
흰색(255)=제거할 구멍. 청킹/dilation/fp16은 CLI 인자로 위임.
"""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path
from typing import Callable

import cv2
import numpy as np

VENDOR = Path(__file__).resolve().parents[2] / "vendor" / "propainter"
SCRIPT = VENDOR / "inference_propainter.py"


def prepare_masks(frame_paths: list[Path], masks_dir: Path, dst_dir: Path) -> int:
    """프레임마다 마스크 PNG를 보장한다(누락 프레임은 검은 마스크=구멍 없음).

    ProPainter는 프레임 수와 마스크 수가 맞아야 한다. alpha 마스크 대비 이진화도 함께 처리.
    반환: 구멍(흰색)이 있는 프레임 수.
    """
    dst_dir.mkdir(parents=True, exist_ok=True)
    holes = 0
    for i, fp in enumerate(frame_paths):
        src = masks_dir / f"{i:06d}.png"
        if src.exists():
            m = cv2.imread(str(src), cv2.IMREAD_GRAYSCALE)
            m = (m > 127).astype(np.uint8) * 255  # 이진화(흰=제거)
        else:
            h, w = cv2.imread(str(fp)).shape[:2]
            m = np.zeros((h, w), np.uint8)
        if m.any():
            holes += 1
        cv2.imwrite(str(dst_dir / f"{i:06d}.png"), m)
    return holes


def run_propainter(
    frames_dir: Path,
    masks_dir: Path,
    out_dir: Path,
    *,
    dilate_px: int = 8,
    fp16: bool = True,
    fps: float = 30.0,
    resize_ratio: float = 1.0,
    python_exe: str | None = None,
    progress: Callable[[str], None] | None = None,
) -> Path:
    """ProPainter를 subprocess로 실행하고 결과 mp4 경로를 반환한다."""
    if not SCRIPT.exists():
        raise RuntimeError(f"ProPainter가 없습니다: {SCRIPT} (vendor/propainter 클론 필요)")

    cmd = [
        python_exe or sys.executable,
        str(SCRIPT),
        "-i", str(Path(frames_dir).resolve()),
        "-m", str(Path(masks_dir).resolve()),
        "-o", str(Path(out_dir).resolve()),
        "--mask_dilation", str(int(dilate_px)),
        "--save_fps", str(int(round(fps))),
        "--resize_ratio", str(resize_ratio),
    ]
    if fp16:
        cmd.append("--fp16")

    # cwd=VENDOR: ProPainter가 상대경로 weights/를 여기서 찾는다.
    proc = subprocess.Popen(
        cmd, cwd=str(VENDOR), stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT, text=True, bufsize=1,
    )
    for line in proc.stdout:  # tqdm/로그를 그대로 흘려보냄
        line = line.rstrip()
        if line and progress:
            progress(line)
    proc.wait()
    if proc.returncode != 0:
        raise RuntimeError(f"ProPainter 실패 (exit {proc.returncode})")

    # ProPainter 출력: <out_dir>/<입력폴더명>/inpaint_out.mp4
    result = Path(out_dir).resolve() / Path(frames_dir).name / "inpaint_out.mp4"
    if not result.exists():
        raise RuntimeError(f"ProPainter 출력이 없습니다: {result}")
    return result
