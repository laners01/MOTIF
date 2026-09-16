package com.ssafy.motif.coreapi.admin.video;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.admin.video.dto.AdminVideoItem;
import com.ssafy.motif.coreapi.domain.video.VideoErrorCode;
import com.ssafy.motif.coreapi.domain.video.service.VideoItemAssembler;
import com.ssafy.motif.coreapi.global.response.PageResponses;
import com.ssafy.motif.domain.core.video.AdminVideoRow;
import com.ssafy.motif.domain.core.video.ParticipationVideoRepository;
import com.ssafy.motif.domain.core.video.Visibility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 어드민 참여 영상 조회 (어드민 명세 §3.12.1·§3.12.2) — S15P21D104-575.
 *
 * <p><b>대행이 아니라 탐색이다.</b> 경로는 {@code /as-artist} 아래가 아니고 액터도
 * 아티스트가 아니다 — 어드민이 신고 대상이나 회원의 영상을 찾아보는 조회다. 그래서
 * 사용자 API 위임(§3.12 본문)과 달리 지금 만들 수 있다.
 *
 * <p><b>팬 API 와 정반대로 아무것도 감추지 않는다.</b> PRIVATE 도 삭제분도 보인다.
 * 신고 상세(§3.13.2)가 이미 삭제된 대상을 보여줘야 "이미 지워졌으니 기각" 을 판단할 수
 * 있고, 회원 상세도 그 사람의 비공개 영상을 세어야 한다.
 *
 * <p>URL 조립은 {@code VideoItemAssembler} 를 그대로 쓴다 — 보호 등급 판정과 재생목록
 * 주소 규칙을 어드민이 다시 구현하면 한쪽만 고쳤을 때 갈린다(§1.7).
 */
@Service
@RequiredArgsConstructor
public class AdminVideoService {

    private final ParticipationVideoRepository videoRepository;
    private final VideoItemAssembler videoItemAssembler;

    /**
     * §3.12.1 — 목록.
     *
     * @param sort 명세는 {@code recommended|latest} 를 두지만 지금은 최신순뿐이다.
     *             추천 정렬은 랭킹 스냅샷(커서 기반)을 써야 하는데 이 목록은 offset
     *             페이지에 필터가 붙어 구조가 다르다. 신고·회원 상세는 시간순이 맞고,
     *             추천이 실제로 필요한 것은 대행 UI(577)다 — 그때 넣는다.
     *             <b>조용히 최신순으로 떨어뜨리지 않고 400 으로 막는다.</b> 화면이
     *             추천을 요청했는데 시간순이 오면 아무도 눈치채지 못한다
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminVideoItem> list(Integer challengeSeq, Integer userSeq,
                                             Visibility visibility, boolean includeDeleted,
                                             String sort, int page, int size) {
        requireSupportedSort(sort);
        return PageResponses.of(videoRepository
                .searchForAdmin(challengeSeq, userSeq, visibility, includeDeleted,
                        PageResponses.pageable(page, size))
                .map(this::item));
    }

    /** §3.12.2 — 단건. 삭제분도 준다. */
    @Transactional(readOnly = true)
    public AdminVideoItem detail(long videoSeq) {
        return find(videoSeq)
                .orElseThrow(() -> new BusinessException(VideoErrorCode.VIDEO_NOT_FOUND,
                        "미존재 영상: videoSeq=" + videoSeq));
    }

    /**
     * 없으면 비어 있는 채로 준다 — 신고 상세(§3.13.2)가 쓴다.
     *
     * <p>거기서는 대상이 없는 것이 404 가 아니다. 대상 행이 사라져도 신고는 남아 있고,
     * 처리자는 그것을 기각으로 닫을 수 있어야 한다.
     */
    @Transactional(readOnly = true)
    public Optional<AdminVideoItem> find(long videoSeq) {
        return videoRepository.findAdminDetail(videoSeq).map(this::item);
    }

    private AdminVideoItem item(AdminVideoRow row) {
        // userSeq 를 null 로 넘긴다 — isMine·liked 는 어드민 관점에 의미가 없고
        // AdminVideoItem 이 그 둘을 싣지 않는다
        return AdminVideoItem.of(videoItemAssembler.toItem(row.feedRow(), null, false),
                row.deleted(), row.reportCount());
    }

    private static void requireSupportedSort(String sort) {
        if (sort != null && !"latest".equals(sort)) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER,
                    "지금은 sort=latest 만 지원한다 — 추천 정렬은 대행 UI(S15P21D104-577)에서 넣는다: " + sort);
        }
    }
}
