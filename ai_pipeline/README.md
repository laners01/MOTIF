# run_final_v2 Cloud Run

`docs/03-integration-spec.md` v0.3을 기준으로 한 세 실행 모드입니다.

- CPU 접수 서비스: `Dockerfile.intake` (`python -m run_final_cloud_run_job.intake`)
- GPU 합성 Job: `python -m run_final_cloud_run_job.worker` (Docker 기본 CMD)
- GPU 배경 사전계산 Job: `python -m run_final_cloud_run_job.precompute_worker`

## 처리 흐름

사전계산 Job은 `MOTIF_PRECOMPUTE={"job_uuid":"...","attempt":1,"base_video_key":"...","source_video_key":"..."}`를 받아 `S3_BUCKET`에서 영상을 읽습니다. `source_video_key`는 분석용 원본이 `base_video_key`와 다를 때만 넣으며 생략하면 base와 같습니다. 분석 artifact를 먼저 올린 뒤 `base_video_key`의 SHA-256으로 결정되는 `precomputed/{key-hash}/ready.json`을 마지막에 기록합니다.

합성 요청에는 새 필드를 요구하지 않습니다. 접수 서비스는 백엔드 JSON을 검증하고 그대로 `MOTIF_JOB`에 넣어 합성 Job을 시작합니다. 합성 Job은 `base_video_key`로 같은 READY key를 계산하며 배경 분석/인페인팅은 재실행하지 않습니다.

## 공통 환경변수

`MOTIF_CALLBACK_BASE`, `MOTIF_WORKER_TOKEN`, AWS 자격증명, `AWS_REGION=ap-northeast-2`, `S3_BUCKET=motif-media`, 선택적으로 `PRECOMPUTED_PREFIX=precomputed`, `MODEL_BUNDLE_VERSION=models-v1`을 설정합니다. 접수 서비스에는 추가로 `COMPOSE_JOB_RESOURCE=projects/{project}/locations/{region}/jobs/{job}`가 필요합니다.

접수 서비스 계정에는 `run.jobs.runWithOverrides`, Job 계정에는 기존 입력 GET/결과 PUT 외에 `precomputed/*` GET/PUT 권한이 필요합니다. 모델 가중치 버킷은 `/mnt/model-weights`에 마운트하며 `docker/weight_mounts.txt`의 심링크를 사용합니다.

## Cloud Run 설정

두 Job 모두 `--tasks=1 --max-retries=0`으로 배포합니다. Compose timeout은 백엔드에 전달한 값과 같아야 합니다. 같은 이미지를 사용하고 command만 실행 모드별로 override할 수 있습니다.

```text
compose:    python -m run_final_cloud_run_job.worker
precompute: python -m run_final_cloud_run_job.precompute_worker
intake:     python -m run_final_cloud_run_job.intake
```

## CPU 검증

```bash
PYTHONDONTWRITEBYTECODE=1 /home/j-j15d104/.conda/envs/video-layer/bin/python \
  -m unittest run_final_v2_cloud_run_job_compliant.test_server \
              run_final_v2_cloud_run_job_compliant.test_precomputed_jobs
```

GPU 추론, Docker build/push, 네트워크 호출은 이 검증에 포함하지 않습니다. 현재 기능 범위와 제약은 `docs/05-integration-compliance.md`를 확인하십시오.
