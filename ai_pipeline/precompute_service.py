"""Analyze one base video and publish its immutable artifact to S3."""
import logging
import os
import shutil
import time
from .precomputed_artifact import create_archive, sha256
from .precompute_schemas import PrecomputeRequest, PrecomputeResponse
from .runtime import PIPELINE_ROOT, inside_pipeline
from .s3_storage import configured_bucket, precomputed_artifact_key, precomputed_manifest_key
from .service import _safe_job_id

log = logging.getLogger("motif_precompute")

def _person_descriptors(ready: dict) -> list[dict]:
    width, height = ready["record"]["size"]
    rows = []
    for value in ready["people"].values():
        box = value["bbox"]
        rows.append({"id": int(value["id"]), "anchor": {
            "x": ((box["x0"] + box["x1"]) / 2) / width,
            "y": box["y1"] / height,
            "r": max(box["w"] / width, box["h"] / height) / 2,
        }})
    return sorted(rows, key=lambda row: row["id"])

def precompute_background(request: PrecomputeRequest, runtime, *, job_id: str) -> PrecomputeResponse:
    started = time.monotonic()
    job_id = _safe_job_id(job_id)
    bucket = configured_bucket()
    work = inside_pipeline(PIPELINE_ROOT / "work/motif_precompute" / job_id)
    source = work / "background.mp4"
    destination = inside_pipeline(PIPELINE_ROOT / "output/motif_precompute" / job_id)
    work.mkdir(parents=True, exist_ok=True)
    destination.mkdir(parents=True, exist_ok=True)
    try:
        source_video_key = request.source_video_key or request.base_video_key
        runtime.s3.download_file(bucket, source_video_key, str(source))
        ready = runtime.prepost.run_pre(runtime.prepost.relative(source), gpu=runtime.gpu,
                                        output_dir=runtime.prepost.relative(destination))
        # 인코딩(§2): 완전한 asset → portable 패키지(PNG 무손실 + NPY XZ, receipt 검증)로 내보낸다.
        package_dir = destination / "package"
        export = runtime.packing.export_package(ready, destination=runtime.prepost.relative(package_dir))
        runtime.packing.verify_package(export["directory"])
        archive = work / "background-precomputed.tar.gz"
        create_archive(PIPELINE_ROOT, archive, [runtime.prepost.ex.local(export["directory"])])
        archive_sha = sha256(archive)
        artifact_key = precomputed_artifact_key(request.base_video_key, archive_sha)
        manifest_key = precomputed_manifest_key(request.base_video_key)
        # artifact를 먼저 완전히 업로드한 뒤에만 READY를 기록한다(§2).
        runtime.s3.upload_file(str(archive), bucket, artifact_key, ExtraArgs={"ContentType": "application/gzip"})
        descriptor = {
            "format_version": 1, "portable_format": "portable-precomputed-v1",
            "status": "ready", "base_video_key": request.base_video_key,
            "source_video_key": source_video_key, "package_path": export["directory"],
            "artifact_key": artifact_key, "artifact_sha256": archive_sha,
            "background_sha256": ready["source_sha256"],
            "implementation": ready["implementation"],
            "model_bundle_version": os.environ.get("MODEL_BUNDLE_VERSION", "models-v1"),
            "people": _person_descriptors(ready),
        }
        import json
        runtime.s3.put_object(Bucket=bucket, Key=manifest_key,
            Body=json.dumps(descriptor, ensure_ascii=False, indent=2).encode(), ContentType="application/json")
        return PrecomputeResponse(job_uuid=request.job_uuid, base_video_key=request.base_video_key,
            manifest_key=manifest_key, background_sha256=ready["source_sha256"],
            people=[row["id"] for row in descriptor["people"]],
            processing_ms=round((time.monotonic() - started) * 1000))
    finally:
        try:
            runtime.pipeline.release_predictor()
            runtime.torch.cuda.empty_cache()
        finally:
            shutil.rmtree(work, ignore_errors=True)
            shutil.rmtree(destination, ignore_errors=True)
