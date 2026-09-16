package com.ssafy.motif.coreapi.domain.video.service;

import com.ssafy.motif.coreapi.domain.media.MediaKeys;
import com.ssafy.motif.coreapi.domain.media.MediaPlaylistQueryService;
import com.ssafy.motif.coreapi.domain.video.dto.VideoItem;
import com.ssafy.motif.coreapi.global.media.MediaUrlResolver;
import com.ssafy.motif.coreapi.global.media.PublicMediaUrlResolver;
import com.ssafy.motif.domain.core.video.VideoFeedRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 투영 → 참여 영상 항목 (API 명세 §8.1 · §8.2) — <b>항목이 채워지는 유일한 지점</b>.
 *
 * <p>명세는 §8.1 피드 항목과 §8.2 단건 응답이 <b>같은 형태</b>라고 못박았다. 조립을
 * 서비스마다 두면 같은 영상이 <b>진입 경로에 따라 다르게</b> 보인다 — 캐러셀에서 들어간
 * 뷰어와 챌린지에서 들어간 뷰어가 다른 URL 을 재생하거나 한쪽에만 자물쇠가 그려지는
 * 식이고, 예외도 로그도 남지 않는다.
 *
 * <p>상태가 없다. 여기 있는 판단은 전부 <b>한 행을 보고 끝나는</b> 것뿐이고, 요청자에게
 * 달린 값({@code isLiked}·{@code isMine})은 파라미터로 받는다 — 그래야 목록이 한 번
 * 읽은 좋아요 집합을 그대로 쓸 수 있다.
 */
@Component
@RequiredArgsConstructor
public class VideoItemAssembler {

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
     * @param userSeq 요청자 — {@code isMine} 판정에만 쓴다
     * @param liked   요청자의 좋아요 여부. 목록은 페이지 분을 한 번에 읽어 넘기고
     *                단건은 1건만 읽어 넘긴다
     */
    public VideoItem toItem(VideoFeedRow row, Integer userSeq, boolean liked) {
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
