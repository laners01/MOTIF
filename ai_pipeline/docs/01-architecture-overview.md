# 시스템 아키텍처 개요

`intake.py`는 CPU Cloud Run Service이다. MOTIF 토큰과 요청을 검증하고 Cloud Run v2 Jobs API에 `MOTIF_JOB`, `MOTIF_REQUEST_ID` override를 전달한 뒤 202를 반환한다.

`precompute_worker.py`는 분석 원본 key와 합성 lookup용 base key를 받아 분석·인페인팅·포즈·깊이·조명 데이터를 immutable archive로 S3에 올리고 READY manifest를 마지막에 쓴다.

`worker.py`는 백엔드 요청 JSON을 env에서 읽고 S3의 두 입력 및 READY artifact를 받는다. `service.py`는 배경 분석을 호출하지 않고 `run_post`만 실행하고, 결과를 요청의 `output_video_key`에 업로드한다. 이후 `motif_client.py`가 v0.3 callback을 보낸다.
