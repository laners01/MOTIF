package com.ssafy.motif.coreapi.domain.auth.dto;

import com.ssafy.motif.domain.core.user.User;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 명세 §2.1·§2.2 응답의 {@code user} 요약.
 *
 * @param profileImageUrl 공개 등급이라 CloudFront 일반 URL이다(명세 §1.7).
 *                        키가 없으면 {@code null} — 신규 가입은 항상 이 경우다.
 *                        변환은 {@code global.media.PublicMediaUrlResolver} 가 한다.
 */
public record UserSummaryResponse(
        @Schema(description = "사용자 PK", example = "1")
        Integer userSeq,

        @Schema(description = "최초 가입 시에는 임시값 user_{userSeq}. 확정은 온보딩 완료 API(§3.3)", example = "user_1")
        String nickname,

        @Schema(description = "CloudFront 일반 URL(공개 등급 — §1.7). 미설정이면 기본 프로필 이미지")
        String profileImageUrl
) {

    public static UserSummaryResponse of(User user, String profileImageUrl) {
        return new UserSummaryResponse(user.getUserSeq(), user.getNickname(), profileImageUrl);
    }
}
