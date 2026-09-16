package com.ssafy.motif.domain.core.video;

/**
 * HOT 카드 한 장의 재료 (API 명세 §6.1) —
 * {@link ParticipationVideoRepository#findHotCards} 투영.
 *
 * <p><b>엔티티를 모으지 않고 한 번에 조인한다.</b> §6.1 은 경량 카드라 필요한 값이
 * 영상·곡·작성자에서 각각 두세 칸뿐이고 전부 직선 조인으로 닿는다. 엔티티 4묶음을
 * 따로 읽어 map 으로 맞추는 방식({@code ChallengeQueryService.feed} 가 그렇게 한다)은
 * 거기에 조인으로 못 구하는 값(좋아요·구독 여부 집합)이 섞여 있어서고, 여기에는
 * 그런 값이 없다.
 *
 * <p><b>순서를 신뢰하지 않는다.</b> {@code in} 조회는 입력 순서를 보장하지 않으므로
 * 호출부가 스냅샷 순서로 다시 세운다.
 *
 * @param videoKey     마스터 재생목록 키. 공개 등급이므로 무서명 CDN URL 이 된다
 * @param thumbnailKey 같은 프리픽스의 {@code thumb.jpg}
 */
public record HotVideoRow(
        Long videoSeq,
        String videoKey,
        String thumbnailKey,
        Integer durationMs,
        Long viewCount,
        String songTitle,
        String singerName,
        Integer authorUserSeq,
        String authorNickname) {
}
