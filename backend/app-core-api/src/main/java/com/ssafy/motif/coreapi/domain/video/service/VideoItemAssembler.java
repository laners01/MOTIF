package com.ssafy.motif.coreapi.domain.video.service;

import com.ssafy.motif.coreapi.domain.video.dto.VideoItem;
import com.ssafy.motif.coreapi.global.media.MediaUrlResolver;
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

    /**
     * 주소는 전부 여기서 만든다 — 썸네일·프로필은 {@code resolve}, 재생목록은
     * {@code resolvePlaylist}.
     *
     * <p>둘을 나눠 부르는 이유는 <b>HLS 가 단일 파일이 아니기 때문이다</b>. 재생목록에
     * 단일 파일 서명을 붙이면 재생목록만 열리고 조각 요청이 403 이 된다(ADR-0012).
     */
    private final MediaUrlResolver mediaUrlResolver;

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
     * 재생목록 주소 — <b>판정은 {@link MediaUrlResolver} 한 곳이다</b>.
     *
     * <p>등급을 여기서 가르지 않는다. 키 프리픽스가 등급이고({@code MediaKeys#isProtected}),
     * 보호 등급이면 리졸버가 프리픽스 서명을 붙여 준다(ADR-0012). 예전에는 이 분기를
     * 호출부마다 들고 있었는데, 그러면 새 화면이 생길 때마다 등급 판정이 하나씩 늘어난다.
     */
    private String videoUrl(VideoFeedRow row) {
        return mediaUrlResolver.resolvePlaylist(row.videoKey());
    }
}
