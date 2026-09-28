"""CPU-only boundary tests for the MOTIF v0.3 integration."""
import json
import os
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
from urllib.error import HTTPError
from . import motif_client
from .motif_client import MotifClient
from .schemas import ProcessRequest, ProcessResponse
from .s3_storage import precomputed_manifest_key
from .worker import _fail_code, run_job, validate_single_task

PAYLOAD = {
    "job_uuid": "b7c93b4e-0a56-4d2e-9f0d-2f6e8a54d61c", "job_type": "CHALLENGE", "attempt": 1,
    "raw_video_key": "raw/a.mp4", "base_video_key": "edited/base.mp4",
    "output_video_key": "results/challenge/a.mp4", "correction_enabled": True,
    "slot": {"participant_mask": 1, "anchor": {"x": .2, "y": .7, "r": .1}},
    "base_duration_ms": 15000,
}

class ContractTest(unittest.TestCase):
    def test_schema_is_snake_case_and_ignores_unknown_fields(self):
        request = ProcessRequest.model_validate({**PAYLOAD, "future_field": 1})
        self.assertEqual(request.raw_video_key, "raw/a.mp4")
        with self.assertRaises(Exception):
            ProcessRequest.model_validate({**PAYLOAD, "raw_video_key": None})

    def test_precomputed_key_uses_challenge_participant_seq(self):
        with patch.dict(os.environ, {"PRECOMPUTED_PREFIX": "precomputed"}):
            key = precomputed_manifest_key("private/edited/11/31/7d2f1a0e.mp4")
            self.assertEqual(key, "precomputed/11/31/ready.json")
            self.assertEqual(key, precomputed_manifest_key("private/edited/11/31/7d2f1a0e.mp4"))
        with self.assertRaises(ValueError):  # seq 경로가 아니면 즉시 실패(조용한 오폴더 방지)
            precomputed_manifest_key("edited/base.mp4")

    def test_motif_branch_does_s3_io_and_skips_precompute(self):
        from . import service
        payload = {**PAYLOAD, "job_type": "MOTIF", "slot": None, "correction_enabled": False,
                   "base_video_key": "assets/x.mp4", "output_video_key": "results/motif/a.mp4"}
        request = ProcessRequest.model_validate(payload)
        got = {"down": [], "up": []}
        runtime = SimpleNamespace(
            gpu=0, cv2=object(),
            s3=SimpleNamespace(
                download_file=lambda bucket, key, dest: got["down"].append(key),
                upload_file=lambda src, bucket, key, ExtraArgs=None: got["up"].append((key, ExtraArgs))),
            motif=SimpleNamespace(
                prepare=lambda bg, fg, *, gpu: {"bg": bg, "fg": fg, "gpu": gpu},
                finish=lambda session: {"final_video": "output/run_motif/s/final.mp4"}),
            prepost=SimpleNamespace(relative=lambda p: "rel/" + Path(p).name),
            ex=SimpleNamespace(local=lambda p, exists=False: Path(p)),
            pipeline=SimpleNamespace(release_predictor=lambda: None),
            torch=SimpleNamespace(cuda=SimpleNamespace(empty_cache=lambda: None)))
        with patch.dict(os.environ, {"S3_BUCKET": "motif-media"}), \
                patch.object(service, "_video_duration", return_value=request.base_duration_ms / 1000), \
                patch.object(service, "precomputed_manifest_key") as precompute:
            response = service.process_video(request, runtime)
        self.assertEqual(got["down"], ["raw/a.mp4", "assets/x.mp4"])   # 유저(raw) → 에셋(assets)
        self.assertEqual(got["up"], [("results/motif/a.mp4", {"ContentType": "video/mp4"})])
        self.assertEqual(response.result_video_key, "results/motif/a.mp4")  # 요청 키 그대로 반환
        precompute.assert_not_called()  # MOTIF는 precomputed를 만들지도 요구하지도 않는다

    def test_choose_people_uses_only_background_frames_holder(self):
        from .service import _choose_people
        descriptor = {"people": [{"id": 1}, {"id": 2}]}
        # 제거 대상 = background_frames 보유 인물. anchor 미사용.
        self.assertEqual(_choose_people(descriptor, {1}), (1, 2))
        self.assertEqual(_choose_people(descriptor, {2}), (2, 1))
        for bad in (set(), {1, 2}):   # 0명·2명(모호)은 단일-제거 계약 위반 → 거부
            with self.assertRaises(Exception):
                _choose_people(descriptor, bad)

    def test_worker_reports_done_and_failed_without_raising(self):
        request = ProcessRequest.model_validate(PAYLOAD)
        class Client:
            event = None
            def done(self, req, response): self.event = ("done", response.result_video_key)
            def failed(self, req, code, message, processing_ms, worker_id): self.event = ("failed", code)
        client = Client()
        response = ProcessResponse(result_video_key=request.output_video_key, processing_ms=1, attempt=1, worker_id="test")
        run_job(request, client, runtime_loader=lambda: object(), processor=lambda *a, **k: response)
        self.assertEqual(client.event, ("done", request.output_video_key))
        run_job(request, client, runtime_loader=lambda: (_ for _ in ()).throw(RuntimeError("CUDA OOM")))
        self.assertEqual(client.event, ("failed", "GPU_ERROR"))

    def test_single_task_guard(self):
        with patch.dict(os.environ, {"CLOUD_RUN_TASK_COUNT": "2", "CLOUD_RUN_TASK_INDEX": "0"}):
            with self.assertRaises(RuntimeError): validate_single_task()

    def test_error_mapping(self):
        self.assertEqual(_fail_code(ValueError("유저 영상에서 여러 사람을 찾았습니다.")), "MULTIPLE_PERSONS")
        self.assertEqual(_fail_code(RuntimeError("decode failed")), "RAW_UNREADABLE")

    def test_callback_uses_contract_headers_and_does_not_retry_4xx(self):
        client = MotifClient("https://backend.example", "secret", "request-id")
        error = HTTPError("https://backend.example", 401, "bad token", {}, None)
        with patch.object(motif_client, "urlopen", side_effect=error) as call:
            client._post("/failed", {"x": 1})
        self.assertEqual(call.call_count, 1)
        sent = call.call_args.args[0]
        self.assertEqual(sent.get_header("X-motif-worker-token"), "secret")
        self.assertEqual(sent.get_header("X-request-id"), "request-id")

if __name__ == "__main__": unittest.main()
