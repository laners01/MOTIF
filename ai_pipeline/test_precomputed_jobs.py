import unittest
from unittest.mock import patch
from . import motif_client
from .motif_client import MotifClient
from .precompute_schemas import PrecomputeRequest, PrecomputeResponse
from .precompute_service import _person_descriptors
from .precompute_worker import run_job

class PrecomputeContractTest(unittest.TestCase):
    def test_request_needs_only_existing_base_key(self):
        request = PrecomputeRequest.model_validate({"job_uuid": "job-1", "base_video_key": "assets/a.mp4", "unknown": 1})
        self.assertEqual(request.base_video_key, "assets/a.mp4")
        self.assertIsNone(request.source_video_key)
        separated = PrecomputeRequest.model_validate({"job_uuid": "job-2", "base_video_key": "edited/a.mp4", "source_video_key": "challenges/a.mp4"})
        self.assertEqual(separated.source_video_key, "challenges/a.mp4")

    def test_bbox_becomes_normalized_anchor(self):
        ready = {"record": {"size": [1000, 500]}, "people": {"2": {
            "id": 2, "bbox": {"x0": 100, "x1": 300, "y0": 50, "y1": 450, "w": 200, "h": 400}}}}
        row = _person_descriptors(ready)[0]
        self.assertEqual(row["id"], 2)
        self.assertEqual(row["anchor"], {"x": .2, "y": .9, "r": .4})

    def test_callback_targets_precompute_resource(self):
        client = MotifClient("https://backend.example", "secret", "rid", resource="precompute-jobs")
        with patch.object(motif_client, "urlopen") as call:
            call.return_value.__enter__.return_value.read.return_value = b""
            client._post(client._path("job 1", "done"), {"x": 1})
        self.assertEqual(call.call_args.args[0].full_url,
                         "https://backend.example/api/worker/v1/precompute-jobs/job%201/done")

    def test_run_job_dispatches_done_and_failed(self):
        request = PrecomputeRequest.model_validate({"job_uuid": "job-1", "base_video_key": "assets/a.mp4"})
        class Client:
            event = None
            def done(self, req, response): self.event = ("done", response.manifest_key)
            def failed(self, req, code, message, ms, worker_id): self.event = ("failed", code)
        client = Client()
        response = PrecomputeResponse(job_uuid="job-1", base_video_key="assets/a.mp4",
            manifest_key="precomputed/x/ready.json", background_sha256="d", people=[1], processing_ms=1)
        run_job(request, client, runtime_loader=lambda: object(), processor=lambda *a, **k: response)
        self.assertEqual(client.event, ("done", "precomputed/x/ready.json"))
        run_job(request, client, runtime_loader=lambda: (_ for _ in ()).throw(RuntimeError("CUDA OOM")))
        self.assertEqual(client.event, ("failed", "GPU_ERROR"))

if __name__ == "__main__": unittest.main()
