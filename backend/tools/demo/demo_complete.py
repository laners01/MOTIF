"""시연용 합성 강제 완료 — 미리 만들어 둔 결과 영상으로 잡을 DONE 처리하고 FCM 을 보낸다.

운영 노드에서 실행한다 (kubectl 이 되는 곳):
    python3 demo_complete.py                # 가장 최근의 미완료 CHALLENGE 잡
    python3 demo_complete.py <job_uuid>     # 특정 잡

app-media 가 내는 composite.packaged 를 대신 발행한다. core-api 가 받아서 잡을 DONE 으로
확정하고 composite.finished → app-notification → FCM 까지 정상 경로로 흐른다.
AI 가 나중에 /done·/failed 를 보내도 이미 DONE 이라 무시된다.

ponytail: 결과 영상은 모든 잡이 한 벌을 공유한다. 서명 재발급(POST /media/{jobUuid}/signature)이
잡 uuid 프리픽스를 서명하면 2시간 만료 뒤 403 이 날 수 있다 — 시연 한 번에는 충분하다.
"""
import base64
import json
import subprocess
import sys
import time
import urllib.request
import uuid
from datetime import datetime, timezone

RESULT_PREFIX = "private/challenge/0e0bbb36-f27c-4b1a-af51-c46278aff312"
MGMT = "http://localhost:15672/api/exchanges/%2Fmotif/motif.topic/publish"


def sh(*args):
    return subprocess.run(args, check=True, capture_output=True, text=True).stdout.strip()


def psql(sql):
    return sh("kubectl", "exec", "deploy/postgres", "--", "psql", "-U", "postgres", "-d", "motif",
              "-At", "-c", sql)


def find_job(job_uuid):
    where = (f"job_uuid = '{uuid.UUID(job_uuid)}'" if job_uuid
             else "job_type = 'CHALLENGE' AND status <> 'DONE' ORDER BY job_seq DESC LIMIT 1")
    row = psql(f"SELECT job_seq, job_uuid, status FROM motif_core.composite_jobs WHERE {where};")
    if not row:
        sys.exit("대상 잡이 없다")
    job_seq, found_uuid, status = row.splitlines()[0].split("|")
    return int(job_seq), found_uuid, status


def main():
    job_seq, job_uuid, status = find_job(sys.argv[1] if len(sys.argv) > 1 else None)
    print(f"대상 잡: seq={job_seq} uuid={job_uuid} status={status}")
    if status == "DONE":
        sys.exit("이미 DONE 이다")

    password = base64.b64decode(sh("kubectl", "get", "secret", "rabbitmq-secret", "-o",
                                   "jsonpath={.data.RABBITMQ_ADMIN_PASSWORD}")).decode()
    event = {
        "event_id": str(uuid.uuid4()),
        "occurred_at": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "media_job_uuid": str(uuid.uuid4()),
        "source_type": "COMPOSITE_RESULT",
        "source_seq": job_seq,
        "output_prefix": RESULT_PREFIX,
        "master_key": RESULT_PREFIX + "/master.m3u8",
        "thumbnail_key": RESULT_PREFIX + "/thumb.jpg",
        "duration_ms": None,
    }
    body = json.dumps({
        "properties": {"content_type": "application/json", "delivery_mode": 2},
        "routing_key": "composite.packaged",
        "payload": json.dumps(event),
        "payload_encoding": "string",
    }).encode()

    forward = subprocess.Popen(["kubectl", "port-forward", "deploy/rabbitmq", "15672:15672"],
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        time.sleep(2)
        req = urllib.request.Request(MGMT, data=body, method="POST")
        req.add_header("Content-Type", "application/json")
        req.add_header("Authorization", "Basic " + base64.b64encode(f"admin:{password}".encode()).decode())
        with urllib.request.urlopen(req) as res:
            routed = json.load(res).get("routed")
    finally:
        forward.terminate()

    if not routed:
        sys.exit("발행은 됐는데 routed=false — 바인딩을 확인한다")
    time.sleep(2)
    print("발행 완료 → 현재 상태:", psql(
        f"SELECT status, result_video_key FROM motif_core.composite_jobs WHERE job_seq = {job_seq};"))


if __name__ == "__main__":
    main()
