package com.ssafy.motif.coreapi.domain.video.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.common.response.CursorResponse;
import com.ssafy.motif.coreapi.domain.challenge.ChallengeErrorCode;
import com.ssafy.motif.coreapi.domain.media.MediaKeys;
import com.ssafy.motif.coreapi.domain.media.MediaPlaylistQueryService;
import com.ssafy.motif.coreapi.domain.video.dto.VideoItem;
import com.ssafy.motif.coreapi.global.media.MediaUrlResolver;
import com.ssafy.motif.coreapi.global.media.PublicMediaUrlResolver;
import com.ssafy.motif.coreapi.global.response.Cursors;
import com.ssafy.motif.domain.core.challenge.ChallengeRepository;
import com.ssafy.motif.domain.core.video.ParticipationVideoRepository;
import com.ssafy.motif.domain.core.video.VideoFeedKeyRow;
import com.ssafy.motif.domain.core.video.VideoFeedRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 챌린지별 참여 영상 피드 (API 명세 §8.1).
 *
 * <p>정렬은 <b>서버 고정 추천순</b>이다({@code sort} 파라미터가 없다). 점수식은
 * {@link ParticipationVideoRepository#FEED_SCORE} 하나에만 있고 여기서는 순서를 받아
 * 카드를 채울 뿐이다.
 *
 * <p>읽기 전용 트랜잭션이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VideoFeedQueryService {

    /** §1.9 size 상한 — 커서 목록 공통. */
    private static final int MAX_SIZE = 100;

    private final ChallengeRepository challengeRepository;
    private final ParticipationVideoRepository participationVideoRepository;

    /** 썸네일·프로필처럼 <b>그 자체로 끝나는</b> 단일 파일 주소용 — 등급 판정은 이쪽이 한다. */
    private final MediaUrlResolver mediaUrlResolver;

    /**
     * 공개 사본이 준비된 영상의 재생목록용.
     *
     * <p>재생목록에 {@link MediaUrlResolver} 를 쓰면 <b>HLS 에 단일 파일 서명을 붙이는</b>
     * 잘못된 경로가 열린다 — 재생목록만 열리고 조각 요청이 403 이 된다(§6.1 과 같은
     * 이유). 보호 등급 쪽은 서명이 아니라 core-api 주소로 간다.
     */
    private final PublicMediaUrlResolver publicMediaUrlResolver;

    /**
     * §8.1 챌린지별 참여 영상 피드 — 커서, 추천 정렬.
     *
     * <p><b>챌린지를 먼저 읽어 404 로 끊는다.</b> 미존재·삭제 챌린지가 "영상 0건" 과
     * 같은 빈 목록으로 나가면 클라이언트가 둘을 구분할 수 없다(§8.1 실패표).
     *
     * <p>{@code limit + 1} 건을 읽어 넘치면 잘라내고 그 사실을 {@code hasNext} 로 쓴다 —
     * 총 개수를 세지 않는 것이 커서 방식의 요점이다.
     *
     * <p><b>한 페이지에 조회 4번으로 끝낸다.</b> 챌린지 · 순서 · 카드 · 좋아요 각각
     * 한 번이다. 항목마다 읽으면 20행에 그만큼 왕복이 늘어난다.
     */
    public CursorResponse<VideoItem> feed(Integer userSeq, Integer challengeSeq,
                                          String cursor, int size) {
        challengeRepository.findByChallengeSeqAndDeletedFalse(challengeSeq)
                .orElseThrow(() -> new BusinessException(
                        ChallengeErrorCode.CHALLENGE_NOT_FOUND, "challengeSeq: " + challengeSeq));

        int limit = Math.clamp(size, 1, MAX_SIZE);
        Cursors.Score at = Cursors.decodeScore(cursor);

        // 커서 유무로 메서드를 가른다 — null 을 쿼리 파라미터로 흘리지 않는다
        // (ChallengeRepository javadoc: 타입 없는 null → Postgres 추론 → 운영 500)
        List<VideoFeedKeyRow> keys = at == null
                ? participationVideoRepository.findFeedKeys(challengeSeq, userSeq, limit + 1)
                : participationVideoRepository.findFeedKeysAfter(
                        challengeSeq, userSeq, at.score(), at.lastSeq(), limit + 1);

        boolean hasNext = keys.size() > limit;
        List<VideoFeedKeyRow> page = hasNext ? keys.subList(0, limit) : keys;
        if (page.isEmpty()) {
            return CursorResponse.forward(List.of(), null);
        }

        List<VideoItem> list = cards(page, userSeq);

        VideoFeedKeyRow last = page.getLast();
        String nextCursor = hasNext
                ? Cursors.encodeScore(last.getScore(), last.getVideoSeq())
                : null;
        return CursorResponse.forward(list, nextCursor);
    }

    /**
     * 순서 묶음 → 카드 목록.
     *
     * <p>{@code in} 조회는 입력 순서를 보장하지 않으므로 순서 쿼리가 준 차례대로 다시
     * 세운다. 같은 트랜잭션 안이라 카드가 비는 경우는 없지만
     * {@code null} 을 건너뛰는 분기는 남긴다 — 비면 그 자리를 빼는 것이 맞고
     * ({@code HotVideoQueryService} 와 같은 처리), {@code nextCursor} 는 목록이 아니라
     * <b>순서 쿼리의 마지막 자리</b>로 만들므로 그 항목을 다음 페이지에서 다시 읽지 않는다.
     */
    private List<VideoItem> cards(List<VideoFeedKeyRow> page, Integer userSeq) {
        List<Long> videoSeqs = page.stream().map(VideoFeedKeyRow::getVideoSeq).toList();

        Map<Long, VideoFeedRow> rows = participationVideoRepository.findFeedCards(videoSeqs)
                .stream()
                .collect(Collectors.toMap(VideoFeedRow::videoSeq, Function.identity()));
        Set<Long> liked = Set.copyOf(
                participationVideoRepository.findLikedVideoSeqs(userSeq, videoSeqs));

        List<VideoItem> list = new ArrayList<>(page.size());
        for (Long videoSeq : videoSeqs) {
            VideoFeedRow row = rows.get(videoSeq);
            if (row != null) {
                list.add(toItem(row, userSeq, liked.contains(videoSeq)));
            }
        }
        return list;
    }

    private VideoItem toItem(VideoFeedRow row, Integer userSeq, boolean liked) {
        return new VideoItem(
                row.videoSeq(),
                row.challengeSeq(),
                row.description(),
                videoUrl(row),
                mediaUrlResolver.resolve(row.thumbnailKey()),
                row.durationMs(),
                new VideoItem.Author(
                        row.userSeq(),
                        row.authorNickname(),
                        mediaUrlResolver.resolve(row.authorProfileImageKey())),
                row.viewCount(),
                row.likeCount(),
                row.commentCount(),
                row.hasArtistLike(),
                row.hasArtistComment(),
                liked,
                row.commentEnabled(),
                row.visibility(),
                row.userSeq().equals(userSeq),
                row.regDttm());
    }

    /**
     * 재생목록 주소 — <b>키 프리픽스가 등급이다</b>.
     *
     * <p>{@code visibility} 로 가르지 않는다. 게시는 공개 사본을 {@code public/} 으로
     * 복사할 뿐 키를 바꾸지 않아, {@code PUBLIC} 인데 키는 아직 {@code private/} 인 행이
     * 정상적으로 존재한다({@code MediaKeys#isProtected} javadoc). 거기에 무서명 URL 을
     * 만들면 403 이다.
     *
     * <p>보호 등급은 CDN 이 아니라 core-api 를 가리킨다 — 조각까지 서명이 붙은
     * 재생목록을 거기서 다시 써서 낸다(§1.7).
     */
    private String videoUrl(VideoFeedRow row) {
        return MediaKeys.isProtected(row.videoKey())
                ? MediaPlaylistQueryService.masterPlaylistUrl(row.jobUuid())
                : publicMediaUrlResolver.resolve(row.videoKey());
    }
}
