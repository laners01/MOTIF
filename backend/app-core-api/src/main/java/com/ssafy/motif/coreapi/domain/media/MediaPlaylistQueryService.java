package com.ssafy.motif.coreapi.domain.media;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.global.media.MediaPlaylistService;
import com.ssafy.motif.domain.core.job.CompositeJob;
import com.ssafy.motif.domain.core.job.CompositeJobRepository;
import com.ssafy.motif.domain.core.video.ParticipationVideo;
import com.ssafy.motif.domain.core.video.ParticipationVideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 보호 등급 HLS 재생목록의 <b>인가</b> (API 명세 §1.7).
 *
 * <p>재생목록을 다시 쓰는 일은 {@link MediaPlaylistService} 가 이미 한다. 여기가 맡는
 * 것은 그 앞의 두 가지다 — <b>누가 볼 수 있는가</b>와 <b>어느 프리픽스인가</b>.
 * 둘을 서비스에 모아 두는 이유는 {@code MediaPlaylistService} 가 "이 프리픽스를 서명해
 * 달라"만 받는 순수한 층으로 남아야 소유자 조회 방식이 바뀌어도 그쪽을 안 고치기
 * 때문이다.
 *
 * <p>── 누가 볼 수 있는가 ────────────────────────────────────────────────
 *
 * <table border="1">
 *   <caption>잡 종류별 인가</caption>
 *   <tr><th>대상</th><th>볼 수 있는 사람</th></tr>
 *   <tr><td>{@code MOTIF} 결과물</td><td>잡 소유자만 — 공개 등급이 없다</td></tr>
 *   <tr><td>{@code CHALLENGE}, 게시 전(행 없음)</td><td>잡 소유자만 — 게시 전 프리뷰</td></tr>
 *   <tr><td>{@code CHALLENGE}, {@code PUBLIC} 게시</td><td>누구나</td></tr>
 *   <tr><td>{@code CHALLENGE}, {@code PRIVATE} 게시</td><td>잡 소유자만</td></tr>
 *   <tr><td>{@code CHALLENGE}, 삭제된 게시물</td><td>아무도 — 소유자 포함(§1.8)</td></tr>
 * </table>
 *
 * <p><b>거부는 전부 404 다</b>({@link MediaErrorCode#PLAYLIST_NOT_FOUND}). 403 으로
 * 가르면 "그 영상은 존재하고 당신 것이 아니다"가 응답에서 읽힌다 — {@code jobUuid} 만
 * 있으면 누구나 두드릴 수 있는 주소라 더 그렇다.
 *
 * <p>── 어느 프리픽스인가 ──────────────────────────────────────────────
 *
 * <p><b>언제나 {@code composite_jobs.result_video_key} 에서 구한다</b> — PUBLIC 으로
 * 게시돼 {@code public/} 으로 복사된 뒤에도 이 값은 바뀌지 않으므로(§1.7 — 바꾸면
 * 재생 중인 클라이언트가 404 를 만난다) 보호 트리를 가리키는 유일하게 안정된 값이다.
 * 공개 사본은 이 엔드포인트를 거칠 필요가 없다. 조회 API 가 PUBLIC 영상에는 CDN
 * 직통 주소를 내주고, 여기는 서명이 필요한 경로만 담당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaPlaylistQueryService {

    /** 자식 재생목록 주소의 뿌리. {@code MediaController} 의 매핑과 같은 값이어야 한다. */
    public static final String API_BASE_PATH = "/api/v1/media";

    private final CompositeJobRepository compositeJobRepository;
    private final ParticipationVideoRepository participationVideoRepository;
    private final MediaPlaylistService playlistService;

    /** 마스터 재생목록 — 화질별 주소를 core-api 로 돌려 내린다. */
    public String master(Integer userSeq, String jobUuid) {
        String prefix = MediaKeys.prefixOf(authorize(userSeq, jobUuid).getResultVideoKey());
        try {
            return playlistService.master(prefix, API_BASE_PATH + "/" + jobUuid);
        } catch (MediaPlaylistService.MediaPlaylistNotFoundException e) {
            throw notFound();
        }
    }

    /** 화질별 재생목록 — 조각 주소에 서명을 박아 내린다. */
    public String variant(Integer userSeq, String jobUuid, String rendition) {
        String prefix = MediaKeys.prefixOf(authorize(userSeq, jobUuid).getResultVideoKey());
        if (!MediaPlaylistService.isKnownRendition(rendition)) {
            // 경로에서 온 값이라 없는 화질을 물을 수 있다. 400 이 아니라 404 인 것은
            // "없는 주소"가 맞기 때문이고, 무엇보다 이 값이 S3 키에 그대로 이어붙어서
            // 화이트리스트를 통과하지 못한 값으로는 아무것도 하지 않는다
            throw notFound();
        }
        try {
            return playlistService.variant(prefix, rendition);
        } catch (MediaPlaylistService.MediaPlaylistNotFoundException e) {
            // 사다리에 있어도 원본보다 큰 단은 만들어지지 않는다(HlsPackager.ladderFor)
            throw notFound();
        }
    }

    /**
     * 잡을 찾고 볼 자격을 확인한다.
     *
     * <p>{@code DONE} 이 아니면 {@code result_video_key} 가 NULL 이다
     * ({@code ck_composite_jobs_status_shape}) — 인가를 통과시켜 봐야 읽을 것이 없으므로
     * 여기서 끊는다. 가공 진행 상황은 §13.3 이 답할 몫이다.
     */
    private CompositeJob authorize(Integer userSeq, String jobUuid) {
        CompositeJob job = compositeJobRepository.findByJobUuid(parse(jobUuid))
                .orElseThrow(MediaPlaylistQueryService::notFound);
        if (!job.isDone()) {
            throw notFound();
        }
        switch (job.getJobType()) {
            case MOTIF -> requireOwner(job, userSeq);
            case CHALLENGE -> authorizeChallenge(job, userSeq);
        }
        return job;
    }

    /**
     * 참여 영상 — 게시 여부와 공개 범위로 갈린다.
     *
     * <p><b>행이 없으면 게시 전이다.</b> 합성은 게시(§8.1)보다 먼저 끝나 있어야 하고
     * (게시 전 프리뷰도 재생 대상이다 — §1.7), 그 구간은 소유자만 본다.
     */
    private void authorizeChallenge(CompositeJob job, Integer userSeq) {
        ParticipationVideo video = participationVideoRepository.findByJobSeq(job.getJobSeq())
                .orElse(null);
        if (video == null) {
            requireOwner(job, userSeq);
            return;
        }
        if (video.isDeleted()) {
            // §1.8 — 삭제된 영상은 소유자에게도 내주지 않는다. 여기서 소유자를 통과시키면
            // "지웠는데 아직 재생된다"가 되고, 그 상태를 본인이 확인할 방법이 없다
            throw notFound();
        }
        if (video.isPublic()) {
            return;
        }
        requireOwner(job, userSeq);
    }

    private static void requireOwner(CompositeJob job, Integer userSeq) {
        if (!job.isOwnedBy(userSeq)) {
            throw notFound();
        }
    }

    /** 경로에서 온 값이라 UUID 가 아닐 수 있다. 형식 오류도 "없는 주소"로 합친다. */
    private static UUID parse(String jobUuid) {
        try {
            return UUID.fromString(jobUuid);
        } catch (IllegalArgumentException e) {
            throw notFound();
        }
    }

    private static BusinessException notFound() {
        return new BusinessException(MediaErrorCode.PLAYLIST_NOT_FOUND);
    }
}
