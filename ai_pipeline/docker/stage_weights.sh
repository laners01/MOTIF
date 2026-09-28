#!/usr/bin/env bash
# 런타임 가중치 준비 래퍼(엔트리포인트). 워커 실행 전에 심링크를 재구성하고,
# 필요하면 로컬 디스크로 스테이징한 뒤 exec 로 워커를 넘긴다.
#
# 왜 런타임인가: universal 이미지의 심링크는 빌드 시 한 경로로만 박힌다. 플랫폼마다
# 가중치 위치가 다르므로(Cloud Run gcsfuse=/mnt/model-weights, Vertex GCS FUSE=/gcs/<bucket>)
# 여기서 실제 마운트에 맞게 다시 링크한다.
#
# 사용(컨테이너 command):
#   bash docker/stage_weights.sh python -m run_final_cloud_run_job.precompute_worker
#
# 동작(전부 env 로 제어, 미설정 시 아무것도 안 하고 그대로 exec → Cloud Run 무영향):
#   WEIGHTS_MOUNT      가중치 원본 마운트. 예: Vertex=/gcs/ssafy-motif-models
#                      (미설정 → 빌드 시 박힌 심링크 그대로 사용, 스테이징 없음)
#   STAGE_WEIGHTS_DST  설정 시: WEIGHTS_MOUNT 에서 이 경로(로컬 SSD 등)로 1회 복사 후
#                      그쪽으로 링크(콜드로딩 제거, 계획서 §4.4 2단계). 미설정 → 원본 직접 링크(1단계).
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
mount="${WEIGHTS_MOUNT:-}"

if [ -z "$mount" ]; then
  exec "$@"   # 아무 설정 없음: 빌드 시 심링크 그대로(Cloud Run 경로) → 통과
fi

effective="$mount"
if [ -n "${STAGE_WEIGHTS_DST:-}" ]; then
  effective="$STAGE_WEIGHTS_DST"
  echo "[stage_weights] $mount → $effective 로 복사 시작" >&2
  # weight_mounts.txt 에 나열된 경로만 복사(정확히 필요한 ~17.7GB). 이미 있으면 건너뜀.
  while IFS= read -r line; do
    line="${line%%#*}"; line="$(echo "$line" | xargs)"; [ -z "$line" ] && continue
    clean="${line%/}"
    src="$mount/$clean"; dst="$effective/$clean"
    [ -e "$dst" ] && { echo "[stage_weights] 존재, 건너뜀: $clean" >&2; continue; }
    mkdir -p "$(dirname "$dst")"
    echo "[stage_weights] 복사: $clean" >&2
    cp -a "$src" "$dst"
  done < "$root/docker/weight_mounts.txt"
  echo "[stage_weights] 복사 완료" >&2
fi

# 심링크를 effective 마운트로 재생성(빌드 시 것 덮어씀).
bash "$root/docker/link_weight_mounts.sh" "$root" "$effective"
echo "[stage_weights] 심링크 → $effective, 워커 실행: $*" >&2
exec "$@"
