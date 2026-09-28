"""전경(isolated) + 배경 합성 파이프라인.

Phase A: SAM2 마스크에서 합집합 bbox를 구해 전경을 고정 크기 RGBA 크롭으로 export.
Phase C: 배경 영상 위에 전경 크롭을 위치/스케일 조정해 알파 합성.

모든 이미지 연산은 cv2 기준(BGR / BGRA)으로 통일한다. 알파는 채널 3.
"""
from __future__ import annotations

import hashlib
import subprocess
import tempfile
from pathlib import Path
from typing import Callable

import cv2
import imageio_ffmpeg
import numpy as np
from scipy.ndimage import distance_transform_edt


ROOT = Path(__file__).resolve().parents[2]
WORK_DIR = ROOT / "work"


def _project_temp_dir(prefix: str) -> Path:
    WORK_DIR.mkdir(parents=True, exist_ok=True)
    return Path(tempfile.mkdtemp(prefix=prefix, dir=WORK_DIR))


# ---------- 공통 인코더 (SESSION 비의존) ----------
def encode(frames_dir: Path, out_path: Path, fps: float, audio_source: Path | None = None) -> None:
    """PNG 또는 기존 JPEG 프레임을 mp4로 인코딩한다."""
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    extension = 'png' if (Path(frames_dir) / '000000.png').is_file() else 'jpg'
    cmd = [ffmpeg, "-y", "-loglevel", "error",
           "-framerate", f"{fps:.6f}", "-i", str(Path(frames_dir) / f"%06d.{extension}")]
    if audio_source is not None:
        duration=len(list(Path(frames_dir).glob(f'*.{extension}')))/fps
        cmd += ["-i", str(audio_source), "-map", "0:v:0", "-map", "1:a:0?",
                "-c:a", "aac", "-b:a", "192k", "-af", f"apad=whole_dur={duration:.9f},atrim=duration={duration:.9f}"]
    # yuv420p(4:2:0)는 짝수 해상도만 허용 → 홀수 크롭이면 1px 패딩으로 맞춘다.
    cmd += ["-vf", "pad=ceil(iw/2)*2:ceil(ih/2)*2",
            "-c:v", "libx264", "-preset", "medium", "-crf", "18",
            "-pix_fmt", "yuv420p", str(out_path)]
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        raise RuntimeError(f"ffmpeg 인코딩 실패 (code {proc.returncode}):\n{proc.stderr}")


# ---------- Phase A: 합집합 bbox 크롭 ----------
def union_bbox(masks_dir: Path, n_frames: int, margin: float = 0.06) -> tuple[int, int, int, int]:
    """전 프레임 마스크의 합집합 bbox(+여백 %). (x0, y0, x1, y1) 폐구간 반환."""
    masks_dir = Path(masks_dir)
    x0 = y0 = 10**9
    x1 = y1 = -1
    H = W = 0
    for i in range(n_frames):
        p = masks_dir / f"{i:06d}.png"
        if not p.exists():
            continue
        m = cv2.imread(str(p), cv2.IMREAD_GRAYSCALE)
        if m is None:
            continue
        H, W = m.shape[:2]
        ys, xs = np.where(m > 127)
        if xs.size == 0:
            continue
        x0, x1 = min(x0, int(xs.min())), max(x1, int(xs.max()))
        y0, y1 = min(y0, int(ys.min())), max(y1, int(ys.max()))
    if x1 < 0:
        raise RuntimeError("마스크에 전경 영역이 없습니다.")
    bw, bh = x1 - x0 + 1, y1 - y0 + 1
    px, py = int(bw * margin), int(bh * margin)
    x0, y0 = max(0, x0 - px), max(0, y0 - py)
    x1, y1 = min(W - 1, x1 + px), min(H - 1, y1 + py)
    return x0, y0, x1, y1


def export_isolated_crop(
    frame_paths: list[Path],
    masks_dir: Path,
    out_dir: Path,
    *,
    margin: float = 0.06,
    fps: float = 30.0,
    audio_source: Path | None = None,
    progress: Callable[[float, str], None] | None = None,
) -> tuple[Path, dict]:
    """전 프레임을 합집합 bbox로 크롭한 RGBA 시퀀스 + 미리보기 mp4 생성.

    반환: (크롭 디렉터리, bbox dict). bbox dict = {x0,y0,x1,y1,w,h}.
    """
    out_dir = Path(out_dir)
    n = len(frame_paths)
    x0, y0, x1, y1 = union_bbox(masks_dir, n, margin)
    crop_dir = out_dir / "isolated_crop"
    crop_dir.mkdir(parents=True, exist_ok=True)

    # 알파를 눈으로 보기 위한 체커보드 미리보기
    prev_dir = _project_temp_dir("composite_preview_")
    cw, ch = x1 - x0 + 1, y1 - y0 + 1
    checker = _checkerboard(ch, cw)

    for i, fp in enumerate(frame_paths):
        bgr = cv2.imread(str(fp))
        mp = Path(masks_dir) / f"{i:06d}.png"
        m = cv2.imread(str(mp), cv2.IMREAD_GRAYSCALE) if mp.exists() else None
        if m is None:
            m = np.zeros(bgr.shape[:2], np.uint8)
        m = (m > 127).astype(np.uint8) * 255

        crop = bgr[y0:y1 + 1, x0:x1 + 1]
        acrop = m[y0:y1 + 1, x0:x1 + 1]
        bgra = np.dstack([crop, acrop]).astype(np.uint8)
        cv2.imwrite(str(crop_dir / f"{i:06d}.png"), bgra)

        prev = place(checker.copy(), bgra, 0, 0, 1.0)
        cv2.imwrite(str(prev_dir / f"{i:06d}.jpg"), prev, [cv2.IMWRITE_JPEG_QUALITY, 92])
        if progress:
            progress((i + 1) / n, f"isolated 크롭 {i + 1}/{n}")

    preview_mp4 = out_dir / "isolated_crop.mp4"
    encode(prev_dir, preview_mp4, fps, audio_source=audio_source)
    bbox = {"x0": x0, "y0": y0, "x1": x1, "y1": y1, "w": cw, "h": ch}
    return crop_dir, bbox


def _checkerboard(h: int, w: int, tile: int = 16) -> np.ndarray:
    board = np.zeros((h, w, 3), np.uint8)
    for yy in range(0, h, tile):
        for xx in range(0, w, tile):
            if ((yy // tile) + (xx // tile)) % 2 == 0:
                board[yy:yy + tile, xx:xx + tile] = 90
            else:
                board[yy:yy + tile, xx:xx + tile] = 150
    return board


# ---------- Phase C: 합성 코어 ----------
def foreground_blur_sigma(fg_bgra, bg, x, y, scale):
    """Heuristic edge sharpness match at output scale, capped at two pixels."""
    h, w = fg_bgra.shape[:2]
    size = (max(1, round(w * scale)), max(1, round(h * scale)))
    fg = cv2.resize(fg_bgra, size, interpolation=cv2.INTER_AREA if scale < 1 else cv2.INTER_LINEAR)
    region = _sample_bg_region(bg, x, y, *size).astype(np.uint8)
    valid = cv2.erode((fg[..., 3] > 250).astype(np.uint8), np.ones((7, 7), np.uint8)) > 0
    if valid.sum() < 64:
        return 0.0

    def sharpness(bgr):
        gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY).astype(np.float32)
        dx = cv2.Sobel(gray, cv2.CV_32F, 1, 0)
        dy = cv2.Sobel(gray, cv2.CV_32F, 0, 1)
        gradient = (dx * dx + dy * dy)[valid].mean()
        if gradient < 4:  # Flat areas cannot provide a useful sharpness estimate.
            return None
        lap = cv2.Laplacian(gray, cv2.CV_32F)
        return float((lap * lap)[valid].mean() / gradient)

    target = sharpness(region)
    original = sharpness(fg[..., :3])
    if target is None or original is None or original <= target:
        return 0.0
    candidates = [(abs(original - target), 0.0)]
    for sigma in (0.4, 0.8, 1.0):
        score = sharpness(soften_foreground(fg, sigma)[..., :3])
        if score is not None:
            candidates.append((abs(score - target), sigma))
    return min(candidates)[1]


def soften_foreground(fg_bgra, sigma):
    """Blur visible foreground colors only; retain alpha and avoid transparent RGB bleed."""
    if sigma <= 0:
        return fg_bgra.copy()
    alpha = fg_bgra[..., 3:4].astype(np.float32) / 255
    weight = cv2.GaussianBlur(alpha[..., 0], (0, 0), sigma)[..., None]
    color = cv2.GaussianBlur(fg_bgra[..., :3].astype(np.float32) * alpha, (0, 0), sigma)
    out = fg_bgra.copy()
    out[..., :3] = np.clip(color / np.maximum(weight, 1e-6), 0, 255).round().astype(np.uint8)
    return out


def resample_foreground(fg_bgra, scale, *, soften_edges=True):
    """Alpha-weighted cubic enlargement with a narrow output-pixel matte transition."""
    if not np.isfinite(scale) or scale <= 0:
        raise ValueError('scale must be positive and finite')
    h, w = fg_bgra.shape[:2]
    size = (max(1, round(w*scale)), max(1, round(h*scale)))
    alpha = fg_bgra[..., 3].astype(np.float32)/255
    premult = fg_bgra[..., :3].astype(np.float32)*alpha[..., None]
    if size != (w, h):
        interp = cv2.INTER_AREA if scale < 1 else cv2.INTER_CUBIC
        alpha = cv2.resize(alpha, size, interpolation=interp).clip(0, 1)
        premult = cv2.resize(premult, size, interpolation=interp)
    # Cubic kernels can overshoot at high-contrast edges.
    premult = np.minimum(np.maximum(premult, 0), alpha[..., None]*255)
    color = premult/np.maximum(alpha[..., None], 1e-6)
    smooth_alpha = alpha
    if soften_edges:
        smooth_alpha = cv2.GaussianBlur(alpha, (5, 5), .85)
        edge_color = cv2.GaussianBlur(premult, (5, 5), .85)/np.maximum(smooth_alpha[..., None], 1e-6)
        # Retain opaque interior detail; only transition pixels use extended edge color.
        edge = (smooth_alpha < .999) | (alpha < .999)
        color[edge] = edge_color[edge]
    return np.dstack([color.clip(0, 255).round().astype(np.uint8),
                      (smooth_alpha*255).round().astype(np.uint8)])


def _affine_foreground(fg, shape, transform):
    """Shared continuous pixel-center transform for foreground color and alpha."""
    h,w=shape[:2];s=float(transform.get('scale',1.))
    x,y=float(transform.get('x',0.)),float(transform.get('y',0.))
    if not np.isfinite([s,x,y]).all() or s<=0:raise ValueError('유효한 배치 좌표/스케일 필요')
    alpha=fg[:,:,3:4].astype(np.float32)/255.
    premult=np.concatenate([fg[:,:,:3].astype(np.float32)*alpha,alpha],axis=2)
    if s<1:
        # Antialias the footprint of a destination pixel before shrinking.
        sigma=.5*np.sqrt(1/s**2-1)
        premult=cv2.GaussianBlur(premult,(0,0),sigma)
    matrix=np.array([[s,0,x],[0,s,y]],np.float64)
    interpolation=cv2.INTER_LANCZOS4 if s>=1 else cv2.INTER_LINEAR
    mapped=cv2.warpAffine(premult,matrix,(w,h),flags=interpolation,borderMode=cv2.BORDER_CONSTANT)
    alpha=mapped[:,:,3:4].clip(0,1)
    color=np.divide(mapped[:,:,:3],alpha,out=np.zeros_like(mapped[:,:,:3]),where=alpha>1e-6)
    return np.concatenate([color,alpha*255],axis=2).clip(0,255).round().astype(np.uint8)


def placed_points(points, transform):
    """Use the same coordinates for depth anatomy and image placement."""
    x,y=transform.get('x',0.),transform.get('y',0.)
    if not transform.get('subpixel',False):x,y=round(x),round(y)
    return np.asarray(points)*transform.get('scale',1.)+[x,y]


def place(bg: np.ndarray, fg_bgra: np.ndarray, x: float, y: float, scale: float,
          *, blur_sigma: float = 0, soften_edges: bool = True, subpixel: bool = False) -> np.ndarray:
    """bg(BGR) 위에 fg(BGRA)를 (x,y) 좌상단·scale배로 알파 합성한 복사본을 반환.

    화면 밖으로 나가는 부분은 잘라낸다. bg는 수정하지 않는다.
    """
    out = bg.copy()
    if subpixel:
        if blur_sigma>0:fg_bgra=soften_foreground(fg_bgra,blur_sigma)
        fg=_affine_foreground(fg_bgra,bg.shape,dict(x=x,y=y,scale=scale))
        alpha=fg[:,:,3:4].astype(np.float32)/255.
        return (fg[:,:,:3]*alpha+bg*(1-alpha)).clip(0,255).round().astype(np.uint8)
    fg_bgra = resample_foreground(fg_bgra, scale, soften_edges=soften_edges)
    if blur_sigma > 0:
        fg_bgra = soften_foreground(fg_bgra, blur_sigma)
    fh, fw = fg_bgra.shape[:2]
    H, W = out.shape[:2]
    x, y = int(round(x)), int(round(y))
    x0, y0 = max(0, x), max(0, y)
    x1, y1 = min(W, x + fw), min(H, y + fh)
    if x0 >= x1 or y0 >= y1:
        return out  # 완전히 화면 밖
    fx0, fy0 = x0 - x, y0 - y
    fg = fg_bgra[fy0:fy0 + (y1 - y0), fx0:fx0 + (x1 - x0)]
    alpha = fg[..., 3:4].astype(np.float32) / 255.0
    region = out[y0:y1, x0:x1].astype(np.float32)
    out[y0:y1, x0:x1] = (fg[..., :3].astype(np.float32) * alpha + region * (1 - alpha)).astype(np.uint8)
    return out


def _sample_bg_region(bg: np.ndarray, x: float, y: float, w: int, h: int) -> np.ndarray:
    """전경이 놓일 배경 영역 [y:y+h, x:x+w]을 float32로 반환. 화면 밖은 가장자리 복제."""
    H, W = bg.shape[:2]
    x, y = int(round(x)), int(round(y))
    ix0, iy0 = max(0, x), max(0, y)
    ix1, iy1 = min(W, x + w), min(H, y + h)
    if ix0 >= ix1 or iy0 >= iy1:  # 완전히 화면 밖 → 전체 평균색
        return np.full((h, w, 3), bg.reshape(-1, 3).mean(0), np.float32)
    core = bg[iy0:iy1, ix0:ix1].astype(np.float32)
    return cv2.copyMakeBorder(
        core, iy0 - y, (y + h) - iy1, ix0 - x, (x + w) - ix1, cv2.BORDER_REPLICATE
    )


def placed_alpha(fg, shape, transform):
    """The same scale, clipping and alpha quantization used by place()."""
    if transform.get('subpixel',False):return _affine_foreground(fg,shape,transform)[:,:,3]
    h, w = shape[:2]
    result = np.zeros((h, w), np.uint8)
    scale = transform.get('scale', 1)
    fg = resample_foreground(fg, scale, soften_edges=not transform.get('preserve_detail', False))
    fh, fw = fg.shape[:2]
    x, y = round(transform.get('x', 0)), round(transform.get('y', 0))
    x0, y0, x1, y1 = max(0, x), max(0, y), min(w, x+fw), min(h, y+fh)
    if x1 > x0 and y1 > y0:
        result[y0:y1, x0:x1] = fg[y0-y:y1-y, x0-x:x1-x, 3]
    return result


def relight_fg_to_bg(
    fg_bgra: np.ndarray, bg: np.ndarray, x: float, y: float, scale: float,
    *, sigma_frac: float = 0.25, strength: float = 0.7,
    eps: float = 1.0, clip_lo: float = 0.25, clip_hi: float = 4.0,
) -> np.ndarray:
    """전경(BGRA)의 저주파 조도/색을, 놓일 배경 영역에 맞춰 곱셈으로 이식한 복사본.

    relight_transfer.py와 같은 원리: ratio = blur(배경영역) / blur(전경). 저주파(=조명/색
    캐스트)만 옮기고 고주파(질감·얼굴)는 전경 원본 유지. 알파는 그대로 둔다.
    strength로 세기 조절(0=변화없음, 1=배경 저주파에 완전 일치). 물리적 재조명이 아니라
    '톤·명암을 배경에 녹여넣는' 수준.
    """
    fh, fw = fg_bgra.shape[:2]
    pw, ph = max(1, round(fw * scale)), max(1, round(fh * scale))
    region = _sample_bg_region(bg, x, y, pw, ph)
    region = cv2.resize(region, (fw, fh), interpolation=cv2.INTER_LINEAR)
    sigma = max(1.0, sigma_frac * min(fw, fh))
    # ponytail: 알파 무시 blur라 크롭 여백(전경 원본 배경)도 섞인다. 잔상 크면 알파가중 blur로 승급.
    tgt = gaussian_lowfreq(region, sigma)
    src = gaussian_lowfreq(fg_bgra[..., :3].astype(np.float32), sigma)
    ratio = np.clip((tgt + eps) / (src + eps), clip_lo, clip_hi)
    ratio = 1.0 + strength * (ratio - 1.0)
    out = fg_bgra.copy()
    out[..., :3] = np.clip(fg_bgra[..., :3].astype(np.float32) * ratio, 0, 255).astype(np.uint8)
    return out


def gaussian_lowfreq(img: np.ndarray, sigma: float) -> np.ndarray:
    if sigma <= 8:
        return cv2.GaussianBlur(img, (0, 0), sigmaX=sigma, sigmaY=sigma)
    # 큰 시그마: 다운스케일→작은 블러→업스케일로 근사(저주파라 결과 거의 동일, 수십 배 빠름).
    h, w = img.shape[:2]
    s = 8.0 / sigma
    small = cv2.resize(img, (max(1, round(w * s)), max(1, round(h * s))), interpolation=cv2.INTER_AREA)
    small = cv2.GaussianBlur(small, (0, 0), sigmaX=8, sigmaY=8)
    return cv2.resize(small, (w, h), interpolation=cv2.INTER_LINEAR)


def sample_frames(video_path: Path, k: int = 5) -> list[np.ndarray]:
    """배경 영상에서 균등 간격으로 k장을 뽑아 BGR 리스트로 반환(positioner 미리보기용)."""
    if k < 1:
        raise ValueError('샘플 수는 1 이상이어야 합니다.')
    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        raise ValueError(f'영상을 열 수 없습니다: {video_path}')
    total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT)) or 1
    idxs = ([total // 2] if k == 1 else
            np.linspace(0, max(0, total - 1), k, dtype=int).tolist())
    out = []
    for i in idxs:
        cap.set(cv2.CAP_PROP_POS_FRAMES, i)
        ok, frame = cap.read()
        if ok:
            out.append(frame)
    cap.release()
    if len(out) != len(idxs):
        raise ValueError('배경 샘플 프레임을 읽지 못했습니다.')
    return out


def color_transfer_lab(fg: np.ndarray, bg: np.ndarray, x: float, y: float, scale: float,
                       strength: float = 0.7, state: dict | None = None) -> np.ndarray:
    """Masked Reinhard transfer against the local background; keep alpha unchanged."""
    h, w = fg.shape[:2]
    region = cv2.resize(_sample_bg_region(bg, x, y, max(1, round(w*scale)),
                                          max(1, round(h*scale))).astype(np.uint8), (w, h))
    mask = cv2.erode((fg[..., 3] > 220).astype(np.uint8), np.ones((3, 3), np.uint8)) > 0
    if mask.sum() < 32:
        return fg.copy()
    src = cv2.cvtColor(fg[..., :3], cv2.COLOR_BGR2LAB).astype(np.float32)
    dst = cv2.cvtColor(region, cv2.COLOR_BGR2LAB).astype(np.float32)
    a = src[mask]; b = dst[mask]
    source_mean, target_mean = a.mean(0), b.mean(0)
    ratio = np.clip(b.std(0)/(a.std(0)+1e-3), .65, 1.5)
    if state is not None:
        current = np.r_[source_mean, target_mean, ratio]
        previous = state.get('lab')
        if previous is None or np.max(np.abs(previous[3:6]-target_mean)) > 30:
            state['lab'] = current
        else:
            state['lab'] = .9*previous + .1*current
        source_mean, target_mean, ratio = np.split(state['lab'], 3)
    target = (src - source_mean) * ratio + target_mean
    mixed = np.clip(src + strength * (target-src), 0, 255).astype(np.uint8)
    out = fg.copy()
    out[..., :3] = cv2.cvtColor(mixed, cv2.COLOR_LAB2BGR)
    return out


def shadow_matte(alpha: np.ndarray, x: int, y: int, shape: tuple,
                 angle: float = 35.0, length: float = 0.35) -> tuple[np.ndarray, np.ndarray]:
    """Project silhouette toward the ground at its lowest opaque pixel."""
    H, W = shape[:2]
    ys, xs = np.where(alpha > 100)
    contact = np.zeros((H, W), np.float32)
    cast = contact.copy()
    if len(xs) == 0:
        return contact, cast
    foot_y = y + int(ys.max())
    foot_x = x + int(np.median(xs[ys >= ys.max()-max(2, alpha.shape[0]//25)]))
    if not (0 <= foot_y < H):
        return contact, cast
    radius = max(2, round((xs.max()-xs.min()+1)*.12))
    cv2.ellipse(contact, (foot_x, foot_y), (radius, max(2, radius//4)),
                0, 0, 360, 1, -1)
    contact = cv2.GaussianBlur(contact, (0, 0), max(1, radius*.35))
    a = alpha.astype(np.float32)/255
    height = max(1, ys.max()-ys.min())
    reach = max(1, height*length)
    dx = np.cos(np.deg2rad(angle))*reach
    dy = np.sin(np.deg2rad(angle))*reach*.35
    # Silhouette top projects farthest; the feet remain at the contact point.
    src = np.float32([[0, ys.max()], [alpha.shape[1], ys.max()], [0, ys.min()]])
    dst = np.float32([[x, foot_y], [x+alpha.shape[1], foot_y],
                      [x+dx, foot_y+dy]])
    matrix = cv2.getAffineTransform(src, dst)
    cast = cv2.warpAffine(a, matrix, (W, H), flags=cv2.INTER_LINEAR)
    cast = cv2.GaussianBlur(cast, (0, 0), max(2, height*.025))
    yy, xx = np.indices((H, W))
    distance = np.hypot(xx-foot_x, yy-foot_y)
    cast *= np.exp(-distance/max(1, reach*1.5))
    return np.clip(contact, 0, 1), np.clip(cast, 0, 1)


def _edge_decontaminate(fg: np.ndarray) -> np.ndarray:
    """Recover narrow-band alpha and foreground color from source crop RGB."""
    raw = fg[..., 3]
    mask = (raw > 127).astype(np.uint8)
    inside = cv2.erode(mask, np.ones((5, 5), np.uint8)) > 0
    outside = cv2.dilate(mask, np.ones((7, 7), np.uint8)) == 0
    if not inside.any() or not outside.any():
        return fg.copy()
    _, near_fg = distance_transform_edt(~inside, return_indices=True)
    _, near_bg = distance_transform_edt(~outside, return_indices=True)
    color = fg[..., :3].astype(np.float32)
    F = color[tuple(near_fg)]
    B = color[tuple(near_bg)]
    delta = F-B
    den = np.sum(delta*delta, axis=2)
    estimate = np.clip(np.sum((color-B)*delta, axis=2)/np.maximum(den,1),0,1)
    fit = np.linalg.norm(color - (B+estimate[...,None]*delta),axis=2)
    band = cv2.dilate(mask,np.ones((7,7),np.uint8))>0
    band &= cv2.erode(mask,np.ones((7,7),np.uint8))==0
    base = cv2.GaussianBlur(raw.astype(np.float32)/255,(3,3),.85)
    good = band & (den>900) & (fit<18)
    a = base.copy()
    a[good] = .2*base[good]+.8*estimate[good]
    out = fg.copy()
    out[...,3] = (a*255).round().clip(0,255).astype(np.uint8)
    visible = band & (a>0)
    # Recover foreground color from the compositing equation where reliable.
    recovered = (color-(1-a[...,None])*B)/np.maximum(a[...,None],.15)
    color_out = F.copy()
    use = good & (a>.25)
    color_out[use] = np.clip(recovered[use],0,255)
    out[..., :3][visible] = color_out[visible].round().clip(0,255).astype(np.uint8)
    return out


def _grain_sigma(bg: np.ndarray) -> float:
    gray = cv2.cvtColor(bg, cv2.COLOR_BGR2GRAY).astype(np.float32)
    residual = gray - cv2.GaussianBlur(gray, (3, 3), 0)
    flat = cv2.Laplacian(gray, cv2.CV_32F)
    values = residual[np.abs(flat) < np.percentile(np.abs(flat), 40)]
    return float(np.clip(np.std(values) if values.size else 0, 0, 2.5))


def _motion_blur(fg: np.ndarray, dx: float, dy: float) -> np.ndarray:
    magnitude=float(np.hypot(dx,dy))
    if magnitude < 3.:
        return fg
    length = min(7, max(2, round(.75*magnitude)))
    alpha = fg[..., 3:4].astype(np.float32)/255
    premult = fg[..., :3].astype(np.float32)*alpha
    color_sum = np.zeros_like(premult)
    alpha_sum = np.zeros_like(alpha[..., 0])
    for t in np.linspace(-.5, .5, length):
        matrix = np.float32([[1, 0, dx*t], [0, 1, dy*t]])
        color_sum += cv2.warpAffine(premult, matrix, (fg.shape[1], fg.shape[0]))
        alpha_sum += cv2.warpAffine(alpha[..., 0], matrix, (fg.shape[1], fg.shape[0]))
    out = fg.copy()
    out[..., :3] = np.clip(color_sum/np.maximum(alpha_sum[..., None], 1e-6), 0, 255).astype(np.uint8)
    out[..., 3] = np.clip(alpha_sum/length*255, 0, 255).astype(np.uint8)
    return out


def frame_transform(transform, index):
    """Resolve the background-frame placement shared by color and alpha rendering."""
    path = transform.get('trajectory')
    if path is None:
        return transform
    if not 0 <= index < len(path):
        raise ValueError('배치 경로의 프레임 수가 부족합니다.')
    point = path[index]
    values = [point.get(k, float('nan')) for k in ('x', 'y', 'scale')]
    if not np.isfinite(values).all() or values[2] <= 0:
        raise ValueError('유효하지 않은 프레임별 배치 좌표/스케일')
    resolved = {k: v for k, v in transform.items() if k != 'trajectory'}
    resolved.update(x=values[0], y=values[1], scale=values[2])
    return resolved


def foreground_frame_time(index, start, foreground_fps, background_fps, frames, shortfall):
    """Map a background frame to fractional foreground time without changing playback speed."""
    values = (foreground_fps, background_fps)
    if not np.isfinite(values).all() or min(values) <= 0 or frames < 1:
        raise ValueError('전경/배경 FPS와 전경 프레임 수가 유효해야 합니다.')
    if shortfall not in {'loop', 'hold', 'disappear'}:
        raise ValueError("shortfall은 'loop', 'hold', 'disappear' 중 하나여야 합니다.")
    elapsed = index - start
    if elapsed < 0:
        return None
    source_time = elapsed * foreground_fps / background_fps
    if source_time < frames:
        return float(source_time)
    if shortfall == 'loop':
        return float(source_time % frames)
    if shortfall == 'hold':
        return float(frames - 1)
    return None


def render_object_video(
    bg_video: Path, fg_crop_dir: Path, transform: dict, out_path: Path, *,
    foreground_fps: float, foreground_video: Path, source_masks: Path,
    destination_exclusion_masks: Path | None = None, fg_shortfall: str = "loop",
    dof_match: bool = True, motion_blur: bool = True, edge_decontam: bool = True,
    scene_harmonization: bool = True,
    progress: Callable[[float, str], None] | None = None,
) -> Path:
    """Render only the placed foreground on neutral gray before final compositing."""
    from source.util import removal_experiments as ex
    checked=[bg_video,fg_crop_dir,out_path,foreground_video,source_masks]
    if destination_exclusion_masks is not None:checked.append(destination_exclusion_masks)
    for path in checked:
        if not Path(path).resolve().is_relative_to(ROOT):
            raise ValueError(f'프로젝트 밖 경로: {path}')
    fg_paths=sorted(Path(fg_crop_dir).glob('*.png'))
    if not fg_paths: raise RuntimeError(f'전경 크롭이 없습니다: {fg_crop_dir}')
    if fg_shortfall not in {'loop','hold','disappear'}:
        raise ValueError("fg_shortfall은 'loop', 'hold', 'disappear' 중 하나여야 합니다.")
    cap=cv2.VideoCapture(str(bg_video));count=int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    fps=cap.get(cv2.CAP_PROP_FPS) or 30.;w=int(cap.get(cv2.CAP_PROP_FRAME_WIDTH));h=int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    cap.release()
    if count < 1 or w < 1 or h < 1:
        raise ValueError('배경 영상 메타데이터가 유효하지 않습니다.')
    if transform.get('trajectory') is not None and len(transform['trajectory']) != count:
        raise ValueError('배치 경로는 배경 영상 프레임 수와 같아야 합니다.')
    alpha_dir=ex.local(Path(out_path).parent/'alpha');alpha_dir.mkdir(exist_ok=False)
    rgb_dir=ex.local(Path(out_path).parent/'rgb');rgb_dir.mkdir(exist_ok=False)
    representative=cv2.imread(str(fg_paths[len(fg_paths)//2]),cv2.IMREAD_UNCHANGED)
    if representative is None or representative.ndim != 3 or representative.shape[2] != 4:
        raise ValueError('전경 크롭은 BGRA PNG여야 합니다.')
    sample=sample_frames(bg_video,1)[0]
    base=frame_transform(transform,0)
    blur_sigma=foreground_blur_sigma(representative,sample,base.get('x',0),base.get('y',0),base.get('scale',1)) if dof_match else 0.
    from source.enhancement.temporal import FrameSampler
    from source.relighting import scene_harmonization as scene
    if scene_harmonization:
        scene_curve,scene_record=scene.build(
            foreground_video,source_masks,bg_video,fg_paths,transform,foreground_fps,fps,count,
            fg_shortfall,destination_exclusion=destination_exclusion_masks)
    else:
        scene_curve=None
        scene_record=dict(version='disabled',keyframes=[],diagnostics=[],
                          reason='foreground already aligned and corrected on the background timeline')
    sampler=FrameSampler(fg_paths+[fg_paths[0]] if fg_shortfall=='loop' else fg_paths)
    start=int(transform.get('start',0));previous_center=None;previous_k=None
    def frames():
        nonlocal previous_center,previous_k
        for index in range(count):
            k=foreground_frame_time(index,start,foreground_fps,fps,len(fg_paths),fg_shortfall)
            current=frame_transform(transform,index)
            if k is None:
                fg=np.zeros((h,w,4),np.uint8);previous_center=previous_k=None
            else:
                crop=sampler.sample(k)
                if scene_curve is not None:
                    crop=scene.apply(crop,scene.at(scene_curve,index))
                if edge_decontam: crop=_edge_decontaminate(crop)
                ys,xs=np.where(crop[...,3]>100)
                local_center=(float(xs.mean()),float(ys.mean())) if xs.size else None
                center=(current.get('x',0)+current.get('scale',1)*local_center[0],
                        current.get('y',0)+current.get('scale',1)*local_center[1]) if local_center else None
                scale=max(float(current.get('scale',1)),1e-6)
                displacement=(0.,0.) if previous_center is None or center is None else (
                    (center[0]-previous_center[0])/scale,(center[1]-previous_center[1])/scale)
                use_motion=(motion_blur and previous_k is not None and k>=previous_k
                            and np.hypot(*displacement)>=3.)
                # DoF and directional motion blur are alternatives, not stacked.
                if use_motion: crop=_motion_blur(crop,*displacement)
                elif blur_sigma>0: crop=soften_foreground(crop,blur_sigma)
                previous_center,previous_k=center,k
                fg=_affine_foreground(crop,(h,w,3),{**current,'subpixel':True})
            alpha=fg[...,3]
            if not cv2.imwrite(str(alpha_dir/f'{index:06d}.png'),alpha,[cv2.IMWRITE_PNG_COMPRESSION,1]):
                raise OSError('객체 알파 저장 실패')
            canvas=np.full((h,w,3),128,np.uint8);visible=alpha>0;canvas[visible]=fg[...,:3][visible]
            if not cv2.imwrite(str(rgb_dir/f'{index:06d}.png'),canvas,[cv2.IMWRITE_PNG_COMPRESSION,1]):
                raise OSError('무손실 객체 RGB 저장 실패')
            if progress: progress((index+1)/count,f'객체 렌더 {index+1}/{count}')
            yield canvas
    # Preview only. Relighting consumes rgb/*.png directly, never this H.264 file.
    ex.encode_frames(frames(),out_path,fps,playback=True,fast_preview=True)
    rgb_sha256=hashlib.sha256(''.join(ex.digest(path) for path in ex.sequence(rgb_dir,count)).encode()).hexdigest()
    ex.write_json(ex.local(Path(out_path).with_suffix('.composition.json')),{
        'source_sha256':ex.digest(out_path),'alpha_dir':str(alpha_dir.relative_to(ROOT)),
        'rgb_dir':str(rgb_dir.relative_to(ROOT)),'rgb_sha256':rgb_sha256,
        'audio_source':str(Path(bg_video).resolve().relative_to(ROOT)),'frames':count,'fps':fps,
        'transform':transform,'stage':'object_before_composite','neutral_background':128,
        'foreground_fps':float(foreground_fps),'background_fps':float(fps),
        'time_mapping':'fractional_foreground_time-v1','scene_harmonization':scene_record,
        'corrections':dict(dof_match=dof_match,dof_max_sigma=1.,motion_blur=motion_blur,
                           motion_blur_min_displacement=3.,stack_blurs=False,
                           edge_decontam=edge_decontam,
                           scene_harmonization=scene_harmonization)})
    return Path(out_path)


def composite_video(
    bg_video: Path,
    fg_crop_dir: Path,
    transform: dict,
    out_path: Path,
    *,
    fps: float | None = None,
    fg_shortfall: str = "loop",  # loop / hold / disappear
    relight: bool = False,        # 전경 조도를 배경에 맞춰 이식
    relight_strength: float = 0.7,
    save_alpha: bool = False,
    save_foreground: bool = False,
    occlusion_dir: Path | None = None,
    contact_shadow: bool = True,
    cast_shadow: bool = False,
    lab_match: bool = True,
    grain_match: bool = True,
    dof_match: bool = True,
    motion_blur: bool = False,
    edge_decontam: bool = True,
    lighting_check: bool = True,
    light_angle: float = 35.0,
    audio_source: Path | None = None,
    progress: Callable[[float, str], None] | None = None,
) -> Path:
    """배경 영상 전체 길이에 맞춰 전경 크롭을 합성한다. transform={x,y,scale,start}.

    start: 전경이 등장하기 시작하는 배경 프레임 인덱스.
    fg_shortfall: 전경이 배경보다 짧을 때 loop(반복)/hold(마지막 정지)/disappear(사라짐).
    """
    for path in (bg_video, fg_crop_dir, out_path, audio_source, occlusion_dir):
        if path is not None and not Path(path).resolve().is_relative_to(ROOT):
            raise ValueError(f'프로젝트 밖 경로: {path}')
    if save_foreground and not save_alpha:
        raise ValueError('전경 RGB 저장은 alpha 저장과 함께 사용해야 합니다.')
    fg_paths = sorted(Path(fg_crop_dir).glob("*.png"))
    if not fg_paths:
        raise RuntimeError(f"전경 크롭이 없습니다: {fg_crop_dir}")
    n_fg = len(fg_paths)
    x, y = transform.get("x", 0), transform.get("y", 0)
    scale = transform.get("scale", 1.0)
    start = int(transform.get("start", 0))
    preserve_detail = bool(transform.get("preserve_detail", False))
    quality_strength = 0.0 if preserve_detail else float(transform.get("quality_strength", 0))
    extra_blur = 0.0 if preserve_detail else float(transform.get("extra_blur", 0))
    if not np.isfinite(extra_blur) or not 0 <= extra_blur <= 6:
        raise ValueError("추가 흐림은 0~6px이어야 합니다.")
    if not 0 <= quality_strength <= 1:
        raise ValueError("화질 맞춤 강도는 0~1이어야 합니다.")
    # Same representative foreground/background as the positioning preview; fixed across time.
    blur_sigma = 0.0
    if quality_strength:
        representative = cv2.imread(str(fg_paths[len(fg_paths) // 2]), cv2.IMREAD_UNCHANGED)
        blur_sigma = foreground_blur_sigma(representative, sample_frames(bg_video, 1)[0],
                                          x, y, scale) * quality_strength
    blur_sigma = float(np.hypot(blur_sigma, extra_blur))
    print(f"전경 화질 맞춤: 강도 {quality_strength:.1f} · blur sigma {blur_sigma:.2f}px (배경 보정 없음)")

    cap = cv2.VideoCapture(str(bg_video))
    n_bg = int(cap.get(cv2.CAP_PROP_FRAME_COUNT)) or 0
    bg_fps = fps or (cap.get(cv2.CAP_PROP_FPS) or 30.0)
    if 'trajectory' in transform:
        if len(transform['trajectory']) != n_bg:
            cap.release()
            raise ValueError('배치 경로와 배경 영상의 프레임 수 불일치')
        for index in range(n_bg):frame_transform(transform, index)

    tmp = _project_temp_dir("composite_frames_")
    if save_alpha:
        from source.util import removal_experiments as ex
        alpha_dir = ex.local(Path(out_path).parent / 'alpha')
        alpha_dir.mkdir(exist_ok=False)
        if save_foreground:
            rgb_dir = ex.local(Path(out_path).parent / 'rgb')
            rgb_dir.mkdir(exist_ok=False)
    j = 0
    previous_center = None
    lab_state = {}
    if dof_match:
        try:
            representative = cv2.imread(str(fg_paths[len(fg_paths)//2]), cv2.IMREAD_UNCHANGED)
            preview = sample_frames(bg_video, 1)[0]
            blur_sigma = max(blur_sigma, foreground_blur_sigma(representative, preview, x, y, scale))
        except (cv2.error, ValueError, IndexError) as exc:
            print(f'DoF 건너뜀: {exc}')
    if lighting_check and not -180 <= light_angle <= 180:
        print('광원 각도가 범위를 벗어나 기본 방향(35도)을 사용합니다.')
        light_angle = 35.0
    occluder = None
    if occlusion_dir is not None:
        from source.enhancement.occlusion import Renderer
        occluder = Renderer(occlusion_dir, n_bg,
                            [int(cap.get(cv2.CAP_PROP_FRAME_WIDTH)), int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))], bg_fps)
    try:
        while True:
            ok, bg = cap.read()
            if not ok:
                break
            current_transform = frame_transform(transform, j)
            x, y, scale = (current_transform.get('x', 0), current_transform.get('y', 0),
                           current_transform.get('scale', 1))
            k = j - start
            alpha = np.zeros(bg.shape[:2], np.uint8) if (save_alpha or occluder is not None) else None
            object_rgb = np.full_like(bg, 128) if save_foreground else None
            if k < 0:  # 아직 전경 등장 전
                out = bg
            else:
                if k >= n_fg:
                    if fg_shortfall == "loop":
                        k = k % n_fg
                    elif fg_shortfall == "hold":
                        k = n_fg - 1
                    else:  # disappear
                        k = -1
                if k < 0:
                    out = bg
                else:
                    fg = cv2.imread(str(fg_paths[k]), cv2.IMREAD_UNCHANGED)
                    def safe(name, enabled, fn, value):
                        if not enabled:
                            return value
                        try:
                            return fn(value)
                        except Exception as exc:
                            print(f'{name} 건너뜀 (프레임 {j}): {exc}')
                            return value
                    fg = safe('경계', edge_decontam, _edge_decontaminate, fg)
                    fg = safe('LAB', lab_match,
                              lambda v: color_transfer_lab(v, bg, x, y, scale, state=lab_state), fg)
                    if relight:
                        fg = safe('RGB gain', True,
                                  lambda v: relight_fg_to_bg(v, bg, x, y, scale, strength=relight_strength), fg)
                    if motion_blur and previous_center is not None:
                        ys, xs = np.where(fg[..., 3] > 100)
                        if xs.size:
                            center = (float(xs.mean()), float(ys.mean()))
                            fg = safe('모션블러', True,
                                      lambda v: _motion_blur(v, center[0]-previous_center[0],
                                                             center[1]-previous_center[1]), fg)
                            previous_center = center
                    else:
                        ys, xs = np.where(fg[..., 3] > 100)
                        previous_center = (float(xs.mean()), float(ys.mean())) if xs.size else None
                    if save_alpha or occluder is not None:
                        alpha = placed_alpha(fg, bg.shape, current_transform)
                    if save_foreground:
                        placed_fg = _affine_foreground(
                            fg, bg.shape,
                            {**current_transform,
                             'subpixel': current_transform.get('subpixel', False)},
                        )
                        visible = placed_fg[..., 3] > 0
                        object_rgb[visible] = placed_fg[..., :3][visible]
                    shadow_bg = bg
                    if contact_shadow or cast_shadow:
                        try:
                            scaled_alpha = resample_foreground(fg, scale)[..., 3]
                            contact, cast = shadow_matte(scaled_alpha, round(x), round(y), bg.shape,
                                                          angle=light_angle)
                            shade = (.42*contact if contact_shadow else 0) + (.23*cast if cast_shadow else 0)
                            shadow_bg = np.clip(bg.astype(np.float32)*(1-np.clip(shade, 0, .65))[..., None],
                                                0, 255).astype(np.uint8)
                        except (cv2.error, ValueError) as exc:
                            print(f'그림자 건너뜀 (프레임 {j}): {exc}')
                    out = place(shadow_bg, fg, x, y, scale, blur_sigma=blur_sigma,
                                soften_edges=not preserve_detail, subpixel=current_transform.get('subpixel',False))
                    if grain_match:
                        try:
                            noise = np.random.default_rng(j).normal(0, _grain_sigma(bg), out.shape[:2])
                            matte = alpha if save_alpha else placed_alpha(fg, bg.shape, current_transform)
                            out = np.clip(out.astype(np.float32)+noise[..., None]
                                          *(matte[..., None].astype(np.float32)/255), 0, 255).astype(np.uint8)
                        except (cv2.error, ValueError) as exc:
                            print(f'grain 건너뜀 (프레임 {j}): {exc}')
            if occluder is not None:
                out, alpha = occluder.apply(out, alpha, j, bg)
            if save_foreground:
                object_rgb[alpha == 0] = 128
                if not cv2.imwrite(str(rgb_dir / f'{j:06d}.png'), object_rgb,
                                   [cv2.IMWRITE_PNG_COMPRESSION, 1]):
                    raise OSError('합성 전경 RGB 저장 실패')
            if save_alpha and not cv2.imwrite(str(alpha_dir / f'{j:06d}.png'), alpha,
                                             [cv2.IMWRITE_PNG_COMPRESSION, 1]):
                raise OSError('합성 알파 저장 실패')
            if not cv2.imwrite(str(tmp / f"{j:06d}.png"), out, [cv2.IMWRITE_PNG_COMPRESSION, 1]):
                raise OSError('합성 프레임 저장 실패')
            j += 1
            if progress and n_bg:
                progress(j / n_bg, f"합성 {j}/{n_bg}")
    finally:
        cap.release()
        if occluder is not None:occluder.close()
    if j == 0:
        raise RuntimeError("배경 영상에서 프레임을 읽지 못했습니다.")
    encode(tmp, Path(out_path), bg_fps, audio_source=audio_source or bg_video)
    if save_alpha:
        record = {
            'source_sha256': ex.digest(out_path), 'alpha_dir': str(alpha_dir.relative_to(ROOT)),
            'audio_source': str(Path(audio_source or bg_video).resolve().relative_to(ROOT)),
            'frames': j, 'fps': bg_fps, 'transform': transform,
            'occlusion': None if occluder is None else dict(folder=str(Path(occlusion_dir).resolve().relative_to(ROOT)),
                                                          hidden_pixels=occluder.hidden_pixels),
            'resampling': 'premultiplied_subpixel_affine' if transform.get('subpixel',False) else 'premultiplied_cubic_area', 'edge_sigma_px': .85,
            'edge_matte': 'local_color_trimap_3px' if edge_decontam else 'off',
            'intermediate_format': 'lossless_png',
            'corrections': dict(contact_shadow=contact_shadow, cast_shadow=cast_shadow,
                                lab_match=lab_match, grain_match=grain_match, dof_match=dof_match,
                                motion_blur=motion_blur, edge_decontam=edge_decontam,
                                lighting_check=lighting_check,
                                light_angle=light_angle),
        }
        if save_foreground:
            record.update(
                rgb_dir=str(rgb_dir.relative_to(ROOT)),
                rgb_sha256=hashlib.sha256(''.join(
                    ex.digest(path) for path in ex.sequence(rgb_dir, j)
                ).encode()).hexdigest(),
                stage='placed_foreground_before_relighting',
                neutral_background=128,
            )
        ex.write_json(ex.local(Path(out_path).with_suffix('.composition.json')), record)
    return Path(out_path)
