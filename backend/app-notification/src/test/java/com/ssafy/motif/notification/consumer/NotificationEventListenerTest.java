package com.ssafy.motif.notification.consumer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ssafy.motif.event.contract.ChallengeCreatedEvent;
import com.ssafy.motif.event.contract.CommentCreatedEvent;
import com.ssafy.motif.event.contract.CompositeFinishedEvent;
import com.ssafy.motif.event.contract.NoticeCreatedEvent;
import com.ssafy.motif.event.topology.RoutingKeys;
import com.ssafy.motif.domain.notif.token.DeviceTokenRepository;
import com.ssafy.motif.notification.fanout.SubscriberTokenQuery;
import com.ssafy.motif.notification.push.FcmSender;
import com.ssafy.motif.notification.push.PushMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 라우팅 키로 타입을 가르는지 (S15P21D104-312·313).
 *
 * <p><b>이게 이 파일의 존재 이유다.</b> 한 큐에 {@code @RabbitListener} 를 여러 개 달면
 * 브로커가 메시지를 컨슈머들에게 라운드로빈으로 나눠, 챌린지 이벤트가 공지 리스너로
 * 간다. 그런데 MQ 컨버터가 {@code FAIL_ON_UNKNOWN_PROPERTIES} 를 꺼둬서 <b>변환이
 * 성공한다</b> — {@code noticeSeq=null}·{@code body=null} 인 "공지" 푸시가 예외 없이
 * 나간다. 사람이 푸시를 직접 받아보기 전엔 아무도 모른다.
 *
 * <p>Spring 컨텍스트 없이 돈다 — 가르는 규칙이 라우팅 키라 브로커가 필요 없다.
 */
class NotificationEventListenerTest {

    /** {@code MqConfig} 의 매퍼와 같은 구성이어야 한다 — snake_case 가 어긋나면 전부 null 이 된다. */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private SubscriberTokenQuery subscriberTokenQuery;
    private DeviceTokenRepository deviceTokenRepository;
    private FcmSender fcmSender;
    private EventIdempotencyGuard idempotency;
    private NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        subscriberTokenQuery = mock(SubscriberTokenQuery.class);
        deviceTokenRepository = mock(DeviceTokenRepository.class);
        fcmSender = mock(FcmSender.class);
        idempotency = mock(EventIdempotencyGuard.class);
        listener = new NotificationEventListener(
                subscriberTokenQuery, deviceTokenRepository, fcmSender, idempotency, MAPPER);

        given(idempotency.claim(anyString())).willReturn(true);
        given(subscriberTokenQuery.activeTokensOfSubscribers(anyInt())).willReturn(List.of("tok-1"));
        given(deviceTokenRepository.findActiveTokensOf(anyInt())).willReturn(List.of("tok-1"));
        given(fcmSender.send(any(), any())).willReturn(1);
    }

    @Test
    @DisplayName("challenge.created — NEW_CHALLENGE 로 나가고 구독자를 아티스트로 찾는다")
    void challengeCreated() {
        listener.onEvent(message(challenge("evt-1")), RoutingKeys.CHALLENGE_CREATED, null);

        PushMessage sent = captureSent();
        assertThat(sent.type()).isEqualTo(PushMessage.TYPE_NEW_CHALLENGE);
        assertThat(sent.title()).isEqualTo("카리나의 새 챌린지");
        assertThat(sent.body()).isEqualTo("Whiplash");
        assertThat(sent.data()).containsEntry("challengeSeq", "11");
        verify(subscriberTokenQuery).activeTokensOfSubscribers(3);
    }

    @Test
    @DisplayName("comment.created — NEW_COMMENT 로 수신자 본인의 기기에만 간다")
    void commentCreated() {
        listener.onEvent(message(comment("evt-c", null)), RoutingKeys.COMMENT_CREATED, null);

        PushMessage sent = captureSent();
        assertThat(sent.type()).isEqualTo(PushMessage.TYPE_NEW_COMMENT);
        assertThat(sent.title()).isEqualTo("팬1님이 댓글을 남겼어요");
        assertThat(sent.body()).isEqualTo("영상 잘 봤어요");
        // 게시물 종류에 따라 대상 키 이름이 갈린다(§14.3)
        assertThat(sent.data()).containsEntry("videoSeq", "500").containsEntry("commentSeq", "900");
        // 구독자 팬아웃이 아니다 — 수신자 한 명의 기기만 본다
        verify(deviceTokenRepository).findActiveTokensOf(42);
        verify(subscriberTokenQuery, Mockito.never()).activeTokensOfSubscribers(anyInt());
    }

    @Test
    @DisplayName("comment.replied — NEW_REPLY 로 나가고 parentCommentSeq 가 실린 이벤트다")
    void replyCreated() {
        listener.onEvent(message(comment("evt-r", 800L)), RoutingKeys.REPLY_CREATED, null);

        PushMessage sent = captureSent();
        assertThat(sent.type()).isEqualTo(PushMessage.TYPE_NEW_REPLY);
        assertThat(sent.title()).isEqualTo("팬1님이 답글을 남겼어요");
        assertThat(sent.data()).containsEntry("commentSeq", "900")
                .containsEntry("parentCommentSeq", "800");
    }

    @Test
    @DisplayName("notice.created — NEW_NOTICE 로 나간다")
    void noticeCreated() {
        listener.onEvent(message(new NoticeCreatedEvent(
                "evt-2", Instant.now(), 77, 3, "카리나", "오늘 공연 잘 봤어요")),
                RoutingKeys.NOTICE_CREATED, null);

        PushMessage sent = captureSent();
        assertThat(sent.type()).isEqualTo(PushMessage.TYPE_NEW_NOTICE);
        assertThat(sent.title()).isEqualTo("카리나 공지");
        assertThat(sent.body()).isEqualTo("오늘 공연 잘 봤어요");
        assertThat(sent.data()).containsEntry("noticeSeq", "77");
    }

    @Test
    @DisplayName("본문이 비어 나가지 않는다 — 타입이 섞이면 여기가 먼저 깨진다")
    void bodyIsNeverNull() {
        listener.onEvent(message(challenge("evt-3")), RoutingKeys.CHALLENGE_CREATED, null);

        // 리스너를 여러 개 달았을 때의 증상이 정확히 이것이었다 —
        // 챌린지 이벤트가 공지로 변환돼 body 가 null 인 푸시가 나갔다
        assertThat(captureSent().body()).isNotBlank();
    }

    @Test
    @DisplayName("재전달이면 발송하지 않는다")
    void redeliveryIsSkipped() {
        given(idempotency.claim(anyString())).willReturn(false);

        listener.onEvent(message(challenge("evt-4")), RoutingKeys.CHALLENGE_CREATED, null);

        verify(fcmSender, Mockito.never()).send(any(), any());
    }

    @Test
    @DisplayName("발송이 실패하면 멱등 키를 반납한다 — 재시도가 막히면 안 된다")
    void releasesOnFailure() {
        given(fcmSender.send(any(), any())).willThrow(new IllegalStateException("FCM 실패"));

        assertThatThrownBy(() -> listener.onEvent(
                message(challenge("evt-5")), RoutingKeys.CHALLENGE_CREATED, null))
                .isInstanceOf(IllegalStateException.class);

        verify(idempotency).release("evt-5");
    }

    @Test
    @DisplayName("모르는 라우팅 키는 던져서 DLQ 로 보낸다 — 조용히 버리지 않는다")
    void unknownRoutingKeyGoesToDlq() {
        assertThatThrownBy(() -> listener.onEvent(
                new Message("{}".getBytes(), new MessageProperties()), "video.liked", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("video.liked");
    }

    private static ChallengeCreatedEvent challenge(String eventId) {
        return new ChallengeCreatedEvent(eventId, Instant.now(), 11, 3, "카리나", "Whiplash", "thumb.jpg");
    }

    private PushMessage captureSent() {
        ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
        verify(fcmSender).send(captor.capture(), any());
        return captor.getValue();
    }

    /** 참여 영상 댓글. {@code parentCommentSeq} 가 있으면 답글이다. */
    private static CommentCreatedEvent comment(String eventId, Long parentCommentSeq) {
        return new CommentCreatedEvent(eventId, Instant.now(), "VIDEO", 500L, 900L,
                parentCommentSeq, 42, "팬1", "영상 잘 봤어요");
    }

    private Message message(Object payload) {
        try {
            return new Message(MAPPER.writeValueAsBytes(payload), new MessageProperties());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    // --- §13 합성 결과 (S15P21D104-319) -------------------------------------------

    @Test
    @DisplayName("composite.finished DONE — COMPOSITE_DONE 이 잡 주인에게 1회 나간다")
    void compositeFinishedDone() {
        listener.onEvent(message(finished("evt-c1", "DONE", null, "MOTIF")),
                RoutingKeys.COMPOSITE_FINISHED, null);

        PushMessage sent = captureSent();
        assertThat(sent.type()).isEqualTo(PushMessage.TYPE_COMPOSITE_DONE);
        assertThat(sent.title()).isEqualTo("합성이 끝났어요");
        assertThat(sent.body()).isEqualTo("보관함에 저장됐어요");
        assertThat(sent.data())
                .containsEntry("jobUuid", "3fa85f64-5717-4562-b3fc-2c963f66afa6")
                .containsEntry("jobType", "MOTIF")
                // 성공에는 칸 자체가 없다 — 앱이 존재 여부로 갈라도 된다
                .doesNotContainKey("failCode");
        // 구독 팬아웃이 아니라 잡 주인 한 명이다
        verify(deviceTokenRepository).findActiveTokensOf(7);
        verify(subscriberTokenQuery, Mockito.never()).activeTokensOfSubscribers(anyInt());
    }

    @Test
    @DisplayName("CHALLENGE 완료는 본문이 다르다 — 게시(§8.8)로 보낸다")
    void compositeFinishedDoneForChallenge() {
        listener.onEvent(message(finished("evt-c2", "DONE", null, "CHALLENGE")),
                RoutingKeys.COMPOSITE_FINISHED, null);

        assertThat(captureSent().body()).isEqualTo("결과를 확인하고 게시해 보세요");
    }

    @Test
    @DisplayName("FAILED — COMPOSITE_FAILED 에 failCode 가 실린다. 본문은 앱이 고른다")
    void compositeFinishedFailed() {
        listener.onEvent(message(finished("evt-c3", "FAILED", "FACE_NOT_DETECTED", "CHALLENGE")),
                RoutingKeys.COMPOSITE_FINISHED, null);

        PushMessage sent = captureSent();
        assertThat(sent.type()).isEqualTo(PushMessage.TYPE_COMPOSITE_FAILED);
        assertThat(sent.title()).isEqualTo("합성에 실패했어요");
        assertThat(sent.data()).containsEntry("failCode", "FACE_NOT_DETECTED");
    }

    @Test
    @DisplayName("재전달은 보내지 않는다 — 선점에 실패하면 발송 자체가 없다")
    void redeliveredFinishedIsSkipped() {
        given(idempotency.claim("evt-c4")).willReturn(false);

        listener.onEvent(message(finished("evt-c4", "DONE", null, "MOTIF")),
                RoutingKeys.COMPOSITE_FINISHED, null);

        verify(fcmSender, Mockito.never()).send(any(), any());
    }

    @Test
    @DisplayName("composite.packaged·composite.done 은 DLQ 로 가지 않는다 — 조용히 흘린다")
    void compositeKeysThisServiceDoesNotOwnAreIgnored() {
        // 페이로드를 읽지도 않는다 — 키만 보고 흘린다
        Message packaged = message(java.util.Map.of("event_id", "evt-c5"));

        listener.onEvent(packaged, RoutingKeys.COMPOSITE_PACKAGED, null);
        listener.onEvent(packaged, RoutingKeys.COMPOSITE_DONE, null);

        verify(fcmSender, Mockito.never()).send(any(), any());
    }

    private static CompositeFinishedEvent finished(String eventId, String status,
                                                   String failCode, String jobType) {
        return new CompositeFinishedEvent(eventId, Instant.now(),
                "3fa85f64-5717-4562-b3fc-2c963f66afa6", jobType, 7, status, failCode,
                "MOTIF".equals(jobType) ? 10L : null);
    }
}
