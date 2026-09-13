package com.ssafy.motif.coreapi.domain.artist.controller;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.CursorResponse;
import com.ssafy.motif.common.response.ListResponse;
import com.ssafy.motif.coreapi.domain.artist.docs.ArtistControllerDocs;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistChallengeResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistDetailResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistNoticeResponse;
import com.ssafy.motif.coreapi.domain.artist.service.ArtistQueryService;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팬 앱 아티스트 조회 API (명세 §5.3 · §5.4 · §5.5). Base URL 은 {@code /api/v1}.
 *
 * <p><b>셋 다 인증이 필요하다</b>(🔓 아님) — §5.3 은 {@code isSubscribed}·
 * {@code subscribedDays}·{@code pass} 를, §5.5 는 {@code isLiked} 를 호출자 기준으로
 * 채운다. §5.4 만 항목에 내 상태가 없지만 팬 앱 로그인 뒤 화면이라 공개로 열지 않는다.
 *
 * <p>§5.1 랭킹·§5.2 검색·§5.9 자동완성·§5.6·§5.7 구독은 이 티켓 범위가 아니다 —
 * 같은 {@code /artists} 경로라도 별개 티켓이라 여기 매핑이 없다.
 *
 * <p>경로가 {@code /api/v1/**} 이고 {@code /api/v1/admin/**} 이 아니므로 springdoc
 * {@code user} 그룹 문서에 자동으로 잡힌다({@code OpenApiConfig}).
 *
 * <p>Swagger 문서는 {@link ArtistControllerDocs} 가 갖는다.
 */
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/artists")
public class ArtistController implements ArtistControllerDocs {

    private final ArtistQueryService artistQueryService;

    /** §5.3 아티스트 상세. */
    @Override
    @GetMapping("/{artistSeq}")
    public ApiResponse<ArtistDetailResponse> detail(@AuthenticationPrincipal AuthenticatedUser user,
                                                    @PathVariable Integer artistSeq) {
        return ApiResponse.success(artistQueryService.detail(user.userSeq(), artistSeq));
    }

    /**
     * §5.4 아티스트 챌린지 그리드 — 비페이지 전량(회의 확정 R2).
     *
     * <p>principal 을 받지 않는다 — §5.4 항목에는 "내" 상태 칸이 없다
     * ({@code challengeSeq}·{@code thumbnailUrl}·{@code viewCount} 뿐). 인증 자체는
     * {@code SecurityConfig} 의 {@code anyRequest().authenticated()} 가 걸며, 쓰지도
     * 않을 값을 받아 두면 "여기서 뭔가 사용자별로 갈린다"는 오해만 남는다.
     */
    @Override
    @GetMapping("/{artistSeq}/challenges")
    public ApiResponse<ListResponse<ArtistChallengeResponse>> challenges(
            @PathVariable Integer artistSeq) {
        return ApiResponse.success(artistQueryService.challenges(artistSeq));
    }

    /**
     * §5.5 아티스트 공지 목록 — <b>커서</b>(§1.9). 최초 호출은 {@code cursor} 를 생략한다.
     *
     * <p>어드민 목록(§1.5 offset)과 달리 {@code page} 파라미터가 없다 — 공지는 실시간
     * 유입으로 offset 이 밀리는 대상이라 §1.9 가 커서로 지정한 목록이다.
     */
    @Override
    @GetMapping("/{artistSeq}/notices")
    public ApiResponse<CursorResponse<ArtistNoticeResponse>> notices(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer artistSeq,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                artistQueryService.notices(user.userSeq(), artistSeq, cursor, size));
    }
}
