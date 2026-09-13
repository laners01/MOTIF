package com.ssafy.motif.coreapi.domain.challenge.controller;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.domain.challenge.docs.ChallengeControllerDocs;
import com.ssafy.motif.coreapi.domain.challenge.dto.ChallengeDetailResponse;
import com.ssafy.motif.coreapi.domain.challenge.service.ChallengeQueryService;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팬 앱 챌린지 조회 API (명세 §7.2). Base URL 은 {@code /api/v1}.
 *
 * <p><b>인증이 필요하다</b>(🔓 아님) — 응답의 {@code isLiked}·{@code isSubscribed} 가
 * 호출자 기준이다.
 *
 * <p>§7.1 피드·§7.3 좋아요·§7.4 조회 이벤트는 이 티켓 범위가 아니라 매핑이 없다.
 *
 * <p>경로가 {@code /api/v1/**} 이고 {@code /api/v1/admin/**} 이 아니므로 springdoc
 * {@code user} 그룹 문서에 자동으로 잡힌다({@code OpenApiConfig}).
 *
 * <p>Swagger 문서는 {@link ChallengeControllerDocs} 가 갖는다.
 */
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/challenges")
public class ChallengeController implements ChallengeControllerDocs {

    private final ChallengeQueryService challengeQueryService;

    /** §7.2 챌린지 상세 Bottom Sheet. */
    @Override
    @GetMapping("/{challengeSeq}")
    public ApiResponse<ChallengeDetailResponse> detail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer challengeSeq) {
        return ApiResponse.success(challengeQueryService.detail(user.userSeq(), challengeSeq));
    }
}
