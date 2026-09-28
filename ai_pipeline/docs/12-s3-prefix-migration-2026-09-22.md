# S3 경로 이관(private/) 영향 분석과 대응

**출처**: `docs/11-backend-request-s3-prefix.md` (백엔드 → AI팀). 편집본(base) S3 경로를 `edited/{seq}/{uuid}.mp4` → **`private/edited/{seq}/{uuid}.mp4`** 로 이동(CloudFront 서명이 `private/*`에만 걸려서). 접수 JSON 필드·타입·콜백 규약 불변, `base_video_key` 값 문자열만 변경. `output_video_key`는 `results/…` 그대로.

## 결론 — 우리(워커) 코드 변경 **없음**
- 경로는 전부 요청 JSON에서 오는 불투명 문자열. 코드에 `edited/` 하드코딩 없음(테스트/문서 예시뿐).
- `schemas.py` 검증 통과: `private/edited/…mp4`는 상대경로(`/`시작·`..` 없음) + `.mp4`.
- 사전계산 폴더 = `sha256(base_video_key)` → **경로-무관**(어떤 문자열이든 동작).
- `participant_mask` 단일 비트화(1/2/4/8) + `slots[]` 0~1개 = 기존 검증이 이미 처리하는 부분집합(`_selected_slot`은 "슬롯 2개+"만 거부).

## 두 잡이 실제 건드리는 S3 (코드 조사 기준)
precompute = `precompute_service.py`, compose = `service.py`. 클라이언트 `boto3.client("s3")`가 env(`AWS_REGION=ap-northeast-2`, `AWS_ACCESS_KEY_ID/SECRET`=Secret Manager)로 자동 구성. 버킷 `motif-media`(`S3_BUCKET`), prefix `precomputed`(`PRECOMPUTED_PREFIX`).

| 프리픽스 | GetObject | PutObject | 쓰는 곳 | 상태 |
|---|---|---|---|---|
| `private/edited/*` | ✓ | | compose base + **precompute base** | **추가 완료 ✓ (2026-09-22)** |
| `precomputed/*` | ✓ | ✓ | precompute가 씀, compose가 읽음 | 우리 테스트로 get/put 실증 ✓ |
| `results/*` | | ✓ | compose 결과 출력(운영) | 원 명세 허용, 우리 실측은 안 함 |
| `raw/*` | ✓ | | compose `raw_video_key` | 원 명세 허용, 우리 실측은 안 함 |
| source_video_key 프리픽스 | ✓ | | precompute(source 있을 때) | ⚠ 아래 열린 질문 |

*ListBucket 불필요*(코드가 list 안 함, 전부 직접 GetObject/PutObject by key. 없는 키는 404→INPUT_NOT_FOUND).

## 남은 확인 항목
1. **⚠ 열린 질문 — precompute `source_video_key` 프리픽스.**
   precompute는 `source_video_key or base_video_key`를 다운로드. **precompute 호출 주체 = 백엔드 API 서버**(확정). API 서버가 source_video_key에 넣는 값:
   - `null` → base(`private/edited/*`) 사용 → 이미 커버.
   - 편집 전 원본(예: `challenges/*`) → **그 프리픽스 GetObject 필요**(원 명세에 `GET challenges/*` 있음 → challenges면 커버, 다른 프리픽스면 추가).
   → **API 서버가 보내는 source_video_key 실제 값 1개 확인 필요.**
2. `precomputed/*` GET+PUT 운영 크레덴셜 존재(테스트로 실증됨, 운영 동일 키인지만 확인).
3. `results/*` PUT / `raw/*` GET — 코드상 필수. 원 명세에 있음. 불안하면 probe 1회.

## 해시 정체성 주의 (재-precompute)
사전계산 폴더 = `sha256(base_video_key)`. 접두사가 바뀌면 해시가 달라져 폴더가 달라짐:
- 옛 `edited/…`로 precompute한 배경이 있으면 compose가 `sha256("private/edited/…")`를 봐서 **못 찾음**.
- precompute 호출 미가동 상태라 기존 인벤토리 없을 가능성 큼 → **앞으로 새 키(`private/edited/*`)로만 precompute하면 일관.** precompute·compose가 **동일 base_video_key**를 받는 것이 전제(둘 다 백엔드 API 서버가 발급).

## base_duration_ms
이미 필드 존재(검증 `>0`). 백엔드가 편집본 길이(ms, 챌린지 원본 ±500ms)를 함께 전달 → 코드 변경 없음.
