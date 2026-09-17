package com.ssafy.motif.coreapi.admin.actas;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.admin.AdminPaths;
import com.ssafy.motif.coreapi.admin.audit.AdminAuditLogger;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.domain.comment.dto.CommentCreateRequest;
import com.ssafy.motif.coreapi.domain.comment.dto.CommentCreateResponse;
import com.ssafy.motif.coreapi.domain.comment.dto.CommentDeleteResponse;
import com.ssafy.motif.coreapi.domain.comment.dto.CommentItem;
import com.ssafy.motif.coreapi.domain.comment.dto.ReplyItem;
import com.ssafy.motif.coreapi.domain.comment.service.CommentCommandService;
import com.ssafy.motif.coreapi.domain.comment.service.CommentQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
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
 * 댓글·답글 대행 (어드민 명세 §3.12) — S15P21D104-409.
 *
 * <p><b>여기에는 규칙이 없다.</b> 대행은 "{@code artistSeq} 를 액터 {@code user_seq} 로
 * 바꿔 사용자 API 와 같은 서비스 메서드를 부르는 것"이라고 명세가 정했고, 그래서 이
 * 클래스는 해석과 위임만 한다. 깊이 2단·부모-대상 일치·잠긴 영상 403·본인 댓글만
 * 삭제·{@code comment_count}·{@code has_artist_comment} 파급은 전부
 * {@link CommentCommandService} 안에 이미 있다 — 대행용으로 다시 쓰면 두 경로의 규칙이
 * 갈라지고, 갈라진 쪽은 아무도 테스트하지 않는다.
 *
 * <p><b>정렬 "내 댓글 1순위" 도 공짜로 맞는다</b>(§9.1) — {@link CommentQueryService} 가
 * {@code viewerSeq} 로 판정하므로 아티스트를 액터로 넘기면 아티스트 댓글이 1순위가 된다.
 * 대행 화면이 자기 댓글을 맨 위에서 찾게 하려는 것이 명세의 의도다.
 *
 * <p><b>감사 로그는 쓰기에만 붙는다</b>(§1.3) — 대행 수행자를 DB 에 남길 자리가 없어
 * 로그가 갈음하는 것이고, 조회는 남길 행 자체가 없다. 기록은 성공 뒤다: 실패는 거절이
 * 업무적으로 의미 있는 지점에서만 남기는 것이 이 레포의 관례고(예 {@code GROUP_IN_USE}),
 * 대행에는 그런 지점이 없다 — 삭제된 아티스트 404 는 {@link ActAsArtistResolver} 가 낸다.
 *
 * <p>감사 호출이 서비스가 아니라 컨트롤러에 있는 것은 대행에 도메인 서비스가 없기
 * 때문이다. 로그 한 줄을 위해 위임만 하는 서비스를 세우면 규칙이 없는 계층이 하나 늘고,
 * 다음 사람은 거기에 규칙을 넣는다.
 *
 * <p>좋아요 대행은 573 이다 — 이 컨트롤러가 아니다.
 */
@Tag(name = "Admin Act-As Comment", description = "아티스트 대행 — 댓글·답글 (어드민 명세 §3.12)")
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping(AdminPaths.API + "/as-artist/{artistSeq}")
public class AdminActAsCommentController {

    private final ActAsArtistResolver actAsArtistResolver;
    private final CommentQueryService commentQueryService;
    private final CommentCommandService commentCommandService;
    private final AdminAuditLogger auditLogger;

    @Operation(summary = "챌린지 댓글 목록 대행", description = "§9.1 과 같다. 정렬 1순위가 아티스트 본인 댓글이다.")
    @GetMapping("/challenges/{challengeSeq}/comments")
    public ApiResponse<PageResponse<CommentItem>> challengeComments(
            @PathVariable Integer artistSeq,
            @PathVariable Integer challengeSeq,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(commentQueryService.challengeComments(
                actAsArtistResolver.actorUserSeq(artistSeq), challengeSeq, page, size));
    }

    @Operation(summary = "참여 영상 댓글 목록 대행",
            description = "§9.1 과 같다. PRIVATE 영상은 대행 대상이 아니라 404 다(§3.12 실패표).")
    @GetMapping("/videos/{videoSeq}/comments")
    public ApiResponse<PageResponse<CommentItem>> videoComments(
            @PathVariable Integer artistSeq,
            @PathVariable Long videoSeq,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(commentQueryService.videoComments(
                actAsArtistResolver.actorUserSeq(artistSeq), videoSeq, page, size));
    }

    @Operation(summary = "공지 댓글 목록 대행", description = "§9.1 과 같다.")
    @GetMapping("/notices/{noticeSeq}/comments")
    public ApiResponse<PageResponse<CommentItem>> noticeComments(
            @PathVariable Integer artistSeq,
            @PathVariable Integer noticeSeq,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(commentQueryService.noticeComments(
                actAsArtistResolver.actorUserSeq(artistSeq), noticeSeq, page, size));
    }

    @Operation(summary = "답글 펼치기 대행", description = "§9.2 와 같다.")
    @GetMapping("/comments/{type}/{commentSeq}/replies")
    public ApiResponse<PageResponse<ReplyItem>> replies(
            @PathVariable Integer artistSeq,
            @PathVariable String type,
            @PathVariable Long commentSeq,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(commentQueryService.replies(
                actAsArtistResolver.actorUserSeq(artistSeq), type, commentSeq, page, size));
    }

    @Operation(summary = "챌린지 댓글·답글 작성 대행", description = "§9.3 과 같다. 201.")
    @PostMapping("/challenges/{challengeSeq}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CommentCreateResponse> writeOnChallenge(
            @AuthenticationPrincipal AuthenticatedAdmin admin,
            @PathVariable Integer artistSeq,
            @PathVariable Integer challengeSeq,
            @Valid @RequestBody CommentCreateRequest request) {
        CommentCreateResponse response = commentCommandService.writeOnChallenge(
                actAsArtistResolver.actorUserSeq(artistSeq), challengeSeq, request);
        auditLogger.actAs(admin, artistSeq, writeAction(request, "CHALLENGE"),
                response.commentSeq(), AdminAuditLogger.RESULT_OK);
        return ApiResponse.success(response);
    }

    @Operation(summary = "참여 영상 댓글·답글 작성 대행",
            description = "§9.3 과 같다. 자기 챌린지의 영상이면 has_artist_comment 가 함께 선다.")
    @PostMapping("/videos/{videoSeq}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CommentCreateResponse> writeOnVideo(
            @AuthenticationPrincipal AuthenticatedAdmin admin,
            @PathVariable Integer artistSeq,
            @PathVariable Long videoSeq,
            @Valid @RequestBody CommentCreateRequest request) {
        CommentCreateResponse response = commentCommandService.writeOnVideo(
                actAsArtistResolver.actorUserSeq(artistSeq), videoSeq, request);
        auditLogger.actAs(admin, artistSeq, writeAction(request, "VIDEO"),
                response.commentSeq(), AdminAuditLogger.RESULT_OK);
        return ApiResponse.success(response);
    }

    @Operation(summary = "공지 댓글·답글 작성 대행", description = "§9.3 과 같다. 201.")
    @PostMapping("/notices/{noticeSeq}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CommentCreateResponse> writeOnNotice(
            @AuthenticationPrincipal AuthenticatedAdmin admin,
            @PathVariable Integer artistSeq,
            @PathVariable Integer noticeSeq,
            @Valid @RequestBody CommentCreateRequest request) {
        CommentCreateResponse response = commentCommandService.writeOnNotice(
                actAsArtistResolver.actorUserSeq(artistSeq), noticeSeq, request);
        auditLogger.actAs(admin, artistSeq, writeAction(request, "NOTICE"),
                response.commentSeq(), AdminAuditLogger.RESULT_OK);
        return ApiResponse.success(response);
    }

    @Operation(summary = "댓글 삭제 대행",
            description = "§9.4 와 같다 — <b>아티스트 본인 댓글만</b>이다. 타인 댓글은 403 이고, "
                    + "그건 신고 처리(§3.13)로 지운다.")
    @DeleteMapping("/comments/{type}/{commentSeq}")
    public ApiResponse<CommentDeleteResponse> delete(
            @AuthenticationPrincipal AuthenticatedAdmin admin,
            @PathVariable Integer artistSeq,
            @PathVariable String type,
            @PathVariable Long commentSeq) {
        CommentDeleteResponse response = commentCommandService.delete(
                actAsArtistResolver.actorUserSeq(artistSeq), type, commentSeq);
        auditLogger.actAs(admin, artistSeq, "DELETE_COMMENT", commentSeq,
                AdminAuditLogger.RESULT_OK);
        return ApiResponse.success(response);
    }

    /**
     * 답글은 별도 action 이다 — 대행 화면이 "댓글을 몇 개 달았나" 와 "남의 글에 몇 번
     * 끼어들었나" 를 따로 세게 된다(§1.3 {@code action}).
     *
     * <p>{@code AdminAuditLogger.actAs} 에는 {@code targetType} 칸이 없다 — 대상 종류는
     * action 에 붙는다({@code LIKE_VIDEO} 와 같은 모양, 명세 §1.3 예시).
     */
    private static String writeAction(CommentCreateRequest request, String targetType) {
        return (request.parentCommentSeq() == null ? "CREATE_COMMENT_" : "CREATE_REPLY_")
                + targetType;
    }
}
