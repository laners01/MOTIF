"""Compose one MOTIF request using an immutable background artifact."""
import json
import logging
import math
import os
import shutil
import subprocess
import time
from pathlib import Path
from .precomputed_artifact import extract_archive, import_portable, sha256
from .runtime import PIPELINE_ROOT, inside_pipeline
from .s3_storage import configured_bucket, precomputed_manifest_key
from .schemas import ProcessRequest, ProcessResponse

log = logging.getLogger("motif_compose")

class InvalidRequestError(ValueError):
    pass

class RawTooShortError(InvalidRequestError):
    pass

class RawUnreadableError(InvalidRequestError):
    pass

class UploadError(RuntimeError):
    pass

def _safe_job_id(value: str) -> str:
    import re
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}", value):
        raise InvalidRequestError("job_uuid 형식이 올바르지 않습니다.")
    return value

def _video_duration(cv2, path: Path) -> float:
    capture = cv2.VideoCapture(str(path))
    try:
        fps = capture.get(cv2.CAP_PROP_FPS)
        frames = int(capture.get(cv2.CAP_PROP_FRAME_COUNT))
        ok, _ = capture.read()
    finally:
        capture.release()
    if not ok or not math.isfinite(fps) or fps <= 0 or frames < 1:
        raise RawUnreadableError("촬영본 디코딩 실패")
    return frames / fps

def _read_json(s3, bucket: str, key: str) -> dict:
    return json.loads(s3.get_object(Bucket=bucket, Key=key)["Body"].read())

def _choose_people(descriptor: dict, removable: set[int]) -> tuple[int, int]:
    """제거 대상은 배경이 사전계산된(background_frames 보유) 인물로 결정된다.

    패키지는 (challenge, slot)당 고정 단일-제거라 제거 가능 인물이 정확히 1명이다.
    요청 anchor는 배치가 아니라 힌트일 뿐이라 선택에 쓰지 않는다.
    """
    people = descriptor.get("people") or []
    if len(people) < 2:
        raise InvalidRequestError("춤 기준 인물을 포함한 사전계산 배경이 필요합니다.")
    remove = [int(p["id"]) for p in people if int(p["id"]) in removable]
    if len(remove) != 1:
        raise InvalidRequestError(f"배경이 사전계산된 제거 대상 인물이 정확히 1명이어야 합니다: {sorted(remove)}")
    remove_id = remove[0]
    driver_id = next(int(person["id"]) for person in people if int(person["id"]) != remove_id)
    return remove_id, driver_id

def _normalize_output(source: Path, base: Path, destination: Path, duration_ms: int) -> Path:
    """Enforce the backend MP4 contract and use only base audio."""
    destination.parent.mkdir(parents=True, exist_ok=True)
    command = [os.environ.get("IMAGEIO_FFMPEG_EXE") or "ffmpeg", "-y", "-loglevel", "error", "-noautorotate", "-i", str(source),
        "-noautorotate", "-i", str(base), "-map", "0:v:0", "-map", "1:a:0?",
        "-vf", "fps=30,setsar=1", "-metadata:s:v:0", "rotate=0",
        "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-ar", "48000",
        "-t", f"{duration_ms / 1000:.3f}", "-movflags", "+faststart", str(destination)]
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError("최종 MP4 정규화 실패: " + result.stderr[-1000:])
    return destination

def process_video(request: ProcessRequest, runtime, *, job_id: str | None = None) -> ProcessResponse:
    """job_type으로 파이프라인을 고른다. MOTIF는 사전계산 없이 자기완결 합성."""
    if request.job_type == "MOTIF":
        return process_motif(request, runtime, job_id=job_id)
    return process_challenge(request, runtime, job_id=job_id)


def process_motif(request: ProcessRequest, runtime, *, job_id: str | None = None) -> ProcessResponse:
    """에셋 배경(assets/) + 유저 촬영본(raw/)을 받아 조명만 얹어 results/motif/에 올린다.

    배경은 read-only(인페인팅·춤보정·기하·그림자 없음), precomputed도 요구하지 않는다.
    """
    started = time.monotonic()
    job_id = _safe_job_id(job_id or request.job_uuid)
    bucket = configured_bucket()
    work = inside_pipeline(PIPELINE_ROOT / "work/motif_compose" / job_id)
    inputs = inside_pipeline(work / "inputs")
    foreground = inputs / "foreground.mp4"   # raw_video_key: 유저 촬영본
    background = inputs / "background.mp4"    # base_video_key: 구매 에셋
    inputs.mkdir(parents=True, exist_ok=True)
    try:
        runtime.s3.download_file(bucket, request.raw_video_key, str(foreground))
        runtime.s3.download_file(bucket, request.base_video_key, str(background))
        # 노트북과 동일하게 프로젝트 루트 상대 경로 문자열로 넘긴다(핵심 로직 무수정).
        session = runtime.motif.prepare(runtime.prepost.relative(background),
                                        runtime.prepost.relative(foreground), gpu=runtime.gpu)
        result = runtime.motif.finish(session)
        final_video = runtime.ex.local(result["final_video"], exists=True)
        duration = _video_duration(runtime.cv2, final_video)
        if abs(duration - request.base_duration_ms / 1000) > 0.5:
            raise InvalidRequestError(f"출력 길이 {duration:.3f}s가 base_duration_ms와 ±0.5s를 벗어났습니다.")
        try:
            runtime.s3.upload_file(str(final_video), bucket, request.output_video_key,
                                   ExtraArgs={"ContentType": "video/mp4"})
        except Exception as error:
            raise UploadError(f"결과 upload 실패: {error}") from error
        return ProcessResponse(result_video_key=request.output_video_key,
            processing_ms=round((time.monotonic() - started) * 1000), attempt=request.attempt,
            worker_id=os.environ.get("CLOUD_RUN_EXECUTION", "local"))
    finally:
        try:
            runtime.pipeline.release_predictor()
            runtime.torch.cuda.empty_cache()
        except Exception:
            log.exception("[%s] job_uuid=%s GPU cleanup failed", os.environ.get("MOTIF_REQUEST_ID", ""), job_id)
        shutil.rmtree(work, ignore_errors=True)


def process_challenge(request: ProcessRequest, runtime, *, job_id: str | None = None) -> ProcessResponse:
    started = time.monotonic()
    job_id = _safe_job_id(job_id or request.job_uuid)
    bucket = configured_bucket()
    work = inside_pipeline(PIPELINE_ROOT / "work/motif_compose" / job_id)
    inputs = inside_pipeline(work / "inputs")
    foreground = inputs / "foreground.mp4"
    background = inputs / "background.mp4"
    archive = work / "precomputed.tar.gz"
    normalized = work / "result.mp4"
    work.mkdir(parents=True, exist_ok=True)
    inputs.mkdir(parents=True, exist_ok=True)
    try:
        runtime.s3.download_file(bucket, request.raw_video_key, str(foreground))
        runtime.s3.download_file(bucket, request.base_video_key, str(background))
        if _video_duration(runtime.cv2, foreground) + 0.5 < request.base_duration_ms / 1000:
            raise RawTooShortError("촬영본이 베이스보다 짧습니다.")
        manifest_key = precomputed_manifest_key(request.base_video_key)
        descriptor = _read_json(runtime.s3, bucket, manifest_key)
        required = {"status", "base_video_key", "artifact_key", "artifact_sha256", "package_path",
                    "background_sha256", "implementation", "model_bundle_version", "people"}
        if required - descriptor.keys() or descriptor["status"] != "ready":
            raise InvalidRequestError("완료된 배경 사전계산 manifest가 아닙니다.")
        if descriptor.get("portable_format") != "portable-precomputed-v1":
            raise InvalidRequestError("지원하지 않는 사전계산 형식입니다.")
        if descriptor["base_video_key"] != request.base_video_key:
            raise InvalidRequestError("사전계산 배경 key가 요청과 다릅니다.")
        if descriptor["model_bundle_version"] != os.environ.get("MODEL_BUNDLE_VERSION", "models-v1"):
            raise InvalidRequestError("배경 자산과 모델 bundle 버전이 다릅니다.")
        runtime.s3.download_file(bucket, descriptor["artifact_key"], str(archive))
        if sha256(archive) != descriptor["artifact_sha256"]:
            raise RuntimeError("배경 artifact SHA-256 검증 실패")
        # 디코딩(§3): 안전 추출 → verify_package → XZ 복원 → 경로 rebase → SHA-256 pointer 등록.
        extract_archive(PIPELINE_ROOT, archive)
        source_rel, manifest_rel = import_portable(PIPELINE_ROOT, descriptor["package_path"],
            source_sha256=descriptor["background_sha256"], implementation=descriptor["implementation"],
            library=runtime.prepost.LIBRARY)
        precomputed_source = runtime.ex.local(source_rel, exists=True)
        if runtime.ex.digest(precomputed_source) != descriptor["background_sha256"]:
            raise RuntimeError("사전계산 원본 SHA-256 불일치")
        # 제거 대상 = 배경이 사전계산된(background_frames 보유) 인물. 요청 anchor는 쓰지 않는다.
        asset = runtime.prepost.prepared(precomputed_source)
        removable = {int(pid) for pid, person in ((asset or {}).get("people") or {}).items()
                     if isinstance(person, dict) and "background_frames" in person}
        remove_id, driver_id = _choose_people(descriptor, removable)
        ready = runtime.prepost.prepared(precomputed_source, remove_id=remove_id, driver_id=driver_id)
        if ready is None or ready["manifest"] != manifest_rel:
            raise InvalidRequestError("사전계산 배경 자산을 사용할 수 없습니다.")
        result = runtime.prepost.run_post(precomputed_source, foreground, remove_id=remove_id,
            driver_id=driver_id, correction_enabled=request.correction_enabled, gpu=runtime.gpu)
        if result is None or not str(result.get("background_inference", "")).startswith("disabled:"):
            raise RuntimeError("합성 중 배경 분석 비활성 계약을 위반했습니다.")
        final_video = runtime.ex.local(result["final_video"], exists=True)
        _normalize_output(final_video, background, normalized, request.base_duration_ms)
        try:
            runtime.s3.upload_file(str(normalized), bucket, request.output_video_key,
                                   ExtraArgs={"ContentType": "video/mp4"})
        except Exception as error:
            raise UploadError(f"결과 upload 실패: {error}") from error
        return ProcessResponse(result_video_key=request.output_video_key,
            processing_ms=round((time.monotonic() - started) * 1000), attempt=request.attempt,
            worker_id=os.environ.get("CLOUD_RUN_EXECUTION", "local"))
    finally:
        try:
            runtime.pipeline.release_predictor()
            runtime.torch.cuda.empty_cache()
        except Exception:
            log.exception("[%s] job_uuid=%s GPU cleanup failed", os.environ.get("MOTIF_REQUEST_ID", ""), job_id)
        shutil.rmtree(work, ignore_errors=True)
