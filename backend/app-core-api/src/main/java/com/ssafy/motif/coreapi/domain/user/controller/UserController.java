package com.ssafy.motif.coreapi.domain.user.controller;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.domain.user.docs.UserControllerDocs;
import com.ssafy.motif.coreapi.domain.user.dto.UserMeResponse;
import com.ssafy.motif.coreapi.domain.user.service.UserQueryService;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자·마이페이지 API (명세 §4). Base URL 은 {@code /api/v1}.
 *
 * <p>지금은 §4.1 하나다 — §4.2 닉네임 수정·§4.3/4.4 프로필 이미지·§4.6 남의 프로필·
 * §4.7 구독 목록·§4.8 영상 목록·§4.9 활동은 각각 별개 티켓이라 여기 매핑이 없다.
 *
 * <p>Swagger 문서는 {@link UserControllerDocs} 가 갖는다.
 */
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController implements UserControllerDocs {

    private final UserQueryService userQueryService;

    /** §4.1 마이페이지 원콜. */
    @Override
    @GetMapping("/me")
    public ApiResponse<UserMeResponse> me(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(userQueryService.me(user.userSeq()));
    }
}
