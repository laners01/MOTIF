# 스텁 AI 접수 서비스 (ADR-0003) — 실제 AI 가 없어도 접수→콜백 경로가 돌게 하는 가짜.
# 의존성이 없다: 표준 라이브러리 http.server + urllib 뿐이라 pip install 이 사라졌다.
FROM python:3.12-slim

WORKDIR /app
COPY worker.py .

EXPOSE 8080
CMD ["python", "-u", "worker.py"]
