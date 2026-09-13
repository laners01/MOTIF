package com.ssafy.motif.event.contract;

import java.time.Instant;

/**
 * 공지 등록 완료 — core-api → {@code motif.topic}(rk {@code notice.created}) → app-notification.
 *
 * <p>컨슈머는 {@code subscriptions WHERE artist_seq=?} 를 읽어 활성 기기 토큰에
 * {@code NEW_NOTICE} FCM 을 팬아웃한다(어드민 명세 §4). {@link ChallengeCreatedEvent} 와
 * 같은 구조이고 payload 만 다르다.
 *
 * <p><b>등록에서만 발행한다.</b> 수정(§3.10.4)은 이 이벤트를 내지 않는다 — 오타를 고칠
 * 때마다 구독자 전원에게 푸시가 가면 알림이 못 쓰게 된다(명세 확정).
 *
 * <p>직렬화 규약(ADR-0005, README.md): snake_case / ISO-8601 UTC / additive-only.
 * Jackson 애너테이션 금지 — 네이밍은 각 앱의 MQ 컨버터 ObjectMapper가 전담한다.
 *
 * @param eventId        이벤트 자체의 uuid — 컨슈머 멱등 키(Redis {@code SETNX evt:{eventId}} TTL 24h)
 * @param occurredAt     발생 시각 (ISO-8601 UTC)
 * @param artistName     알림 제목에 들어가는 표시 이름({@code users.nickname})
 * @param contentPreview 알림 본문에 그대로 들어가는 앞 60자 (§4). 목록의 80자와 다른 값이다 —
 *                       푸시 본문은 잘려 보이므로 더 짧다. 이미지 키는 싣지 않는다(알림에 쓰지 않는다)
 */
public record NoticeCreatedEvent(
        String eventId,
        Instant occurredAt,
        Integer noticeSeq,
        Integer artistSeq,
        String artistName,
        String contentPreview
) {
}
