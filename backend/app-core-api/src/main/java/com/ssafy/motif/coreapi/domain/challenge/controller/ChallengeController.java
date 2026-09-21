package com.ssafy.motif.coreapi.domain.challenge.controller;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.domain.challenge.docs.ChallengeControllerDocs;
import com.ssafy.motif.common.response.CursorResponse;
import com.ssafy.motif.coreapi.domain.challenge.dto.ChallengeDetailResponse;
import com.ssafy.motif.coreapi.domain.challenge.dto.ChallengeFeedItem;
import com.ssafy.motif.coreapi.domain.challenge.service.ChallengeLikeService;
import com.ssafy.motif.coreapi.domain.challenge.service.ChallengeQueryService;
import com.ssafy.motif.coreapi.domain.view.ViewService;
import com.ssafy.motif.coreapi.domain.view.ViewTarget;
import com.ssafy.motif.coreapi.domain.view.dto.ViewRequest;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팬 앱 챌린지 API (명세 §7.1·§7.2·§7.3·§7.4). Base URL 은 {@code /api/v1}.
 *
 * <p><b>인증이 필요하다</b>(🔓 아님) — 응답의 {@code isLiked}·{@code isSubscribed} 가
 * 호출자 기준이다. §7.4 조회 이벤트는 호출자를 쓰지 않지만 {@code SecurityConfig} 의
 * {@code anyRequest().authenticated()} 로 함께 막힌다.
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
    private final ViewService viewService;

    /**
     * §7.4 조회 이벤트 — <b>202 Accepted</b>. 챌린지별 조회수를 묶어 받는다.
     *
     * <p>경로에 {@code {challengeSeq}} 가 없다(2026-09-21 재정의 — ADR-0016). §8.5 와
     * <b>같은 요청 DTO·같은 서비스</b>를 쓰고 대상 종류만 다르다. 리터럴 세그먼트라
     * {@code /{challengeSeq}} 계열 매핑과 겹치지 않는다.
     *
     * <p><b>호출자를 받지 않는다.</b> 1회 인정 기준이 미결이라(원본 §16 #3) 지금은
     * 사용자별로 가르는 것이 없다 — 쓰지도 않을 파라미터를 미리 두지 않는다.
     */
    @Override
    @PostMapping("/view")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Void> view(@Valid @RequestBody ViewRequest request) {
        viewService.accumulate(ViewTarget.CHALLENGE, request.views());
        return ApiResponse.success();
    }

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
