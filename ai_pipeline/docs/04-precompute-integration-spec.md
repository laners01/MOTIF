# 배경 사전분석(precompute) 연동 명세 — 합성 계약 확장

**대상: 백엔드 팀** · precompute Job을 기존 compose와 **대칭 패턴**으로 백엔드에 추가하기 위한 명세.

> 전제: 기존 `03-integration-spec.md`(compose)를 읽었다고 가정한다. 여기 없는 규약(인증 헤더, snake_case, `X-Request-Id` 추적, 공통 응답 봉투, S3 자격증명 등)은 **전부 compose와 동일**하다.

* * *

0. 한 줄 요약
---------
- **접수(요청 보내기)는 compose와 같은 형식** — 같은 접수 서비스, 경로만 `/precompute`, 바디만 다름.
- **완료 통지도 compose와 같은 형식** — precompute Job이 끝나면 `/done` 또는 `/failed` 콜백을 **1회** 보낸다. (백엔드가 이 두 엔드포인트를 새로 구현하면 됨. S3 폴링 불필요.)

* * *

1. precompute가 하는 일 / 언제 부르나
---------
- **하는 일**: 합성 베이스(`base_video_key` = 편집본/에셋)의 배경을 미리 분석·압축해 S3에 적재. compose가 이걸 소비해 빠르게 합성한다.
- **호출 시점**: 베이스가 S3에 준비된 직후. **compose 접수보다 먼저 precompute가 `/done` 될 것.** precompute 결과(READY)가 없으면 **compose는 그 잡을 실패 처리**한다(배경 분석을 대신 하지 않음).
- 베이스 1개당 precompute 1회면 충분하다(같은 base_video_key 재요청은 덮어써도 안전, 멱등).

* * *

2. 접수 — 백엔드 → AI
-----------------

    POST https://motif-intake-1025574392259.us-central1.run.app/precompute
    X-Motif-Worker-Token: …          ← compose와 같은 토큰
    X-Request-Id: 7c1e…              ← Job이 콜백·로그에 그대로 싣는다
    Content-Type: application/json

**요청 바디** (snake_case)

    {
      "job_uuid": "b7c93b4e-0a56-4d2e-9f0d-2f6e8a54d61c",
      "base_video_key": "edited/11/7d2f…e1.mp4",
      "attempt": 1,
      "source_video_key": null
    }

| 필드 | 타입 | 필수 | 뜻 |
| --- | --- | --- | --- |
| `job_uuid` | string | Y | 잡 식별자. 콜백 URL·로그에 그대로 쓴다 |
| `base_video_key` | string | Y | 배경을 분석할 베이스. **항상 MP4** |
| `attempt` | int | N | 몇 번째 시도인지(기본 1). 로그용 |
| `source_video_key` | string \| null | N | 배경 분석에 쓸 원본이 베이스와 다를 때만 지정. 생략/`null`이면 `base_video_key`를 쓴다 |

> compose 바디의 `job_type / raw_video_key / output_video_key / correction_enabled / participant_mask / slots / base_duration_ms`는 **precompute엔 없다.** 모르는 필드는 무시된다.

**접수 서비스가 줄 응답** (compose와 동일)

| HTTP | 본문 | 백엔드 동작 |
| --- | --- | --- |
| **202** | `{ "execution": "projects/…/executions/motif-precompute-abc12" }` | 처리 중으로 표시 |
| 401 | — | 토큰 불일치. 설정 오류 |
| 503 | `{ "error": "…" }` | 잠시 뒤 다시 접수 |

접수는 **띄우기만 하고 기다리지 않는다.** 완료는 아래 콜백으로 온다.

* * *

3. 콜백 성공 — AI → 백엔드  *(백엔드가 구현할 엔드포인트)*
--------------------

    POST https://j15d104.p.ssafy.io/api/worker/v1/precompute-jobs/{job_uuid}/done
    X-Motif-Worker-Token: …
    X-Request-Id: 7c1e…          ← 받은 값 그대로
    Content-Type: application/json

    {
      "job_uuid": "b7c93b4e-0a56-4d2e-9f0d-2f6e8a54d61c",
      "base_video_key": "edited/11/7d2f…e1.mp4",
      "manifest_key": "precomputed/9a3f…/ready.json",
      "background_sha256": "e3b0c4…",
      "people": [1, 2],
      "processing_ms": 2951000
    }

| 필드 | 필수 | 뜻 |
| --- | --- | --- |
| `job_uuid` | Y | 받은 값 그대로 |
| `base_video_key` | Y | 받은 값 그대로 |
| `manifest_key` | Y | READY manifest의 S3 키. 백엔드가 저장해두면 유용(디버깅/추적용) |
| `background_sha256` | N | 분석한 배경의 해시 |
| `people` | N | 검출된 인물 슬롯 id 목록 |
| `processing_ms` | N | 컨테이너 시작부터 READY 기록까지 |

**S3 적재가 끝난 뒤에** 부른다. 이 콜백을 받으면 그 베이스는 "compose 가능" 상태다.

> compose와 경로 구조가 같다: `composite-jobs` → **`precompute-jobs`** 만 다르다.

* * *

4. 콜백 실패 — AI → 백엔드  *(백엔드가 구현할 엔드포인트)*
--------------------

    POST https://j15d104.p.ssafy.io/api/worker/v1/precompute-jobs/{job_uuid}/failed

    {
      "fail_code": "PERSON_NOT_DETECTED",
      "fail_message": "no person in base",
      "processing_ms": 3120,
      "attempt": 1,
      "worker_id": "motif-precompute-abc12"
    }

| 필드 | 필수 | 뜻 |
| --- | --- | --- |
| `fail_code` | Y | 아래 표. 30자 이내 대문자·밑줄 |
| `fail_message` | N | 로그용 자유 문장(500자 이내) |
| `processing_ms` | N | 시작~실패까지 |
| `attempt` | N | 받은 값 그대로 |
| `worker_id` | N | `CLOUD_RUN_EXECUTION` |

**실패 코드** — compose(`03-integration-spec.md` §6)와 같은 매핑을 재사용한다. precompute에서 실제로 나오는 것:

| `fail_code` | 언제 |
| --- | --- |
| `INPUT_NOT_FOUND` | S3에 `base_video_key`(또는 `source_video_key`)가 없음 |
| `PERSON_NOT_DETECTED` | 인물을 찾지 못함 |
| `MULTIPLE_PERSONS` | 사람이 둘 이상 |
| `RAW_UNREADABLE` | 영상 디코딩 실패 |
| `UPLOAD_FAILED` | 결과 S3 적재 실패 |
| `GPU_ERROR` | OOM 등 GPU 오류 |
| `TIMEOUT` | Job 시간 제한 초과 |
| `INTERNAL_ERROR` | 그 외 전부 |

* * *

5. 콜백에 대한 백엔드 응답 (compose와 동일)
-----------------

| HTTP | 뜻 | Job 동작 |
| --- | --- | --- |
| **200** | 받았다(이미 처리된 잡이어도 200, 멱등) | 끝. exit 0 |
| 401 / 404 | 토큰 불일치 / 모르는 `job_uuid` | 재시도하지 않는다 |
| 5xx · 연결 실패 · 타임아웃 | 백엔드 장애 | **3회 재시도**(1s→4s→16s), 그래도 실패면 exit 0 |

**4xx는 재시도하지 않는다.** 중복 `/done`은 200으로 받으면 안전하다.

* * *

6. S3 산출물 (참고 — 백엔드가 직접 만질 필요 없음)
---------
precompute는 결과를 S3에 적재한다. 콜백을 구현하면 **백엔드가 이 키를 폴링할 필요는 없다**(신호는 콜백이 준다). 디버깅 참고용:

| 키 | 내용 |
| --- | --- |
| `precomputed/{sha256(base_video_key)}/ready.json` | READY manifest (= 콜백의 `manifest_key`) |
| `precomputed/{sha256(base_video_key)}/artifacts/{sha256}.tar.gz` | 압축된 배경 데이터(compose가 소비) |

`{sha256(base_video_key)}` = base_video_key 문자열을 SHA-256 hex. prefix는 AI측 `PRECOMPUTED_PREFIX` env(기본 `precomputed`).

* * *

7. 백엔드에 넘기는 값 요약
---------

| # | 항목 | 값 |
| --- | --- | --- |
| 1 | 접수 엔드포인트 | `POST https://motif-intake-1025574392259.us-central1.run.app/precompute` |
| 2 | 인증 토큰 | compose와 **같은** `X-Motif-Worker-Token` |
| 3 | 백엔드가 구현할 콜백 | `POST /api/worker/v1/precompute-jobs/{job_uuid}/done` · `.../failed` |
| 4 | 호출 순서 제약 | precompute `/done` 이후에 그 베이스의 compose를 접수 |
| 5 | Job timeout | precompute `--task-timeout` = 3600s(현재). 백엔드 close 창은 +5분 |

* * *

8. 확정 필요
---------

| # | 질문 | 기본안 |
| --- | --- | --- |
| 1 | precompute 트리거 주체 — 편집본/에셋 생성 파이프라인이 곧바로 접수하나? | 백엔드가 베이스 생성 직후 `/precompute` 접수 |
| 2 | `/done`·`/failed` 엔드포인트 스펙 이대로 OK? | 위 §3·§4 그대로 |
| 3 | precompute 실패 시 compose를 아예 막을지, 재시도할지 | 실패면 그 베이스로 compose 접수 보류 |
