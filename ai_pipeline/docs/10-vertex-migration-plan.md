# Vertex AI Custom Job 전환 검토안 (보류된 대안)

**작성: 2026-09-21 · 대상: AI 팀 (인계용)**

## 0. 한 줄 요약

GPU 2-Job(precompute/compose)을 **Cloud Run Job → Vertex AI Custom Job(DWS Flex-start, H100 1장)**으로 옮긴다.
목적: Cloud Run의 **60분 task-timeout 벽**과 **Blackwell(sm_120) 커널 미성숙으로 인한 비정상적 느림**을 동시에 제거.
**Spring(백엔드)은 코드 변경 없음** — intake Service의 HTTP 계약과 Job→백엔드 콜백이 그대로 유지된다.

---

## 1. 배경 / 결정

| 항목 | 내용 |
|---|---|
| 문제 1 | Cloud Run GPU Job은 **task-timeout 최대 3600초**. SVOR 1인 ~46분 + 하류 단계 → 2인이면 초과 확정 |
| 문제 2 | RTX PRO 6000(Blackwell sm_120)은 신형이라 flash-attn/TRT/커널 미성숙 → diffusion이 비정상적으로 느림 |
| 근거 | H200/H100(Hopper sm_90)은 성숙한 스택 → "진작 끝났어야" 함. 실리콘 격차(2~3배)로는 46분 설명 불가 |
| 결정 | **Vertex Custom Job + DWS(Flex-start) + H100 1장(a3-highgpu-1g)** 으로 전환 |
| GPU 타겟 | **Hopper(sm_90) + Blackwell(sm_120)**. L4(sm_89)는 24GB로 full_load OOM → **배제** |
| 이미지 | 단일 universal 이미지(pytorch3d `TORCH_CUDA_ARCH_LIST="9.0;12.0"`) → Cloud Run/Vertex 양쪽 동작 |

Vertex Custom Job은 Cloud Run Job과 동일하게 **호출 시에만 가동**(잡 제출→프로비저닝→완료→해체, 도는 동안만 과금).

---

## 2. 아키텍처 변화

**전(현행)**
```
Spring ─POST 접수URL─▶ intake(Cloud Run Service, CPU)
                          │ run.googleapis.com/v2/{job}:run  (env override)
                          ▼
             Cloud Run Job (GPU, RTX PRO 6000)  ── S3 GET/PUT ── 콜백 /done|/failed ─▶ Spring
```

**후(목표)**
```
Spring ─POST 접수URL─▶ intake(Cloud Run Service, CPU)   ← 그대로
                          │ aiplatform.googleapis.com …/customJobs  (containerSpec env/command)
                          ▼
             Vertex Custom Job (GPU, H100 1장, DWS) ── S3 GET/PUT ── 콜백 /done|/failed ─▶ Spring
```

바뀌는 것은 **intake 내부의 "Job 실행 방식" 한 곳**뿐. 그 앞(Spring↔intake)과 뒤(Job→콜백, S3)는 불변.

---

## 3. Spring(백엔드) 영향 = 없음

계약서(`03-integration-spec.md`) 근거:
- **line 238**: "접수 202를 못 받아도 Job은 진행, 콜백은 정상 처리" → Spring은 202의 `execution` 값에 의존하지 않음.
- **line 61 / 8**: 완료 신호는 **Job→백엔드 콜백(POST /done|/failed)**. 아키텍처 무관하게 유지.
- intake HTTP 계약(POST `/precompute`·`/`, `X-Motif-Worker-Token`, `X-Request-Id`, 202)도 유지.

→ **Spring 코드 0 변경.** 단, **timeout 값만 통지**한다(계약서 line 216: 백엔드가 `timeout+5분`에 실패로 닫음). Cloud Run 3600초 → Vertex 값(예: 5400~7200초)으로 바뀌므로 새 값을 백엔드에 알려줄 것.

---

## 4. 변경 상세

### 4.1 `intake.py` — `_launch_job` (핵심 작업)

현행: Cloud Run `run.googleapis.com/v2/{job}:run` + `overrides.containerOverrides.env`.
변경: Vertex `POST https://us-central1-aiplatform.googleapis.com/v1/projects/{PROJECT}/locations/us-central1/customJobs` 로 CustomJob 생성.

요청 본문 골격(구현 시 필드명 최종 확인 표시 ⚠):
```json
{
  "displayName": "motif-precompute-{request_id}",
  "jobSpec": {
    "workerPoolSpecs": [{
      "replicaCount": 1,
      "machineSpec": {
        "machineType": "a3-highgpu-1g",
        "acceleratorType": "NVIDIA_H100_80GB",
        "acceleratorCount": 1
      },
      "containerSpec": {
        "imageUri": "us-central1-docker.pkg.dev/ssafy-motif/video-workers/motif-compose:v8",
        "command": ["python","-m","run_final_cloud_run_job.precompute_worker"],
        "env": [ { "name": "...", "value": "..." } ]   // 4.2 참조
      }
    }],
    "scheduling": {
      "strategy": "FLEX_START",        // ⚠ DWS. enum 정확명 확인(STANDARD/SPOT/FLEX_START)
      "maxWaitDuration": "1800s",      // ⚠ 큐 대기 상한
      "timeout": "7200s"               // Cloud Run 3600 벽 제거
    },
    "serviceAccount": "<GPU 워커 SA>"  // S3/GCS/Secret 접근용
  }
}
```
- 반환: CustomJob 리소스 이름(`projects/.../customJobs/{id}`)을 202 응답의 `execution`으로 그대로 사용(형식만 바뀜, Spring 미의존).
- `_access_token()`(메타데이터 SA 토큰)은 그대로 재사용. intake SA에 `roles/aiplatform.user` 필요(4.6).

### 4.2 env 주입

| 종류 | 변수 | 주입 방법 |
|---|---|---|
| per-run | `MOTIF_PRECOMPUTE` 또는 `MOTIF_JOB` (payload JSON), `MOTIF_REQUEST_ID` | intake가 요청마다 containerSpec.env에 주입 (현행 Cloud Run override와 동일 값) |
| 정적/시크릿 | `MOTIF_CALLBACK_BASE`, `MOTIF_WORKER_TOKEN`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION`, `S3_BUCKET` | 현행은 **Cloud Run Job 정의**에 Secret Manager로 세팅됨. Vertex Custom Job은 매 실행 생성이라 **잡 정의에 상주하는 시크릿 개념이 없다** → 아래 둘 중 택1 |

시크릿 처리(택1, ⚠ 결정 필요):
- **(A) 컨테이너가 런타임에 Secret Manager에서 직접 읽기** — 워커 SA에 `roles/secretmanager.secretAccessor`. 잡 스펙에 평문 노출 없음(권장).
- **(B) intake가 env로 전달** — 구현 단순하나 AWS 키가 CustomJob 스펙에 평문 저장(API로 조회 가능) → 보안상 비권장.

### 4.3 command (kind별)

Cloud Run에선 잡 정의에 command가 박혀 있었다:
- precompute: `["python","-m","run_final_cloud_run_job.precompute_worker"]`
- compose: 기본 CMD `["python","-m","run_final_cloud_run_job.worker"]`

Vertex는 잡을 매번 생성하므로 **intake가 kind에 따라 containerSpec.command를 설정**한다.

### 4.4 가중치 로딩 (~17.7GB)

현행: Cloud Run이 GCS 버킷을 **gcsfuse로 `/mnt/model-weights`에 마운트**, `link_weight_mounts.sh`가 심링크.
- Vertex Custom Job은 **모든 GCS 버킷을 `/gcs/<bucket>` 로 자동 Cloud Storage FUSE 마운트**한다(⚠ 리전/권한 확인). → `gs://ssafy-motif-models` = `/gcs/ssafy-motif-models`.
- `link_weight_mounts.sh`는 mount 경로를 **인자(arg 2)로 받으므로 값만 교체** 가능(빌드 시 `/gcs/ssafy-motif-models` 로). 심링크는 런타임에 resolve되므로 빌드 시 경로 부재는 문제없음.

**콜드 로딩 최적화(2단계)**: `/gcs`(네트워크 FUSE, 캐시 없음)에서 매번 17.7GB를 읽으면 여전히 느리다. H100 노드의 **로컬 SSD로 1회 복사 후 로컬에서 로드**하면 순차 벌크 읽기(랜덤 FUSE보다 빠름)로 단축된다.

**구현됨: `docker/stage_weights.sh`(런타임 엔트리포인트 래퍼).** universal 이미지의 심링크는 빌드 시 한 경로로만 박히므로, 이 래퍼가 런타임에 실제 마운트로 심링크를 재구성한다(`link_weight_mounts.sh` 재사용). env 로 제어:
- `WEIGHTS_MOUNT` 미설정 → 아무것도 안 하고 워커 그대로 exec (**Cloud Run 무영향**, 빌드 시 심링크 사용)
- `WEIGHTS_MOUNT=/gcs/ssafy-motif-models` → 그 경로로 재링크 (**Vertex 1단계**, /gcs 직접)
- `+ STAGE_WEIGHTS_DST=<로컬SSD>` → weight_mounts.txt 목록만 로컬 SSD로 복사 후 재링크 (**Vertex 2단계**)

컨테이너 command: `bash docker/stage_weights.sh python -m <워커모듈>` (intake_vertex.py 의 `_command()` 가 생성). Cloud Run(v8 검증)은 래퍼 없이 `python -m <워커>` 그대로 → 무영향.

### 4.5 worker의 Cloud Run 의존부 → 거의 무변경

| 위치 | 현행 | Vertex에서 |
|---|---|---|
| `worker.py:22` `validate_single_task()` | `CLOUD_RUN_TASK_COUNT`==1, `CLOUD_RUN_TASK_INDEX`==0 | env 없으면 **기본값 "1"/"0"으로 자동 통과** → 손 안 대도 됨 |
| `service.py:143`, `worker.py:48/66` `worker_id` | `CLOUD_RUN_EXECUTION`(없으면 "local") | optional(계약서 line 120 "없어도 처리"). 원하면 Vertex 잡 id(`CLOUD_ML_JOB_ID` 등)로 매핑 |

콜백/S3/합성 로직은 **전부 그대로 재사용**된다.

### 4.6 서비스 계정 권한

- intake SA: `roles/aiplatform.user` (CustomJob 생성).
- 워커 SA(Custom Job이 사용): GCS 읽기(가중치), S3 접근(AWS 키), 콜백 발신, (택A 시)`secretmanager.secretAccessor`.

### 4.7 timeout / 백엔드 통지

- Vertex `scheduling.timeout` = 처리 시간 + 여유(초기값 예: 7200s). Cloud Run 3600 벽 없음.
- **백엔드에 새 값 통지 필수**(실패 타이머 = timeout+5분).

---

## 5. 이미지 (v8, universal)

- pytorch3d `TORCH_CUDA_ARCH_LIST="9.0;12.0"`(Hopper+Blackwell) — L4(8.9) 제외.
  - 수정: `docker/install_model_envs.sh`, `cloudbuild.pytorch3d.yaml` (완료)
  - wheel 재빌드 → `gs://ssafy-motif-models-us/wheels/` → 로컬 `docker/wheels/` 로 내려받아 v8 빌드에 사용.
- 나머지(CUDA 12.8, torch cu128, SAM2 CUDA off)는 Hopper/Blackwell 공통 → 변경 불필요.
- **현재 배포 상태**: precompute=v7, compose=**v4(구형)**. v8로 양쪽 통일 필요.

---

## 6. 쿼터 상태

- Vertex API(`aiplatform.googleapis.com`) 활성화 완료.
- **Preemptible H100(DWS/Spot용) 쿼터 증액 요청 제출**: `motif_h100_preempt_us-central1`, preferred=1, granted=0(승인 대기), region us-central1.
- 잘못 넣었던 standard H100=8은 0으로 철회.
- 확인: `gcloud quotas preferences list --project=ssafy-motif | grep -A4 motif_h100_preempt` → grantedValue 0→1 되면 승인.
- ⚠ DWS(Flex-start)가 preemptible 쿼터를 쓰는지 standard를 쓰는지는 실제 잡 제출 시 최종 확인(안 맞으면 standard 쪽도 요청).

---

## 7. 실행 순서 (체크리스트)

- [x] pytorch3d arch → `9.0;12.0` 수정 (install_model_envs.sh, cloudbuild.pytorch3d.yaml)
- [x] pytorch3d wheel 재빌드 → `gs://ssafy-motif-models-us/wheels/` → 로컬 `docker/wheels/` 다운로드 완료
- [x] intake.py Vertex 버전 초안 작성 → `intake_vertex.py` (py_compile OK)
- [x] 가중치 스테이징 래퍼 작성 → `docker/stage_weights.sh` (§4.4, Cloud Run 무영향 opt-in)
- [~] **v8 빌드**(universal) — 1차 실패(onnxruntime-gpu 1.20.1 부재) → **1.20.2 로 수정 후 재빌드 진행 중**
- [ ] v8 성공 확인 → Cloud Run에서 P1·ffmpeg·SVOR 로깅 검증(precompute 1회)
- [ ] 워커/intake SA 권한(4.6): intake=`aiplatform.user`, 워커=GCS/S3/(택A시)secretAccessor
- [x] H100 쿼터 승인 완료(`motif_h100_preempt_us-central1` granted=1; standard도 granted=8 잔존/폴백)
- [ ] Vertex end-to-end 1회 검증(v8 성공 + intake_vertex 배포 후)
- [ ] DWS `scheduling.strategy` enum + 소비 쿼터(preempt/standard) 실제 제출로 확인
- [ ] `/gcs` 마운트 경로·권한 + 로컬 SSD 경로/용량 실측 → stage_weights env 확정
- [ ] timeout 확정 → **백엔드에 통지**
- [ ] compose 이미지 v4→v8 갱신 + P1 검증 + 2인 배경 검증

---

## 8. 검증 계획

1. **v8 (Cloud Run)**: precompute 1회 실행 → ① SVOR 로딩/추론 분해(`[SVOR 로딩]`·`SVOR chunk completed`), ② ffmpeg BrokenPipe 진짜 사인, ③ P1(DWPose GPU) 동작.
2. **Vertex (H100 승인 후)**: 동일 payload로 precompute → SVOR 실측 시간(H100에서 얼마나 단축되는지), timeout 여유 확인 → compose까지 end-to-end → 콜백 수신 → S3 결과 확인.
3. **2인 배경**: SVOR×2가 새 timeout 안에 드는지(H100 기준) 확인.

---

## 9. 리스크 / 구현 시 확인 (⚠)

| 항목 | 확인 필요 |
|---|---|
| DWS 필드 | `scheduling.strategy` enum 정확명 + `maxWaitDuration` + DWS가 쓰는 쿼터(preemptible vs standard) |
| `/gcs` 마운트 | Vertex Custom Job의 GCS FUSE 자동 마운트 경로·리전·권한(버킷 `ssafy-motif-models`는 `us` 멀티리전 여부) |
| 시크릿 처리 | 4.2 (A) Secret Manager 런타임 읽기 vs (B) env 전달 — 보안상 (A) 권장 |
| ffmpeg 버그 | v8에서 드러날 BrokenPipe 원인(메모리-backed FS 소진 추정) — Vertex는 실디스크라 해소될 수 있으나 `encode_frames` 수정으로 원인은 어차피 표출됨 |
| 콜드 로딩 | `/gcs` 직접 로드가 여전히 느리면 로컬 SSD 복사(4.4 2단계) 필수 |
| compose 이미지 | 현재 v4 → v8로 갱신 필요(P1 포함) |
