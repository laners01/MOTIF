package com.ssafy.motif.event.contract;

import java.time.Instant;

/**
 * 챌린지 등록 완료 — core-api → {@code motif.topic}(rk {@code challenge.created}) → app-notification.
 *
 * <p>컨슈머는 {@code subscriptions WHERE artist_seq=?} 를 읽어 활성 기기 토큰에
 * {@code NEW_CHALLENGE} FCM 을 팬아웃한다(어드민 명세 §4). 코어와 알림 서비스는 HTTP 로
 * 서로를 부르지 않는다 — 한쪽이 죽어도 쪽지는 큐에 남아 나중에 처리된다.
 *
 * <p>직렬화 규약(ADR-0005, README.md): snake_case / ISO-8601 UTC / additive-only.
 * Jackson 애너테이션 금지 — 네이밍은 각 앱의 MQ 컨버터 ObjectMapper가 전담한다.
 *
 * <p><b>키를 보내고 URL 을 보내지 않는다</b>(Claim-Check). CDN 도메인이 바뀌면 이미 큐에
 * 있는 메시지가 죽은 주소를 들고 있게 된다 — 키는 그대로다.
 *
 * @param eventId      이벤트 자체의 uuid — 컨슈머 멱등 키(Redis {@code SETNX evt:{eventId}} TTL 24h)
 * @param occurredAt   발생 시각 (ISO-8601 UTC)
 * @param artistName   알림 제목에 들어가는 표시 이름({@code users.nickname})
 * @param thumbnailKey 등록 시점에는 <b>아직 파일이 없는</b> 키다 — app-media 가 만든다(§1.4)
 */
public record ChallengeCreatedEvent(
        String eventId,
        Instant occurredAt,
        Integer challengeSeq,
        Integer artistSeq,
        String artistName,
        String songTitle,
        String thumbnailKey
) {
}
