# Cloud Run 배포 구성 노트 (2026-09-20)

`cloud_run_job_true_final` (MOTIF 연동 v0.3 대응, 분석/합성 Job 분리 버전)의 빌드·배포
준비 기록. [03-integration-spec.md](03-integration-spec.md)가 백엔드와의 계약이며 이 문서는
그 위에서의 인프라·구현 결정을 정리한다.

## 0. 전체 구성

```
배경(edited/·assets/) 업로드 ─▶ [intake] POST /precompute ─▶ 분석 Job (GPU)
                                                              └ precomputed/{digest}/ready.json + artifact.tar.gz → S3
합성 요청 ─ 백엔드 ─▶ [intake] POST / ─▶ 합성 Job (GPU)
                                          └ ready.json 로드 → 빠른 합성 → results/…mp4 → S3 → /done|/failed 콜백
```

- **이미지 2개**: GPU 이미지(합성·분석 **공용**) + 접수(intake) 이미지(CPU).
- **Job 2개**: 합성 / 분석 — **같은 GPU 이미지**를 command override로 구분.
  - 합성: `python -m run_final_cloud_run_job.worker` (Dockerfile 기본 CMD)
  - 분석: `--command` 로 `python -m run_final_cloud_run_job.precompute_worker`
- **Service 1개**: intake (합성·분석 접수 모두).

프로젝트 `ssafy-motif` · 리전 `asia-southeast1` · GPU `nvidia-rtx-pro-6000`
· 가중치 GCS `ssafy-motif-models` · S3 `motif-media`(ap-northeast-2).

## 1. 이 세션에서 변경한 것 (코드)

| 파일 | 변경 | 이유 |
|---|---|---|
| `docker/install_model_envs.sh` | pytorch3d 빌드에 `MAX_JOBS="${PYTORCH3D_MAX_JOBS:-2}"` | sm_120 소스 컴파일이 Cloud Build 워커 OOM(INTERNAL_ERROR) → 병렬 제한 |
| `Dockerfile` | venv 설치 RUN에 `PYTORCH3D_MAX_JOBS=4` | 32GB 빌드 머신에서 컴파일 가속(OOM 안전 범위) |
| `.dockerignore` | `licenses`·`secrets` 추가 | 라이선스 모델·비밀이 이미지에 굽히는 것 방지 |
| `.gcloudignore` (신규) | 가중치·잔재·비밀 제외 | Cloud Build 소스 업로드 위생 |
| `intake.py` | `/precompute` 경로 분기 + `_launch_job` 공통화 | 배경 분석 요청도 접수(아래 §4) |
| `Dockerfile.intake` | `precompute_schemas.py` COPY 추가 | intake가 `PrecomputeRequest` import |
| `cloudbuild.intake.yaml` (신규) | intake 이미지 빌드 config | `--tag`은 기본 Dockerfile만 봄 |

## 2. 빌드 (Blackwell)

- 베이스 `nvidia/cuda:12.8.1-cudnn-devel`, torch `cu128/2.7.0`, pytorch3d **sm_120 소스 빌드**
  (프리빌트 wheel 없음). 이 부분이 빌드 시간의 대부분(모델 가중치는 이미지에 없음).
- **Cloud Build는 global 풀 + `e2-highcpu-32`** 로 제출한다. asia-southeast1 regional은 그 머신
  타입 quota가 0이라 `FAILED_PRECONDITION`. 8GB 머신은 pytorch3d 컴파일 중 OOM.

```bash
# GPU 이미지 (합성·분석 공용)
gcloud builds submit <소스경로> --project=ssafy-motif \
  --tag=asia-southeast1-docker.pkg.dev/ssafy-motif/video-workers/motif-compose:v1 \
  --machine-type=e2-highcpu-32 --timeout=7200

# 접수 이미지 (CPU, 순수 Python)
gcloud builds submit <소스경로> --project=ssafy-motif \
  --config=cloudbuild.intake.yaml
```

- arm64 Mac에서 로컬 도커 빌드는 비현실적(amd64 CUDA + 컴파일) → Cloud Build 사용.

## 3. 배포

### 합성 Job
```
--gpu=1 --gpu-type=nvidia-rtx-pro-6000 --no-gpu-zonal-redundancy   # GPU Job은 zonal redundancy 미지원
--cpu=20 --memory=80Gi                                             # RTX PRO 6000 최소 요구
--tasks=1 --parallelism=1 --max-retries=0 --task-timeout=<실측 후 확정>
--set-env-vars=MOTIF_CALLBACK_BASE=https://j15d104.p.ssafy.io,AWS_REGION=ap-northeast-2,S3_BUCKET=motif-media,PRECOMPUTED_PREFIX=precomputed
--set-secrets=MOTIF_WORKER_TOKEN=…,AWS_ACCESS_KEY_ID=…,AWS_SECRET_ACCESS_KEY=…
# 볼륨: ephemeral work(6Gi)/output(3Gi)/.cache(1Gi) + cloud-storage ssafy-motif-models(readonly, mount-options=implicit-dirs)→/mnt/model-weights
```
- ephemeral-disk·cloud-storage 볼륨 update는 `gcloud beta run jobs update` 필요.

### 분석 Job
합성 Job과 **동일 스펙 + 동일 볼륨**, 단 `--command=python,-m,run_final_cloud_run_job.precompute_worker`.
`PRECOMPUTED_PREFIX`는 합성과 **같은 값**이어야 키가 맞물린다.

### 접수 Service (intake)
```
이미지=motif-intake:v1 (CPU)
--set-secrets=MOTIF_WORKER_TOKEN=…
--set-env-vars=COMPOSE_JOB_RESOURCE=projects/ssafy-motif/locations/us-central1/jobs/<합성Job>,PRECOMPUTE_JOB_RESOURCE=projects/ssafy-motif/locations/us-central1/jobs/<분석Job>
```
- ⚠️ **JOB_RESOURCE의 리전은 Job이 실제 있는 곳(`us-central1`)이다. intake Service 자신의 배포 리전(`asia-southeast1`)과 다르다** — 여기를 Service 리전으로 잘못 넣으면 `jobs:run`이 404 → intake가 503을 뱉는다(2026-09-22 장애 원인). GPU Job은 us-central1, intake는 asia-southeast1로 분리 운영.
- intake SA에 **두 Job 모두 `run.jobs.run` 실행 권한**(`roles/run.developer` 등).
- 배포 후 나오는 Service URL을 백엔드에 `AI_SUBMIT_URL`로 전달(합성 접수용). 분석 접수는 `AI_SUBMIT_URL + /precompute`.

### 공통 권한
- 합성·분석 Job SA → `ssafy-motif-models` `roles/storage.objectViewer` (가중치 마운트).
- S3 접근은 env의 `AWS_*` 키(백엔드 `motif-gpu-worker`).

## 4. 배경 분석 트리거 (옵션 B — intake push 채택)

`intake.py`에 `POST /precompute` 추가. 배경 영상을 올리는 주체가 이 엔드포인트를 호출한다.
```
POST {AI_SUBMIT_URL}/precompute
X-Motif-Worker-Token: …
X-Request-Id: …
{ "job_uuid":"…", "attempt":1, "base_video_key":"edited/…/x.mp4", "source_video_key": null }
  → 분석 Job :run → 202 {"execution":"…"}
```
- **호출 주체 미확정** (배포 전 결정 필요): AI/운영이 배경을 올리면 그 스크립트가 호출(백엔드 무관);
  백엔드가 배경을 만들면 백엔드가 배경 확정 후 1회 호출.
- 합성은 해당 base의 `ready.json`이 **이미 있다고 전제**한다. 없으면 합성이 `INPUT_NOT_FOUND`로
  실패하므로, 합성 요청 전에 분석이 끝나 있어야 한다.
- (선택) 폴링(Cloud Scheduler로 미분석 배경 스캔)을 백스톱으로 둘 수 있으나 현재는 미구현.
- (선택) 같은 배경 중복 `/precompute` 시 분석이 재실행되어 덮어씀 → 필요 시 분석 Job에
  "ready.json 있으면 skip" 가드 추가.

## 5. 백엔드에 넘길 항목

### (필수) S3 IAM 권한 추가
분석 아티팩트를 `motif-media`의 `precomputed/*`(env `PRECOMPUTED_PREFIX`, 기본 `precomputed`)에
저장한다. 명세 §8 IAM(`motif-gpu-worker`)은 `PUT results/*`만 허용하므로:
- **`precomputed/*` 에 대한 `GetObject` + `PutObject` 추가** 필요 (없으면 분석 PUT·합성 GET 모두 실패).

### 공통 질문 회신 (코드 근거)
| 질문 | 답 |
|---|---|
| 접수 서비스 URL | intake Service URL (배포 후 확정) → `AI_SUBMIT_URL` |
| `--max-retries 0` | 예, 적용 |
| `--task-timeout` | 첫 성공 execution 실측 후 확정·통보 |
| 콜백 경로 | `/api/worker/v1/composite-jobs/{job_uuid}/done` · `/failed` (`/complete` 아님) |
| 실패 코드 | 9종: RAW_TOO_SHORT, RAW_UNREADABLE, UPLOAD_FAILED, INPUT_NOT_FOUND, MULTIPLE_PERSONS, PERSON_NOT_DETECTED, GPU_ERROR, TIMEOUT, INTERNAL_ERROR. **FACE_NOT_DETECTED는 구분 안 함**(PERSON_NOT_DETECTED로 통합) |
| 편집본(슬롯) | 별개 트랙. 합성 Job은 `base_video_key`를 그대로 받아 처리 |

## 6. 미해결 / TODO

- [x] **GPU 이미지 빌드 성공** — `motif-compose:v1` push 완료(2026-09-20, 39m55s,
      MAX_JOBS=4 + e2-highcpu-32 global). pytorch3d sm_120 소스 빌드 통과.
- [x] **intake 이미지 빌드 성공** — `motif-intake:v1` push 완료(2026-09-20, 39s).
- [x] **Secret Manager 등록** — `motif-worker-token`·`aws-access-key-id`·`aws-secret-access-key` + 기본 compute SA에 `secretAccessor`.
- [x] **SA 권한** — 기본 compute SA(`1025574392259-compute@`)에 버킷 `objectViewer` + 프로젝트 `roles/run.developer`(intake가 Job runWithOverrides).
- [x] **Job 2개 배포 + 볼륨** — `motif-compose-job`(합성), `motif-precompute-job`(분석, `--command python -m …precompute_worker`). GPU RTX PRO 6000, `--no-gpu-zonal-redundancy`, 20CPU/80Gi, tasks=1, max-retries=0, task-timeout=45m(잠정). ephemeral 3 + 가중치 버킷 마운트.
- [x] **intake Service 배포** — `motif-intake`, allUsers invoker(토큰이 실제 인증), env `COMPOSE_JOB_RESOURCE`·`PRECOMPUTE_JOB_RESOURCE`. 잘못된 토큰 → 401 확인.
      **URL = `https://motif-intake-1025574392259.asia-southeast1.run.app`** (합성 `POST /`, 분석 `POST /precompute`) → 백엔드에 `AI_SUBMIT_URL`로 전달.
- [ ] **Blackwell 런타임 검증** — 빌드·배포 성공 ≠ 실행 성공. 실제 execute해서 sm_120에서 torch/pytorch3d가 도는지 확인 필요(실 S3 영상 + 토큰 필요).
- [ ] 백엔드: `precomputed/*` IAM 권한(§5) — 없으면 분석 PUT·합성 GET 실패.
- [ ] 배경 `/precompute` 호출 주체 확정(§4) — 추후 마련 예정.
- [ ] `--task-timeout` 실측 후 45m 조정 + 백엔드 통보.
- 가중치 버킷 재적재 불필요(`ssafy-motif-models`에 34.6GB 적재 완료, 2026-09-19/20).
