#!/bin/sh
# 첫 프레임까지 걸리는 시간 — 벽시계 p50/p95.
#
# 사용: ttff.sh <url> [반복수] [라벨...]
#   라벨은 CSV 에 그대로 들어간다: ttff.sh <url> 20 lte_good 720p
#
# 벽시계로 재는 이유: ffmpeg 내부 시간은 디코더 준비를 포함하지 않는다.
# 우리가 비교하려는 건 네트워크 항이고, 단말 상수는 전략과 무관하다(설계문서).
set -eu

URL=${1:?"사용법: ttff.sh <url> [반복수] [network] [tag]"}
N=${2:-20}
NET=${3:--}
TAG=${4:--}
OUT=${OUT:-/work/results}
CSV="$OUT/ttff.csv"

mkdir -p "$OUT"
[ -f "$CSV" ] || echo "network,tag,url,runs,p50_ms,p95_ms,min_ms,requests" > "$CSV"

i=0
times=""
while [ "$i" -lt "$N" ]; do
    s=$(date +%s%N)
    ffmpeg -v error -y -i "$URL" -frames:v 1 -f null - >/dev/null 2>&1 \
        || { echo "FAIL: ffmpeg 가 $URL 에서 프레임을 못 뽑았다" >&2; exit 1; }
    e=$(date +%s%N)
    times="$times $(( (e - s) / 1000000 ))"
    i=$((i + 1))
    printf '.' >&2
done
echo >&2

# HTTP 요청 수는 별도 1회. debug 로그가 측정 시간을 늘리므로 p50/p95 표본에 섞지 않는다.
req=$(ffmpeg -v debug -i "$URL" -frames:v 1 -f null - 2>&1 | grep -c 'request: GET' || true)

echo "$times" | tr ' ' '\n' | grep -v '^$' | sort -n \
| awk -v net="$NET" -v tag="$TAG" -v url="$URL" -v n="$N" -v req="$req" -v csv="$CSV" '
    { v[NR] = $1 }
    END {
        # 최근접 순위법 — 표본이 20개 수준이라 보간해도 의미가 없다.
        p50 = v[int(NR * 0.50) + (NR * 0.50 == int(NR * 0.50) ? 0 : 1)]
        p95 = v[int(NR * 0.95) + (NR * 0.95 == int(NR * 0.95) ? 0 : 1)]
        printf "%s,%s,%s,%d,%d,%d,%d,%d\n", net, tag, url, n, p50, p95, v[1], req >> csv
        printf "ttff %s/%s: p50 %dms p95 %dms min %dms, 요청 %d회\n", net, tag, p50, p95, v[1], req > "/dev/stderr"
    }'
