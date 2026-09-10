#!/bin/sh
# 로컬 스윕에 쓴 영상 3세트를 S3 로 올린다.
#
#   sh backend/tools/poc/upload-s3.sh <버킷>          # 드라이런 — 뭘 올릴지만 보여준다
#   sh backend/tools/poc/upload-s3.sh <버킷> --go     # 실제 업로드
#
# PREFIX(기본 poc)로 서비스 경로와 분리한다. motif-media 는 PoC 전용이 아니라
# assets/·profiles/·raw/ 같은 서비스 프리픽스를 이미 갖고 있다. PoC 가 끝나면
#   aws s3 rm s3://<버킷>/poc/ --recursive
# 한 줄로 지운다.
#
# 자격증명은 이 스크립트가 다루지 않는다. 미리 설정해 둔다:
#   aws configure          (또는 aws sso login / AWS_PROFILE)
#
# 키 배치는 로컬 nginx 경로와 **똑같이** 맞춘다:
#   /videos/<파일>              <- 원본
#   /renditions/720p/<파일>     <- 렌디션
#   /renditions/480p/<파일>
# 그래야 CloudFront 측정에서 session.py 의 --origin 만 바꾸면 되고,
# 로컬 결과와 같은 파일·같은 경로로 나란히 비교된다.
# 파일명 주의 — 팀 영상 31개 중 19개가 한글 자모 분리(NFD)에 공백·괄호를 갖는다.
# macOS 파일시스템이 NFD 로 저장하기 때문이다. S3 키는 바이트열이라 그대로 올라가고,
# session.py 는 읽은 바이트를 그대로 URL 인코딩하므로 도구 경로는 문제없다.
# 다만 **손으로 URL 을 타이핑하면 거의 안 맞는다** — 대부분의 입력기는 NFC 를 낸다.
# 검증할 때도 파일명은 inventory.csv 에서 뽑아 쓴다(verify-cdn 항목 참조).
# 운영에서는 업로드 키가 UUID 라 생기지 않는 문제다. PoC 한정.
set -eu

BUCKET=${1:?"사용법: upload-s3.sh <버킷> [--go]"}
GO=${2:-}
PREFIX=${PREFIX:-poc}
POC=$(cd "$(dirname "$0")/../../local-infra/poc" && pwd)
VIDEO_DIR=$(grep -E '^VIDEO_DIR=' "$POC/.env" 2>/dev/null | cut -d= -f2- || echo "")
[ -n "$VIDEO_DIR" ] || { echo "FAIL: $POC/.env 의 VIDEO_DIR 를 못 읽었다" >&2; exit 1; }

# 1년 캐시. CloudFront 적중률을 재는 게 목적이라 TTL 이 흔들리면 안 된다.
CACHE="public, max-age=31536000, immutable"

set -- \
    "$VIDEO_DIR"                  "s3://$BUCKET/$PREFIX/videos/" \
    "$POC/renditions/720p"        "s3://$BUCKET/$PREFIX/renditions/720p/" \
    "$POC/renditions/480p"        "s3://$BUCKET/$PREFIX/renditions/480p/"

echo "버킷: s3://$BUCKET/$PREFIX/"
while [ $# -gt 0 ]; do
    src=$1 dst=$2; shift 2
    [ -d "$src" ] || { echo "  건너뜀 (없음): $src" >&2; continue; }
    n=$(find "$src" -maxdepth 1 -type f -name '*.mp4' | wc -l | tr -d ' ')
    sz=$(du -sh "$src" | cut -f1)
    echo "  $src -> $dst  ($n 개, $sz)"
    [ "$GO" = --go ] || continue
    aws s3 sync "$src" "$dst" \
        --exclude "*" --include "*.mp4" \
        --content-type video/mp4 \
        --cache-control "$CACHE" \
        --no-progress
done

if [ "$GO" != --go ]; then
    echo
    echo "드라이런이다. 실제로 올리려면 --go 를 붙인다."
    echo "먼저 확인할 것: 예산 알람(런북 §1)이 걸려 있는가 — 개인 계정이다."
else
    echo
    echo "업로드 완료. 다음: 런북 §3 검증 (하네스에서 Range·x-cache·쿼리스트링)"
fi

# --- 업로드 후 검증 ---------------------------------------------------
# CLOUDFRONT_DOMAIN 이 있으면 런북 §3 을 자동으로 돈다. 파일명은 손으로 치지 않고
# inventory.csv 에서 뽑는다 — NFD 한글이라 타이핑으로는 맞출 수 없다.
[ "$GO" = --go ] && [ -n "${CLOUDFRONT_DOMAIN:-}" ] || exit 0

DC="docker compose -f $POC/docker-compose.yml"
key=$($DC exec -T poc-harness python3 -c "
import csv, urllib.parse
for r in csv.DictReader(open('/work/results/inventory.csv', encoding='utf-8')):
    if r['tag'] == '720p':
        print('$PREFIX/renditions/720p/' + urllib.parse.quote(r['file'])); break" | tr -d '\r\n')
U="https://$CLOUDFRONT_DOMAIN/$key"

echo; echo "== 런북 §3 검증: $U"
# GET 으로 확인한다. HEAD 는 본문이 없어 CloudFront·S3 가 Range 를 무시하고 200 을
# 내므로, 런북 §3 의 `curl -sI -r` 는 정상 배포에서도 실패로 보인다.
echo "-- Range (206 + content-range 이어야 한다)"
$DC exec -T poc-harness curl -s -r 0-1023 -o /dev/null -D - "$U" \
    | grep -iE '^HTTP|content-range|x-cache'
echo "-- 캐시 (2회차가 Hit 이어야 한다)"
$DC exec -T poc-harness sh -c "curl -sI '$U' | grep -i x-cache; curl -sI '$U' | grep -i x-cache"
echo "-- 쿼리스트링 제외 (둘 다 Hit 이어야 캐시 키에서 빠진 것)"
$DC exec -T poc-harness sh -c "curl -sI '$U?a=1' | grep -i x-cache; curl -sI '$U?a=2' | grep -i x-cache"
