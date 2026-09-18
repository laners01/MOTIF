package com.ssafy.motif.coreapi.domain.user.controller;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.CursorResponse;
import com.ssafy.motif.common.response.ListResponse;
import com.ssafy.motif.coreapi.domain.user.docs.UserControllerDocs;
import com.ssafy.motif.coreapi.domain.user.dto.ActivityItem;
import com.ssafy.motif.coreapi.domain.user.dto.MotifPassItem;
import com.ssafy.motif.coreapi.domain.user.dto.NicknameUpdateRequest;
import com.ssafy.motif.coreapi.domain.user.dto.NicknameUpdateResponse;
import com.ssafy.motif.coreapi.domain.user.dto.ProfileImagePresignRequest;
import com.ssafy.motif.coreapi.domain.user.dto.ProfileImagePresignResponse;
import com.ssafy.motif.coreapi.domain.user.dto.ProfileImageUpdateRequest;
import com.ssafy.motif.coreapi.domain.user.dto.ProfileImageUpdateResponse;
import com.ssafy.motif.coreapi.domain.user.dto.SubscriptionCardItem;
import com.ssafy.motif.coreapi.domain.user.dto.UserMeResponse;
import com.ssafy.motif.coreapi.domain.user.dto.UserProfileResponse;
import com.ssafy.motif.coreapi.domain.user.dto.VideoPreviewItem;
import com.ssafy.motif.coreapi.domain.user.service.UserActivityQueryService;
import com.ssafy.motif.coreapi.domain.user.service.UserCommandService;
import com.ssafy.motif.coreapi.domain.user.service.UserMotifPassQueryService;
import com.ssafy.motif.coreapi.domain.user.service.UserProfileImageService;
import com.ssafy.motif.coreapi.domain.user.service.UserQueryService;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자·마이페이지 API (명세 §4 · §11.9). Base URL 은 {@code /api/v1}.
 *
 * <p>{@code /me} 와 {@code /{userSeq}} 가 한 컨트롤러에 있다. 경로 세그먼트가 갈리므로
 * 충돌하지 않고, 응답도 전혀 다르다 — §4.1 은 대표 모티프·영상 그리드를, §4.6 은 구독
 * 아티스트를 준다.
 *
 * <p>Swagger 문서는 {@link UserControllerDocs} 가 갖는다.
 */
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController implements UserControllerDocs {

    private final UserQueryService userQueryService;
    private final UserActivityQueryService userActivityQueryService;
    private final UserCommandService userCommandService;
    private final UserProfileImageService userProfileImageService;
    private final UserMotifPassQueryService userMotifPassQueryService;

    /** §4.1 마이페이지 원콜. */
    @Override
    @GetMapping("/me")
    public ApiResponse<UserMeResponse> me(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(userQueryService.me(user.userSeq()));
    }

    /** §4.7 구독 아티스트 카드 — 비페이지 전량. 숍 화면의 아티스트 섹션도 이걸 쓴다. */
    @Override
    @GetMapping("/me/subscriptions")
    public ApiResponse<ListResponse<SubscriptionCardItem>> mySubscriptions(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(userQueryService.subscriptions(user.userSeq()));
    }

    /**
     * §11.9 내 패스 일람 — 비페이지 전량.
     *
     * <p><b>§11 도메인이지만 여기 있다</b> — 경로가 {@code /users/me} 아래이고, 이 레포는
     * 경로가 컨트롤러를 정한다(§11.4 를 {@code ArtistController} 에 넣은 것과 같은 기준).
     * 대신 서비스는 {@link UserQueryService} 에 섞지 않고 따로 뒀다.
     *
     * <p>결제 이력 성격이라 <b>만료·환불된 패스도 함께</b> 온다. "지금 쓸 수 있나" 는
     * §11.4·§4.7 의 {@code passActive} 가 답한다.
     */
    @Override
    @GetMapping("/me/motif-passes")
    public ApiResponse<ListResponse<MotifPassItem>> myMotifPasses(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(userMotifPassQueryService.passes(user.userSeq()));
    }

    /** §4.6 타 사용자 프로필 — 뷰어에서 작성자를 눌렀을 때의 Bottom Sheet. */
    @Override
    @GetMapping("/{userSeq}/profile")
    public ApiResponse<UserProfileResponse> profile(@PathVariable Integer userSeq) {
        return ApiResponse.success(userQueryService.profile(userSeq));
    }

    /** §4.8 내 챌린지 그리드 — 비페이지 전량. 첫 화면 9개는 §4.1 이 이미 줬다. */
    @Override
    @GetMapping("/me/videos")
    public ApiResponse<ListResponse<VideoPreviewItem>> myVideos(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(userQueryService.myVideos(user.userSeq()));
    }

    /**
     * §4.9 내 활동 — <b>커서</b>(§1.9). 최초 호출은 {@code cursor} 를 생략한다.
     *
     * <p>offset 이 아닌 이유는 이 목록이 <b>앞쪽에서 늘어나기</b> 때문이다. 좋아요·댓글을
     * 하나 더 할 때마다 그 카드가 맨 위로 올라와 페이지가 밀린다.
     */
    @Override
    @GetMapping("/me/activities")
    public ApiResponse<CursorResponse<ActivityItem>> myActivities(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                userActivityQueryService.activities(user.userSeq(), cursor, size));
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
