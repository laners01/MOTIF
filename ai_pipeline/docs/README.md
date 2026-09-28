# MOTIF 개발 문서 인덱스

MOTIF 영상 합성 파이프라인(Cloud Run GPU Job + intake 서비스)의 개발·연동·배포 문서 모음입니다. 아래 순서로 읽으면 전체 흐름을 따라갈 수 있습니다.

## 개요 · 진입

_시스템 전경과 운영 요건. 여기서 시작._

- [시스템 아키텍처 개요](01-architecture-overview.md) — intake·worker·service·client 모듈 역할 요약
- [운영 요건 체크리스트](02-operations-requirements.md) — 컴포넌트별 env·IAM·S3 권한·READY 게이트

## 연동 계약 (핵심 산출물)

_백엔드↔AI 서버 계약 명세와 이행 점검._

- [MOTIF 영상 합성 연동 명세 (v0.3) — 백엔드·AI 서버 계약](03-integration-spec.md) — 접수/콜백/스키마/S3/실패코드 근본 계약
- [배경 사전분석(precompute) 연동 명세 — 합성 계약 확장](04-precompute-integration-spec.md) — precompute Job을 compose와 대칭 패턴으로 추가
- [연동 요구사항 이행 점검표 (v0.3)](05-integration-compliance.md) — 명세 10개 항목 대비 구현 이행 매트릭스
- [백엔드 연동 인계 — 배포 후 액션 아이템 (2026-09-20)](06-backend-handoff-2026-09-20.md) — 배포 후 백엔드가 받을 값·처리할 액션

## 배포 · 인프라

_이미지 빌드·Cloud Run 배포·가중치 서빙 결정._

- [Cloud Run 배포 구성 노트 (2026-09-20)](07-deployment-notes-2026-09-20.md) — GPU/intake 이미지 빌드·Job/Service·볼륨·권한
- [개발 인계 — 모델 가중치 이미지 분리 설계 (2026-09-18)](08-weights-image-split-2026-09-18.md) — 가중치를 이미지에서 분리해 GCS 마운트+심링크

## 설계안 (대안 · 미구현)

_검토했거나 보류·미구현한 설계 의사결정._

- ["함께 촬영" 영상 합성 기능 설계안](09-together-shooting-design.md) — 아이돌+사용자 동시 촬영·합성·접촉 보정 설계
- [Vertex AI Custom Job 전환 검토안 (보류된 대안)](10-vertex-migration-plan.md) — Cloud Run 60분 벽 회피용 H100 전환안(철회)

## 팀간 협업 · 변경 대응

_백엔드 요청 원문과 그에 대한 영향 분석._

- [백엔드→AI팀 · 편집본 S3 경로 이동 요청](11-backend-request-s3-prefix.md) — 편집본 S3 경로 private/ 이동·IAM 확인 요청 원문
- [S3 경로 이관(private/) 영향 분석과 대응](12-s3-prefix-migration-2026-09-22.md) — 위 요청에 대한 워커 코드 영향 분석(변경 없음)

## 개발 진행 인계 (작업 히스토리)

_세션별 진행·디버깅·성능 개선 시계열 기록._

- [개발 진행 인계 — 2-Job 파이프라인 구동 (2026-09-20)](13-dev-log-2026-09-20.md) — 2-Job 파이프라인 end-to-end 구동·검증 현황
- [개발 진행 인계 — SVOR 로딩·ENOSPC 병목 해결과 compose 완주 (2026-09-21)](14-dev-log-2026-09-21.md) — SVOR mmap 병목·ENOSPC 해결, compose 완주
- [개발 진행 인계 — 경로 개편·MOTIF 통합·성능 33% 단축 (2026-09-23)](15-dev-log-2026-09-23.md) — seq 경로 개편·MOTIF 통합·병렬화 33% 단축·다중인물 수정

## 테스트 · 검증

_단독 연동 검증 절차._

- [연동 단독 테스트 가이드 (S3·콜백 검증)](16-integration-test-guide.md) — 백엔드 부재기 AI팀 단독 S3+콜백 검증 절차
