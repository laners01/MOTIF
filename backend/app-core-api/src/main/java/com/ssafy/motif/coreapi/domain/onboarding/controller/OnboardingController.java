package com.ssafy.motif.coreapi.domain.onboarding.controller;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.domain.onboarding.docs.OnboardingControllerDocs;
import com.ssafy.motif.coreapi.domain.onboarding.dto.OnboardingRequest;
import com.ssafy.motif.coreapi.domain.onboarding.dto.OnboardingResponse;
import com.ssafy.motif.coreapi.domain.onboarding.service.OnboardingService;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 온보딩 API (명세 §3.3). Base URL 은 {@code /api/v1} (명세 §1.1).
 *
 * <p>§3.1(인기 아티스트)·§3.2(검색)는 §5.1·§5.2 를 온보딩 화면에서 재사용하는 것이라
 * 별도 엔드포인트가 없다 — 이 컨트롤러에는 완료 API 하나만 있다.
 *
 * <p>Swagger 문서는 {@link OnboardingControllerDocs} 가 갖는다.
 */
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/onboarding")
public class OnboardingController implements OnboardingControllerDocs {

    private final OnboardingService onboardingService;

    /** §3.3 온보딩 완료 — 구독 일괄 생성 + 닉네임 확정. */
    @Override
    @PostMapping
    public ApiResponse<OnboardingResponse> complete(@AuthenticationPrincipal AuthenticatedUser user,
                                                    @Valid @RequestBody OnboardingRequest request) {
        return ApiResponse.success(onboardingService.complete(user.userSeq(), request));
    }
}
