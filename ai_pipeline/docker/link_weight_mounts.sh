#!/usr/bin/env bash
# 코드가 기대하는 가중치 경로를 GCS 마운트 지점으로 심링크한다 (빌드 시 1회).
# 마운트가 비어 있으면 심링크는 dangling이 되고, 런타임 검증(runtime.py)이 잡아낸다.
set -euo pipefail

root="${1:?사용법: link_weight_mounts.sh <server-root> [mount-path]}"
mount="${2:-/mnt/model-weights}"
cd "$root"

while IFS= read -r line; do
  line="${line%%#*}"
  line="$(echo "$line" | xargs)"   # 앞뒤 공백 제거
  [ -z "$line" ] && continue
  clean="${line%/}"
  mkdir -p "$(dirname "$clean")"
  ln -sfn "$mount/$clean" "$clean"
done < docker/weight_mounts.txt
