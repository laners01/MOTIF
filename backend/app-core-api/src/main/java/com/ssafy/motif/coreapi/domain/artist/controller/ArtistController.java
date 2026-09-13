package com.ssafy.motif.coreapi.domain.artist.controller;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.domain.artist.docs.ArtistControllerDocs;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistDetailResponse;
import com.ssafy.motif.coreapi.domain.artist.service.ArtistQueryService;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팬 앱 아티스트 조회 API (명세 §5.3). Base URL 은 {@code /api/v1}.
 *
 * <p><b>인증이 필요하다</b>(🔓 아님) — 응답에 "내" 상태
 * ({@code isSubscribed}·{@code subscribedDays}·{@code pass})가 들어 있어 호출자가
 * 누구인지 모르면 채울 수 없는 칸이 생긴다.
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
}
