"""스텁 AI 접수 서비스 (ADR-0003, 합성 파이프라인 작업명세 §4.8).

운영과 **같은 경로**가 로컬에서 돌게 한다 — 접수 HTTP 를 받고, 잠시 뒤 콜백 HTTP 를
되쏜다. 예전에는 job.composite 를 소비하는 큐 워커였는데, AI 가 GCP Cloud Run Jobs 로
가면서 경계가 HTTP 로 바뀌었다(§0). Cloud Run Job 은 상주하지 않아 큐를 구독할 수 없다.

  POST /submit   → 202 {"execution": "stub-..."}  (AI 접수 서비스 흉내)
                   → PROCESS_SECONDS 뒤 백그라운드 스레드가 콜백
  POST {CALLBACK_BASE}/api/worker/v1/composite-jobs/{job_uuid}/done
       (STUB_FAIL_CODE 가 있으면 /failed)
  GET  /health   → 200  (compose healthcheck)

계약 (AI 서버 명세 §3.2·§3.3·§3.4):
  - 접수 본문·콜백 본문 모두 snake_case.
  - result_video_key 는 **받은 output_video_key 를 그대로** 돌려준다. core-api 가
    자기 계산값과 대조하므로 다른 값을 보내면 400 이다.
  - X-Motif-Worker-Token 을 콜백에 싣는다. 없으면 401.
  - 받은 X-Request-Id 를 그대로 이어붙인다 — 로그가 한 ID 로 이어진다(ADR-0005).
"""

import json
import logging
import os
import threading
import time
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(os.environ.get("PORT", "8080"))
# 컨테이너에서 호스트의 core-api 를 부른다. Linux 는 compose 의 extra_hosts 가 채운다
CALLBACK_BASE = os.environ.get("CALLBACK_BASE", "http://host.docker.internal:8081")
WORKER_TOKEN = os.environ.get("WORKER_TOKEN", "motif-local-worker-token")  # 로컬 전용 더미
PROCESS_SECONDS = float(os.environ.get("PROCESS_SECONDS", "2"))
# 값이 있으면 /done 대신 /failed 를 쏜다 — 실패 경로를 로컬에서 재현할 때 쓴다
STUB_FAIL_CODE = os.environ.get("STUB_FAIL_CODE") or None

REQUEST_ID_HEADER = "X-Request-Id"
TOKEN_HEADER = "X-Motif-Worker-Token"

logging.basicConfig(format="%(asctime)s %(levelname)-5s [stub-worker] %(message)s",
                    level=logging.INFO)
logger = logging.getLogger(__name__)


def log(request_id, message):
    # java logback 패턴의 [%X{requestId:-}] 와 같은 꼴로 남겨 한 ID 로 grep 되게 한다
    logger.info("[%s] %s", request_id or "", message)


def callback(job, request_id):
    """PROCESS_SECONDS 뒤 콜백. 접수 응답(202)을 먼저 돌려주려고 스레드로 뺀다."""
    time.sleep(PROCESS_SECONDS)
    job_uuid = job.get("job_uuid")
    if STUB_FAIL_CODE:
        path, body = "failed", {
            "fail_code": STUB_FAIL_CODE,
            "fail_message": f"stub 강제 실패 (STUB_FAIL_CODE={STUB_FAIL_CODE})",
            "processing_ms": int(PROCESS_SECONDS * 1000),
            "attempt": job.get("attempt"),
            "worker_id": "stub-worker",
        }
    else:
        path, body = "done", {
            # 받은 값을 그대로 — core-api 가 자기 계산값과 대조한다(§4.3)
            "result_video_key": job.get("output_video_key"),
            "processing_ms": int(PROCESS_SECONDS * 1000),
            "attempt": job.get("attempt"),
            "worker_id": "stub-worker",
        }

    url = f"{CALLBACK_BASE}/api/worker/v1/composite-jobs/{job_uuid}/{path}"
    headers = {"Content-Type": "application/json", TOKEN_HEADER: WORKER_TOKEN}
    if request_id:
        headers[REQUEST_ID_HEADER] = request_id
    request = urllib.request.Request(
        url, data=json.dumps(body).encode("utf-8"), headers=headers, method="POST")
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            log(request_id, f"콜백 /{path} → {response.status}: job_uuid={job_uuid}")
    except urllib.error.HTTPError as e:
        # 401·404 는 설정 문제다. 스텁은 재시도하지 않는다(AI 명세 §3.5 와 같은 태도)
        log(request_id, f"콜백 /{path} 실패 {e.code}: job_uuid={job_uuid} {e.read()[:200]}")
    except OSError as e:
        log(request_id, f"콜백 /{path} 연결 실패: job_uuid={job_uuid} {e}")


class Handler(BaseHTTPRequestHandler):

    # HTTP/1.1 로 응답한다. 1.0 이면 클라이언트가 keep-alive·chunked 를 못 쓴다
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        pass  # 기본 액세스 로그를 끈다 — 우리 로그만 남긴다

    def _read_body(self):
        """본문을 읽는다 — <b>chunked 를 반드시 다뤄야 한다</b>.

        core-api 의 RestClient(JdkClientHttpRequestFactory)는 Content-Length 없이
        Transfer-Encoding: chunked 로 보낸다. 그걸 모르면 length=0 으로 읽어 본문이
        빈 객체가 되고, 필드가 전부 None 이 된 채로 콜백이 나가 400 을 받는다.
        """
        if "chunked" in (self.headers.get("Transfer-Encoding") or "").lower():
            chunks = []
            while True:
                size = int(self.rfile.readline().split(b";")[0] or b"0", 16)
                if size == 0:
                    self.rfile.readline()   # 마지막 CRLF
                    break
                chunks.append(self.rfile.read(size))
                self.rfile.readline()       # 청크 뒤 CRLF
            return b"".join(chunks)
        return self.rfile.read(int(self.headers.get("Content-Length") or 0))

    def _send(self, status, payload=None):
        body = json.dumps(payload).encode("utf-8") if payload is not None else b""
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if body:
            self.wfile.write(body)

    def do_GET(self):
        if self.path == "/health":
            self._send(200, {"status": "UP"})
        else:
            self._send(404, {"error": "not found"})

    def do_POST(self):
        if self.path != "/submit":
            self._send(404, {"error": "not found"})
            return
        request_id = self.headers.get(REQUEST_ID_HEADER)
        raw = self._read_body()
        try:
            job = json.loads(raw or b"{}")
        except json.JSONDecodeError:
            self._send(400, {"error": "invalid json"})
            return

        job_uuid = job.get("job_uuid")
        if job_uuid is None:
            # 계약 위반을 조용히 넘기지 않는다 — camelCase 로 왔거나 본문이 비었다.
            # 그대로 콜백하면 core-api 가 400 을 주는데 원인이 여기라는 게 안 보인다
            log(request_id, f"본문에 job_uuid 가 없다 — 계약 위반이다: {raw[:300]!r}")
            self._send(400, {"error": "job_uuid missing"})
            return
        log(request_id, f"접수 수신: job_uuid={job_uuid} job_type={job.get('job_type')} "
                        f"attempt={job.get('attempt')} base={job.get('base_video_key')}")
        # Cloud Run Job 을 띄우기만 하고 기다리지 않는다 — 1~3초 안에 202 (§3.2)
        self._send(202, {"execution": f"stub-{job_uuid}"})
        threading.Thread(target=callback, args=(job, request_id), daemon=True).start()


def main():
    mode = f"/failed({STUB_FAIL_CODE})" if STUB_FAIL_CODE else "/done"
    log(None, f"접수 대기: port={PORT} callback={CALLBACK_BASE} "
              f"delay={PROCESS_SECONDS}s mode={mode}")
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()


if __name__ == "__main__":
    main()
