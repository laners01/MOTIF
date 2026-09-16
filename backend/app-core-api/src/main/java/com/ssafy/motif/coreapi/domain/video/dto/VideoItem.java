package com.ssafy.motif.coreapi.domain.video.dto;

import com.ssafy.motif.domain.core.video.Visibility;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 참여 영상 항목 (API 명세 §8.1 · §8.2).
 *
 * <p><b>명세가 정의하는 "참여 영상 항목"이다.</b> §8.2 단건 조회의 {@code data} 와
 * §8.1 피드 {@code list[]} 항목이 <b>같은 형태</b>라고 명세가 못박았다 — 뷰어가 어느
 * 경로로 들어왔든 같은 오버레이를 그리기 때문이다. 그래서 §8.1 이 먼저 만들되 §8 안에
 * 두고, §8.2 티켓이 이 record 를 그대로 쓴다.
 *
 * <p><b>§6.1 {@code HotVideoItem} 과 합치지 않는다.</b> 그쪽은 클릭 즉시 재생만 되면
 * 되는 경량 항목이고(회의 확정 R2) 좋아요·댓글 수와 {@code isLiked} 를 일부러 뺐다.
 * 모양이 겹쳐 보여도 뺀 이유가 살아 있는 한 합치면 한쪽이 쓰지 않을 조인을 끌어온다.
 *
 * @param videoUrl   <b>HLS 마스터 재생목록</b>({@code master.m3u8})이다 — 단일 MP4 가
 *                   아니다(§1.7). 공개 사본이 준비된 영상은 무서명 CloudFront URL 이고,
 *                   보호 등급(내 {@code PRIVATE})은 core-api 재생목록 주소다
 * @param isMine     {@code true} 면 클라이언트가 Bottom Sheet 에 수정·삭제를 노출한다
 * @param visibility 내 {@code PRIVATE} 영상은 내 피드에 보이므로 {@code PUBLIC} 만
 *                   내려가지 않는다 — 클라이언트가 이 값으로 자물쇠를 그린다
 */
@Schema(description = "참여 영상 항목 (§8.1 · §8.2)")
public record VideoItem(
        Long videoSeq,
        Integer challengeSeq,
        String description,

        @Schema(description = "HLS 마스터 재생목록. 보호 등급이면 core-api 주소다")
        String videoUrl,

        String thumbnailUrl,
        Integer durationMs,
        Author author,
        Long viewCount,
        Integer likeCount,
        Integer commentCount,

        @Schema(description = "아티스트가 이 영상에 좋아요를 눌렀는가 — 피드 정렬에 강하게 반영된다")
        boolean hasArtistLike,

        @Schema(description = "아티스트가 이 영상에 댓글을 달았는가 — 피드 정렬에 강하게 반영된다")
        boolean hasArtistComment,

        @Schema(description = "요청자가 좋아요를 눌렀는가")
        boolean isLiked,

        boolean commentEnabled,

        @Schema(description = "PUBLIC | PRIVATE")
        Visibility visibility,

        @Schema(description = "요청자 본인의 영상인가")
        boolean isMine,

        Instant regDttm
) {

    /** 작성자 요약 — 뷰어 하단 표기와 작성자 Bottom Sheet(§4.6) 진입에 쓴다. */
    public record Author(Integer userSeq, String nickname, String profileImageUrl) {
    }
}
