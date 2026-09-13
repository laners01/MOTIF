package com.ssafy.motif.coreapi.admin.notice;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.admin.audit.AdminAuditLogger;
import com.ssafy.motif.coreapi.admin.media.AdminMediaService;
import com.ssafy.motif.coreapi.admin.notice.dto.NoticeDetailResponse;
import com.ssafy.motif.coreapi.admin.notice.dto.NoticeImageResponse;
import com.ssafy.motif.coreapi.admin.notice.dto.NoticeUpdateRequest;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.admin.notice.dto.NoticeCreateRequest;
import com.ssafy.motif.coreapi.admin.notice.dto.NoticeCreateResponse;
import com.ssafy.motif.coreapi.admin.notice.dto.NoticeListItemResponse;
import com.ssafy.motif.coreapi.domain.artist.ArtistErrorCode;
import com.ssafy.motif.coreapi.domain.media.MediaErrorCode;
import com.ssafy.motif.coreapi.domain.notice.NoticeErrorCode;
import com.ssafy.motif.coreapi.global.media.PublicMediaUrlResolver;
import com.ssafy.motif.domain.core.artist.ArtistName;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.notice.Notice;
import com.ssafy.motif.domain.core.notice.NoticeImage;
import com.ssafy.motif.domain.core.notice.NoticeImageRepository;
import com.ssafy.motif.domain.core.notice.NoticeImageRepository.ImageCount;
import com.ssafy.motif.domain.core.notice.NoticeRepository;
import com.ssafy.motif.event.contract.NoticeCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 공지 조회·작성·수정·삭제 (어드민 명세 §3.10) — 571(수정·삭제) · 611(조회) · 402·403(작성).
 *
 * <p><b>수정은 알림을 다시 보내지 않는다.</b> {@code notice.created} 는 등록(§3.10.2)
 * 한 번만 발행한다 — 오타를 고칠 때마다 구독자 전원에게 푸시가 가면 알림이 못 쓰게 된다
 * (명세 확정). 발행은 {@link #create} 안의 {@link #publishCreated} 한 군데뿐이고
 * {@link #update} 에서는 부르지 않는다 — 통합 테스트가 이 경계를 지킨다.
 *
 * <p><b>영상이 없으므로 {@code job.media} 도 없다.</b> 공지는 이미지만 붙고 렌디션·HLS
 * 대상이 아니다 — 챌린지 등록이 메시지 2건인 것과 다르다(§1.6 표).
 *
 * <p><b>이미지는 부분 수정이 없다.</b> {@code imageKeys} 가 오면 기존 행을 하드 삭제하고
 * 배열 순서대로 다시 만든다(DB §28). {@code uq_notice_images_order} 가
 * {@code (notice_seq, display_order)} 를 잠그고 있어 개별 이동은 중간 상태에서 반드시
 * 충돌하는데, 전체 교체는 그 문제가 없다. 생략하면 이미지는 건드리지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AdminNoticeService {

    /** 목록 본문 앞자락 (§3.10.1). 이벤트 페이로드는 더 짧다 — 푸시 본문은 어차피 잘린다. */
    private static final int LIST_PREVIEW_LENGTH = 80;
    /** {@code notice.created} payload 의 {@code contentPreview} (§4). */
    private static final int EVENT_PREVIEW_LENGTH = 60;
    /** §1.5 — size 기본 20 · 최대 100. */
    private static final int MAX_PAGE_SIZE = 100;

    private final NoticeRepository noticeRepository;
    private final NoticeImageRepository noticeImageRepository;
    private final ArtistRepository artistRepository;
    private final AdminMediaService adminMediaService;
    private final PublicMediaUrlResolver mediaUrlResolver;
    private final AdminAuditLogger auditLogger;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 목록 (§3.10.1) — 아티스트 필터 · 삭제분 포함 여부 · 최신순 고정.
     *
     * <p><b>삭제분은 기본으로 빠진다.</b> {@code includeDeleted=true} 일 때만 딸려 오고,
     * 그때도 지우지 않고 {@code isDeleted} 를 실어 보낸다 — 화면(572)이 회색으로 보여
     * 주되 편집·삭제 버튼을 뺀다.
     *
     * <p>이름과 이미지 장수는 <b>페이지당 한 번씩</b> 모아 읽는다. 공지마다 조회하면
     * 100건짜리 페이지에서 왕복이 200회가 된다.
     */
    @Transactional(readOnly = true)
    public PageResponse<NoticeListItemResponse> list(Integer artistSeq, boolean includeDeleted,
                                                     int page, int size) {
        Page<Notice> notices = noticeRepository.findAdminPage(artistSeq, includeDeleted,
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE)));

        Map<Integer, String> artistNames = artistNames(notices.getContent());
        Map<Integer, Long> imageCounts = imageCounts(notices.getContent());

        List<NoticeListItemResponse> items = notices.getContent().stream()
                .map(notice -> new NoticeListItemResponse(
                        notice.getNoticeSeq(),
                        new NoticeListItemResponse.ArtistSummary(
                                notice.getArtistSeq(), artistNames.get(notice.getArtistSeq())),
                        preview(notice.getContent(), LIST_PREVIEW_LENGTH),
                        imageCounts.getOrDefault(notice.getNoticeSeq(), 0L).intValue(),
                        notice.getLikeCount(), notice.getCommentCount(),
                        notice.isDeleted(), notice.getRegDttm()))
                .toList();

        return PageResponse.of(items, notices.getNumber(), notices.getSize(), notices.getTotalElements());
    }

    /** 상세 (§3.10.3) — 본문 전체 + 이미지 순서대로. 삭제분은 없는 것으로 답한다. */
    @Transactional(readOnly = true)
    public NoticeDetailResponse detail(int noticeSeq) {
        return detail(notice(noticeSeq));
    }

    /**
     * 작성 (§3.10.2) — 본문 + 이미지 n행을 한 트랜잭션에, 커밋 후 {@code notice.created}.
     *
     * <p>이미지 INSERT 는 수정 경로와 <b>같은 {@link #replaceImages}</b> 를 쓴다. 새
     * 공지라 지울 행이 없어 앞의 삭제는 빈 동작이고, 대신 HEAD 검증과
     * {@code display_order} 규칙이 한 곳에만 남는다 — 두 벌이면 한쪽만 고쳐져 등록과
     * 수정의 순서 규칙이 갈라진다.
     */
    @Transactional
    public NoticeCreateResponse create(AuthenticatedAdmin admin, NoticeCreateRequest request) {
        requireArtist(request.artistSeq());

        Notice notice = noticeRepository.save(Notice.register(request.artistSeq(), request.content()));
        replaceImages(notice.getNoticeSeq(), request.imageKeys());
        publishCreated(notice);

        auditLogger.write(admin, "CREATE_NOTICE", "NOTICE",
                notice.getNoticeSeq(), AdminAuditLogger.RESULT_OK);
        return new NoticeCreateResponse(notice.getNoticeSeq());
    }

    /** §3.10.4 — 본문 교체 + 이미지 전체 교체. 이벤트 없음. */
    @Transactional
    public NoticeDetailResponse update(AuthenticatedAdmin admin, int noticeSeq, NoticeUpdateRequest request) {
        Notice notice = notice(noticeSeq);
        if (request.content() != null) {
            if (request.content().isBlank()) {
                throw new BusinessException(CommonErrorCode.INVALID_PARAMETER,
                        "content 는 비어 있을 수 없다: noticeSeq=" + noticeSeq);
            }
            notice.changeContent(request.content());
        }
        if (request.imageKeys() != null) {
            replaceImages(noticeSeq, request.imageKeys());
        }

        noticeRepository.touch(noticeSeq, Instant.now());

        auditLogger.write(admin, "UPDATE_NOTICE", "NOTICE", noticeSeq, AdminAuditLogger.RESULT_OK);
        return detail(notice(noticeSeq));   // touch 가 컨텍스트를 비웠으니 다시 읽는다
    }

    /** §3.10.5 — 소프트 삭제. 좋아요·이미지 행은 그대로 둔다. */
    @Transactional
    public void delete(AuthenticatedAdmin admin, int noticeSeq) {
        notice(noticeSeq).softDelete();
        auditLogger.write(admin, "DELETE_NOTICE", "NOTICE", noticeSeq, AdminAuditLogger.RESULT_OK);
    }

    /**
     * 하드 삭제 → {@code display_order} 0..n−1 재생성 (DB §28).
     *
     * <p>HEAD 를 먼저 전부 돌리고 나서 지운다 — 중간에 빠진 키가 나오면 기존 이미지를
     * 이미 날린 뒤가 된다. 빠진 키는 한 건에서 멈추지 않고 <b>전부 모아</b> 돌려준다
     * (§5 — {@code data} 에 키 목록). 어드민이 한 번에 다시 올릴 수 있다.
     */
    private void replaceImages(int noticeSeq, List<String> imageKeys) {
        List<String> missing = imageKeys.stream()
                .filter(key -> !adminMediaService.exists(key))
                .toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(MediaErrorCode.IMAGE_NOT_UPLOADED,
                    "S3 에 없는 이미지 키: " + missing, Map.of("imageKeys", missing));
        }

        noticeImageRepository.deleteByNoticeSeq(noticeSeq);
        noticeImageRepository.flush();   // UNIQUE(notice_seq, display_order) — 삭제가 먼저 DB 에 닿아야 한다
        for (short order = 0; order < imageKeys.size(); order++) {
            noticeImageRepository.save(NoticeImage.of(noticeSeq, imageKeys.get(order), order));
        }
    }

    /**
     * 등록 통지 (§1.6 표 · §4 계약) — <b>커밋 후에</b> 나간다.
     *
     * <p>여기서 부르는 것은 발행이 아니라 스프링 애플리케이션 이벤트다. MQ 로 나가는
     * 시점은 {@code @TransactionalEventListener(AFTER_COMMIT)} 인
     * {@link com.ssafy.motif.coreapi.domain.notice.NoticeEventRelay} 가 정한다.
     */
    private void publishCreated(Notice notice) {
        String artistName = artistRepository.findNames(Set.of(notice.getArtistSeq())).stream()
                .findFirst().map(ArtistName::name).orElse(null);

        eventPublisher.publishEvent(new NoticeCreatedEvent(
                UUID.randomUUID().toString(), Instant.now(),
                notice.getNoticeSeq(), notice.getArtistSeq(), artistName,
                preview(notice.getContent(), EVENT_PREVIEW_LENGTH)));
    }

    /** 아티스트가 존재하고 비삭제인지 (§3.10 실패표 {@code ARTIST_NOT_FOUND}). */
    private void requireArtist(Integer artistSeq) {
        if (artistRepository.countByArtistSeqInAndDeletedFalse(Set.of(artistSeq)) != 1) {
            throw new BusinessException(ArtistErrorCode.ARTIST_NOT_FOUND,
                    "존재하지 않거나 삭제된 아티스트: artistSeq=" + artistSeq);
        }
    }

    /** 목록·이벤트가 함께 쓰는 본문 앞자락. 길이만 다르다(80자 / 60자). */
    private static String preview(String content, int max) {
        return content.length() <= max ? content : content.substring(0, max);
    }

    private Map<Integer, String> artistNames(List<Notice> notices) {
        if (notices.isEmpty()) {
            return Map.of();
        }
        Set<Integer> artistSeqs = notices.stream().map(Notice::getArtistSeq).collect(Collectors.toSet());
        return artistRepository.findNames(artistSeqs).stream()
                .collect(Collectors.toMap(ArtistName::artistSeq, ArtistName::name));
    }

    /** 이미지가 0 장인 공지는 결과에 행이 없다 — 호출부가 기본값 0 을 쓴다. */
    private Map<Integer, Long> imageCounts(List<Notice> notices) {
        if (notices.isEmpty()) {
            return Map.of();
        }
        return noticeImageRepository
                .countByNoticeSeqs(notices.stream().map(Notice::getNoticeSeq).toList()).stream()
                .collect(Collectors.toMap(ImageCount::getNoticeSeq, ImageCount::getImageCount));
    }

    private NoticeDetailResponse detail(Notice notice) {
        List<NoticeImageResponse> images = noticeImageRepository
                .findByNoticeSeqOrderByDisplayOrderAsc(notice.getNoticeSeq()).stream()
                .map(image -> new NoticeImageResponse(
                        mediaUrlResolver.resolve(image.getImageKey()), image.getDisplayOrder()))
                .toList();
        return new NoticeDetailResponse(notice.getNoticeSeq(), notice.getArtistSeq(), notice.getContent(),
                images, notice.getLikeCount(), notice.getCommentCount(), notice.isDeleted(),
                notice.getRegDttm(), notice.getModDttm());
    }

    private Notice notice(int noticeSeq) {
        return noticeRepository.findByNoticeSeqAndDeletedFalse(noticeSeq)
                .orElseThrow(() -> new BusinessException(NoticeErrorCode.NOTICE_NOT_FOUND,
                        "미존재·삭제 공지: noticeSeq=" + noticeSeq));
    }
}
