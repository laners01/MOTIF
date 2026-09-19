package com.ssafy.motif.coreapi.domain.user.dto;

import com.ssafy.motif.domain.core.pass.PassStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 내 패스 일람 항목 (API 명세 §11.9).
 *
 * <p><b>{@code status} 는 실효 상태다 — DB 원시값이 아니다.</b> {@code ACTIVE → EXPIRED}
 * 전이 배치가 없어 <b>기간이 지난 {@code ACTIVE} 행</b>이 정상적으로 존재하는데, 그 값을
 * 그대로 내보내면 클라이언트가 못 쓰는 패스를 활성으로 읽는다. 그래서 DB 가
 * {@code ACTIVE} 라도 {@code expiresDttm} 이 지났으면 {@code EXPIRED} 로 내려간다
 * (S15P21D104-269).
 *
 * <p>판정 자체는 여기서도 서비스에서도 계산하지 않는다 — §11.4·§4.7 의 {@code passActive}
 * 와 같은 조회가 DB 시각으로 답한 것을 그대로 쓴다.
 */
@Schema(description = "내 패스 일람 항목 (§11.9)")
public record MotifPassItem(

        @Schema(description = "아티스트 식별자 — artist.artistSeq 와 같은 값(명세 형태 유지)",
                example = "3")
        Integer artistSeq,

        @Schema(description = "README §3.5 공통 아티스트 요약")
        Artist artist,

        @Schema(description = "ACTIVE | EXPIRED | REFUNDED — **실효 상태**. "
                + "기간이 지난 패스는 DB 가 ACTIVE 여도 EXPIRED 로 내려간다",
                example = "ACTIVE")
        PassStatus status,

        @Schema(description = "시작 시각")
        Instant startedDttm,

        @Schema(description = "만료 시각")
        Instant expiresDttm,

        @Schema(description = "구매한 개월 수", example = "1")
        Short durationMonths,

        @Schema(description = "취소 의사 기록. **이용 판정에는 참여하지 않는다**",
                nullable = true)
        Instant canceledDttm) {

    /** README §3.5 공통 아티스트 요약 — 솔로면 {@code groupName} 이 {@code null} 이다. */
    @Schema(description = "아티스트 요약 (README §3.5)")
    public record Artist(

            @Schema(description = "아티스트 식별자", example = "3")
            Integer artistSeq,

            @Schema(description = "아티스트 이름 — users.nickname", example = "카리나")
            String name,

            @Schema(description = "그룹명. 솔로면 null", example = "aespa")
            String groupName,

            @Schema(description = "프로필 이미지 URL. 미설정이면 null")
            String profileImageUrl) {
    }
}
