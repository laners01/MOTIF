package com.ssafy.motif.coreapi.admin.motif;

import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.admin.motif.dto.AdminMotifResultItem;
import com.ssafy.motif.coreapi.global.media.MediaUrlResolver;
import com.ssafy.motif.coreapi.global.response.PageResponses;
import com.ssafy.motif.domain.core.motif.AdminMotifResultRow;
import com.ssafy.motif.domain.core.motif.MotifResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 어드민 모티프 결과물 조회 (어드민 명세 §3.17.1).
 *
 * <p>팬 API(§11.5)는 소유자 본인 것만 보이지만 어드민은 전 사용자·삭제분을 본다 —
 * 참여 영상 탐색(§3.12.1)과 같은 이유다.
 */
@Service
@RequiredArgsConstructor
public class AdminMotifResultService {

    private final MotifResultRepository motifResultRepository;
    private final MediaUrlResolver mediaUrlResolver;

    @Transactional(readOnly = true)
    public PageResponse<AdminMotifResultItem> list(Integer userSeq, Integer artistSeq,
                                                   Integer assetSeq, boolean includeDeleted,
                                                   int page, int size) {
        return PageResponses.of(motifResultRepository
                .searchForAdmin(userSeq, artistSeq, assetSeq, includeDeleted,
                        PageResponses.pageable(page, size))
                .map(this::item));
    }

    private AdminMotifResultItem item(AdminMotifResultRow row) {
        return new AdminMotifResultItem(
                row.resultSeq(), row.userSeq(), row.userNickname(), row.artistSeq(),
                row.assetSeq(), row.assetTitle(), row.description(),
                mediaUrlResolver.resolvePlaylist(row.videoKey()),
                mediaUrlResolver.resolve(row.thumbnailKey()),
                row.durationMs(), row.deleted(), row.regDttm());
    }
}
