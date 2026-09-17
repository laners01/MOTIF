package com.ssafy.motif.coreapi.domain.notice.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.notice.NoticeErrorCode;
import com.ssafy.motif.domain.core.notice.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공지 좋아요 토글 (API 명세 §10.2).
 *
 * <p><b>멱등이 애플리케이션 분기가 아니라 DB 제약에서 나온다.</b> "이미 눌렀나" 를 묻고
 * 답에 따라 갈라지면 묻는 시점과 쓰는 시점이 벌어져, 동시에 들어온 두 요청이
 * {@code notices.like_count} 를 두 번 움직인다. 대신 삽입·삭제가 <b>실제로 몇 행을
 * 건드렸는지</b>를 보고 그때만 카운터를 움직인다 — {@code notice_likes} 의 복합 PK 가
 * 판정자다 (§9.5 {@code CommentLikeService} 와 같은 방식).
 *
 * <p><b>응답이 없다(204).</b> {@code isLiked}·{@code likeCount} 를 돌려주지 않으므로
 * 클라이언트가 낙관적으로 반영하고 실패 시 롤백한다(§1.2 봉투 예외). 그래서 이 서비스는
 * 아무것도 반환하지 않는다 — 돌려줄 값을 만들려고 카운터를 다시 읽는 왕복이 없다.
 * 정확한 값이 필요하면 §5.5·§6.3 을 다시 받는다.
 *
 * <p><b>§9 의 {@code CommentTargetGuard} 를 쓰지 않는다.</b> 그것은 "댓글을 볼 수 있는
 * 대상인가 + 그 아티스트는 누구인가" 를 답하는 §9 전용 판정이고, 여기서 필요한 것은
 * 공지가 살아 있는지 하나뿐이다. 가져다 쓰면 공지 도메인이 댓글 도메인의 하류가 된다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class NoticeLikeService {

    private final NoticeRepository noticeRepository;

    /** §10.2 좋아요 — 이미 누른 상태면 아무것도 바뀌지 않는다(그래도 204). */
    public void like(Integer userSeq, Integer noticeSeq) {
        toggle(userSeq, noticeSeq, true);
    }

    /** §10.2 해제 — 누르지 않은 상태면 아무것도 바뀌지 않는다(그래도 204). */
    public void unlike(Integer userSeq, Integer noticeSeq) {
        toggle(userSeq, noticeSeq, false);
    }

    /**
     * <p>생성과 해제가 <b>완전히 같은 판정</b>을 거친다 — 한쪽만 느슨하면 삭제된 공지에서
     * 한 방향으로만 카운터가 움직여 되돌릴 수 없는 상태가 남는다.
     */
    private void toggle(Integer userSeq, Integer noticeSeq, boolean on) {
        if (!noticeRepository.existsByNoticeSeqAndDeletedFalse(noticeSeq)) {
            throw new BusinessException(NoticeErrorCode.NOTICE_NOT_FOUND,
                    "noticeSeq: " + noticeSeq);
        }

        int changed = on
                ? noticeRepository.insertLikeIfAbsent(noticeSeq, userSeq)
                : noticeRepository.deleteLikeIfPresent(noticeSeq, userSeq);
        if (changed == 1) {
            noticeRepository.addLikeCount(noticeSeq, on ? 1 : -1);
        }
    }
}
