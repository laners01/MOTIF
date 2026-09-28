"""CPU Cloud Run Service: MOTIF 합성/분석 요청을 검증하고 해당 Job을 시작한다.

  POST /            (또는 그 외 경로)  → 합성 Job (COMPOSE_JOB_RESOURCE, env MOTIF_JOB)
  POST /precompute                     → 배경 분석 Job (PRECOMPUTE_JOB_RESOURCE, env MOTIF_PRECOMPUTE)
둘 다 X-Motif-Worker-Token 검증 후 Job을 띄우고 202로 즉시 응답한다(기다리지 않는다).
"""
import hmac
import json
import logging
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from .schemas import ProcessRequest
from .precompute_schemas import PrecomputeRequest

log = logging.getLogger("motif_intake")

def _required(name):
    value = os.environ.get(name, "").strip()
    if not value:
        raise RuntimeError(f"필수 환경 변수가 없습니다: {name}")
    return value

def _access_token():
    request = Request("http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token",
                      headers={"Metadata-Flavor": "Google"})
    with urlopen(request, timeout=3) as response:
        return json.loads(response.read())["access_token"]

def _launch_job(job_resource: str, primary_env: str, payload: dict, request_id: str) -> str:
    """Cloud Run Job을 env override와 함께 실행하고 execution 이름을 반환한다."""
    job = job_resource.rstrip("/")
    body = {"overrides": {"containerOverrides": [{"env": [
        {"name": primary_env, "value": json.dumps(payload, separators=(",", ":"))},
        {"name": "MOTIF_REQUEST_ID", "value": request_id},
    ]}]}}
    request = Request(f"https://run.googleapis.com/v2/{job}:run", method="POST",
        data=json.dumps(body).encode(), headers={
            "Authorization": f"Bearer {_access_token()}", "Content-Type": "application/json"})
    with urlopen(request, timeout=10) as response:
        result = json.loads(response.read())
    metadata = result.get("metadata") or {}
    return metadata.get("name") or result.get("name") or job

class Handler(BaseHTTPRequestHandler):
    def _json(self, status, body=None):
        data = b"" if body is None else json.dumps(body).encode()
        self.send_response(status)
        if data:
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        if data:
            self.wfile.write(data)

    def do_POST(self):
        token = self.headers.get("X-Motif-Worker-Token", "")
        if not hmac.compare_digest(token, _required("MOTIF_WORKER_TOKEN")):
            self._json(401); return
        request_id = self.headers.get("X-Request-Id", "").strip()
        is_precompute = self.path.rstrip("/").endswith("/precompute")
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length < 2 or length > 1024 * 1024:
                raise ValueError("invalid body size")
            raw = json.loads(self.rfile.read(length))
            if is_precompute:
                payload = PrecomputeRequest.model_validate(raw).model_dump(mode="json")
                execution = _launch_job(_required("PRECOMPUTE_JOB_RESOURCE"), "MOTIF_PRECOMPUTE", payload, request_id)
            else:
                payload = ProcessRequest.model_validate(raw).model_dump(mode="json")
                execution = _launch_job(_required("COMPOSE_JOB_RESOURCE"), "MOTIF_JOB", payload, request_id)
        except (ValueError, json.JSONDecodeError) as error:
            # 본문 검증 실패는 영구 오류 → 400(백엔드가 재시도하지 않도록). 503은 재시도 신호라 오용 금물.
            log.warning("[%s] invalid request: %s", request_id, error)
            self._json(400, {"error": str(error)}); return
        except (HTTPError, URLError, TimeoutError, RuntimeError) as error:
            # 잡 실행/설정 실패는 일시적일 수 있어 503(백엔드 재시도 대상).
            log.exception("[%s] failed to launch %s job", request_id, "precompute" if is_precompute else "compose")
            self._json(503, {"error": str(error)}); return
        log.info("[%s] kind=%s job_uuid=%s execution=%s", request_id,
                 "precompute" if is_precompute else "compose", payload["job_uuid"], execution)
        self._json(202, {"execution": execution})

    def log_message(self, fmt, *args):
        log.info(fmt, *args)

def main():
    logging.basicConfig(level=os.environ.get("LOG_LEVEL", "INFO"))
    server = ThreadingHTTPServer(("0.0.0.0", int(os.environ.get("PORT", "8080"))), Handler)
    server.serve_forever()

if __name__ == "__main__":
    main()
