"""weight_mounts.txt가 download_models.REQUIRED와 정확히 맞물리는지 검증한다.

마운트 목록과 다운로드 목록이 드리프트하면 dangling 심링크(런타임 실패)나
업로드 누락이 생긴다. 양방향 커버리지로 그 드리프트를 빌드 전에 잡는다.
"""

import importlib.util
from pathlib import Path

HERE = Path(__file__).resolve().parent


def _load_required() -> tuple[str, ...]:
    spec = importlib.util.spec_from_file_location("dm", HERE / "download_models.py")
    dm = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(dm)
    return dm.REQUIRED


def _mounts() -> list[str]:
    out = []
    for line in (HERE / "weight_mounts.txt").read_text(encoding="utf-8").splitlines():
        line = line.split("#", 1)[0].strip()
        if line:
            out.append(line)
    return out


def _covers(mount: str, req: str) -> bool:
    return req.startswith(mount) if mount.endswith("/") else req == mount


def demo() -> None:
    mounts = _mounts()
    required = _load_required()
    # 모든 필수 가중치는 정확히 하나의 마운트로 덮인다.
    for req in required:
        hits = [m for m in mounts if _covers(m, req)]
        assert len(hits) == 1, f"{req} → 마운트 {hits} (정확히 1개여야 함)"
    # 죽은 마운트 없음: 각 마운트는 최소 하나의 필수 가중치를 덮는다.
    for mount in mounts:
        assert any(_covers(mount, req) for req in required), f"사용되지 않는 마운트: {mount}"
    print(f"OK: 마운트 {len(mounts)}개가 필수 가중치 {len(required)}개를 정확히 덮는다.")


if __name__ == "__main__":
    demo()
