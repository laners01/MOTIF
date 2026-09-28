package com.ssafy.motif.coreapi.domain.comment.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.comment.CommentErrorCode;
import com.ssafy.motif.coreapi.domain.comment.CommentTargetType;
import com.ssafy.motif.coreapi.domain.report.ReportErrorCode;
import com.ssafy.motif.domain.core.comment.ChallengeCommentRepository;
import com.ssafy.motif.domain.core.comment.NoticeCommentRepository;
import com.ssafy.motif.domain.core.comment.VideoCommentRepository;
import com.ssafy.motif.domain.core.report.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 댓글 신고 접수 (API 명세 §9.6).
 *
 * <p><b>접수만 한다.</b> 신고했다고 댓글이 숨겨지거나 카운터가 바뀌지 않는다 — 판단과
 * 조치는 어드민이 {@code status} 를 옮기며 하는 일이고(어드민 §3.13), 접수가 곧 제재면
 * 신고 하나로 남의 댓글을 내릴 수 있게 된다({@code VideoReportService} §8.7 과 같은 규칙).
 *
 * <p><b>잠긴 영상의 댓글도 신고할 수 있다.</b> §9.6 실패표에 {@code COMMENT_DISABLED} 가
 * 없다 — 문제가 되어 잠긴 게시물일수록 신고가 필요한데 막으면 어드민에게 알릴 길이
 * 사라진다. 같은 이유로 §9.4 삭제도 잠금을 보지 않는다(§9.5 좋아요만 막는다).
 *
 * <p>중복은 애플리케이션이 아니라 DB 가 가른다 —
 * {@link ReportRepository#insertChallengeCommentReport} javadoc 참조.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CommentReportService {

    private final ChallengeCommentRepository challengeCommentRepository;
    private final VideoCommentRepository videoCommentRepository;
    private final NoticeCommentRepository noticeCommentRepository;
    private final CommentTargetGuard targetGuard;
    private final ReportRepository reportRepository;

    /**
     * §9.6 신고 접수.
     *
     * <p><b>보이지 않는 댓글은 신고 대상도 아니다.</b> 판정을 §9.1·§9.5 와 공유하는
     * 이유는 존재 은닉이다(§1.8) — 여기만 느슨하면 신고 응답이 "그 댓글은 있다" 를
     * 알려 주는 탐지 수단이 된다.
     */
    public void report(Integer userSeq, String type, Long commentSeq, String reasonCode) {
        CommentTargetType targetType = CommentTargetType.from(type)
                .orElseThrow(() -> commentNotFound(commentSeq));

        int inserted = switch (targetType) {
            case CHALLENGE -> {
                Integer challengeSeq = challengeCommentRepository.findVisibleTargetSeq(commentSeq)
                        .orElseThrow(() -> commentNotFound(commentSeq));
                targetGuard.orCommentNotFound(commentSeq,
                        () -> targetGuard.requireChallenge(challengeSeq));
                yield reportRepository.insertChallengeCommentReport(
                        userSeq, commentSeq, reasonCode);
            }
            case VIDEO -> {
                Long videoSeq = videoCommentRepository.findVisibleTargetSeq(commentSeq)
                        .orElseThrow(() -> commentNotFound(commentSeq));
                // 잠금을 보지 않는다 — 잠긴 게시물일수록 신고가 필요하다 (§9.6 실패표)
                targetGuard.orCommentNotFound(commentSeq,
                        () -> targetGuard.requireVideoIgnoringCommentLock(userSeq, videoSeq));
                yield reportRepository.insertVideoCommentReport(userSeq, commentSeq, reasonCode);
            }
            case NOTICE -> {
                Integer noticeSeq = noticeCommentRepository.findVisibleTargetSeq(commentSeq)
                        .orElseThrow(() -> commentNotFound(commentSeq));
                targetGuard.orCommentNotFound(commentSeq,
                        () -> targetGuard.requireNotice(noticeSeq));
                yield reportRepository.insertNoticeCommentReport(userSeq, commentSeq, reasonCode);
            }
        };

        if (inserted == 0) {
            throw new BusinessException(ReportErrorCode.ALREADY_REPORTED,
                    "commentSeq: " + commentSeq);
        }
    }

    private static BusinessException commentNotFound(Long commentSeq) {
        return new BusinessException(CommentErrorCode.COMMENT_NOT_FOUND,
                "commentSeq: " + commentSeq);
    }
}
