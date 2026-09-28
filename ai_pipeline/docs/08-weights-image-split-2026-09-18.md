# 개발 인계 — 모델 가중치 이미지 분리 설계 (2026-09-18)

작성 시각: 2026-09-18. 이어서 진행하는 사람이 이 문서만 보고 바로 시작할 수 있도록 정리한다.

## 목표 한 줄

Cloud Run Job 이미지를 Artifact Registry에 올릴 때, **모델 가중치가 이미지에 구워지지 않도록** 분리한다. 이미지에는 코드와 격리 venv만 남기고, 가중치는 **단일 GCS 버킷을 하위 프리픽스별로 가중치 경로에 마운트**한다.

## 지금까지 끝난 것

- **치명 2건 수정 완료** (커밋 `797df07`에 포함, 이미 origin/master에 push됨):
  1. SIGTERM 핸들러 추가 — [worker.py](../worker.py) `main()`. Cloud Run이 타임아웃·취소·프리엠션으로 SIGTERM을 보내면 `mark_failed`를 즉시 보내 Spring 잡이 RUNNING으로 방치되지 않게 한다. 콜백 로직은 `_report_termination`으로 분리해 테스트로 검증([test_server.py](../test_server.py) `test_sigterm_reports_failure_to_spring`).
  2. `mark_complete` 실패 시 출력 S3 위치를 담은 CRITICAL 로그 후 재던짐 — 결과물은 S3에 있는데 잡만 실패로 남는 상황을 조정 가능하게 만든다. 기존 계약(실패로 뒤집지 않음)은 유지.
- `docker/download_models.py`의 gdown `remaining_ok=True` 제거됨(빌드 fail-loud). **주의: GVHMR Drive 폴더 파일 수가 50개를 넘으면 이 변경으로 빌드가 실패한다. 이어서 진행 전 폴더 파일 수를 확인할 것.**
- 격리 venv 4벌 + SMPL-X build-context 방식은 이미 반영됨([Dockerfile](../Dockerfile), [docker/install_model_envs.sh](../docker/install_model_envs.sh)).

## 현재 문제 (왜 작업이 필요한가)

[Dockerfile](../Dockerfile) `RUN python docker/download_models.py` 단계가 SVOR(Wan2.1-VACE-1.3B, T5-xxl 인코더 등), RelightVid, GVHMR, SAM2, Video-Depth-Anything, DWPose, RF-DETR, SMPL-X **가중치 전부를 이미지 레이어에 다운로드**한다. 격리 venv 4벌(torch 포함)까지 더해 최종 이미지가 **40~60GB급**. Cloud Run Job은 웜풀이 없어 매 execution마다 이미지를 통째로 풀링하므로 저장·이그레스 비용과 콜드 스타트가 모두 크다.

[runtime.py](../runtime.py)는 `HF_HUB_OFFLINE=1`/`TRANSFORMERS_OFFLINE=1`로 두고 `vendor/...`·`models/...` 로컬 경로만 읽는다 → **가중치가 그 경로에 존재해야만 동작**. 마운트로 그 경로를 채우면 오프라인 설정은 그대로 둬도 된다.

## 결정된 설계: 단일 버킷 + 개별 마운트 지점

- **버킷은 1개**(`gs://PROJECT-model-weights/` 등). 스토리지 요금은 쪼개도 동일하고, 버킷 1개가 IAM·라이프사이클·이그레스 회계가 단순하다.
- **마운트 지점은 가중치 디렉터리별로 나눈다.** 가중치가 `vendor/` 아래 코드와 섞여 있어(sam2 패키지, GVHMR 코드 등) `vendor/`에 볼륨 하나를 통째로 덮으면 이미지에 구운 코드가 가려진다. 그래서 순수 가중치 하위 경로에만 마운트한다.
- **콜드 스타트는 두 방식 차이 없음**: gcsfuse는 마운트 시 다운로드하지 않고 `open()` 때 지연 스트리밍한다. 실제 읽는 바이트가 같으니 속도 동일. 마운트 개수만큼의 초기 setup은 무시 가능.

버킷 안 레이아웃은 현재 상대경로 그대로 둔다(예: `vendor/svor/models/...`).

## 마운트 대상 경로 (가중치가 사는 디렉터리)

`docker/download_models.py`의 `REQUIRED`에서 추출. 코드/작은 config와 섞이는 경로는 ⚠️ 표시.

- `models/` — `rfdetr/rf-detr-small.pth`, `sam2.1_hiera_large.pt`. ⚠️ `models/registry.json`은 코드이므로 통짜 마운트 시 가려짐 주의.
- `vendor/svor/models/`
- `vendor/RelightVid/models/`
- `vendor/GVHMR/inputs/checkpoints/`
- `vendor/GVHMR/hmr4d/utils/body_model/` — ⚠️ support `.pt`(smplx2smpl_sparse 등)와 코드가 같은 디렉터리. 파일 단위 배치 주의.
- `vendor/MimicMotion/models/DWPose/`
- `vendor/Video-Depth-Anything/checkpoints/`

## 확정된 마운트 방식 (only-dir 대신 단일 마운트 + 심링크)

only-dir 다중 마운트는 채택하지 않았다. 코드 혼재 2곳(`models/`의 registry.json, `vendor/GVHMR/hmr4d/utils/body_model/`의 .py 10개)과 bare 파일 `models/sam2.1_hiera_large.pt`는 디렉터리 마운트로는 코드를 가리거나 마운트 자체가 불가라 **어느 설계든 심링크가 필수**다. 그래서 버킷을 `/mnt/model-weights` 한 지점에 통째 마운트하고, 빌드 시 코드가 기대하는 경로를 그 마운트로 심링크한다. 볼륨 1쌍으로 끝나고 only-dir 지원 여부에 의존하지 않는다.

- 마운트/업로드 경로의 단일 출처: [docker/weight_mounts.txt](../docker/weight_mounts.txt) (끝 `/` = 디렉터리 심링크, 없으면 파일 심링크).
- 빌드 심링크: [docker/link_weight_mounts.sh](../docker/link_weight_mounts.sh) — [Dockerfile](../Dockerfile)에서 호출.
- 버킷 적재: [docker/upload_weights_to_gcs.sh](../docker/upload_weights_to_gcs.sh) (SMPL-X 포함, 1회).
- 드리프트 방지 체크: [docker/test_weight_mounts.py](../docker/test_weight_mounts.py) — weight_mounts.txt ↔ download_models.REQUIRED 양방향 커버리지.
- 마운트 옵션 `implicit-dirs` 필수 — rsync 업로드에는 디렉터리 placeholder가 없어, 이 옵션이 없으면 diffusers/transformers의 디렉터리 로딩이 빈 목록으로 실패한다.

## TODO 체크리스트

1. ✅ **only-dir 검증 → 불필요로 결론.** 단일 마운트 + 심링크 채택(위 섹션).
2. ✅ **가중치 GCS 1회 적재 스크립트** — `upload_weights_to_gcs.sh`.
3. ✅ **Dockerfile 수정** — 다운로드/`--check-only` `RUN` 제거, `link_weight_mounts.sh` 호출 추가, venv import 검사만 유지.
4. ✅ **runtime.py 확인** — 코드 변경 불필요. `load_runtime`의 missing 검사가 dangling 심링크(마운트 미적재)를 그대로 fail-loud로 잡는다. `HF_HUB_OFFLINE` 유지.
5. ✅ **배포 커맨드/문서** — [README.md](../README.md)에 버킷 생성·적재·IAM·`--add-volume` cloud-storage 추가, [docs/02-operations-requirements.md](02-operations-requirements.md) 최초 준비·검증 순서·장애 대응 갱신.
6. ⬜ **IAM 실제 부여** — `video-worker` SA에 가중치 버킷 `roles/storage.objectViewer` (명령은 README에 기재). 실제 프로젝트에서 실행 필요.
7. ⬜ **검증** — 짧은 영상으로 수동 execution, 마운트 경로에서 가중치 읽히는지 + 콜드 스타트 시간 측정. T5-xxl(~11GB) 콜드 스타트가 문제면 gcsfuse `file-cache`를 그 파일에만 켜는 것 검토(ephemeral disk 소비 트레이드오프).

## 남은 주의 (이번 작업 범위 밖이지만 알아둘 것)

- **Spring-side 백스톱** — 치명 2건의 최종 방어선. SIGTERM 핸들러는 best-effort(장시간 torch C 호출 중이면 유예 시간 내 콜백 못 보낼 수 있음). Spring에 lease TTL 재조정 + `/complete` 멱등 처리가 있어야 완전하다. [docs/02-operations-requirements.md](02-operations-requirements.md) "Spring 큐 운용 계약"에 이미 멱등/lease 항목이 있으니 그 구현을 확인.
- **gdown remaining_ok** — 위 "끝난 것" 참고. 빌드 전 GVHMR Drive 폴더 파일 수 확인.

## 참고 파일

[Dockerfile](../Dockerfile) · [docker/download_models.py](../docker/download_models.py) · [runtime.py](../runtime.py) · [worker.py](../worker.py) · [README.md](../README.md) · [docs/02-operations-requirements.md](02-operations-requirements.md)

## run_final_v2 이식 상태

원본 `docker/` 배포 계약과 `weight_mounts.txt`를 복원했다. 해당 목록의 가중치는 제외하고 vendor 실행 소스만 포함했으며, 모델 파일은 Cloud Run execution에서 `/mnt/model-weights` GCS 볼륨으로 제공한다.
