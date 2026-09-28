# 연동 요구사항 이행 점검표 (v0.3)

기준: `03-integration-spec.md` v0.3 및 사전 점검 10개 항목.

| 항목 | 상태 | 구현 |
|---|---|---|
| 1. 배경 사전 처리 계약 | 완료 | `MOTIF_PRECOMPUTE`로 직접 실행하고 READY를 S3에 게시. 선택적 `source_video_key`로 편집본 lookup key와 분석 원본을 분리 백엔드 API 변경 없음 |
| 2. artifact 위치 규칙 | 완료 | `base_video_key` SHA-256 기반 결정적 READY key, artifact SHA-256·모델/구현 버전 검증 |
| 3. 슬롯 연결 | 부분 | normalized anchor를 precompute 인물 bbox와 최근접 매핑. 현재 파이프라인은 요청당 교체 슬롯 1개만 지원 |
| 4. correction_enabled | 완료 | true는 보정 결과, false는 동일 전경 분석의 baseline을 사용 |
| 5. Intake 라우팅 | 완료 | CPU `intake.py`가 토큰 검증 후 compose Job을 비동기 실행 |
| 6. 인증/IAM | 코드 완료 | v0.3 헤더·env 적용. 배포 시 `precomputed/*` S3 권한과 Cloud Run 실행 권한 필요 |
| 7. 중복/멱등성 | 완료 | content-addressed artifact, READY-last, 결과 key 덮어쓰기, 백엔드 콜백 멱등 계약 이용 |
| 8. 결과 영상 | 완료 | ffmpeg로 H.264/yuv420p/AAC·30fps·rotate=0·base audio·요청 길이 정규화 |
| 9. timeout/resources | 배포 설정 | `tasks=1`, `max-retries=0`; 실제 timeout·disk/GPU 크기는 실측 후 배포값 확정 |
| 10. v0.3 실행 계약 | 완료 | `MOTIF_JOB`, snake_case/unknown ignore, key 기반 S3, `/done|/failed`, 4xx 무재시도·5xx 1/4/16초, 보고 후 exit 0 |

## 남은 기능 제약

기존 `run_final_v2` 알고리즘은 한 전경 인물을 한 배경 인물과 교체하는 파이프라인입니다. 따라서 명세 예시처럼 여러 slot bit가 동시에 선택된 요청과, 교체 대상/기준 춤 인물이 없는 MOTIF 에셋은 알고리즘 차원의 추가 구현 없이는 올바르게 합성할 수 없습니다. 현재 코드는 이를 조용히 잘못 처리하지 않고 실패 콜백으로 종료합니다. 이 제약은 백엔드 요청 변경 문제는 아니며 합성 알고리즘 확장 문제입니다.
