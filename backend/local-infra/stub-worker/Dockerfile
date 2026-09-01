# 스텁 GPU 워커 (ADR-0003) — 실제 합성기가 없어도 파이프라인이 돌게 하는 가짜.
FROM python:3.12-slim

RUN pip install --no-cache-dir pika==1.3.2

WORKDIR /app
COPY worker.py .

CMD ["python", "-u", "worker.py"]
