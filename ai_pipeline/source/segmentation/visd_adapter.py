"""Local VISD inference and target-person association; imports no model on load."""
import os
import subprocess
import numpy as np
from source.util import removal_experiments as ex

BASE = "vendor/visd"
CONFIG = BASE + "/visd/configs/SSIS/CondInst_R_101_BiFPN_3x_sem_with_offset_class_Demo.yaml"
WEIGHTS = BASE + "/model_zoo/model_final.pth"
PYTHON = ".model-envs/visd/bin/python"


def require_assets():
    required = [BASE + "/visd/adet/__init__.py", CONFIG, WEIGHTS, PYTHON]
    missing = [p for p in required if not ex.local(p).is_file()]
    if missing:
        raise FileNotFoundError("VISD 로컬 준비가 필요합니다. 자동 설치/다운로드하지 않습니다:\n" + "\n".join(missing))


def select_shadow(masks, classes, associations, scores, body, other_masks, *, min_iou=0.5, margin=0.15, score_threshold=0.1):
    """Match native VISD pred_associations, never pair by spatial proximity."""
    if not (0 <= min_iou <= 1 and 0 <= margin <= 1 and 0 <= score_threshold <= 1):
        raise ValueError("VISD 매칭 임계값은 0~1이어야 합니다.")
    masks = np.asarray(masks, dtype=bool)
    body = np.asarray(body, dtype=bool)
    classes, associations, scores = map(np.asarray, (classes, associations, scores))
    n = len(classes)
    if masks.shape != (n, *body.shape) or any(x.shape != (n,) for x in (classes, associations, scores)):
        raise ValueError("VISD 출력 마스크/필드 크기가 원본 프레임과 일치하지 않습니다.")
    if not np.isfinite(scores).all():
        raise ValueError("VISD 점수가 유효하지 않습니다.")
    other_masks = [np.asarray(m, dtype=bool) for m in other_masks]
    if any(m.shape != body.shape for m in other_masks):
        raise ValueError("다른 사람 마스크 크기가 원본과 다릅니다.")

    def iou(a, b):
        return float((a & b).sum() / max(1, (a | b).sum()))

    candidates = []
    # Official VISD uses class 0=object, 1=shadow and shared association IDs.
    for association in np.unique(associations):
        if association <= 0:
            continue
        objects = np.flatnonzero((associations == association) & (classes == 0))
        shadows = np.flatnonzero((associations == association) & (classes == 1))
        if len(objects) != 1 or len(shadows) != 1:
            continue
        obj, sha = int(objects[0]), int(shadows[0])
        if min(scores[obj], scores[sha]) < score_threshold or not masks[sha].any():
            continue
        overlap = iou(masks[obj], body)
        competitor = max((iou(masks[obj], m) for m in other_masks), default=0.0)
        candidates.append((overlap, competitor, obj, sha, int(association)))
    candidates.sort(reverse=True)
    if not candidates:
        raise ValueError("VISD에서 연결된 물체–그림자 쌍을 찾지 못했습니다. 다른 기준 프레임을 선택하세요.")
    overlap, competitor, obj, sha, association = candidates[0]
    runner_up = candidates[1][0] if len(candidates) > 1 else 0.0
    if overlap < min_iou or overlap - max(competitor, runner_up) < margin:
        raise ValueError(f"VISD 사람 연결이 불확실합니다: IoU={overlap:.3f}, 경쟁={max(competitor, runner_up):.3f}. 다른 기준 프레임을 선택하세요.")
    protected = np.logical_or.reduce(other_masks) if other_masks else np.zeros_like(body)
    shadow = masks[sha] & ~body & ~protected
    if not shadow.any():
        raise ValueError("사람 보호 영역을 제외한 VISD 그림자가 없습니다.")
    return shadow, {"method": "visd_instance_association", "association_id": association,
        "target_iou": overlap, "competing_iou": max(competitor, runner_up),
        "object_score": float(scores[obj]), "shadow_score": float(scores[sha]),
        "min_iou": min_iou, "margin": margin, "score_threshold": score_threshold}


def predict(frame, body, other_masks, work, **options):
    require_assets()
    frame = ex.local(frame, exists=True)
    output = ex.unique_dir(work, "visd")
    result = output / "instances.npz"
    env = os.environ.copy()
    for key, leaf in {"TMPDIR": "tmp", "XDG_CACHE_HOME": "xdg", "TORCH_HOME": "torch", "HF_HOME": "hf", "MPLCONFIGDIR": "mpl", "CUDA_CACHE_PATH": "cuda", "TORCH_EXTENSIONS_DIR": "extensions"}.items():
        cache = ex.local(".cache/visd") / leaf
        cache.mkdir(parents=True, exist_ok=True)
        env[key] = str(cache)
    env.update(PYTHONDONTWRITEBYTECODE="1", HF_HUB_OFFLINE="1", TRANSFORMERS_OFFLINE="1")
    ex.local(PYTHON, exists=True)  # Validate the symlink target but retain the venv entry point.
    command = [str(ex.ROOT / PYTHON), str(ex.local("source/segmentation/visd_runner.py")),
               "--frame", str(frame), "--output", str(result)]
    with (output / "inference.log").open("w") as log:
        proc = subprocess.run(command, cwd=ex.local(BASE), env=env, stdout=log, stderr=subprocess.STDOUT)
    if proc.returncode:
        raise RuntimeError(f"VISD 실행 실패. 의존성과 로그를 확인하세요: {output / 'inference.log'}")
    with np.load(result, allow_pickle=False) as data:
        shadow, settings = select_shadow(data['masks'], data['classes'], data['associations'],
                                        data['scores'], body, other_masks, **options)
    settings.update(config=CONFIG, weights=WEIGHTS, frame=str(frame.relative_to(ex.ROOT)),
                    result=str(result.relative_to(ex.ROOT)))
    ex.write_json(output / "selection.json", settings)
    return shadow, settings
