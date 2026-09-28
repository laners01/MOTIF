package com.ssafy.motif.coreapi.domain.notice.controller;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.coreapi.domain.notice.docs.NoticeControllerDocs;
import com.ssafy.motif.coreapi.domain.notice.service.NoticeLikeService;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
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
 * 공지 API (명세 §10). Base URL 은 {@code /api/v1}.
 *
 * <p><b>이 컨트롤러에 조회가 없는 것은 의도다.</b> 공지 상세 API 는 v0.8 에서 삭제됐고
 * (구 §10.1), 화면이 필요로 하는 것은 §5.5 아티스트 공지 목록과 §6.3 활동 피드가 모두
 * 준다. 공지 댓글(§10.3)은 §9 공통 계약이라 {@code CommentController} 가 갖는다 —
 * 결과적으로 §10 에서 이 파일이 갖는 것은 좋아요 토글뿐이다.
 *
 * <p><b>인증이 필요하다</b>(🔓 아님) — 누가 눌렀는지가 {@code notice_likes} 의 PK 절반이다.
 *
 * <p>Swagger 문서는 {@link NoticeControllerDocs} 가 갖는다.
 */
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class NoticeController implements NoticeControllerDocs {

    private final NoticeLikeService noticeLikeService;

    /**
     * §10.2 좋아요 — <b>204 No Content</b>. 응답 본문이 없다(§1.2 봉투 예외).
     *
     * <p>{@code isLiked}·{@code likeCount} 를 돌려주지 않는다. 클라이언트가 낙관적으로
     * 반영하고 실패 시 롤백한다 — §9.5 댓글 좋아요와 같은 계약이다.
     */
    @Override
    @PostMapping("/notices/{noticeSeq}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void like(@AuthenticationPrincipal AuthenticatedUser user,
                     @PathVariable Integer noticeSeq) {
        noticeLikeService.like(user.userSeq(), noticeSeq);
    }

    /** §10.2 좋아요 해제 — 204. 누르지 않은 상태에서 불러도 204 다(멱등). */
    @Override
    @DeleteMapping("/notices/{noticeSeq}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlike(@AuthenticationPrincipal AuthenticatedUser user,
                       @PathVariable Integer noticeSeq) {
        noticeLikeService.unlike(user.userSeq(), noticeSeq);
    }
}
