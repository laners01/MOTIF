#!/bin/sh
# 이 컨테이너의 네트워크를 프로파일대로 조인다. 호스트가 아니라 컨테이너 eth0.
# 사용: sh netem.sh <wifi|lte_good|lte_poor|3g|off>
#
# 두 가지 모드가 있고 기본은 자동 선택이다 (MODE=ingress|egress 로 강제 가능):
#
#   ingress — 하네스(수신측)에서 조인다. ifb 모듈이 필요하다. CloudFront 처럼
#             우리가 손댈 수 없는 origin 도 조일 수 있어서 이쪽이 원칙이다.
#   egress  — origin(송신측)에서 조인다. origin 의 나가는 방향이 곧 다운로드
#             경로라 결과가 같다. ifb 가 없는 커널(Docker Desktop macOS 의
#             LinuxKit 6.10)에서 쓰는 경로다. 단 원격 origin 은 못 조인다.
#
# 아래 ingress 설명은 원칙 경로에 대한 것이다:
#   - 우리가 재는 건 '내려받는' 속도다. eth0 egress 에 rate 를 걸면 요청·ACK 만
#     조여져서 다운로드가 그대로 빨리 끝난다 — 측정이 성립하지 않는다.
#   - 리눅스는 ingress 에 큐를 직접 못 건다. ifb0 로 넘긴 뒤 거기에 netem 을 건다.
#   - 지연은 한쪽에만 걸어도 왕복에 한 번 실린다 → delay = RTT (rtt/2 아님).
#
# 튜닝하지 않는 것 (실측으로 무의미함을 확인, 다시 넣지 말 것):
#   - 큐 깊이(limit): 40~1000 어디로 줘도 3g 에서 1MiB 가 6.833초로 같았다
#   - GRO/GSO/TSO 끄기: 켜고 끈 차이가 1MiB 6.8337 vs 6.8339초,
#     TTFF 크기인 64KB 1.3516 vs 1.3518초 — 소수점 4자리까지 동일했다
#     (netem 은 합쳐진 패킷도 길이에 비례해 지연시키므로 평균 속도가 보존된다)
#
# 스윕은 wifi·lte_good 2종만 쓴다(docs/poc/video-delivery.md 확정).
# lte_poor·3g 는 netem 이 실제로 걸렸는지 확인하는 자체검사용 — 20Mbps 에선
# 1MiB 가 0.4초라 측정 오차에 묻혀 "걸렸다"를 증명하지 못한다.
set -eu

DEV=${DEV:-eth0}
IFB=ifb0
MODE=${MODE:-auto}

clear_all() {
    tc qdisc del dev "$DEV" root 2>/dev/null || true
    tc qdisc del dev "$DEV" ingress 2>/dev/null || true
    tc qdisc del dev "$IFB" root 2>/dev/null || true
}

case "${1:-}" in
    wifi)     RTT=10ms  RATE=100mbit  ;;   # 기준선
    lte_good) RTT=50ms  RATE=20mbit   ;;
    lte_poor) RTT=100ms RATE=4mbit    ;;
    3g)       RTT=300ms RATE=1500kbit ;;
    off)      clear_all; echo "netem off ($DEV)"; exit 0 ;;
    *) echo "사용법: $0 <wifi|lte_good|lte_poor|3g|off>" >&2; exit 2 ;;
esac

clear_all

# 자동은 ingress 아니면 실패다. egress 로 자동 강등하지 않는다 — 수신측에서
# egress 를 걸면 요청·ACK 만 조여지고 다운로드는 그대로 빨라서, 조여지지 않은
# 수치가 "성공"으로 나온다. egress 는 origin 에서 쓰라고 있는 것이라 호출자가
# 명시해야 한다 (run-all.sh·selfcheck.sh 가 그렇게 넘긴다).
if [ "$MODE" = auto ]; then
    if ip link show "$IFB" > /dev/null 2>&1 || ip link add "$IFB" type ifb 2>/dev/null; then
        MODE=ingress
    else
        echo "FAIL: ifb 가 없다 — 이 컨테이너에서는 다운링크를 조일 수 없다." >&2
        echo "      origin 에서 MODE=egress 로 돌린다 (run-all.sh·selfcheck.sh 가 자동으로 한다):" >&2
        echo "      docker compose exec -e MODE=egress poc-origin sh /work/profiles/netem.sh $1" >&2
        exit 1
    fi
fi

if [ "$MODE" = egress ]; then
    # 송신측에서 지연과 대역폭을 한 qdisc 에 건다. 나가는 방향이 곧 다운로드라
    # ingress 모드와 같은 것을 잰다. 지연은 한쪽에만 걸어도 왕복에 한 번 실린다.
    tc qdisc add dev "$DEV" root netem delay "$RTT" rate "$RATE"
    echo "netem $1 [egress]: rtt $RTT, 다운링크 $RATE ($DEV)"
    exit 0
fi

if ! ip link show "$IFB" > /dev/null 2>&1; then
    ip link add "$IFB" type ifb || {
        echo "FAIL: ifb0 을 만들 수 없다 — 커널에 ifb 모듈이 없다." >&2
        echo "      MODE=egress 로 poc-origin 에서 돌린다 (run-all.sh 가 자동으로 그렇게 한다)." >&2
        exit 1
    }
fi
ip link set "$IFB" up

# 내려받기: eth0 로 들어오는 것을 전부 ifb0 로 넘기고 거기서 대역폭을 건다
tc qdisc add dev "$DEV" handle ffff: ingress
tc filter add dev "$DEV" parent ffff: protocol all u32 match u32 0 0 \
    action mirred egress redirect dev "$IFB"
tc qdisc add dev "$IFB" root netem rate "$RATE"

# 지연: 나가는 쪽에 한 번
tc qdisc add dev "$DEV" root netem delay "$RTT"

echo "netem $1 [ingress]: rtt $RTT, 다운링크 $RATE ($DEV <- $IFB)"
