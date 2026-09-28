# 연동 단독 테스트 가이드 (S3·콜백 검증)

**2026-09-21 · 대상: AI 팀 · 백엔드 담당자 부재 기간 2026-09-22 ~ 09-24**

Cloud Run Job 없이 **로컬(또는 아무 GPU 머신)에서** 연동을 끝까지 시험할 수 있습니다.
백엔드는 이미 배포돼 있고 24시간 떠 있습니다. 문의 없이 진행하실 수 있도록 필요한 값을
전부 적었습니다.

---

## 1. 결론부터 — 세 가지만 하면 됩니다

Cloud Run Job 은 **껍데기일 뿐**입니다. 실제로 필요한 동작은 셋입니다.

1. S3 에서 입력 2개를 받는다
2. 합성해서 **지정된 키**에 올린다
3. 백엔드에 콜백 한 번 친다

이걸 로컬 파이썬 스크립트로 해도 백엔드 쪽은 **완전히 똑같이** 동작합니다.
접수 HTTP(`AI_SUBMIT_URL`)는 Cloud Run 이 준비되면 그때 붙이면 되고,
**지금 테스트에는 필요 없습니다.**

---

## 2. 준비물 (이미 전달드린 것)

| 것 | 값 |
|---|---|
| S3 버킷 · 리전 | `motif-media` · `ap-northeast-2` |
| S3 자격증명 | IAM `motif-gpu-worker` 액세스 키 (전달 완료) |
| 워커 토큰 | `X-Motif-Worker-Token` (전달 완료) |
| 콜백 주소 | `https://j15d104.p.ssafy.io` |

### 접근 가능한 S3 경로

| 프리픽스 | 권한 |
|---|---|
| `raw/` `edited/` `challenges/` `assets/` | **읽기** |
| `results/` | **쓰기** |
| `precomputed/` | **읽기·쓰기** |
| `private/` | 목록만 (내용은 막힘 — 백엔드 산출물) |

---

## 3. 테스트 데이터 — 이미 올려뒀습니다

**베이스(편집본)** — 인물이 지워진 영상. 576×1024 · 30fps · 15.000s

```
edited/test9001/m1.mp4
```

**촬영본** — 사용자 영상. 576×1026 · 60fps · 15.000s
**잡 5개분을 미리 올려뒀습니다.** 한 잡당 하나씩 쓰시면 됩니다.

| # | `job_uuid` | `raw_video_key` |
|---|---|---|
| 1 | `33ac69eb-e21b-482e-a852-f3ab51feea78` | `raw/33ac69eb-e21b-482e-a852-f3ab51feea78.mp4` |
| 2 | `960ca1e3-c910-474f-8707-6ca3f5723741` | `raw/960ca1e3-c910-474f-8707-6ca3f5723741.mp4` |
| 3 | `bca894e4-01d8-4af9-bc94-0be28d2157e5` | `raw/bca894e4-01d8-4af9-bc94-0be28d2157e5.mp4` |
| 4 | `143cc646-dcce-4816-83ce-b69c49e4f494` | `raw/143cc646-dcce-4816-83ce-b69c49e4f494.mp4` |
| 5 | `13a6bed9-9cd1-4417-b380-f4cc67ef5b73` | `raw/13a6bed9-9cd1-4417-b380-f4cc67ef5b73.mp4` |

**왜 5개인가** — 콜백은 **잡 하나당 한 번만** 유효합니다. 성공 콜백을 받으면 그 잡은
`DONE` 으로 닫히고, 같은 잡에 다시 콜백하면 200 을 주되 **아무 일도 일어나지 않습니다**.
시행착오할 때마다 다음 번호를 쓰세요.

### 잡 하나의 전체 페이로드

`{N}` 자리에 위 표의 `job_uuid` 를 넣으시면 됩니다.

```json
{
  "job_uuid": "33ac69eb-e21b-482e-a852-f3ab51feea78",
  "job_type": "CHALLENGE",
  "attempt": 1,
  "raw_video_key": "raw/33ac69eb-e21b-482e-a852-f3ab51feea78.mp4",
  "base_video_key": "edited/test9001/m1.mp4",
  "output_video_key": "results/challenge/33ac69eb-e21b-482e-a852-f3ab51feea78.mp4",
  "correction_enabled": true,
  "participant_mask": 1,
  "slots": [
    { "participant_mask": 1, "anchor": { "x": 0.3, "y": 0.45, "r": 0.0995 } }
  ],
  "base_duration_ms": 15000
}
```

**`output_video_key` 는 반드시 이 형식입니다** — `results/challenge/{job_uuid}.mp4`.
백엔드가 같은 값을 다시 계산해 대조하므로, 다르면 콜백이 400 입니다.

---

## 4. 0단계 — 합성 없이 연동부터 뚫기 (권장)

**합성이 아직 안 되더라도 연동 경로는 먼저 검증할 수 있습니다.** 아무 MP4 나
`output_video_key` 에 올리고 콜백을 치면, 백엔드는 그것을 정상 결과로 처리합니다.

베이스를 그대로 복사해 쓰면 됩니다(S3 내 복사라 다운로드 불필요):

```python
import boto3
s3 = boto3.client("s3", region_name="ap-northeast-2")
JOB = "33ac69eb-e21b-482e-a852-f3ab51feea78"

s3.copy_object(
    Bucket="motif-media",
    CopySource={"Bucket": "motif-media", "Key": "edited/test9001/m1.mp4"},
    Key=f"results/challenge/{JOB}.mp4",
    ContentType="video/mp4",
    MetadataDirective="REPLACE",
)
print("업로드 완료")
```

그 다음 콜백:

```bash
JOB=33ac69eb-e21b-482e-a852-f3ab51feea78
curl -i -X POST "https://j15d104.p.ssafy.io/api/worker/v1/composite-jobs/$JOB/done" \
  -H "X-Motif-Worker-Token: $MOTIF_WORKER_TOKEN" \
  -H "X-Request-Id: aitest-1" \
  -H "Content-Type: application/json" \
  -d "{\"result_video_key\":\"results/challenge/$JOB.mp4\",\"processing_ms\":1000,\"attempt\":1,\"worker_id\":\"local-test\"}"
```

**200 이 오면 연동 성공입니다.** 이 시점부터 백엔드가 알아서 진행합니다 —
HLS 인코딩, 잡 완료 확정, 푸시 발송까지.

---

## 5. 결과 확인 — 백엔드 접속 없이 S3 로 판별합니다

콜백 후 **10~30초** 뒤에 확인하세요. 백엔드가 그 사이 ffmpeg 로 HLS 를 만듭니다.

```python
import boto3
s3 = boto3.client("s3", region_name="ap-northeast-2")
JOB = "33ac69eb-e21b-482e-a852-f3ab51feea78"

r = s3.list_objects_v2(Bucket="motif-media", Prefix=f"private/challenge/{JOB}/")
for o in r.get("Contents", []):
    print(o["Key"], o["Size"])
print("(아직 없음)" if not r.get("Contents") else "")
```

이렇게 나오면 **전 구간 성공**입니다:

```
private/challenge/{JOB}/480p/data.m4s
private/challenge/{JOB}/480p/index.m3u8
private/challenge/{JOB}/720p/data.m4s
private/challenge/{JOB}/720p/index.m3u8
private/challenge/{JOB}/master.m3u8
private/challenge/{JOB}/thumb.jpg
```

`private/` 은 목록만 보이고 내용은 막혀 있습니다(백엔드 산출물이라 정상입니다).
**목록에 뜨는 것만으로 성공 판정이 됩니다.**

30초가 지나도 비어 있으면 6절을 보세요.

---

## 6. 콜백 응답 코드 — 이게 진단표입니다

| 응답 | 뜻 | 할 일 |
|---|---|---|
| **200** | 정상 수신 | 끝. 5절로 |
| **400** | `result_video_key` 가 백엔드 계산값과 다름 | `results/challenge/{job_uuid}.mp4` 인지 확인. **재시도 무의미** |
| **401** | 토큰 불일치 | `X-Motif-Worker-Token` 확인. **재시도 무의미** |
| **404** | 모르는 `job_uuid` | 위 표의 5개 중 하나인지 확인. **재시도 무의미** |
| **5xx · 타임아웃** | 백엔드 일시 장애 | 3회까지 재시도(1s → 4s → 16s) |

**4xx 는 재시도하지 마세요.** 설정이나 값이 틀린 것이라 같은 답이 옵니다.

### 200 인데 `private/` 이 안 생길 때

콜백은 받았지만 백엔드 인코딩이 실패한 경우입니다. 원인은 거의 하나입니다 —
**`results/` 에 올린 파일을 ffmpeg 가 못 읽는 경우**입니다.

- MP4 · H.264(yuv420p) · AAC 인지
- 0바이트가 아닌지
- `ContentType` 이 `video/mp4` 인지

다음 `job_uuid` 로 다시 시도하시면 됩니다.

---

## 7. 실패 보고 테스트

합성이 실패하는 경로도 시험하실 수 있습니다. 남는 `job_uuid` 하나로:

```bash
JOB=13a6bed9-9cd1-4417-b380-f4cc67ef5b73
curl -i -X POST "https://j15d104.p.ssafy.io/api/worker/v1/composite-jobs/$JOB/failed" \
  -H "X-Motif-Worker-Token: $MOTIF_WORKER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fail_code":"FACE_NOT_DETECTED","fail_message":"테스트","processing_ms":3000,"attempt":1,"worker_id":"local-test"}'
```

200 이면 잡이 `FAILED` 로 닫히고 사용자에게 실패 푸시가 나갑니다.
`fail_code` 는 **30자 이내 대문자·밑줄**이어야 합니다.

---

## 8. 진짜 합성으로 넘어갈 때

0단계가 통과하면 같은 절차에서 **복사 대신 실제 합성 결과를 올리기만** 하면 됩니다.

1. `raw_video_key` · `base_video_key` 를 S3 에서 받는다
2. 합성한다 (`precomputed/` 캐시는 자유롭게 읽고 쓰셔도 됩니다)
3. `output_video_key` 에 올린다 — **ContentType `video/mp4`**
4. `/done` 콜백

결과 MP4 규격:

| 항목 | 값 |
|---|---|
| 컨테이너·코덱 | MP4 · H.264(yuv420p) + AAC |
| 해상도 | **베이스와 같게** (576×1024). 업스케일하지 않음 |
| 길이 | 베이스 ±500ms (15.000s 기준) |
| 회전 | **픽셀이 바로 서 있게.** rotate 메타데이터로 세우지 않음 |
| fps | 고정 (30 권장) |

---

## 9. 연락

백엔드 담당자가 **09-22 ~ 09-24 부재**입니다. 그 기간에도 서버는 계속 떠 있으므로
위 절차는 그대로 동작합니다.

막히시면 아래를 함께 남겨 주시면 복귀 후 바로 원인을 짚을 수 있습니다.

- 사용한 `job_uuid`
- 콜백 요청 전문(헤더 `X-Request-Id` 포함)과 **응답 코드·본문**
- `results/` 에 올린 객체의 크기와 `ContentType`
- `ffprobe -show_streams` 결과

`X-Request-Id` 를 매번 다른 값으로 넣어 주시면(예: `aitest-1`, `aitest-2`) 백엔드 로그에서
해당 건만 정확히 추려낼 수 있습니다.

---

## 부록. 잡을 더 만들어야 할 때

5개를 다 쓰셨으면 백엔드 DB 에 행을 넣어야 해서 담당자 복귀가 필요합니다.
다만 **이미 `DONE` 이 된 잡에 다시 콜백해도 200 이 옵니다**(멱등). 그래서
콜백 연동 자체를 반복 확인하는 용도로는 계속 쓰실 수 있고,
**새로 HLS 가공까지 돌려 보려면** 새 잡이 필요합니다.
