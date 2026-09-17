package com.ssafy.motif.coreapi.domain.user.dto;

import com.ssafy.motif.domain.core.pass.PassStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 내 패스 일람 항목 (API 명세 §11.9).
 *
 * <p><b>원시값을 그대로 준다.</b> "지금 쓸 수 있나" 는 여기서 계산하지 않는다 — 그
 * 판정은 §11.4(아티스트별 패스)와 §4.7(구독 카드)의 {@code passActive} 가 답한다. 세 곳이
 * 각자 계산하면 같은 패스가 화면마다 다르게 보인다.
 *
 * <p>그래서 {@code status} 만 보면 틀린다 — 만료 배치가 아직 없어 <b>기간이 지난
 * {@code ACTIVE} 행</b>이 정상적으로 존재한다. 실제 판정은
 * {@code status='ACTIVE' AND expiresDttm > now} 다.
 */
@Schema(description = "내 패스 일람 항목 (§11.9)")
public record MotifPassItem(

        @Schema(description = "아티스트 식별자 — artist.artistSeq 와 같은 값(명세 형태 유지)",
                example = "3")
        Integer artistSeq,

        @Schema(description = "README §3.5 공통 아티스트 요약")
        Artist artist,

        @Schema(description = "ACTIVE | EXPIRED | REFUNDED. **이용 가능 여부가 아니다**",
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
