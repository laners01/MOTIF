package com.ssafy.motif.coreapi.domain.motif.controller;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.CursorResponse;
import com.ssafy.motif.coreapi.domain.motif.docs.MotifResultControllerDocs;
import com.ssafy.motif.coreapi.domain.motif.dto.MotifResultDetailResponse;
import com.ssafy.motif.coreapi.domain.motif.dto.MotifResultItem;
import com.ssafy.motif.coreapi.domain.motif.service.MotifResultQueryService;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팬 앱 보관함 API (명세 §11.5 · §11.6). Base URL 은 {@code /api/v1}.
 *
 * <p>같은 {@code /api/v1/motif} 아래지만 에셋({@code /motif/assets})·숍
 * ({@code /motif/shop})과 경로가 달라 컨트롤러가 갈린다 — 이 레포는 경로가 컨트롤러를
 * 정한다.
 *
 * <p><b>인증이 필요하다</b> — 대상이 "내 결과물" 이다.
 *
 * <p>§11.8 수정·삭제는 아직 없다 — 같은 {@code /motif/results} 경로라도 별개 티켓이라
 * 여기 매핑이 없다.
 *
 * <p>경로가 {@code /api/v1/**} 이고 {@code /api/v1/admin/**} 이 아니므로 springdoc
 * {@code user} 그룹 문서에 자동으로 잡힌다({@code OpenApiConfig}).
 *
 * <p>Swagger 문서는 {@link MotifResultControllerDocs} 가 갖는다.
 */
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/motif/results")
public class MotifResultController implements MotifResultControllerDocs {

    private final MotifResultQueryService motifResultQueryService;

    /**
     * §11.5 보관함 목록 — <b>커서</b>(§1.9). 최초 호출은 {@code cursor} 를 생략한다.
     *
     * <p>offset 이 아닌 이유는 보관함이 <b>앞쪽에서 늘어나는</b> 목록이기 때문이다.
     * 합성이 끝날 때마다 새 결과물이 맨 위에 붙어 페이지가 밀린다.
     *
     * <p>{@code artistSeq} 는 §5.8 "내 모티프 전체 보기" 의 진입점이다.
     */
    @Override
    @GetMapping
    public ApiResponse<CursorResponse<MotifResultItem>> results(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Integer artistSeq,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                motifResultQueryService.results(user.userSeq(), artistSeq, cursor, size));
    }

    /**
     * §11.6 결과물 상세 — 재생 화면.
     *
     * <p>목록(§11.5)이 주지 않는 재생 주소·문구·에셋이 여기 있다. 서명은 두 종류가
     * 섞여 나간다 — 영상은 프리픽스, 썸네일은 단일 파일(ADR-0012).
     */
    @Override
    @GetMapping("/{resultSeq}")
    public ApiResponse<MotifResultDetailResponse> detail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long resultSeq) {
        return ApiResponse.success(motifResultQueryService.detail(user.userSeq(), resultSeq));
    }
}
