package com.ssafy.motif.coreapi.domain.artist.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * 명세 §5.5 {@code data.list[]} — 아티스트 공지 한 건.
 *
 * <p><b>별도의 공지 상세 API 가 없다</b>(구 §10.1 은 v0.8 에서 삭제). 그래서 목록 항목이
 * 본문 전체와 이미지 전체를 싣는다 — 화면이 목록에서 바로 펼쳐 읽는다.
 *
 * <p>{@code title} 은 없다.
 */
public record ArtistNoticeResponse(
        Integer noticeSeq,

        Artist artist,

        @Schema(description = "본문 전체")
        String content,

        @Schema(description = "displayOrder 오름차순 전체 배열. 대표 이미지 전용 필드는 쓰지 않는다")
        List<Image> images,

        int likeCount,

        int commentCount,

        @Schema(description = "내가 이 공지에 좋아요를 눌렀는지")
        boolean isLiked,

        Instant regDttm
) {

    /**
     * §5.5 {@code artist} — <b>{@code groupName} 은 제외한다</b>(명세 명시).
     *
     * <p>§5.3 상세와 필드가 달라 {@code ArtistDetailResponse} 를 재사용하지 않는다.
     */
    public record Artist(
            Integer artistSeq,

            @Schema(description = "아티스트 표시 이름 (users.nickname)", example = "카리나")
            String name,

            @Schema(description = "CloudFront 일반 URL(공개 등급 — §1.7). 미설정이면 null", nullable = true)
            String profileImageUrl
    ) {
    }

    /** §5.5 {@code images[]} — 공지 이미지는 공개 등급이다(§1.7). */
    public record Image(
            @Schema(description = "CloudFront 일반 URL(공개 등급 — §1.7). 미설정이면 null", nullable = true)
            String imageUrl,

            short displayOrder
    ) {
    }
}
