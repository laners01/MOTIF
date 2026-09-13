package com.ssafy.motif.coreapi.domain.media;

import com.ssafy.motif.event.contract.MediaJobRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * 미디어 후처리 요청 발행 (ADR-0010 D1).
 *
 * <p>합성 결과와 어드민이 올린 챌린지 원본이 <b>이 한 곳</b>을 거쳐 같은 큐로 간다.
 * 어드민 업로드 API 가 생기면 이 컴포넌트를 부르면 된다 — 발행 형태를 다시 짜지 않는다.
 *
 * <p>MQ 발행 자체는 {@link MediaJobEventRelay} 가 <b>커밋 후에</b> 한다(ADR-0005).
 * 대상 행이 아직 없는데 app-media 가 먼저 도착하면 갱신할 행을 못 찾는다.
 */
@Component
@RequiredArgsConstructor
public class MediaJobPublisher {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * @param sourceType   {@code COMPOSITE_RESULT} | {@code CHALLENGE_ORIGIN}
     * @param sourceSeq    갱신할 행의 PK
     * @param sourceKey    S3 원본 키 (재생용이 아니라 입력이므로 MP4 그대로)
     * @param outputPrefix  산출물 프리픽스 — {@link MediaKeys} 로 만든다
     * @param thumbnailAtMs 썸네일을 뽑을 시각(ms). null 이면 워커 기본값(1.5초)
     * @return 이 후처리 잡의 uuid — 멱등 키
     */
    public String publish(String sourceType, long sourceSeq, String sourceKey,
                          String outputPrefix, Long thumbnailAtMs) {
        String mediaJobUuid = UUID.randomUUID().toString();
        eventPublisher.publishEvent(new MediaJobRequestedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                mediaJobUuid,
                sourceType,
                sourceSeq,
                sourceKey,
                outputPrefix,
                thumbnailAtMs));
        return mediaJobUuid;
    }
}
