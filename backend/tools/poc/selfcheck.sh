#!/bin/sh
# 하네스 자체검사 — 팀 영상 없이 파이프라인 전체를 돌린다.
#
#   ffmpeg 합성 클립 -> probe -> session -> report -> ±5% 대조
#
# **호스트에서** 돈다: sh backend/tools/poc/selfcheck.sh
#
# 티켓 541 의 verify.sh 는 "환경(netem·Range·로그)"을 검사한다. 이건 "도구"를
# 검사한다. 둘 다 영상이 없어도 돈다 — 그래야 영상 문제와 코드 문제가 분리된다.
set -eu

HERE=$(cd "$(dirname "$0")" && pwd)
POC=${POC:-$HERE/../../local-infra/poc}
DC="docker compose -f $POC/docker-compose.yml"

DIR=/srv/renditions/selfcheck
OUT=/work/results/selfcheck
CLIPS=${CLIPS:-6}

h() { $DC exec -T poc-harness "$@"; }

if h ip link add ifbprobe type ifb >/dev/null 2>&1; then
    h ip link del ifbprobe >/dev/null 2>&1 || true
    NETEM_SVC=poc-harness NETEM_MODE=ingress
else
    NETEM_SVC=poc-origin NETEM_MODE=egress
fi
netem() { $DC exec -T -e "MODE=$NETEM_MODE" "$NETEM_SVC" sh /work/profiles/netem.sh "$1" >&2; }
trap 'netem off || true' EXIT INT TERM
echo "0) 셰이핑 $NETEM_SVC ($NETEM_MODE)" >&2

h sh -c "mkdir -p $DIR $OUT && rm -f $OUT/session.csv $OUT/inventory.csv $OUT/offsets.csv"

# ---- 1. 합성 클립 -----------------------------------------------------
# 진짜 H.264 MP4 라 moov·GOP·Range 가 실제로 동작한다. 8초로 짧게 잡아
# 자체검사가 1분 안에 끝나게 한다.
h sh -c "i=1; while [ \$i -le $CLIPS ]; do f=$DIR/clip\$i.mp4; \
    [ -f \"\$f\" ] || ffmpeg -v error -y \
        -f lavfi -i 'testsrc2=size=720x1280:rate=30:duration=8' \
        -f lavfi -i \"sine=frequency=\$((300 + i * 60)):duration=8\" \
        -c:v libx264 -preset ultrafast -b:v 2500k -pix_fmt yuv420p \
        -c:a aac -b:a 96k -shortest -movflags +faststart \"\$f\"; \
    i=\$((i+1)); done"
echo "1) 합성 클립 $CLIPS 개 준비" >&2

# ---- 2. probe ---------------------------------------------------------
h sh /work/tools/probe.sh "$DIR" selfcheck "$OUT"
rows=$(h sh -c "awk 'NR>1' $OUT/inventory.csv | wc -l" | tr -d ' \r\n')
[ "$rows" = "$CLIPS" ] || { echo "FAIL: 인벤토리가 $rows 행이다 ($CLIPS 이어야 한다)" >&2; exit 1; }

# +faststart 로 만들었으니 전부 front 여야 한다 — moov 판정이 뒤집혀 있으면 걸린다.
rear=$(h sh -c "awk -F, 'NR>1 && \$8==\"rear\"' $OUT/inventory.csv | wc -l" | tr -d ' \r\n')
[ "$rear" = 0 ] || { echo "FAIL: +faststart 인데 rear 가 $rear 개 — moov 판정이 틀렸다" >&2; exit 1; }
echo "2) probe OK — $rows 행, moov 전부 front" >&2

# ---- 2b. netem 이 진짜 걸리는가 ---------------------------------------
# 프로파일을 바꿔도 시간이 같으면 셰이핑 지점을 잘못 고른 것이다. 그 상태로
# 나온 스윕은 전부 "네트워크 무제한" 수치라 쓸모가 없다.
clip=$(h sh -c "awk -F, 'NR>1 {print \$1; exit}' $OUT/inventory.csv" | tr -d ' \r\n')
t_of() {
    netem "$1"
    h curl -s -r 0-524287 -o /dev/null -w '%{time_total}' \
        "http://poc-origin/renditions/selfcheck/$clip" | tr -d ' \r\n'
}
fast=$(t_of wifi); slow=$(t_of 3g); netem off
ratio=$(awk -v a="$slow" -v b="$fast" 'BEGIN{printf "%.1f", (b>0? a/b : 0)}')
awk -v r="$ratio" 'BEGIN{ if (r < 5) exit 1 }' \
    || { echo "FAIL: 512KiB 가 wifi ${fast}s / 3g ${slow}s (${ratio}배) — netem 이 안 걸렸다" >&2; exit 1; }
echo "2b) netem OK — 512KiB wifi ${fast}s vs 3g ${slow}s (${ratio}배)" >&2

# ---- 3. 세션 ----------------------------------------------------------
# 이탈 확률을 0.3 으로 높게 준다 — 낭비 지표가 0 이 아닌 값을 내는지 봐야 한다.
netem lte_good
h python3 /work/tools/session.py \
    --feed "$OUT/inventory.csv" --tag selfcheck \
    --policy N=2,T=1.5 --origin http://poc-origin/renditions/selfcheck \
    --network lte_good --sessions 2 --exit-prob 0.3 --out "$OUT/session.csv"
netem off

waste=$(h sh -c "awk -F, 'NR>1 && \$11==\"0\" {s+=\$10} END{print s+0}' $OUT/session.csv" | tr -d ' \r\n')
[ "$waste" -gt 0 ] || { echo "FAIL: 낭비 바이트가 0 이다 — 이탈 모델이 동작하지 않는다" >&2; exit 1; }
echo "3) 세션 OK — 미시청 프리페치 ${waste} 바이트 집계됨" >&2

# ---- 4. 리포트 + ±5% 대조 ---------------------------------------------
h python3 /work/tools/report.py --results "$OUT" --out "$OUT/report.md" > /dev/null
echo "4) 리포트 OK — 로그 대조 ±5% 통과" >&2
h grep -m1 '^\*\*최대 오차' "$OUT/report.md" || true
echo "selfcheck 통과 ($POC/results/selfcheck/report.md)" >&2
