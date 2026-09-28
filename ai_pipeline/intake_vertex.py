"""[초안] CPU Cloud Run Service: MOTIF 요청을 검증하고 Vertex AI Custom Job을 시작한다.

현행 intake.py(Cloud Run Job :run)의 Vertex 버전 초안. HTTP 계약(경로/토큰/202)과
콜백 방식은 그대로 두고, "Job 실행" 부분만 Vertex customJobs 생성으로 교체한다.

의존성 무추가 원칙: google-cloud-aiplatform SDK 대신 metadata SA 토큰 + urllib(현행과 동일).

⚠ 구현 시 확인 필요:
  - scheduling.strategy 의 DWS 정확 enum (아래 VERTEX_STRATEGY, 기본 "FLEX_START")
  - DWS가 소비하는 쿼터(preemptible vs standard) — 잡 제출 시 확인
  - 가중치 /gcs 마운트 경로·권한 (10-vertex-migration-plan.md §4.4)
  - 시크릿 처리: 지금 초안은 (B) intake env 통과 방식(잡 스펙에 평문 노출).
    운영은 (A) 컨테이너가 Secret Manager 런타임 읽기 권장(§4.2).
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

# kind → (워커 모듈, per-run payload 를 실을 env 이름)
# 실제 command 는 stage_weights.sh 래퍼로 감싼다(런타임 심링크 재구성 + 로컬 SSD 스테이징).
_WORKER = {
    "precompute": ("run_final_cloud_run_job.precompute_worker", "MOTIF_PRECOMPUTE"),
    "compose":    ("run_final_cloud_run_job.worker",            "MOTIF_JOB"),
}
def _command(kind: str) -> list:
    module, _ = _WORKER[kind]
    return ["bash", "docker/stage_weights.sh", "python", "-m", module]

# 가중치 준비 env(stage_weights.sh 가 읽음). Vertex 기본값 → intake env 로 덮어쓰기 가능.
#   WEIGHTS_MOUNT: Vertex GCS FUSE 경로. STAGE_WEIGHTS_DST: 설정 시 로컬 SSD 복사(2단계).
WEIGHT_ENV = ("WEIGHTS_MOUNT", "STAGE_WEIGHTS_DST")

# 잡 컨테이너로 그대로 넘길 정적 env(현행 Cloud Run Job 정의에 있던 값들).
# ⚠ (B) 방식: AWS 키가 CustomJob 스펙에 평문 저장됨. 운영은 (A) Secret Manager 런타임 읽기로.
PASSTHROUGH_ENV = ("MOTIF_CALLBACK_BASE", "MOTIF_WORKER_TOKEN",
                   "AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY", "AWS_REGION", "S3_BUCKET")


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


def _env_list(kind_env: str, payload: dict, request_id: str) -> list:
    """CustomJob containerSpec.env: per-run 2개 + 정적 passthrough."""
    env = [
        {"name": kind_env, "value": json.dumps(payload, separators=(",", ":"))},
        {"name": "MOTIF_REQUEST_ID", "value": request_id},
    ]
    for name in PASSTHROUGH_ENV + WEIGHT_ENV:
        value = os.environ.get(name)
        if value:
            env.append({"name": name, "value": value})
    return env


def _launch_job(kind: str, payload: dict, request_id: str) -> str:
    """Vertex AI Custom Job(H100 1장, DWS)을 생성하고 잡 리소스 이름을 반환한다."""
    command = _command(kind)
    _, kind_env = _WORKER[kind]
    project = _required("GCP_PROJECT")
    region = os.environ.get("VERTEX_REGION", "us-central1")
    body = {
        "displayName": f"motif-{kind}-{request_id or 'noid'}"[:128],
        "jobSpec": {
            "workerPoolSpecs": [{
                "replicaCount": 1,
                "machineSpec": {
                    "machineType": os.environ.get("VERTEX_MACHINE", "a3-highgpu-1g"),
                    "acceleratorType": os.environ.get("VERTEX_ACCEL", "NVIDIA_H100_80GB"),
                    "acceleratorCount": int(os.environ.get("VERTEX_ACCEL_COUNT", "1")),
                },
                "containerSpec": {
                    "imageUri": _required("WORKER_IMAGE"),
                    "command": command,
                    "env": _env_list(kind_env, payload, request_id),
                },
            }],
            "scheduling": {
                # ⚠ DWS Flex-start. enum 정확명 확인(STANDARD/SPOT/FLEX_START).
                "strategy": os.environ.get("VERTEX_STRATEGY", "FLEX_START"),
                "maxWaitDuration": os.environ.get("VERTEX_MAX_WAIT", "1800s"),
                "timeout": os.environ.get("VERTEX_TIMEOUT", "7200s"),
            },
            "serviceAccount": _required("WORKER_SA"),
        },
    }
    url = f"https://{region}-aiplatform.googleapis.com/v1/projects/{project}/locations/{region}/customJobs"
    request = Request(url, method="POST", data=json.dumps(body).encode(), headers={
        "Authorization": f"Bearer {_access_token()}", "Content-Type": "application/json"})
    with urlopen(request, timeout=15) as response:
        result = json.loads(response.read())
    return result.get("name", url)  # projects/.../customJobs/{id} (Spring 미의존, 로그/추적용)


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
        kind = "precompute" if self.path.rstrip("/").endswith("/precompute") else "compose"
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length < 2 or length > 1024 * 1024:
                raise ValueError("invalid body size")
            raw = json.loads(self.rfile.read(length))
            schema = PrecomputeRequest if kind == "precompute" else ProcessRequest
            payload = schema.model_validate(raw).model_dump(mode="json")
            execution = _launch_job(kind, payload, request_id)
        except (ValueError, json.JSONDecodeError) as error:
            log.warning("[%s] invalid request: %s", request_id, error)
            self._json(503, {"error": str(error)}); return
        except (HTTPError, URLError, TimeoutError, RuntimeError) as error:
            log.exception("[%s] failed to launch %s job", request_id, kind)
            self._json(503, {"error": str(error)}); return
        log.info("[%s] kind=%s job_uuid=%s execution=%s", request_id, kind, payload["job_uuid"], execution)
        self._json(202, {"execution": execution})

    def log_message(self, fmt, *args):
        log.info(fmt, *args)


def main():
    logging.basicConfig(level=os.environ.get("LOG_LEVEL", "INFO"))
    server = ThreadingHTTPServer(("0.0.0.0", int(os.environ.get("PORT", "8080"))), Handler)
    server.serve_forever()


if __name__ == "__main__":
    main()
