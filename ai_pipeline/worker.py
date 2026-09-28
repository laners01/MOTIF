"""MOTIF v0.3 GPU compose Cloud Run Job entrypoint."""
import json
import logging
import os
import signal
import time
from pydantic import ValidationError
from .motif_client import MotifClient
from .runtime import load_runtime
from .schemas import ProcessRequest
from .service import RawTooShortError, RawUnreadableError, UploadError, process_video

log = logging.getLogger("motif_compose_job")

def _required(name):
    value = os.environ.get(name, "").strip()
    if not value:
        raise RuntimeError(f"필수 환경 변수가 없습니다: {name}")
    return value

def validate_single_task():
    if int(os.environ.get("CLOUD_RUN_TASK_COUNT", "1")) != 1 or int(os.environ.get("CLOUD_RUN_TASK_INDEX", "0")) != 0:
        raise RuntimeError("이 워커는 tasks=1 설정에서만 실행할 수 있습니다.")

def _fail_code(error: BaseException) -> str:
    message = str(error).lower()
    if isinstance(error, RawTooShortError): return "RAW_TOO_SHORT"
    if isinstance(error, RawUnreadableError): return "RAW_UNREADABLE"
    if isinstance(error, UploadError): return "UPLOAD_FAILED"
    response = getattr(error, "response", {})
    if response.get("Error", {}).get("Code") in {"404", "NoSuchKey", "NotFound"}:
        return "INPUT_NOT_FOUND"
    if "여러 사람" in str(error): return "MULTIPLE_PERSONS"
    if "사람을 찾지" in str(error): return "PERSON_NOT_DETECTED"
    if "too short" in message or "짧" in str(error): return "RAW_TOO_SHORT"
    if "decode" in message or "디코딩" in str(error): return "RAW_UNREADABLE"
    if "upload" in message or "putobject" in message: return "UPLOAD_FAILED"
    if "cuda" in message or "gpu" in message or "out of memory" in message: return "GPU_ERROR"
    return "INTERNAL_ERROR"

def run_job(request, client, runtime_loader=load_runtime, processor=process_video):
    started = time.monotonic()
    try:
        result = processor(request, runtime_loader(), job_id=request.job_uuid)
    except Exception as error:
        log.exception("job_uuid=%s processing failed", request.job_uuid)
        client.failed(request, _fail_code(error), f"{type(error).__name__}: {error}",
                      round((time.monotonic() - started) * 1000), os.environ.get("CLOUD_RUN_EXECUTION", "local"))
        return
    client.done(request, result)

def main():
    logging.basicConfig(level=os.environ.get("LOG_LEVEL", "INFO"),
                        format="%(asctime)s [%(levelname)s] %(message)s")
    validate_single_task()
    request_id = _required("MOTIF_REQUEST_ID")
    try:
        request = ProcessRequest.model_validate_json(_required("MOTIF_JOB"))
    except (ValidationError, ValueError, RuntimeError) as error:
        log.error("[%s] invalid MOTIF_JOB: %s", request_id, error)
        return
    client = MotifClient(_required("MOTIF_CALLBACK_BASE"), _required("MOTIF_WORKER_TOKEN"), request_id)
    started = time.monotonic()
    def terminate(signum, _frame):
        client.failed(request, "TIMEOUT", f"terminated by signal {signum}",
                      round((time.monotonic() - started) * 1000), os.environ.get("CLOUD_RUN_EXECUTION", "local"))
        os._exit(0)
    signal.signal(signal.SIGTERM, terminate)
    log.info("[%s] job_uuid=%s attempt=%s started", request_id, request.job_uuid, request.attempt)
    run_job(request, client)
    log.info("[%s] job_uuid=%s finished", request_id, request.job_uuid)

if __name__ == "__main__":
    main()
