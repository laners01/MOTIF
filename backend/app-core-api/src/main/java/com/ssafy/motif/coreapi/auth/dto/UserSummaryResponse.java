package com.ssafy.motif.coreapi.auth.dto;

import com.ssafy.motif.domain.core.user.User;

/**
 * 명세 §2.1 응답의 {@code user} 요약.
 *
 * @param profileImageUrl 공개 등급이라 CloudFront 일반 URL이다(명세 §1.7).
 *                        키가 없으면 {@code null} — 신규 가입은 항상 이 경우다.
 */
public record UserSummaryResponse(
        Integer userSeq,
        String nickname,
        String profileImageUrl
) {

    public static UserSummaryResponse of(User user, String publicMediaBaseUrl) {
        return new UserSummaryResponse(
                user.getUserSeq(),
                user.getNickname(),
                toPublicUrl(user.getProfileImageKey(), publicMediaBaseUrl));
    }

    private static String toPublicUrl(String key, String baseUrl) {
        if (key == null || key.isBlank() || baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        return baseUrl.endsWith("/") ? baseUrl + key : baseUrl + "/" + key;
    }
}
