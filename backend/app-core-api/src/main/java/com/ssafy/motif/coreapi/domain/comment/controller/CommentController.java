package com.ssafy.motif.coreapi.domain.comment.controller;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.domain.comment.docs.CommentControllerDocs;
import com.ssafy.motif.coreapi.domain.comment.dto.CommentCreateRequest;
import com.ssafy.motif.coreapi.domain.comment.dto.CommentCreateResponse;
import com.ssafy.motif.coreapi.domain.comment.dto.CommentDeleteResponse;
import com.ssafy.motif.coreapi.domain.comment.dto.CommentItem;
import com.ssafy.motif.coreapi.domain.comment.dto.ReplyItem;
import com.ssafy.motif.coreapi.domain.comment.service.CommentCommandService;
import com.ssafy.motif.coreapi.domain.comment.service.CommentQueryService;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 댓글 API (명세 §9.1). Base URL 은 {@code /api/v1}.
 *
 * <p><b>경로가 세 리소스 하위로 갈리지만 컨트롤러는 하나다.</b> 명세 §9 가 세 대상의
 * 댓글을 하나의 계약으로 못박았으므로 응답 형태·정렬·비노출 규칙이 한자리에 있어야
 * 한다. {@code ChallengeController}·{@code VideoController} 에 나눠 넣으면 같은 계약이
 * 세 파일로 흩어지고, 그 셋은 서로 다른 티켓이 병렬로 고치는 파일이다.
 *
 * <p>개별 조작은 {@code /comments/{type}/{commentSeq}} 를 쓴다 — §9.2 답글이 여기 있고
 * §9.4 삭제 · §9.5 좋아요 · §9.6 신고는 각 티켓이 더한다.
 *
 * <p><b>인증이 필요하다</b>(🔓 아님) — {@code isLiked}·{@code isMine} 뿐 아니라
 * <b>정렬 2순위</b>가 호출자 기준이다.
 *
 * <p>Swagger 문서는 {@link CommentControllerDocs} 가 갖는다.
 */
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class CommentController implements CommentControllerDocs {

    private final CommentQueryService commentQueryService;
    private final CommentCommandService commentCommandService;

    /** §9.1 챌린지 루트 댓글. */
    @Override
    @GetMapping("/challenges/{challengeSeq}/comments")
    public ApiResponse<PageResponse<CommentItem>> challengeComments(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer challengeSeq,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                commentQueryService.challengeComments(user.userSeq(), challengeSeq, page, size));
    }

    /** §9.1 참여 영상 루트 댓글 — 잠긴 영상은 목록도 403 이다. */
    @Override
    @GetMapping("/videos/{videoSeq}/comments")
    public ApiResponse<PageResponse<CommentItem>> videoComments(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long videoSeq,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                commentQueryService.videoComments(user.userSeq(), videoSeq, page, size));
    }

    /** §9.1 공지 루트 댓글. */
    @Override
    @GetMapping("/notices/{noticeSeq}/comments")
    public ApiResponse<PageResponse<CommentItem>> noticeComments(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer noticeSeq,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                commentQueryService.noticeComments(user.userSeq(), noticeSeq, page, size));
    }

    /**
     * §9.2 답글 펼치기.
     *
     * <p>{@code type} 을 {@code String} 으로 받는 이유는 {@code CommentTargetType} 에
     * 적었다 — 모르는 값은 400 이 아니라 404 여야 한다.
     */
    @Override
    @GetMapping("/comments/{type}/{commentSeq}/replies")
    public ApiResponse<PageResponse<ReplyItem>> replies(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String type,
            @PathVariable Long commentSeq,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                commentQueryService.replies(user.userSeq(), type, commentSeq, page, size));
    }

    /** §9.3 챌린지 댓글·답글 작성. */
    @Override
    @PostMapping("/challenges/{challengeSeq}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CommentCreateResponse> writeOnChallenge(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer challengeSeq,
            @Valid @RequestBody CommentCreateRequest request) {
        return ApiResponse.success(
                commentCommandService.writeOnChallenge(user.userSeq(), challengeSeq, request));
    }

    /** §9.3 참여 영상 댓글·답글 작성 — 잠긴 영상은 403 이다. */
    @Override
    @PostMapping("/videos/{videoSeq}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CommentCreateResponse> writeOnVideo(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long videoSeq,
            @Valid @RequestBody CommentCreateRequest request) {
        return ApiResponse.success(
                commentCommandService.writeOnVideo(user.userSeq(), videoSeq, request));
    }

    /** §9.3 공지 댓글·답글 작성. */
    @Override
    @PostMapping("/notices/{noticeSeq}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CommentCreateResponse> writeOnNotice(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer noticeSeq,
            @Valid @RequestBody CommentCreateRequest request) {
        return ApiResponse.success(
                commentCommandService.writeOnNotice(user.userSeq(), noticeSeq, request));
    }

    /** §9.4 삭제 — 본인 댓글만. 루트를 지우면 직계 답글도 함께 지워진다. */
    @Override
    @DeleteMapping("/comments/{type}/{commentSeq}")
    public ApiResponse<CommentDeleteResponse> delete(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String type,
            @PathVariable Long commentSeq) {
        return ApiResponse.success(
                commentCommandService.delete(user.userSeq(), type, commentSeq));
    }
}
