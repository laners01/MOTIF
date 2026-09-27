package com.ssafy.motif.coreapi.admin.motif;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.admin.audit.AdminAuditLogger;
import com.ssafy.motif.coreapi.admin.media.AdminMediaService;
import com.ssafy.motif.coreapi.admin.motif.dto.MotifThumbnailResponse;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.domain.motif.MotifErrorCode;
import com.ssafy.motif.coreapi.admin.motif.dto.AdminMotifResultItem;
import com.ssafy.motif.coreapi.global.media.MediaUrlResolver;
import com.ssafy.motif.coreapi.global.response.PageResponses;
import com.ssafy.motif.domain.core.motif.AdminMotifResultRow;
import com.ssafy.motif.domain.core.motif.MotifResult;
import com.ssafy.motif.domain.core.motif.MotifResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

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
    private final AdminMediaService adminMediaService;
    private final AdminAuditLogger auditLogger;

    /** 썸네일 교체 키의 자리 — {@code AdminMediaKind.MOTIF_THUMBNAIL} 과 같다. */
    private static final String THUMBNAIL_PREFIX = "private/motif-thumbnails/";

    @Transactional(readOnly = true)
    public PageResponse<AdminMotifResultItem> list(Integer userSeq, Integer artistSeq,
                                                   Integer assetSeq, boolean includeDeleted,
                                                   int page, int size) {
        return PageResponses.of(motifResultRepository
                .searchForAdmin(userSeq, artistSeq, assetSeq, includeDeleted,
                        PageResponses.pageable(page, size))
                .map(this::item));
    }

    /**
     * §3.17.2 — 썸네일 교체. 영상 프레임이 아니라 어드민이 올린 이미지로 바꾼다.
     *
     * <p>키는 {@code MOTIF_THUMBNAIL} presign 이 준 것만 받는다. 아무 키나 받으면 다른
     * 결과물·다른 사용자의 파일을 가리키게 할 수 있다. 원래 썸네일 파일은 지우지 않는다.
     */
    @Transactional
    public MotifThumbnailResponse changeThumbnail(AuthenticatedAdmin admin, long resultSeq,
                                                  String thumbnailKey) {
        if (!thumbnailKey.startsWith(THUMBNAIL_PREFIX)) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER,
                    "MOTIF_THUMBNAIL 로 올린 키가 아니다: " + thumbnailKey);
        }
        if (!adminMediaService.exists(thumbnailKey)) {
            throw new BusinessException(AdminErrorCode.MEDIA_NOT_UPLOADED,
                    "S3 에 없는 썸네일 키: " + thumbnailKey, Map.of("thumbnailKey", thumbnailKey));
        }
        MotifResult result = motifResultRepository.findById(resultSeq)
                .orElseThrow(() -> new BusinessException(MotifErrorCode.MOTIF_RESULT_NOT_FOUND,
                        "미존재 결과물: resultSeq=" + resultSeq));
        result.changeThumbnail(thumbnailKey);
        auditLogger.write(admin, "UPDATE_MOTIF_THUMBNAIL", "MOTIF_RESULT", resultSeq,
                AdminAuditLogger.RESULT_OK);
        return new MotifThumbnailResponse(resultSeq, mediaUrlResolver.resolve(thumbnailKey));
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
