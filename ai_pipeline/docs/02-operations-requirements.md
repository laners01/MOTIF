# 운영 요건 체크리스트

- intake: `COMPOSE_JOB_RESOURCE`, `MOTIF_WORKER_TOKEN`; 실행 SA에 Cloud Run Job override 실행 권한
- compose: 명세의 callback/AWS env, GCS 모델 mount, `tasks=1`, `max-retries=0`
- precompute: AWS env, GCS 모델 mount, `MOTIF_PRECOMPUTE`
- S3: 기존 입력 GET·결과 PUT에 더해 `precomputed/*` GET/PUT 허용
- READY가 없거나 hash/version이 다르면 compose는 배경 분석을 대신 실행하지 않고 실패한다.
- artifact 업로드 후 READY를 기록하므로 불완전한 artifact는 소비되지 않는다.
- 실제 GPU 실행으로 cold start, 최대 VRAM/RAM/disk, compose/precompute timeout을 각각 측정해야 한다.
