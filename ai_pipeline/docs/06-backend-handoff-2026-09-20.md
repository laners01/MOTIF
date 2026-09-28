# 백엔드 연동 인계 — 배포 후 액션 아이템 (2026-09-20)

MOTIF 합성/분석 파이프라인(AI측)을 Cloud Run으로 배포 완료했다. 아래는 백엔드가
연동을 위해 **받아야 할 값**과 **처리해야 할 작업**이다. 계약 본문은
[03-integration-spec.md](03-integration-spec.md), 인프라 결정은
[07-deployment-notes-2026-09-20.md](07-deployment-notes-2026-09-20.md) 참조.

---

## 1. 받아야 할 값

### AI_SUBMIT_URL (합성 접수 엔드포인트)
```
https://motif-intake-1025574392259.asia-southeast1.run.app
```
- 합성 요청: `POST /`  (아래 §3)
- 인증 헤더 `X-Motif-Worker-Token` 필수 → 없거나 틀리면 `401`.
- 즉시 `202 {"execution": "..."}` 반환(작업 완료를 기다리지 않음). 실제 결과는 콜백으로 통지.

> `/precompute` 경로도 존재하나 **호출 주체는 AI 내부**다(배경 영상 업로드 시 자동 수행).
> 백엔드가 호출할 필요 없음 — §5 참조.

---

## 2. 백엔드가 처리해야 할 작업

### (필수·차단) S3 IAM 권한 추가 — `precomputed/*`
분석 결과(조명·춤보정 데이터)를 `motif-media`의 **`precomputed/*`** 에 저장하고,
합성 시 다시 읽는다. 현재 워커 키(`motif-gpu-worker`)는 명세 §8상 `results/*` PUT만 허용.

- **`precomputed/*` 에 `s3:GetObject` + `s3:PutObject` 추가 필요.**
- 없으면: 분석 Job이 결과 PUT에서 `AccessDenied` → 합성 Job도 `ready.json` GET 실패 → 합성 전부 `INPUT_NOT_FOUND`.

접두사는 env `PRECOMPUTED_PREFIX`(기본 `precomputed`)로 통제. 기본값 사용 시 `precomputed/*`.

### (필수) 콜백 엔드포인트 수신 준비
합성 완료/실패를 워커가 아래로 POST한다(`/complete` 아님 주의):
```
POST {MOTIF_CALLBACK_BASE}/api/worker/v1/composite-jobs/{job_uuid}/done
POST {MOTIF_CALLBACK_BASE}/api/worker/v1/composite-jobs/{job_uuid}/failed
헤더: X-Motif-Worker-Token, X-Request-Id, Content-Type: application/json
```
- 현재 `MOTIF_CALLBACK_BASE=https://j15d104.p.ssafy.io` 로 배포됨. 변경 시 통보 요망.
- 재시도: 1s→4s→16s 백오프 4회. **4xx는 재시도 안 함**(즉시 포기). → 4xx로 응답 시 콜백 유실 주의.
- 워커는 항상 exit 0(콜백으로만 성패 전달). 콜백 미수신 = 워커가 죽었거나 타임아웃.

**`/done` 바디**
```json
{ "result_video_key": "...", "processing_ms": 12345, "attempt": 1, "worker_id": "..." }
```
**`/failed` 바디**
```json
{ "fail_code": "GPU_ERROR", "fail_message": "...", "processing_ms": 123, "attempt": 1, "worker_id": "..." }
```

---

## 3. 합성 요청 스키마 (`POST /`)

```json
{
  "job_uuid": "…",              // 필수, 1~128자
  "job_type": "CHALLENGE",      // "CHALLENGE" | "MOTIF"
  "attempt": 1,                 // ≥1
  "raw_video_key": "raw/…mp4",  // 사용자 원본 (상대 key)
  "base_video_key": "edited/…mp4",   // 배경 (MP4, 상대 key) — §4 주의
  "output_video_key": "results/…mp4",// 결과 저장 위치 (MP4, 상대 key)
  "correction_enabled": true,
  "participant_mask": 1,        // ≥0. 0이면 slots 비어야 함
  "slots": [ { "participant_mask": 1, "anchor": {"x":0.5,"y":0.5,"r":0.3} } ],
  "base_duration_ms": 30000     // >0
}
```
검증 실패(스키마 위반, 절대경로·`..` 포함 key 등) → `503 {"error": "..."}`. 알 수 없는 필드는 무시.

---

## 4. `base_video_key` 일관성 (중요)

분석 결과는 `digest = sha256(base_video_key)` 로 색인해 `precomputed/{digest}/ready.json` 에 저장된다.
**합성 요청의 `base_video_key` 문자열이 분석 때와 완전히 동일해야** 사전계산 결과가 매칭된다.

- 같은 배경이라도 key 문자열이 1글자라도 다르면 캐시 미스 → `INPUT_NOT_FOUND`.
- 즉 배경 영상의 S3 key는 한 번 정해지면 **불변**으로 다뤄야 함(리네임·재업로드 시 재분석 필요).

---

## 5. precompute(배경 분석) 트리거 — 백엔드 협의 대상 아님

- 배경 영상이 S3에 올라오면 **AI측이 분석 Job을 별도 수행**한다(합성과 무관한 사전 단계).
- 합성 요청 시점에는 해당 `base_video_key`의 `ready.json`이 **이미 존재한다고 전제**한다.
- 따라서 백엔드는 precompute를 호출하지 않는다. 다만 **아직 분석이 안 끝난 배경으로 합성을 요청하면**
  `INPUT_NOT_FOUND`로 실패하므로, 배경 확정→분석 완료 후에 합성을 태워야 함(운영 순서).
- (호출 주체 구현은 AI측 추후 확정 예정)

---

## 6. 실패 코드 (9종)

`RAW_TOO_SHORT`, `RAW_UNREADABLE`, `UPLOAD_FAILED`, `INPUT_NOT_FOUND`,
`MULTIPLE_PERSONS`, `PERSON_NOT_DETECTED`, `GPU_ERROR`, `TIMEOUT`, `INTERNAL_ERROR`.

- **`FACE_NOT_DETECTED`는 별도 구분 안 함** → `PERSON_NOT_DETECTED`로 통합.
- `TIMEOUT`: Cloud Run task-timeout 도달(SIGTERM) 시.

---

## 7. 확정/보류 항목

| 항목 | 상태 |
|---|---|
| `--max-retries 0` (재시도 없음) | ✅ 적용 |
| `--task-timeout` | 잠정 45분. 첫 성공 execution 실측 후 확정·통보 |
| 콜백 경로 | `/done` · `/failed` (`/complete` 아님) |
| 편집본(슬롯) 트랙 | 합성 Job이 `base_video_key`를 그대로 받아 처리 |
| 리전 | AI GPU Job = `asia-southeast1`, S3 = `ap-northeast-2`(cross-region) |

---

## 8. 백엔드 액션 아이템 요약

- [ ] **S3 IAM: `precomputed/*` GetObject+PutObject 추가** (§2, 차단 항목)
- [ ] 콜백 `/done`·`/failed` 수신 엔드포인트 준비 + 4xx 남발 금지 (§2)
- [ ] `AI_SUBMIT_URL` 반영 (§1)
- [ ] `base_video_key` 불변 정책 합의 (§4)
- [ ] (통보 대기) 확정 `--task-timeout` 값 수신 (§7)
