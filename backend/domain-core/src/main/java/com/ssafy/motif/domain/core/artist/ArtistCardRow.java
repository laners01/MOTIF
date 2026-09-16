package com.ssafy.motif.domain.core.artist;

/**
 * 랭킹 항목의 표시용 투영 (API 명세 §5.1) — {@link ArtistRepository#findCards}.
 *
 * <p><b>이름과 얼굴은 {@code artists} 가 아니라 {@code users} 에 있다</b>
 * ({@code nickname}·{@code profile_image_key}) — {@code artists} 는 {@code role='ARTIST'}
 * 사용자의 도메인 확장이다. 그룹명은 또 다른 마스터 테이블이고 솔로면 {@code null} 이라
 * left join 이다({@link ArtistName}·{@link ArtistListRow} 와 같은 근거).
 *
 * <p><b>{@link SubscribedArtistRow} 와 합치지 않는다.</b> 네 칸이 겹쳐 한때 같은 모양
 * 이었지만, §4.7 이 {@code subscribedDttm} 을 더하면서 갈라졌다 — 그쪽은 {@code subscriptions}
 * 가 FROM 절에 있어 공짜로 얻는 값이고, 랭킹은 구독을 조인하지도 않는다. 합쳐 두면
 * 랭킹 쿼리가 쓰지도 않을 칸을 채우려고 구독 테이블을 끌어오거나 {@code null} 을 밀어
 * 넣어야 한다.
 *
 * @param profileImageKey S3 키. URL 변환은 응답 조립 시점에 호출부가 한다
 *                        (공개 등급이면 {@code PublicMediaUrlResolver})
 * @param groupName       솔로 아티스트는 {@code null}
 */
public record ArtistCardRow(
        Integer artistSeq, String name, String profileImageKey, String groupName) {
}
