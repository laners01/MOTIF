package com.ssafy.motif.coreapi.admin.actas;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.coreapi.admin.AdminPaths;
import com.ssafy.motif.coreapi.admin.audit.AdminAuditLogger;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.domain.challenge.service.ChallengeLikeService;
import com.ssafy.motif.coreapi.domain.comment.service.CommentLikeService;
import com.ssafy.motif.coreapi.domain.notice.service.NoticeLikeService;
import com.ssafy.motif.coreapi.domain.video.service.VideoLikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 좋아요 대행 (어드민 명세 §3.12 · ADMIN-11) — S15P21D104-408 · 573.
 *
 * <p>{@link AdminActAsCommentController} 와 같은 원칙이다 — 규칙은 사용자 서비스에 있고
 * 여기는 액터 해석과 위임, 그리고 감사 로그만 한다. 카운터·{@code has_artist_like}
 * 파급은 {@link VideoLikeService} 안에 이미 있다(§8.3).
 *
 * <p><b>좋아요는 FCM 이 없다</b>(ADMIN-11 파급 표) — 댓글 대행과 달리 알림이 나가지
 * 않는다. 좋아요 알림 자체가 서비스에 없다.
 *
 * <p>응답은 사용자 API 와 같은 <b>204</b> 다. 어드민 화면도 낙관적으로 반영하고 실패 시
 * 되돌린다({@code admin.js} 의 {@code api()} 가 204 를 이미 처리한다).
 *
 * <p>ADMIN-11 파급 표의 좋아요 3종(챌린지·참여 영상·공지)과 댓글 좋아요가 전부 여기 있다.
 */
@Tag(name = "Admin Act-As Like", description = "아티스트 대행 — 좋아요 (어드민 명세 §3.12)")
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping(AdminPaths.API + "/as-artist/{artistSeq}")
public class AdminActAsLikeController {

    private final ActAsArtistResolver actAsArtistResolver;
    private final VideoLikeService videoLikeService;
    private final ChallengeLikeService challengeLikeService;
    private final NoticeLikeService noticeLikeService;
    private final CommentLikeService commentLikeService;
    private final AdminAuditLogger auditLogger;

    @Operation(summary = "참여 영상 좋아요 대행",
            description = "§8.3 과 같다. 그 영상이 속한 챌린지의 아티스트로 누르면 "
                    + "has_artist_like 가 함께 선다 — §8.1 피드 정렬에 반영된다.")
    @PostMapping("/videos/{videoSeq}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void likeVideo(@AuthenticationPrincipal AuthenticatedAdmin admin,
                          @PathVariable Integer artistSeq,
                          @PathVariable Long videoSeq) {
        videoLikeService.like(actAsArtistResolver.actorUserSeq(artistSeq), videoSeq);
        auditLogger.actAs(admin, artistSeq, "LIKE_VIDEO", videoSeq, AdminAuditLogger.RESULT_OK);
    }

    @Operation(summary = "참여 영상 좋아요 해제 대행",
            description = "§8.3 과 같다. 멱등이라 누르지 않은 상태에서 불러도 204 다.")
    @DeleteMapping("/videos/{videoSeq}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlikeVideo(@AuthenticationPrincipal AuthenticatedAdmin admin,
                            @PathVariable Integer artistSeq,
                            @PathVariable Long videoSeq) {
        videoLikeService.unlike(actAsArtistResolver.actorUserSeq(artistSeq), videoSeq);
        auditLogger.actAs(admin, artistSeq, "UNLIKE_VIDEO", videoSeq, AdminAuditLogger.RESULT_OK);
    }

    @Operation(summary = "공지 좋아요 대행", description = "§10.2 와 같다. 204.")
    @PostMapping("/notices/{noticeSeq}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void likeNotice(@AuthenticationPrincipal AuthenticatedAdmin admin,
                           @PathVariable Integer artistSeq,
                           @PathVariable Integer noticeSeq) {
        noticeLikeService.like(actAsArtistResolver.actorUserSeq(artistSeq), noticeSeq);
        auditLogger.actAs(admin, artistSeq, "LIKE_NOTICE", noticeSeq, AdminAuditLogger.RESULT_OK);
    }

    @Operation(summary = "공지 좋아요 해제 대행", description = "§10.2 와 같다. 멱등이다.")
    @DeleteMapping("/notices/{noticeSeq}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlikeNotice(@AuthenticationPrincipal AuthenticatedAdmin admin,
                             @PathVariable Integer artistSeq,
                             @PathVariable Integer noticeSeq) {
        noticeLikeService.unlike(actAsArtistResolver.actorUserSeq(artistSeq), noticeSeq);
        auditLogger.actAs(admin, artistSeq, "UNLIKE_NOTICE", noticeSeq, AdminAuditLogger.RESULT_OK);
    }

    @Operation(summary = "댓글 좋아요 대행",
            description = "§9.5 와 같다. type 은 challenge · video · notice 다 — 댓글 PK 가 "
                    + "테이블마다 따로 도는 시퀀스라 번호만으로는 어느 것인지 알 수 없다.")
    @PostMapping("/comments/{type}/{commentSeq}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void likeComment(@AuthenticationPrincipal AuthenticatedAdmin admin,
                            @PathVariable Integer artistSeq,
                            @PathVariable String type,
                            @PathVariable Long commentSeq) {
        commentLikeService.like(actAsArtistResolver.actorUserSeq(artistSeq), type, commentSeq);
        auditLogger.actAs(admin, artistSeq, "LIKE_COMMENT", commentSeq, AdminAuditLogger.RESULT_OK);
    }

    @Operation(summary = "댓글 좋아요 해제 대행", description = "§9.5 와 같다. 멱등이다.")
    @DeleteMapping("/comments/{type}/{commentSeq}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlikeComment(@AuthenticationPrincipal AuthenticatedAdmin admin,
                              @PathVariable Integer artistSeq,
                              @PathVariable String type,
                              @PathVariable Long commentSeq) {
        commentLikeService.unlike(actAsArtistResolver.actorUserSeq(artistSeq), type, commentSeq);
        auditLogger.actAs(admin, artistSeq, "UNLIKE_COMMENT", commentSeq, AdminAuditLogger.RESULT_OK);
    }


    @Operation(summary = "챌린지 좋아요 대행", description = "§7.3 과 같다. 204.")
    @PostMapping("/challenges/{challengeSeq}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void likeChallenge(@AuthenticationPrincipal AuthenticatedAdmin admin,
                              @PathVariable Integer artistSeq,
                              @PathVariable Integer challengeSeq) {
        challengeLikeService.like(actAsArtistResolver.actorUserSeq(artistSeq), challengeSeq);
        auditLogger.actAs(admin, artistSeq, "LIKE_CHALLENGE", challengeSeq,
                AdminAuditLogger.RESULT_OK);
    }

    @Operation(summary = "챌린지 좋아요 해제 대행", description = "§7.3 과 같다. 멱등이다.")
    @DeleteMapping("/challenges/{challengeSeq}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlikeChallenge(@AuthenticationPrincipal AuthenticatedAdmin admin,
                                @PathVariable Integer artistSeq,
                                @PathVariable Integer challengeSeq) {
        challengeLikeService.unlike(actAsArtistResolver.actorUserSeq(artistSeq), challengeSeq);
        auditLogger.actAs(admin, artistSeq, "UNLIKE_CHALLENGE", challengeSeq,
                AdminAuditLogger.RESULT_OK);
    }

}
