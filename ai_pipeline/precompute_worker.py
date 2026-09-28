"""Background precompute Cloud Run Job entrypoint.

Compose와 대칭으로 done/failed 콜백을 백엔드에 1회 보낸다(resource=precompute-jobs).
READY manifest는 여전히 S3에 기록하지만(compose가 소비), 완료/실패 신호는 콜백이 준다.
"""
import logging
import os
import signal
import time
from .motif_client import MotifClient
from .precompute_schemas import PrecomputeRequest
from .precompute_service import precompute_background
from .runtime import load_runtime
from .worker import _fail_code, _required, validate_single_task

log = logging.getLogger("motif_precompute_job")

def run_job(request, client, runtime_loader=load_runtime, processor=precompute_background):
    started = time.monotonic()
    try:
        result = processor(request, runtime_loader(), job_id=request.job_uuid)
    except Exception as error:
        log.exception("job_uuid=%s precompute failed", request.job_uuid)
        client.failed(request, _fail_code(error), f"{type(error).__name__}: {error}",
                      round((time.monotonic() - started) * 1000), os.environ.get("CLOUD_RUN_EXECUTION", "local"))
        return
    client.done(request, result)

def main():
    logging.basicConfig(level=os.environ.get("LOG_LEVEL", "INFO"),
                        format="%(asctime)s [%(levelname)s] %(message)s")
    validate_single_task()
    request_id = _required("MOTIF_REQUEST_ID") if os.environ.get("MOTIF_REQUEST_ID", "").strip() else None
    request = PrecomputeRequest.model_validate_json(_required("MOTIF_PRECOMPUTE"))
    request_id = request_id or request.job_uuid
    client = MotifClient(_required("MOTIF_CALLBACK_BASE"), _required("MOTIF_WORKER_TOKEN"), request_id,
                         resource="precompute-jobs")
    started = time.monotonic()
    def terminate(signum, _frame):
        client.failed(request, "TIMEOUT", f"terminated by signal {signum}",
                      round((time.monotonic() - started) * 1000), os.environ.get("CLOUD_RUN_EXECUTION", "local"))
        os._exit(0)
    signal.signal(signal.SIGTERM, terminate)
    log.info("[%s] job_uuid=%s precompute started", request_id, request.job_uuid)
    run_job(request, client)
    log.info("[%s] job_uuid=%s precompute finished", request_id, request.job_uuid)

if __name__ == "__main__":
    main()
