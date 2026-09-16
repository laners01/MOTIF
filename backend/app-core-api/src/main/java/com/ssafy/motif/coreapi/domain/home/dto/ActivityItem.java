package com.ssafy.motif.coreapi.domain.home.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * 구독 아티스트 활동 한 건 (API 명세 §6.3).
 *
 * <p><b>이종 항목이다.</b> {@code type} 에 따라 {@link #asset} 또는 {@link #notice} 중
 * 하나만 채워진다. {@code @JsonInclude(NON_NULL)} 로 <b>안 쓰는 쪽은 응답에서 빠진다</b> —
 * {@code "notice": null} 이 실리면 클라이언트가 {@code type} 외에 null 검사까지 하게 된다.
 * 전역 설정이 아니라 이 record 에만 붙인 것은, 다른 응답에서 "값이 없음" 과 "칸이 없음"
 * 을 구분해야 하는 자리를 건드리지 않기 위해서다.
 *
 * <p>sealed 타입 + {@code @JsonTypeInfo} 로 가르지 않았다 — Swagger 예시와 안드로이드
 * 파싱이 둘 다 복잡해지는데, 타입이 둘뿐이고 앞으로 늘 계획도 없다.
 *
 * <p><b>{@code artist} 는 두 타입이 같은 모양이다</b>(README §3.5 공통 아티스트 요약).
 * 원본 명세 예시는 {@code NOTICE} 쪽에서 {@code groupName} 을 빠뜨렸지만, 같은 화면
 * 같은 자리에 쓰이는 값이라 응답 타입을 둘로 나눌 이유가 없다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "구독 아티스트 활동 항목 (§6.3)")
public record ActivityItem(

        @Schema(description = "MOTIF_ASSET | NOTICE — 이 값으로 분기한다")
        ActivityType type,

        @Schema(description = "활동 발생 시각. 두 타입 통합 정렬의 기준이다")
        Instant regDttm,

        Artist artist,

        @Schema(description = "type=MOTIF_ASSET 일 때만 있다", nullable = true)
        Asset asset,

        @Schema(description = "type=NOTICE 일 때만 있다", nullable = true)
        Notice notice
) {

    public static ActivityItem ofAsset(Instant regDttm, Artist artist, Asset asset) {
        return new ActivityItem(ActivityType.MOTIF_ASSET, regDttm, artist, asset, null);
    }

    public static ActivityItem ofNotice(Instant regDttm, Artist artist, Notice notice) {
        return new ActivityItem(ActivityType.NOTICE, regDttm, artist, null, notice);
    }

    public enum ActivityType {

        /** 신규 모티프 에셋 등록. 클릭하면 §11.2 에셋 상세로 간다. */
        MOTIF_ASSET,

        /** 아티스트 공지. 상세 API 가 없어 이 항목이 공지 화면의 진입 데이터다. */
        NOTICE
    }

    /** README §3.5 공통 아티스트 요약 — 솔로면 {@code groupName} 이 {@code null} 이다. */
    public record Artist(Integer artistSeq, String name, String groupName, String profileImageUrl) {
    }

    /**
     * 에셋 요약 — <b>좋아요·댓글 카운트가 없다</b>. 에셋에는 그 기능 자체가 없다(확정).
     *
     * @param title {@code motif_assets.title} 전용 컬럼이다. 소개 문구({@code description})
     *              와 다른 필드다
     */
    public record Asset(Integer assetSeq, String title, String thumbnailUrl, int durationMs) {
    }

    /**
     * 공지 — §5.5 목록 항목과 같은 필드 구성이다.
     *
     * <p>본문 전체와 이미지 전체를 싣는 것은 공지 상세 API 가 없기 때문이다(구 §10.1
     * 삭제). {@code isLiked} 를 함께 내려 화면이 바로 좋아요 상태를 그린다.
     */
    public record Notice(
            Integer noticeSeq,
            String content,
            @Schema(description = "displayOrder 오름차순 전체 배열")
            List<Image> images,
            int likeCount,
            int commentCount,
            boolean isLiked) {
    }

    /** 공지 이미지는 공개 등급이다(§1.7). */
    public record Image(String imageUrl, short displayOrder) {
    }
}
