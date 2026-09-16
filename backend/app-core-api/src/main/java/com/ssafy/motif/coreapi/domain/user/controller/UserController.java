package com.ssafy.motif.coreapi.domain.user.controller;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.ListResponse;
import com.ssafy.motif.coreapi.domain.user.docs.UserControllerDocs;
import com.ssafy.motif.coreapi.domain.user.dto.NicknameUpdateRequest;
import com.ssafy.motif.coreapi.domain.user.dto.NicknameUpdateResponse;
import com.ssafy.motif.coreapi.domain.user.dto.ProfileImagePresignRequest;
import com.ssafy.motif.coreapi.domain.user.dto.ProfileImagePresignResponse;
import com.ssafy.motif.coreapi.domain.user.dto.ProfileImageUpdateRequest;
import com.ssafy.motif.coreapi.domain.user.dto.ProfileImageUpdateResponse;
import com.ssafy.motif.coreapi.domain.user.dto.UserMeResponse;
import com.ssafy.motif.coreapi.domain.user.dto.VideoPreviewItem;
import com.ssafy.motif.coreapi.domain.user.service.UserCommandService;
import com.ssafy.motif.coreapi.domain.user.service.UserProfileImageService;
import com.ssafy.motif.coreapi.domain.user.service.UserQueryService;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자·마이페이지 API (명세 §4). Base URL 은 {@code /api/v1}.
 *
 * <p>지금은 §4.1·§4.2·§4.3·§4.4·§4.8 이다 — §4.6 남의 프로필·§4.7 구독 목록·§4.9 활동은
 * 각각 별개 티켓이라 여기 매핑이 없다.
 *
 * <p>Swagger 문서는 {@link UserControllerDocs} 가 갖는다.
 */
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController implements UserControllerDocs {

    private final UserQueryService userQueryService;
    private final UserCommandService userCommandService;
    private final UserProfileImageService userProfileImageService;

    /** §4.1 마이페이지 원콜. */
    @Override
    @GetMapping("/me")
    public ApiResponse<UserMeResponse> me(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(userQueryService.me(user.userSeq()));
    }

    /** §4.8 내 챌린지 그리드 — 비페이지 전량. 첫 화면 9개는 §4.1 이 이미 줬다. */
    @Override
    @GetMapping("/me/videos")
    public ApiResponse<ListResponse<VideoPreviewItem>> myVideos(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(userQueryService.myVideos(user.userSeq()));
    }

    /** §4.2 닉네임 수정 — 형식 검증만, 중복 확인은 없다. */
    @Override
    @PatchMapping("/me/nickname")
    public ApiResponse<NicknameUpdateResponse> updateNickname(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody NicknameUpdateRequest request) {
        return ApiResponse.success(
                userCommandService.changeNickname(user.userSeq(), request.nickname()));
    }

    /** §4.3 프로필 이미지 업로드 URL 발급 — 바이트는 앱 → S3 직행이다. */
    @Override
    @PostMapping("/me/profile-image/presign")
    public ApiResponse<ProfileImagePresignResponse> presignProfileImage(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ProfileImagePresignRequest request) {
        return ApiResponse.success(userProfileImageService.presign(
                user.userSeq(), request.contentType(), request.contentLength()));
    }

    /** §4.4 프로필 이미지 반영 — PUT 업로드를 마친 뒤 호출한다. */
    @Override
    @PatchMapping("/me/profile-image")
    public ApiResponse<ProfileImageUpdateResponse> updateProfileImage(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ProfileImageUpdateRequest request) {
        return ApiResponse.success(
                userCommandService.changeProfileImage(user.userSeq(), request.imageKey()));
    }
}
