"""MOTIF v0.3 callback client."""
import json
import time
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen

class CallbackError(RuntimeError):
    pass

class MotifClient:
    def __init__(self, base_url: str, token: str, request_id: str, timeout_seconds: float = 15,
                 resource: str = "composite-jobs"):
        if not base_url.startswith(("http://", "https://")):
            raise ValueError("MOTIF_CALLBACK_BASE는 http(s) URL이어야 합니다.")
        self.base_url = base_url.rstrip("/")
        self.token = token
        self.request_id = request_id
        self.timeout_seconds = timeout_seconds
        self.resource = resource

    def _post(self, path: str, payload: dict) -> None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        delays = (1, 4, 16)
        for attempt in range(4):
            request = Request(self.base_url + path, data=data, method="POST", headers={
                "X-Motif-Worker-Token": self.token,
                "X-Request-Id": self.request_id,
                "Content-Type": "application/json",
                "Accept": "application/json",
            })
            try:
                with urlopen(request, timeout=self.timeout_seconds) as response:
                    response.read()
                return
            except HTTPError as error:
                if 400 <= error.code < 500:
                    return
                if attempt == 3:
                    return
            except (URLError, TimeoutError):
                if attempt == 3:
                    return
            time.sleep(delays[attempt])

    def _path(self, job_uuid: str, suffix: str) -> str:
        return f"/api/worker/v1/{self.resource}/{quote(job_uuid, safe='')}/{suffix}"

    def done(self, request, response) -> None:
        self._post(self._path(request.job_uuid, "done"), response.model_dump())

    def failed(self, request, fail_code: str, message: str, processing_ms: int, worker_id: str) -> None:
        self._post(self._path(request.job_uuid, "failed"), {
            "fail_code": fail_code,
            "fail_message": message[-500:],
            "processing_ms": processing_ms,
            "attempt": request.attempt,
            "worker_id": worker_id,
        })
