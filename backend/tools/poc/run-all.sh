#!/bin/sh
# 스윕 전체 — 프로파일 x 화질 x 정책, 끝에 리포트.
#
# **호스트에서** 돈다 (컨테이너 안이 아니다):
#   sh backend/tools/poc/run-all.sh
#
# 측정 자체는 여전히 하네스 컨테이너 안에서 한다. 호스트가 맡는 건 프로파일
# 전환뿐인데, 셰이핑을 거는 컨테이너가 커널에 따라 달라지기 때문이다:
#   ifb 있음 -> 하네스 ingress (원칙. 원격 origin 도 조인다)
#   ifb 없음 -> origin egress  (Docker Desktop macOS. 로컬 origin 만 가능)
#
# 조절 (환경변수):
#   PROFILES  기본 "wifi lte_good lte_poor"  (3g 는 자체검사 전용 — 세션 한 번에 10분 넘는다)
#   POLICIES  기본 "N=1,T=1.0 N=2,T=1.5 N=3,T=3.0"
#   SESSIONS  조합당 세션 수 (기본 2)
#   EXIT_PROB 스와이프당 이탈 확률 (기본 0.05 — 제안값, 팀 확인 필요)
#   REALTIME  1 이면 재버퍼 실측을 추가로 돈다 (기본 1). 느리다 — 아래 주석 참조
#   SKIP_PROBE 1 이면 인벤토리를 다시 만들지 않는다
set -eu

HERE=$(cd "$(dirname "$0")" && pwd)
POC=${POC:-$HERE/../../local-infra/poc}
DC="docker compose -f $POC/docker-compose.yml"

OUT=/work/results
PROFILES=${PROFILES:-"wifi lte_good lte_poor"}
POLICIES=${POLICIES:-"N=1,T=1.0 N=2,T=1.5 N=3,T=3.0"}
SESSIONS=${SESSIONS:-2}
EXIT_PROB=${EXIT_PROB:-0.05}
REALTIME=${REALTIME:-1}
REALTIME_PROFILE=${REALTIME_PROFILE:-lte_poor}
REALTIME_POLICY=${REALTIME_POLICY:-N=2,T=1.5}
TTFF_RUNS=${TTFF_RUNS:-20}

h() { $DC exec -T poc-harness "$@"; }

# ---- 셰이핑 지점 선택 -------------------------------------------------
if h ip link add ifbprobe type ifb >/dev/null 2>&1; then
    h ip link del ifbprobe >/dev/null 2>&1 || true
    NETEM_SVC=poc-harness NETEM_MODE=ingress
else
    NETEM_SVC=poc-origin NETEM_MODE=egress
fi
echo "== 셰이핑: $NETEM_SVC ($NETEM_MODE)" >&2
netem() { $DC exec -T -e "MODE=$NETEM_MODE" "$NETEM_SVC" sh /work/profiles/netem.sh "$1" >&2; }
trap 'netem off || true' EXIT INT TERM

# ---- 태그 -------------------------------------------------------------
# origin = 팀이 준 원본(VIDEO_DIR). 나머지는 transcode.sh 가 만든 사본이다.
tag_url() { case "$1" in origin) echo "http://poc-origin/videos" ;;
                         *) echo "http://poc-origin/renditions/$1" ;; esac; }
tag_dir() { case "$1" in origin) echo "/srv/videos" ;;
                         *) echo "/srv/renditions/$1" ;; esac; }

TAGS="origin"
for t in $(h sh -c 'ls /srv/renditions 2>/dev/null' | tr -d '\r'); do
    [ "$t" = selfcheck ] && continue          # 자체검사용 합성 클립은 스윕 대상이 아니다
    TAGS="$TAGS $t"
done
echo "== 태그: $TAGS" >&2

# ---- 인벤토리 ---------------------------------------------------------
if [ "${SKIP_PROBE:-0}" != 1 ]; then
    first=1
    for t in $TAGS; do
        [ "$first" = 1 ] && a=0 || a=1
        $DC exec -T -e "APPEND=$a" poc-harness sh /work/tools/probe.sh "$(tag_dir "$t")" "$t" "$OUT"
        first=0
    done
fi

h sh -c "rm -f $OUT/session.csv $OUT/ttff.csv"

# ---- 스윕 -------------------------------------------------------------
for prof in $PROFILES; do
    netem "$prof"
    for tag in $TAGS; do
        url=$(tag_url "$tag")
        for pol in $POLICIES; do
            h python3 /work/tools/session.py \
                --feed "$OUT/inventory.csv" --tag "$tag" \
                --policy "$pol" --origin "$url" --network "$prof" \
                --sessions "$SESSIONS" --exit-prob "$EXIT_PROB" \
                --out "$OUT/session.csv"
        done
        # TTFF 는 프로파일·화질당 한 번. 인벤토리 첫 파일을 대표로 쓴다.
        # 파일명에 공백·한글·괄호가 있으므로 URL 인코딩해서 넘긴다.
        f=$(h python3 -c "
import csv, sys, urllib.parse
for r in csv.DictReader(open('$OUT/inventory.csv', encoding='utf-8')):
    if r['tag'] == '$tag':
        print(urllib.parse.quote(r['file'])); break
" | tr -d '\r\n')
        [ -n "$f" ] && h sh /work/tools/ttff.sh "$url/$f" "$TTFF_RUNS" "$prof" "$tag"
    done
done

# ---- 재버퍼 실측 -------------------------------------------------------
# --realtime 은 영상을 실제 재생 속도로 받는다 = 세션 하나가 시청 시간만큼 걸린다
# (30개면 7분 안팎). 그래서 전 조합이 아니라 "끊길 수 있는 곳"에서만 돌린다.
# 여유가 8배인 wifi/lte_good 은 산술로 이미 답이 나와 있어 돌릴 이유가 없다.
if [ "$REALTIME" = 1 ]; then
    echo "== 재버퍼 실측 ($REALTIME_PROFILE, $REALTIME_POLICY) — 느리다" >&2
    netem "$REALTIME_PROFILE"
    for tag in $TAGS; do
        h python3 /work/tools/session.py \
            --feed "$OUT/inventory.csv" --tag "$tag" \
            --policy "$REALTIME_POLICY" --origin "$(tag_url "$tag")" \
            --network "${REALTIME_PROFILE}-rt" --sessions 1 \
            --exit-prob "$EXIT_PROB" --realtime --out "$OUT/session.csv"
    done
fi

netem off
trap - EXIT INT TERM

h python3 /work/tools/report.py --results "$OUT" --out "$OUT/report.md"
echo "== 리포트: $POC/results/report.md" >&2
