package com.ssafy.motif.coreapi.domain.user.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.common.response.ListResponse;
import com.ssafy.motif.coreapi.domain.media.MediaPlaylistQueryService;
import com.ssafy.motif.coreapi.domain.user.UserErrorCode;
import com.ssafy.motif.coreapi.domain.user.dto.UserMeResponse;
import com.ssafy.motif.coreapi.domain.user.dto.UserProfileResponse;
import com.ssafy.motif.coreapi.domain.user.dto.VideoPreviewItem;
import com.ssafy.motif.coreapi.global.media.MediaUrlResolver;
import com.ssafy.motif.domain.core.artist.SubscribedArtistRow;
import com.ssafy.motif.domain.core.artist.SubscriptionRepository;
import com.ssafy.motif.domain.core.job.CompositeJob;
import com.ssafy.motif.domain.core.job.CompositeJobRepository;
import com.ssafy.motif.domain.core.motif.MotifResult;
import com.ssafy.motif.domain.core.motif.MotifResultRepository;
import com.ssafy.motif.domain.core.user.User;
import com.ssafy.motif.domain.core.user.UserRepository;
import com.ssafy.motif.domain.core.video.ParticipationVideo;
import com.ssafy.motif.domain.core.video.ParticipationVideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 사용자 조회 (API 명세 §4.1 · §4.6 · §4.8).
 *
 * <p><b>원콜이다.</b> 프로필·대표 모티프·영상 프리뷰를 한 번에 내려준다(회의 확정
 * 2026-09-02 개편). 첫 화면에 필요한 것이 셋뿐이라 화면 하나에 왕복 세 번을 쓸 이유가
 * 없다는 판단이고, 전체 목록은 §4.8 이 따로 맡는다.
 *
 * <p>── URL 을 만드는 규칙 ────────────────────────────────────────────────
 *
 * <p><b>이미지는 전부 {@link MediaUrlResolver} 를 거친다.</b> 공개인지 보호인지는 S3
 * 키의 프리픽스가 정하고, 그 판정은 거기 한 곳에만 있다. 여기서 {@code visibility} 를
 * 보고 가르면 PUBLIC 게시 직후처럼 "공개인데 키는 아직 {@code private/}" 인 행에서
 * 403 이 난다(§1.7 — 게시는 복사일 뿐 키를 바꾸지 않는다).
 *
 * <p><b>영상 주소는 다르다.</b> 모티프 결과물은 보호 등급이라 CDN 이 아니라 core-api
 * 재생목록 경로를 준다 — HLS 는 재생목록 안에서 조각을 더 요청하는데 서명이 거기까지
 * 따라가지 않기 때문이다.
 *
 * <p>읽기 전용 트랜잭션이다 — 쓰기 경로가 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryService {

    /**
     * 마이페이지 첫 화면 프리뷰 건수 — 3×3 (§4.1 기본안).
     *
     * <p>화면 사정이라 리포지토리가 아니라 여기 둔다. 더보기·전체 그리드는 §4.8 이다.
     */
    public static final int PREVIEW_SIZE = 9;

    /**
     * §4.8 이 이 건수를 넘기면 {@code warn} 이다. <b>자르지는 않는다.</b>
     *
     * <p>명세가 "전량 반환의 전제는 본인 영상 수가 크지 않다 — 수백 건이 쌓이면 재검토
     * 대상"이라고 적어 뒀는데, <b>재검토할 시점을 알 방법이 없다.</b> 상한을 조용히 걸면
     * 사용자에게는 영상이 사라진 것으로 보이고(자를 거면 커서를 다시 넣는 게 맞다 —
     * 그건 R4 에서 뒤집은 결정이다), 아무것도 안 하면 느려진 뒤에야 안다. 그래서
     * 자르지 않고 시끄럽게만 만든다.
     */
    private static final int MY_VIDEOS_WARN_THRESHOLD = 200;

    private final UserRepository userRepository;
    private final MotifResultRepository motifResultRepository;
    private final CompositeJobRepository compositeJobRepository;
    private final ParticipationVideoRepository participationVideoRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final MediaUrlResolver mediaUrlResolver;

    /** §4.1 마이페이지 원콜. */
    public UserMeResponse me(Integer userSeq) {
        User user = userRepository.findById(userSeq)
                .orElseThrow(() -> new IllegalStateException(
                        "인증된 토큰의 사용자가 users 에 없다 — userSeq=" + userSeq));

        return new UserMeResponse(
                user.getUserSeq(),
                user.getNickname(),
                mediaUrlResolver.resolve(user.getProfileImageKey()),
                repMotif(user),
                previews(userSeq));
    }

    /**
     * 대표 모티프 — <b>없을 수 있는 경우가 셋이다</b>.
     *
     * <p>① 설정하지 않았다({@code rep_motif_result_seq} 가 NULL) ② 가리키는 결과물이
     * 삭제됐다 ③ 가리키는 결과물이 애초에 없거나 남의 것이다. ②③ 이 가능한 이유는
     * 이 참조에 <b>FK 가 없기 때문이다</b> — 순환 참조를 피하려고 논리 참조로 뒀다
     * (changeSet 0002 주석). DB 가 막아 주지 않으므로 읽는 쪽이 방어한다.
     *
     * <p>셋 다 {@code null} 로 답한다. 클라이언트에게는 "대표 모티프가 없다" 하나이고,
     * 셋을 구분해 봐야 화면이 할 수 있는 일이 같다.
     */
    private UserMeResponse.RepMotif repMotif(User user) {
        Long resultSeq = user.getRepMotifResultSeq();
        if (resultSeq == null) {
            return null;
        }
        MotifResult result = motifResultRepository
                .findByResultSeqAndUserSeqAndDeletedFalse(resultSeq, user.getUserSeq())
                .orElse(null);
        if (result == null) {
            return null;
        }
        // 재생목록 주소는 jobUuid 로 만든다 — 산출물 프리픽스와 같은 값이고, 순차 정수를
        // 공개 네임스페이스에 드러내지 않으려는 규약이다(MediaKeys)
        CompositeJob job = compositeJobRepository.findById(result.getJobSeq()).orElse(null);
        if (job == null) {
            return null;
        }
        return new UserMeResponse.RepMotif(
                result.getResultSeq(),
                MediaPlaylistQueryService.masterPlaylistUrl(job.getJobUuid()),
                mediaUrlResolver.resolve(result.getThumbnailKey()));
    }

    /**
     * §4.6 타 사용자 프로필 — 참여 영상 뷰어의 작성자 Bottom Sheet.
     *
     * <p><b>삭제된 사용자는 없는 것으로 답한다</b>(§1.8). 403 으로 갈라 내면 "그 사람은
     * 있는데 탈퇴했다"가 응답에서 읽히는데, {@code userSeq} 를 찍어 볼 수 있는 자리라
     * 더 그렇다.
     *
     * <p><b>본인 seq 로 불러도 막지 않는다.</b> 화면 흐름상 일어나기 어렵고, 굳이 막으면
     * 클라이언트가 "내 seq 인지" 분기를 하나 더 갖는다. 마이페이지는 §4.1 로 따로 있다.
     */
    public UserProfileResponse profile(Integer userSeq) {
        User user = userRepository.findById(userSeq)
                .filter(target -> !target.isDeleted())
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND,
                        "미존재 또는 삭제된 사용자: userSeq=" + userSeq));

        List<UserProfileResponse.SubscribedArtist> artists =
                subscriptionRepository.findSubscribed(userSeq).stream()
                        .map(this::toSubscribedArtist)
                        .toList();

        return new UserProfileResponse(
                user.getUserSeq(),
                user.getNickname(),
                mediaUrlResolver.resolve(user.getProfileImageKey()),
                artists);
    }

    private UserProfileResponse.SubscribedArtist toSubscribedArtist(SubscribedArtistRow row) {
        return new UserProfileResponse.SubscribedArtist(
                row.artistSeq(),
                row.name(),
                row.groupName(),
                mediaUrlResolver.resolve(row.profileImageKey()));
    }

    /**
     * §4.8 내 챌린지 그리드 — <b>비페이지 전량</b>(회의 확정 R4, 커서에서 전환).
     *
     * <p>§4.1 이 첫 화면에 최신 9개를 그리고, "더보기/전체" 진입 시 이 API 가 1회
     * 호출된다. 항목의 모양은 §4.1 과 같다 — 같은 그리드다.
     *
     * <p><b>상한을 두지 않는다.</b> 조용히 자르면 사용자에게는 영상이 사라진 것으로
     * 보인다. 대신 {@link #MY_VIDEOS_WARN_THRESHOLD} 를 넘으면 로그로 알린다.
     */
    public ListResponse<VideoPreviewItem> myVideos(Integer userSeq) {
        List<VideoPreviewItem> videos = items(userSeq, Pageable.unpaged());
        if (videos.size() > MY_VIDEOS_WARN_THRESHOLD) {
            log.warn("§4.8 전량 반환이 커지고 있다 — userSeq={} count={} (커서 전환 재검토)",
                    userSeq, videos.size());
        }
        return ListResponse.of(videos);
    }

    /**
     * 내 영상 3×3 프리뷰.
     *
     * <p><b>0건이면 빈 배열이다</b>(2026-09-13 확정). 원본 명세는 {@code null} 로
     * 적었는데, {@code null} 과 빈 배열을 구분하면 클라이언트가 같은 "없음"에 분기를
     * 둘 갖게 된다.
     */
    private List<VideoPreviewItem> previews(Integer userSeq) {
        return items(userSeq, PageRequest.of(0, PREVIEW_SIZE));
    }

    /**
     * §4.1·§4.8 공통 — 같은 필터·같은 항목이고 건수만 다르다.
     *
     * <p>둘을 나누면 §1.8 비노출(삭제 챌린지 제외)이나 썸네일 등급 판정이 한쪽에서만
     * 고쳐질 수 있다. 그때 증상은 "첫 화면과 더보기가 다른 목록을 보여 준다" 인데,
     * 어느 쪽이 맞는지 화면만 봐서는 알 수 없다.
     */
    private List<VideoPreviewItem> items(Integer userSeq, Pageable pageable) {
        List<ParticipationVideo> videos = participationVideoRepository.findMine(userSeq, pageable);
        return videos.stream()
                .map(video -> new VideoPreviewItem(
                        video.getVideoSeq(),
                        mediaUrlResolver.resolve(video.getThumbnailKey()),
                        video.getViewCount(),
                        video.getVisibility()))
                .toList();
    }
}
