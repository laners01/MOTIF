package com.ssafy.motif.domain.core.artist;

/**
 * 구독 중인 아티스트 한 줄 (API 명세 §4.6) — {@link SubscriptionRepository#findSubscribed} 투영.
 *
 * <p><b>이름과 얼굴은 {@code artists} 가 아니라 {@code users} 에 있다</b>
 * ({@code nickname}·{@code profile_image_key}) — {@code artists} 는 {@code role='ARTIST'}
 * 사용자의 도메인 확장이다. 그룹명은 또 다른 마스터 테이블이고 솔로면 {@code null} 이라
 * left join 이다({@link ArtistName}·{@link ArtistListRow} 와 같은 근거).
 *
 * <p>{@code subscribedDttm} 은 §4.7 의 {@code subscribedDays} 계산에만 쓰고 §4.6 은
 * 무시한다. <b>{@code subscriptions} 가 이미 FROM 절에 있어 조인이 늘지 않으므로</b>
 * 투영을 둘로 쪼개지 않았다 — {@link ArtistName} 에 {@code groupName} 을 더하지 않은
 * 것과는 사정이 다르다(그쪽은 {@code artist_groups} 조인이 새로 붙는다).
 *
 * @param profileImageKey S3 키. URL 변환은 응답 조립 시점에 호출부가 한다
 * @param groupName       솔로 아티스트는 {@code null}
 * @param subscribedDttm  구독 시각({@code subscriptions.reg_dttm}) — 정렬 기준이자
 *                        §4.7 {@code subscribedDays} 의 기준
 */
public record SubscribedArtistRow(
        Integer artistSeq, String name, String profileImageKey, String groupName,
        java.time.Instant subscribedDttm) {
}
