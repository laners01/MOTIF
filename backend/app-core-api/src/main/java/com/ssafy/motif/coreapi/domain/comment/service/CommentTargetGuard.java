package com.ssafy.motif.coreapi.domain.comment.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.challenge.ChallengeErrorCode;
import com.ssafy.motif.coreapi.domain.comment.CommentErrorCode;
import com.ssafy.motif.coreapi.domain.notice.NoticeErrorCode;
import com.ssafy.motif.coreapi.domain.video.service.VideoAccessGuard;
import com.ssafy.motif.domain.core.artist.Artist;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.challenge.Challenge;
import com.ssafy.motif.domain.core.challenge.ChallengeRepository;
import com.ssafy.motif.domain.core.notice.Notice;
import com.ssafy.motif.domain.core.notice.NoticeRepository;
import com.ssafy.motif.domain.core.video.ParticipationVideo;
import com.ssafy.motif.domain.core.video.ParticipationVideoRepository;
import com.ssafy.motif.domain.core.video.VideoFeedRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * "이 대상에 댓글을 볼 수 있는가, 그리고 이 대상의 아티스트는 누구인가" —
 * <b>§9 전체가 공유하는 단일 판정</b> (API 명세 §9).
 *
 * <p>§9.1 목록뿐 아니라 §9.2 답글 · §9.3 작성 · §9.5 좋아요가 모두 같은 질문으로
 * 시작한다. 대상 3종의 판정을 API 마다 따로 두면 <b>한쪽에서만 보이는 게시물</b>이
 * 생긴다 — 목록은 404 인데 작성은 받아 주면 그 응답이 "그 영상은 존재한다" 를 알려 주는
 * 탐지 수단이 된다(§1.8 존재 은닉이 뚫리는 방식이 대개 이렇다).
 *
 * <p><b>돌려주는 값이 대상 아티스트의 {@code users.user_seq} 인 것은 의도다.</b> 정렬
 * 1순위와 {@code author.isArtist} 뱃지가 <b>같은 판정</b>이어야 하는데, 그 근거는
 * {@code role=ARTIST} 가 아니라 "이 콘텐츠에 연결된 아티스트인가" 다(명세 §9.1 — 다른
 * ARTIST 계정의 댓글은 일반 댓글이다). 대상마다 출처가 다르므로
 * ({@code challenges.artist_seq} · 영상에 <b>복사된</b> {@code artist_seq} ·
 * {@code notices.artist_seq}) 여기서 한 번 풀어 하나의 값으로 만든다.
 */
@Component
@RequiredArgsConstructor
public class CommentTargetGuard {

    /**
     * 아티스트를 못 찾았을 때 쓰는 값. {@code users.user_seq} 는 IDENTITY 라 양수뿐이므로
     * <b>어느 작성자와도 일치하지 않는다</b> — 그 대상의 댓글은 전부 일반 댓글이 된다.
     *
     * <p>{@code null} 을 넘기지 않는 이유: 쿼리 파라미터의 타입 없는 {@code null} 은
     * Postgres 가 엉뚱하게 추론해 런타임에 터질 수 있다({@code ArtistRepository.search}
     * 의 {@code lower(bytea)} 사고). 비교 자체가 성립하는 값을 넘긴다.
     */
    private static final Integer NO_ARTIST = -1;

    private final ChallengeRepository challengeRepository;
    private final ParticipationVideoRepository participationVideoRepository;
    private final NoticeRepository noticeRepository;
    private final ArtistRepository artistRepository;
    private final VideoAccessGuard videoAccessGuard;

    /** 챌린지 — 삭제된 챌린지는 없는 것으로 본다(§1.8). */
    public Integer requireChallenge(Integer challengeSeq) {
        Challenge challenge = challengeRepository.findByChallengeSeqAndDeletedFalse(challengeSeq)
                .orElseThrow(() -> new BusinessException(
                        ChallengeErrorCode.CHALLENGE_NOT_FOUND, "challengeSeq: " + challengeSeq));
        return artistUserSeq(challenge.getArtistSeq());
    }

    /**
     * 참여 영상 — <b>보이는가</b>는 §8 의 판정을 그대로 쓴다.
     *
     * <p>{@link VideoAccessGuard#requireVisible} 이 미존재 · 삭제 영상 · 삭제 사용자의
     * 콘텐츠 · 삭제된 챌린지 소속 · <b>타인의 {@code PRIVATE}</b> 을 모두 같은 404 로
     * 만든다. 댓글이 그 판정을 다시 짜면 §8 과 어긋나는 순간 존재 은닉이 뚫린다.
     *
     * <p>댓글 잠금은 그다음이다 — <b>보이지 않는 영상에 403 을 주면 안 된다</b>.
     * 403 은 "그 영상은 있다" 를 알려 주기 때문이다.
     */
    public Integer requireVideo(Integer viewerSeq, Long videoSeq) {
        VideoFeedRow video = videoAccessGuard.requireVisible(viewerSeq, videoSeq);
        if (!video.commentEnabled()) {
            throw new BusinessException(CommentErrorCode.COMMENT_DISABLED, "videoSeq: " + videoSeq);
        }
        // VideoFeedRow 에는 artist_seq 가 없다(§8.1 항목이 쓰지 않는 칸이다). §8 의 투영에
        // 칸을 더하는 대신 여기서 한 번 더 읽는다 — 그 record 는 병렬로 열려 있는 §8
        // 티켓들이 함께 쓰고, 칸을 더하면 리베이스에서 되돌아온다.
        return participationVideoRepository.findById(videoSeq)
                .map(ParticipationVideo::getArtistSeq)
                .map(this::artistUserSeq)
                .orElse(NO_ARTIST);
    }

    /** 공지 — 삭제된 공지는 없는 것으로 본다. */
    public Integer requireNotice(Integer noticeSeq) {
        Notice notice = noticeRepository.findByNoticeSeqAndDeletedFalse(noticeSeq)
                .orElseThrow(() -> new BusinessException(
                        NoticeErrorCode.NOTICE_NOT_FOUND, "noticeSeq: " + noticeSeq));
        return artistUserSeq(notice.getArtistSeq());
    }

    /**
     * {@code artists.artist_seq} → {@code users.user_seq}.
     *
     * <p><b>삭제 여부를 묻지 않는다</b>({@code findById}). 여기서 답하는 것은 "이 콘텐츠의
     * 주인이 누구인가" 이고, 아티스트가 소프트 삭제됐다고 그가 단 댓글이 일반 댓글로
     * 내려앉으면 <b>같은 목록의 정렬이 조용히 바뀐다</b>.
     */
    private Integer artistUserSeq(Integer artistSeq) {
        return artistRepository.findById(artistSeq)
                .map(Artist::getUserSeq)
                .orElse(NO_ARTIST);
    }
}
