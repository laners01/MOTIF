"""스텁 GPU 워커 (ADR-0003).

job.composite를 소비해 PROCESS_SECONDS(기본 2초) 뒤 composite.done을 발행하는
가짜 합성기 — 오프라인에서도 발행→소비→완료 전 파이프라인이 돈다.

계약 (ADR-0005, module-event/README.md):
  - 페이로드는 snake_case 봉투: event_id / occurred_at(ISO-8601 UTC) / job_uuid ...
  - 수신한 x-request-id 헤더를 그대로 발행 헤더로 이어붙이고 로그에 출력한다.
  - prefetch 1 + manual ack — 처리 완료 전에 죽으면 브로커가 자동 재큐한다.
  - content_type=application/json — Java 컨버터가 JSON으로 인식하는 조건.
"""

import json
import logging
import os
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path

import pika
import pika.exceptions

MQ_HOST = os.environ.get("MQ_HOST", "rabbitmq")
MQ_USERNAME = os.environ.get("MQ_USERNAME", "motif")
MQ_PASSWORD = os.environ.get("MQ_PASSWORD", "motif")  # 로컬 전용 더미
MQ_VHOST = os.environ.get("MQ_VHOST", "/motif")
PROCESS_SECONDS = float(os.environ.get("PROCESS_SECONDS", "2"))

EXCHANGE = "motif.topic"
CONSUME_QUEUE = "job.composite"
PUBLISH_ROUTING_KEY = "composite.done"
REQUEST_ID_HEADER = "x-request-id"
HEALTH_FILE = Path("/tmp/healthy")

logging.basicConfig(format="%(asctime)s %(levelname)-5s [stub-worker] %(message)s",
                    level=logging.INFO)
logger = logging.getLogger(__name__)


def log(request_id, message):
    # java logback 패턴의 [%X{requestId:-}] 와 같은 꼴로 남겨 한 ID로 grep 되게 한다
    logger.info("[%s] %s", request_id or "", message)


def on_message(channel, method, properties, body):
    headers = properties.headers or {}
    request_id = headers.get(REQUEST_ID_HEADER)
    job = json.loads(body)
    job_uuid = job.get("job_uuid")

    log(request_id, f"job.composite 수신: job_uuid={job_uuid} "
                    f"job_type={job.get('job_type')} priority={properties.priority}")

    time.sleep(PROCESS_SECONDS)  # 가짜 합성

    done_event = {
        "event_id": str(uuid.uuid4()),
        "occurred_at": datetime.now(timezone.utc).isoformat(timespec="milliseconds")
                                                 .replace("+00:00", "Z"),
        "job_uuid": job_uuid,
    }
    channel.basic_publish(
        exchange=EXCHANGE,
        routing_key=PUBLISH_ROUTING_KEY,
        body=json.dumps(done_event).encode("utf-8"),
        properties=pika.BasicProperties(
            content_type="application/json",
            delivery_mode=2,  # persistent
            # 받은 x-request-id를 그대로 이어붙인다 (ADR-0005 추적)
            headers={REQUEST_ID_HEADER: request_id} if request_id else None,
        ),
    )
    channel.basic_ack(delivery_tag=method.delivery_tag)
    log(request_id, f"composite.done 발행: job_uuid={job_uuid}")


def main():
    params = pika.ConnectionParameters(
        host=MQ_HOST,
        virtual_host=MQ_VHOST,
        credentials=pika.PlainCredentials(MQ_USERNAME, MQ_PASSWORD),
        heartbeat=30,
    )
    while True:
        try:
            connection = pika.BlockingConnection(params)
        except pika.exceptions.AMQPConnectionError:
            log(None, f"MQ({MQ_HOST}) 연결 대기...")
            time.sleep(2)
            continue

        try:
            channel = connection.channel()
            channel.basic_qos(prefetch_count=1)
            # 큐는 definitions.json이 선언한다 — 워커는 선언하지 않는다 (운영 관례)
            channel.basic_consume(queue=CONSUME_QUEUE, on_message_callback=on_message)
            HEALTH_FILE.touch()
            log(None, f"소비 시작: queue={CONSUME_QUEUE} (prefetch 1, manual ack)")
            channel.start_consuming()
        except pika.exceptions.AMQPError as e:
            log(None, f"연결 끊김({e!r}) — 재접속")
            time.sleep(2)
        finally:
            HEALTH_FILE.unlink(missing_ok=True)
            try:
                connection.close()
            except Exception:
                pass


if __name__ == "__main__":
    main()
