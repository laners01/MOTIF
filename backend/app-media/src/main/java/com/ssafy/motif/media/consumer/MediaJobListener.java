package com.ssafy.motif.media.consumer;

import com.ssafy.motif.event.contract.MediaJobRequestedEvent;
import com.ssafy.motif.event.topology.Queues;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * {@code job.media} 소비.
 *
 * <p>페이로드 클래스를 리스너 시그니처가 명시한다 — {@code __TypeId__} 헤더에
 * 의존하지 않는다 (ADR-0005). {@code x-request-id} 헤더 → MDC 복원으로
 * API → 큐 → 워커 로그가 한 ID로 이어진다.
 *
 * <p>실패는 재시도 3회 후 {@code dlq.job.media} 로 격리된다(MqConfig). 인코딩
 * 실패는 대개 소스 파일 문제라 재시도로 풀리지 않지만, S3 일시 오류는 풀린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaJobListener {

    private static final String REQUEST_ID_HEADER = "x-request-id";
    private static final String MDC_KEY = "requestId";

    private final MediaJobProcessor processor;

    @RabbitListener(queues = Queues.JOB_MEDIA)
    public void onMediaJob(MediaJobRequestedEvent event,
                           @Header(name = REQUEST_ID_HEADER, required = false) String requestId) {
        if (requestId != null) {
            MDC.put(MDC_KEY, requestId);
        }
        try {
            log.info("job.media 수신: mediaJobUuid={} sourceType={} sourceKey={}",
                    event.mediaJobUuid(), event.sourceType(), event.sourceKey());
            processor.process(event);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
