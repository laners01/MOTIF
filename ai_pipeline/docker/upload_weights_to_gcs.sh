#!/usr/bin/env bash
# 가중치를 로컬로 한 번 받아 GCS 버킷에 repo-상대경로 그대로 올린다 (1회 적재).
# 버킷 레이아웃은 weight_mounts.txt 경로와 동일 → 런타임 마운트가 그대로 맞물린다.
#
# 사용법:
#   docker/upload_weights_to_gcs.sh gs://PROJECT-model-weights /srv/models/smplx/SMPLX_NEUTRAL.npz
#   HF_TOKEN_FILE=/srv/secrets/hf_token docker/upload_weights_to_gcs.sh gs://PROJECT-model-weights /path/SMPLX_NEUTRAL.npz
# SMPLX 경로를 생략하면 다운로드를 건너뛰고 로컬에 이미 받아둔 가중치를 그대로 올린다.
set -euo pipefail

bucket="${1:?사용법: upload_weights_to_gcs.sh gs://버킷명 [/경로/SMPLX_NEUTRAL.npz]}"
smplx="${2:-}"
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

# 1) 가중치 로컬 적재 (SMPLX가 주어지면 다운로드까지 수행)
# PYTHON으로 다운로드용 deps(huggingface_hub·rfdetr)가 깔린 인터프리터를 지정한다.
# 지정 안 하면 python3 → python 순으로 찾는다(pyenv 환경에선 절대경로 지정 권장).
py="${PYTHON:-$(command -v python3 || command -v python)}"
if [ -n "$smplx" ]; then
  "$py" docker/download_models.py --smplx "$smplx" \
    ${HF_TOKEN_FILE:+--hf-token-file "$HF_TOKEN_FILE"}
fi

# 2) weight_mounts.txt의 경로만 버킷에 업로드 (디렉터리는 rsync, 파일은 cp)
while IFS= read -r line; do
  line="${line%%#*}"
  line="$(echo "$line" | xargs)"
  [ -z "$line" ] && continue
  if [ "${line: -1}" = "/" ]; then
    clean="${line%/}"
    [ -d "$clean" ] || { echo "누락된 디렉터리: $clean" >&2; exit 1; }
    gcloud storage rsync --recursive "$clean" "$bucket/$clean"
  else
    [ -f "$line" ] || { echo "누락된 파일: $line" >&2; exit 1; }
    gcloud storage cp "$line" "$bucket/$line"
  fi
done < docker/weight_mounts.txt

echo "업로드 완료: $bucket"
