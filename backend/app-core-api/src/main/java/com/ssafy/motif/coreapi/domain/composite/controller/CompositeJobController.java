package com.ssafy.motif.coreapi.domain.composite.controller;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.domain.composite.docs.CompositeJobControllerDocs;
import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobCreateRequest;
import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobCreateResponse;
import com.ssafy.motif.coreapi.domain.composite.service.CompositeJobCommandService;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 합성 파이프라인 API (명세 §13). Base URL 은 {@code /api/v1}.
 *
 * <p><b>챌린지와 모티프가 같은 엔드포인트를 쓴다</b> — {@code jobType} 이 가른다. 별도
 * API 를 만들지 않는 이유는 잡 행·원본 키·상태 전이·FCM 이 전부 같기 때문이다.
 *
 * <p><b>전부 인증이 필요하다</b> — 잡은 호출자 것이고 MOTIF 의 권한 판정도 호출자 기준이다.
 *
 * <p>경로가 {@code /api/v1/**} 이고 {@code /api/v1/admin/**} 이 아니므로 springdoc
 * {@code user} 그룹 문서에 자동으로 잡힌다({@code OpenApiConfig}).
 *
 * <p>Swagger 문서는 {@link CompositeJobControllerDocs} 가 갖는다.
 */
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/composite-jobs")
public class CompositeJobController implements CompositeJobControllerDocs {

    private final CompositeJobCommandService compositeJobCommandService;

    /**
     * §13.1 잡 생성 + 촬영본 업로드 URL 발급 — <b>201 Created</b>.
     *
     * <p>{@code @Valid} 는 {@code jobType} 필수·{@code participantMask} 음수 금지만 본다.
     * 형태(CHALLENGE 면 {@code challengeSeq}, MOTIF 면 {@code assetSeq})는 서비스가 판정해
     * 같은 400 봉투로 낸다.
     */
    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CompositeJobCreateResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CompositeJobCreateRequest request) {
        return ApiResponse.success(compositeJobCommandService.create(user.userSeq(), request));
    }
}
