package com.ssafy.motif.coreapi.domain.user.service;

import com.ssafy.motif.coreapi.domain.media.MediaPlaylistQueryService;
import com.ssafy.motif.coreapi.domain.user.dto.UserMeResponse;
import com.ssafy.motif.coreapi.global.media.MediaUrlResolver;
import com.ssafy.motif.domain.core.job.CompositeJob;
import com.ssafy.motif.domain.core.job.CompositeJobRepository;
import com.ssafy.motif.domain.core.motif.MotifResult;
import com.ssafy.motif.domain.core.motif.MotifResultRepository;
import com.ssafy.motif.domain.core.user.User;
import com.ssafy.motif.domain.core.user.UserRepository;
import com.ssafy.motif.domain.core.video.ParticipationVideo;
import com.ssafy.motif.domain.core.video.ParticipationVideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 마이페이지 조회 (API 명세 §4.1) — S15P21D104-278.
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

    private final UserRepository userRepository;
    private final MotifResultRepository motifResultRepository;
    private final CompositeJobRepository compositeJobRepository;
    private final ParticipationVideoRepository participationVideoRepository;
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
     * 내 영상 3×3 프리뷰.
     *
     * <p><b>0건이면 빈 배열이다</b>(2026-09-13 확정). 원본 명세는 {@code null} 로
     * 적었는데, {@code null} 과 빈 배열을 구분하면 클라이언트가 같은 "없음"에 분기를
     * 둘 갖게 된다.
     */
    private List<UserMeResponse.VideoPreview> previews(Integer userSeq) {
        List<ParticipationVideo> videos = participationVideoRepository
                .findMyPreview(userSeq, PageRequest.of(0, PREVIEW_SIZE));
        return videos.stream()
                .map(video -> new UserMeResponse.VideoPreview(
                        video.getVideoSeq(),
                        mediaUrlResolver.resolve(video.getThumbnailKey()),
                        video.getViewCount(),
                        video.getVisibility()))
                .toList();
    }
}
