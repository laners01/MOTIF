#!/usr/bin/env python3
"""세션 시뮬레이터 — 가짜 사용자가 피드를 스와이프하며 Range 로 받는다.

아이템마다: 현재 것을 W초분 받고("시청"), 다음 N개의 첫 T초를 받는다("프리페치").
이미 받아둔 구간은 다시 받지 않는다 — 실제 플레이어와 같고, 그래야 egress 가 맞다.

의존성은 표준 라이브러리뿐이다. 하네스 이미지에 pip 이 없다.

각 요청 URL 에 ?s=<runid> 를 붙인다. nginx 가 request_uri 를 통째로 로그에
남기므로, 실행별로 서버 측 바이트를 골라낼 수 있다 — ±5% 대조의 근거다.
정적 파일 서빙은 쿼리스트링을 무시하므로 응답에는 영향이 없다.
"""
import argparse
import csv
import http.client
import os
import random
import sys
import time
import urllib.parse
import uuid

# 시청 행동 모델 (설계문서 확정): 3초 스킵 40% / 절반 30% / 완시청 30%
WATCH_MODEL = ((0.40, "skip"), (0.30, "half"), (0.30, "full"))
SKIP_SECONDS = 3.0
SLICE_SECONDS = 1.0  # --realtime 에서 재버퍼를 판정하는 단위

# 스와이프 한 번마다 이 확률로 세션이 끝난다.
#
# 설계문서의 확정 시청 모델에는 이탈이 없다. 그런데 이탈이 없으면 피드 30개를
# 전부 보게 되고, 프리페치한 것이 100% 소비되어 "낭비"가 구조적으로 0 이 된다.
# 그러면 N·T 를 키울수록 무조건 유리해져(선반입은 늘고 총 바이트는 그대로) 주
# 실험이 성립하지 않는다 — 최적값이 N=∞ 로 나온다.
#
# 0.05 는 제안값이다(평균 ~20개 소비). 팀 회의에서 확정한다. 0 으로 두면 확정
# 문서 그대로 돌아가고, report.py 가 낭비 지표를 무효로 표시한다.
DEFAULT_EXIT_PROB = 0.05


def load_feed(path, tag):
    with open(path, newline="", encoding="utf-8") as fh:
        rows = [r for r in csv.DictReader(fh) if not tag or r["tag"] == tag]
    if not rows:
        sys.exit(f"FAIL: {path} 에 tag={tag} 인 항목이 없다")
    feed = []
    for r in rows:
        rate = float(r["bitrate"]) / 8.0  # bytes/sec
        feed.append({
            "file": r["file"],
            "duration": float(r["duration"]),
            "rate": rate,
            "size": int(r["size"]),
        })
    return feed


def parse_policy(text):
    """'N=2,T=1.5' -> (2, 1.5)"""
    kv = dict(p.split("=", 1) for p in text.split(","))
    return int(kv["N"]), float(kv["T"])


class Fetcher:
    """Range 요청 한 곳. 받은 바이트와 걸린 시간만 돌려준다.

    커넥션을 재사용한다(HTTP/1.1 keep-alive). 매번 새로 열면 요청마다 핸드셰이크
    1 RTT + 슬로스타트를 다시 문다 — RTT 300ms 구간에서는 1초짜리 조각 하나에
    600ms 넘는 고정비가 붙어서, 대역폭이 남는데도 재버퍼로 잡힌다. 실제
    플레이어와 CDN 은 전부 커넥션을 재사용하므로 그쪽이 맞는 모델이다.
    """

    def __init__(self, origin, runid, timeout):
        u = urllib.parse.urlsplit(origin)
        self.scheme = u.scheme or "http"
        self.host = u.netloc
        self.base = u.path.rstrip("/")
        self.runid = runid
        self.timeout = timeout
        self.requests = 0
        self.reconnects = 0
        self._conn = None

    def _connect(self):
        cls = (http.client.HTTPSConnection if self.scheme == "https"
               else http.client.HTTPConnection)
        self._conn = cls(self.host, timeout=self.timeout)

    def get(self, name, start, end):
        """[start, end] 바이트를 받는다. 받은 바이트 수와 소요 초를 돌려준다."""
        if end < start:
            return 0, 0.0
        path = f"{self.base}/{urllib.parse.quote(name)}?s={self.runid}"
        t0 = time.monotonic()
        for attempt in (1, 2):      # 서버가 idle 커넥션을 닫았으면 한 번 다시 연다
            try:
                if self._conn is None:
                    self._connect()
                self._conn.request("GET", path, headers={"Range": f"bytes={start}-{end}"})
                resp = self._conn.getresponse()
                body = resp.read()  # 끝까지 읽어야 커넥션을 다음 요청에 쓸 수 있다
                status = resp.status
                break
            except (http.client.HTTPException, OSError):
                if self._conn is not None:
                    self._conn.close()
                self._conn = None
                self.reconnects += 1
                if attempt == 2:
                    raise
        if status not in (200, 206):
            sys.exit(f"FAIL: {self.scheme}://{self.host}{path} 가 {status} 를 냈다")
        if status == 200 and start > 0:
            sys.exit(f"FAIL: origin 이 Range 를 무시했다(200) — {path}")
        self.requests += 1
        return len(body), time.monotonic() - t0

    def close(self):
        if self._conn is not None:
            self._conn.close()
            self._conn = None


def run_session(feed, policy_n, policy_t, fetcher, rng, realtime, exit_prob):
    got = [0] * len(feed)          # 아이템별로 앞에서부터 받아둔 바이트
    watch_b = [0] * len(feed)
    pre_b = [0] * len(feed)
    watched = [False] * len(feed)
    secs = [0.0] * len(feed)
    stalls = [0] * len(feed)

    def want_bytes(i, seconds):
        return min(int(feed[i]["rate"] * seconds), feed[i]["size"])

    def fetch_upto(i, target):
        """이 아이템을 앞에서부터 target 바이트까지 채운다. 늘어난 양을 돌려준다."""
        if target <= got[i]:
            return 0, 0.0
        n, dt = fetcher.get(feed[i]["file"], got[i], target - 1)
        got[i] += n
        return n, dt

    for i in range(len(feed)):
        # 스와이프 시점에 이탈 판정. 첫 아이템은 이미 열었으므로 예외다.
        if i > 0 and rng.random() < exit_prob:
            break
        watched[i] = True
        item = feed[i]

        roll, acc = rng.random(), 0.0
        for p, kind in WATCH_MODEL:
            acc += p
            if roll < acc:
                break
        w = {"skip": min(SKIP_SECONDS, item["duration"]),
             "half": item["duration"] / 2.0,
             "full": item["duration"]}[kind]
        secs[i] = w

        target = want_bytes(i, w)
        if not realtime:
            n, _ = fetch_upto(i, target)
            watch_b[i] += n
        else:
            # 1초 조각으로 나눠 받으며 버퍼 잔량을 따라간다.
            # 재생은 벽시계 1초당 영상 1초를 먹고, 조각 하나는 영상 1초를 채운다.
            #
            # 한계: 프리페치를 재생 중이 아니라 재생 후에 돌린다. 실제 플레이어는
            # 둘을 동시에 하므로 대역폭을 나눠 쓰고, 그만큼 여기 스톨 수는 낙관적이다.
            # 동시 실행은 스레드가 필요해서 두지 않았다 — 판정에는 영향이 없다.
            # 원본은 이미 전 조각이 스톨하고, 렌디션은 여유가 5배라 프리페치를
            # 얹어도 안 넘는다(0.78Mbps 재생 + 0.17Mbps 프리페치 vs 4Mbps).
            # 여유가 2배 밑으로 내려가는 렌디션을 검토하게 되면 그때 동시화한다.
            buf = got[i] / item["rate"]      # 프리페치로 이미 확보한 재생 가능 초
            played = 0.0
            while played < w:
                step = min(SLICE_SECONDS, w - played)
                n, dt = fetch_upto(i, want_bytes(i, played + step))
                watch_b[i] += n
                buf += step - dt
                if buf < 0:
                    stalls[i] += 1
                    buf = 0.0
                played += step

        for k in range(i + 1, min(i + 1 + policy_n, len(feed))):
            n, _ = fetch_upto(k, want_bytes(k, policy_t))
            pre_b[k] += n

    return dict(got=got, watch_b=watch_b, pre_b=pre_b,
                watched=watched, secs=secs, stalls=stalls)


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--feed", required=True, help="probe.sh 가 만든 inventory.csv")
    ap.add_argument("--tag", default="", help="inventory 의 tag 로 거른다 (720p 등)")
    ap.add_argument("--policy", required=True, help="N=2,T=1.5")
    ap.add_argument("--origin", required=True, help="http://poc-origin/videos")
    ap.add_argument("--network", default="-", help="CSV 라벨 (netem 프로파일명)")
    ap.add_argument("--sessions", type=int, default=1)
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--exit-prob", type=float, default=DEFAULT_EXIT_PROB,
                    help="스와이프당 이탈 확률 (기본 %(default)s — 제안값, 팀 확인 필요). "
                         "0 이면 낭비 지표가 구조적으로 0 이 된다")
    ap.add_argument("--realtime", action="store_true",
                    help="비트레이트 속도로 받으며 재버퍼를 판정한다 (세션이 영상 길이만큼 걸린다)")
    ap.add_argument("--timeout", type=float, default=120.0)
    ap.add_argument("--out", default="/work/results/session.csv")
    args = ap.parse_args()

    policy_n, policy_t = parse_policy(args.policy)
    feed = load_feed(args.feed, args.tag)

    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
    new = not os.path.exists(args.out)
    with open(args.out, "a", newline="", encoding="utf-8") as fh:
        wr = csv.writer(fh)
        if new:
            wr.writerow(["runid", "session", "item", "file", "policy", "network",
                         "file_tag", "watch_seconds", "bytes_watch", "bytes_prefetch",
                         "watched", "stalls", "exit_prob"])

        for s in range(args.sessions):
            runid = uuid.uuid4().hex[:12]
            rng = random.Random(args.seed + s)
            f = Fetcher(args.origin, runid, args.timeout)
            t0 = time.monotonic()
            r = run_session(feed, policy_n, policy_t, f, rng, args.realtime, args.exit_prob)
            f.close()
            elapsed = time.monotonic() - t0

            for i, item in enumerate(feed):
                if r["watch_b"][i] == 0 and r["pre_b"][i] == 0:
                    continue
                wr.writerow([runid, s, i, item["file"], args.policy, args.network,
                             args.tag or "-", f"{r['secs'][i]:.2f}",
                             r["watch_b"][i], r["pre_b"][i],
                             int(r["watched"][i]), r["stalls"][i], args.exit_prob])

            total = sum(r["watch_b"]) + sum(r["pre_b"])
            waste = sum(p for p, w in zip(r["pre_b"], r["watched"]) if not w)
            print(f"session {s} [{args.network}/{args.tag or '-'}/{args.policy}] "
                  f"run={runid} {total/1e6:.1f}MB 낭비={waste/1e6:.2f}MB "
                  f"스톨={sum(r['stalls'])} 요청={f.requests} {elapsed:.1f}s",
                  file=sys.stderr)


if __name__ == "__main__":
    main()
