package com.ssafy.motif.coreapi.domain.home.controller;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.CursorResponse;
import com.ssafy.motif.coreapi.domain.home.docs.HomeControllerDocs;
import com.ssafy.motif.coreapi.domain.home.dto.HotVideoItem;
import com.ssafy.motif.coreapi.domain.home.service.HotVideoQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 홈 화면 API (명세 §6). Base URL 은 {@code /api/v1}.
 *
 * <p>홈은 세 섹션인데 여기에 오는 것은 둘뿐이다 — §6.2 Who's On TOP4 는
 * {@code /artists/ranking?size=4} 로 §5.1 을 그대로 재사용한다(명세 확정).
 *
 * <p><b>인증이 필요하다</b>(🔓 아님). 다만 §6.1 응답은 호출자에 따라 달라지지 않으므로
 * principal 을 받지 않는다 — 인증 강제는 {@code SecurityConfig} 의
 * {@code anyRequest().authenticated()} 가 한다.
 *
 * <p>경로가 {@code /api/v1/**} 이고 {@code /api/v1/admin/**} 이 아니므로 springdoc
 * {@code user} 그룹 문서에 자동으로 잡힌다({@code OpenApiConfig}).
 *
 * <p>Swagger 문서는 {@link HomeControllerDocs} 가 갖는다.
 */
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/home")
public class HomeController implements HomeControllerDocs {

    private final HotVideoQueryService hotVideoQueryService;

    /** §6.1 실시간 인기 참여 영상 — 커서, 스냅샷 순서 고정. */
    @Override
    @GetMapping("/hot-videos")
    public ApiResponse<CursorResponse<HotVideoItem>> hotVideos(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(hotVideoQueryService.hotVideos(cursor, size));
    }
}
