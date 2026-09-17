package com.ssafy.motif.coreapi.domain.challenge.controller;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.domain.challenge.docs.ChallengeControllerDocs;
import com.ssafy.motif.common.response.CursorResponse;
import com.ssafy.motif.coreapi.domain.challenge.dto.ChallengeDetailResponse;
import com.ssafy.motif.coreapi.domain.challenge.dto.ChallengeFeedItem;
import com.ssafy.motif.coreapi.domain.challenge.service.ChallengeLikeService;
import com.ssafy.motif.coreapi.domain.challenge.service.ChallengeQueryService;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팬 앱 챌린지 조회 API (명세 §7.1·§7.2). Base URL 은 {@code /api/v1}.
 *
 * <p><b>인증이 필요하다</b>(🔓 아님) — 응답의 {@code isLiked}·{@code isSubscribed} 가
 * 호출자 기준이다.
 *
 * <p>§7.3 좋아요·§7.4 조회 이벤트는 아직 매핑이 없다.
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
    private final ChallengeLikeService challengeLikeService;

    /**
     * §7.1 숏폼 피드 — 커서.
     *
     * <p>매핑이 {@code /{challengeSeq}} 보다 <b>먼저 잡힌다</b>. 리터럴 경로가 경로
     * 변수보다 구체적이라 스프링이 {@code /feed} 를 상세로 보내지 않는다 — 순서에
     * 의존하는 것이 아니라 매칭 규칙이다.
     */
    @Override
    @GetMapping("/feed")
    public ApiResponse<CursorResponse<ChallengeFeedItem>> feed(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(challengeQueryService.feed(user.userSeq(), cursor, size));
    }

    /** §7.2 챌린지 상세 Bottom Sheet. */
    @Override
    @GetMapping("/{challengeSeq}")
    public ApiResponse<ChallengeDetailResponse> detail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer challengeSeq) {
        return ApiResponse.success(challengeQueryService.detail(user.userSeq(), challengeSeq));
    }

    /**
     * §7.3 좋아요 — <b>204 No Content</b>. 응답 본문이 없다(§1.2 봉투 예외).
     *
     * <p>§8.3 영상 · §9.5 댓글 · §10.2 공지 좋아요와 같은 계약이다.
     */
    @Override
    @PostMapping("/{challengeSeq}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void like(@AuthenticationPrincipal AuthenticatedUser user,
                     @PathVariable Integer challengeSeq) {
        challengeLikeService.like(user.userSeq(), challengeSeq);
    }

    /** §7.3 좋아요 해제 — 204. 누르지 않은 상태에서 불러도 204 다(멱등). */
    @Override
    @DeleteMapping("/{challengeSeq}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlike(@AuthenticationPrincipal AuthenticatedUser user,
                       @PathVariable Integer challengeSeq) {
        challengeLikeService.unlike(user.userSeq(), challengeSeq);
    }

}
