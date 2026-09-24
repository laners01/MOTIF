package com.ssafy.motif.coreapi.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 타 사용자 프로필 (API 명세 §4.6).
 *
 * <p>참여 영상 뷰어에서 작성자를 눌렀을 때 뜨는 <b>Bottom Sheet</b> 데이터다.
 * {@code participationCount} 는 제거됐다(회의 확정 R2).
 *
 * <p>본인 마이페이지(§4.1)와는 다른 응답이다 — 남의 화면에는 대표 모티프도 영상
 * 그리드도 없고, 대신 구독 중인 아티스트가 붙는다.
 *
 * @param profileImageUrl 무서명 공개 CDN URL. 미설정이면 기본 프로필 이미지
 * @param subscribedArtists 구독 최신순. 없으면 빈 배열
 */
@Schema(description = "타 사용자 프로필 (§4.6)")
public record UserProfileResponse(
        Integer userSeq,
        String nickname,
        @Schema(description = "공개 CDN URL. 미설정이면 기본 프로필 이미지")
        String profileImageUrl,
        @Schema(description = "구독 중인 아티스트. 구독 최신순. 없으면 []")
        List<SubscribedArtist> subscribedArtists) {

    /**
     * 공통 아티스트 요약 (상세 명세 README §3.5).
     *
     * <p><b>{@code groupName} 은 원본 §4.6 이 뺐던 것을 되살린 것이다</b> — 아티스트를
     * 참조하는 모든 응답이 같은 모양을 쓴다는 규약에 맞춘다. 상황마다 필드를 빼면
     * 클라이언트가 같은 개념에 타입을 여럿 갖게 된다.
     *
     * <p>공용 record 로 꺼내지 않은 것은 <b>지금 이 모양을 중첩 객체로 쓰는 곳이 여기
     * 하나뿐</b>이기 때문이다 — §5.3 은 네 필드를 평탄화해 갖고 있다. 두 번째로 중첩해
     * 쓰는 API 가 생기면 그때 꺼낸다.
     *
     * @param name      {@code artists.user_seq → users.nickname}. {@code artists} 에 이름 칸이 없다
     * @param groupName 솔로 아티스트는 {@code null}
     */
    @Schema(description = "아티스트 요약 (README §3.5)")
    public record SubscribedArtist(
            Integer artistSeq,
            String name,
            @Schema(description = "솔로 아티스트는 null", nullable = true)
            String groupName,
            @Schema(description = "공개 CDN URL. 미설정이면 기본 프로필 이미지")
            String profileImageUrl) {
    }
}
