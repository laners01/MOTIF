package com.ssafy.motif.coreapi.admin.notice;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.admin.media.AdminMediaService;
import com.ssafy.motif.coreapi.admin.notice.dto.NoticeCreateRequest;
import com.ssafy.motif.coreapi.admin.notice.dto.NoticeCreateResponse;
import com.ssafy.motif.coreapi.admin.notice.dto.NoticeDetailResponse;
import com.ssafy.motif.coreapi.admin.notice.dto.NoticeImageResponse;
import com.ssafy.motif.coreapi.admin.notice.dto.NoticeListItemResponse;
import com.ssafy.motif.coreapi.admin.notice.dto.NoticeUpdateRequest;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.domain.artist.ArtistErrorCode;
import com.ssafy.motif.coreapi.domain.media.MediaErrorCode;
import com.ssafy.motif.coreapi.domain.notice.NoticeErrorCode;
import com.ssafy.motif.domain.core.notice.NoticeImageRepository;
import com.ssafy.motif.domain.core.notice.NoticeRepository;
import com.ssafy.motif.domain.core.user.User;
import com.ssafy.motif.domain.core.user.UserRepository;
import com.ssafy.motif.domain.core.user.UserRole;
import com.ssafy.motif.event.contract.NoticeCreatedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * 공지 조회·작성을 실제 DB에 대고 검증한다 — 611(§3.10.1·§3.10.3) · 402·403(§3.10.2).
 *
 * <p>수정·삭제(571)는 {@link AdminNoticeServiceLocalDbTest} 가 맡는다.
 *
 * <p><b>단언은 전부 이 테스트가 만든 아티스트로 좁힌다.</b> 로컬 compose DB 는 재사용되고
 * 시드 공지가 들어 있을 수 있어, 전체 건수에 기대면 남의 데이터로 깨진다.
 *
 * <p>발행은 {@code RabbitTemplate} 이 아니라 <b>애플리케이션 이벤트</b>로 본다. 실제 MQ
 * 전송은 {@code AFTER_COMMIT} 이라 트랜잭션을 롤백하는 테스트에서는 애초에 돌지 않는다 —
 * 거기에 {@code never()} 를 걸면 무엇을 고쳐도 통과하는 빈 검증이 된다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
@RecordApplicationEvents
class AdminNoticeApiLocalDbTest {

    private static final String LONG_CONTENT = "가".repeat(200);

    @Autowired
    private AdminNoticeService adminNoticeService;
    @Autowired
    private NoticeRepository noticeRepository;
    @Autowired
    private NoticeImageRepository noticeImageRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ApplicationEvents events;
    @PersistenceContext
    private EntityManager entityManager;

    @MockitoBean
    private AdminMediaService adminMediaService;

    private AuthenticatedAdmin admin;
    private Integer artistSeq;

    @BeforeEach
    void setUp() {
        given(adminMediaService.exists(anyString())).willReturn(true);
        admin = new AuthenticatedAdmin(1, newUser().getUserSeq(), "운영자");
        artistSeq = newArtist();
    }

    // --- 611 목록 ------------------------------------------------------------

    @Test
    @DisplayName("목록은 최신순이고 삭제분은 기본으로 빠진다")
    void listIsNewestFirstAndHidesDeleted() {
        Integer first = newNotice(artistSeq, "첫 번째", List.of());
        Integer second = newNotice(artistSeq, "두 번째", List.of());
        Integer deleted = newNotice(artistSeq, "삭제될 공지", List.of());
        adminNoticeService.delete(admin, deleted);
        entityManager.flush();

        PageResponse<NoticeListItemResponse> page = adminNoticeService.list(artistSeq, false, 0, 20);

        assertThat(page.list()).extracting(NoticeListItemResponse::noticeSeq)
                .containsExactly(second, first);
        assertThat(page.totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("includeDeleted=true 면 삭제분도 isDeleted 를 달고 딸려 온다")
    void includeDeletedReturnsDeletedRows() {
        Integer alive = newNotice(artistSeq, "살아 있음", List.of());
        Integer deleted = newNotice(artistSeq, "삭제됨", List.of());
        adminNoticeService.delete(admin, deleted);
        entityManager.flush();

        PageResponse<NoticeListItemResponse> page = adminNoticeService.list(artistSeq, true, 0, 20);

        assertThat(page.list()).extracting(NoticeListItemResponse::noticeSeq)
                .containsExactly(deleted, alive);
        assertThat(page.list().getFirst().isDeleted()).isTrue();
    }

    @Test
    @DisplayName("artistSeq 를 주면 그 아티스트 공지만, 안 주면 남의 것도 섞여 나온다")
    void artistFilterNarrowsToOneArtist() {
        Integer mine = newNotice(artistSeq, "내 공지", List.of());
        Integer otherArtist = newArtist();
        newNotice(otherArtist, "남의 공지", List.of());
        entityManager.flush();

        assertThat(adminNoticeService.list(artistSeq, false, 0, 20).list())
                .extracting(NoticeListItemResponse::noticeSeq)
                .containsExactly(mine);
        assertThat(adminNoticeService.list(null, false, 0, 20).totalElements())
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("본문은 앞 80자만, 이미지는 장수만 실린다")
    void itemCarriesPreviewAndImageCount() {
        newNotice(artistSeq, LONG_CONTENT, List.of("notices/a.jpg", "notices/b.jpg"));
        entityManager.flush();

        NoticeListItemResponse item = adminNoticeService.list(artistSeq, false, 0, 20).list().getFirst();

        assertThat(item.contentPreview()).hasSize(80).isEqualTo(LONG_CONTENT.substring(0, 80));
        assertThat(item.imageCount()).isEqualTo(2);
        assertThat(item.artist().artistSeq()).isEqualTo(artistSeq);
    }

    @Test
    @DisplayName("이미지 0 장인 공지도 imageCount 0 으로 나온다 — group by 에 행이 없다")
    void noticeWithoutImagesCountsZero() {
        newNotice(artistSeq, "이미지 없음", List.of());
        entityManager.flush();

        assertThat(adminNoticeService.list(artistSeq, false, 0, 20).list().getFirst().imageCount())
                .isZero();
    }

    @Test
    @DisplayName("페이지 봉투 — size 로 자르고 hasNext·totalPages 를 계산한다")
    void pageEnvelopeIsComputed() {
        for (int i = 0; i < 3; i++) {
            newNotice(artistSeq, "공지 " + i, List.of());
        }
        entityManager.flush();

        PageResponse<NoticeListItemResponse> first = adminNoticeService.list(artistSeq, false, 0, 2);
        assertThat(first.list()).hasSize(2);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.hasNext()).isTrue();
        assertThat(first.hasPrevious()).isFalse();

        PageResponse<NoticeListItemResponse> last = adminNoticeService.list(artistSeq, false, 1, 2);
        assertThat(last.list()).hasSize(1);
        assertThat(last.hasNext()).isFalse();
        assertThat(last.hasPrevious()).isTrue();
    }

    @Test
    @DisplayName("size 는 100 을 넘지 못한다 (§1.5)")
    void sizeIsCappedAtHundred() {
        newNotice(artistSeq, "하나", List.of());
        entityManager.flush();

        assertThat(adminNoticeService.list(artistSeq, false, 0, 5000).size()).isEqualTo(100);
    }

    // --- 611 상세 ------------------------------------------------------------

    @Test
    @DisplayName("상세는 본문 전체와 이미지를 display_order 순으로 준다")
    void detailReturnsFullContentAndOrderedImages() {
        Integer noticeSeq = newNotice(artistSeq, LONG_CONTENT,
                List.of("notices/a.jpg", "notices/b.jpg", "notices/c.jpg"));
        entityManager.flush();

        NoticeDetailResponse detail = adminNoticeService.detail(noticeSeq);

        assertThat(detail.content()).isEqualTo(LONG_CONTENT);
        assertThat(detail.images()).extracting(NoticeImageResponse::displayOrder)
                .containsExactly((short) 0, (short) 1, (short) 2);
    }

    @Test
    @DisplayName("삭제된 공지의 상세는 404 — 어드민에게도 없는 것으로 답한다")
    void deletedNoticeDetailIsNotFound() {
        Integer noticeSeq = newNotice(artistSeq, "삭제될 공지", List.of());
        adminNoticeService.delete(admin, noticeSeq);
        entityManager.flush();

        assertThatThrownBy(() -> adminNoticeService.detail(noticeSeq))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(NoticeErrorCode.NOTICE_NOT_FOUND);
    }

    // --- 402 · 403 작성 ------------------------------------------------------

    @Test
    @DisplayName("작성 — 배열 순서가 display_order 0..n−1 로 깔린다")
    void createStoresImagesInArrayOrder() {
        NoticeCreateResponse created = adminNoticeService.create(admin, new NoticeCreateRequest(
                artistSeq, "컴백 소식", List.of("notices/a.jpg", "notices/b.jpg", "notices/c.jpg")));
        entityManager.flush();

        assertThat(created.noticeSeq()).isNotNull();
        assertThat(noticeImageRepository.findByNoticeSeqOrderByDisplayOrderAsc(created.noticeSeq()))
                .extracting(image -> image.getImageKey() + "@" + image.getDisplayOrder())
                .containsExactly("notices/a.jpg@0", "notices/b.jpg@1", "notices/c.jpg@2");
        assertThat(noticeRepository.findById(created.noticeSeq()).orElseThrow().getContent())
                .isEqualTo("컴백 소식");
    }

    @Test
    @DisplayName("imageKeys 를 생략하면 이미지 없는 공지가 된다")
    void createWithoutImagesIsValid() {
        NoticeCreateResponse created = adminNoticeService.create(admin,
                new NoticeCreateRequest(artistSeq, "이미지 없는 공지", null));
        entityManager.flush();

        assertThat(noticeImageRepository.findByNoticeSeqOrderByDisplayOrderAsc(created.noticeSeq()))
                .isEmpty();
    }

    @Test
    @DisplayName("올라오지 않은 이미지 키는 409 — 부재 키를 전부 모아 돌려준다")
    void createRejectsMissingImageKeys() {
        given(adminMediaService.exists("notices/b.jpg")).willReturn(false);
        given(adminMediaService.exists("notices/c.jpg")).willReturn(false);

        assertThatThrownBy(() -> adminNoticeService.create(admin, new NoticeCreateRequest(
                artistSeq, "컴백 소식", List.of("notices/a.jpg", "notices/b.jpg", "notices/c.jpg"))))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> {
                    BusinessException e = (BusinessException) thrown;
                    assertThat(e.getErrorCode()).isEqualTo(MediaErrorCode.IMAGE_NOT_UPLOADED);
                    assertThat(e.getData()).isEqualTo(
                            java.util.Map.of("imageKeys", List.of("notices/b.jpg", "notices/c.jpg")));
                });
    }

    @Test
    @DisplayName("없는 아티스트로는 쓸 수 없다")
    void createRejectsUnknownArtist() {
        assertThatThrownBy(() -> adminNoticeService.create(admin,
                new NoticeCreateRequest(-1, "컴백 소식", List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ArtistErrorCode.ARTIST_NOT_FOUND);
    }

    @Test
    @DisplayName("작성은 notice.created 1건 — 본문 앞 60자를 싣는다")
    void createPublishesNoticeCreatedOnce() {
        NoticeCreateResponse created = adminNoticeService.create(admin,
                new NoticeCreateRequest(artistSeq, LONG_CONTENT, List.of()));

        List<NoticeCreatedEvent> published = events.stream(NoticeCreatedEvent.class).toList();
        assertThat(published).hasSize(1);
        assertThat(published.getFirst().noticeSeq()).isEqualTo(created.noticeSeq());
        assertThat(published.getFirst().artistSeq()).isEqualTo(artistSeq);
        assertThat(published.getFirst().contentPreview()).hasSize(60);
        assertThat(published.getFirst().eventId()).isNotBlank();
    }

    @Test
    @DisplayName("수정은 notice.created 를 다시 내지 않는다 — 등록에서만 발행한다")
    void updateDoesNotRepublish() {
        NoticeCreateResponse created = adminNoticeService.create(admin,
                new NoticeCreateRequest(artistSeq, "원래 본문", List.of()));
        entityManager.flush();

        adminNoticeService.update(admin, created.noticeSeq(),
                new NoticeUpdateRequest("고친 본문", List.of("notices/x.jpg")));

        assertThat(events.stream(NoticeCreatedEvent.class)).hasSize(1);   // 등록 때의 1건 그대로
    }

    // --- 픽스처 -------------------------------------------------------------

    private Integer newNotice(Integer artistSeq, String content, List<String> imageKeys) {
        Integer seq = ((Number) entityManager.createNativeQuery("""
                        insert into motif_core.notices (artist_seq, content)
                        values (:artistSeq, :content)
                        returning notice_seq
                        """)
                .setParameter("artistSeq", artistSeq)
                .setParameter("content", content)
                .getSingleResult()).intValue();

        for (short order = 0; order < imageKeys.size(); order++) {
            entityManager.createNativeQuery("""
                            insert into motif_core.notice_images (notice_seq, image_key, display_order)
                            values (:noticeSeq, :imageKey, :displayOrder)
                            """)
                    .setParameter("noticeSeq", seq)
                    .setParameter("imageKey", imageKeys.get(order))
                    .setParameter("displayOrder", order)
                    .executeUpdate();
        }
        return seq;
    }

    private User newUser() {
        User user = userRepository.saveAndFlush(User.signUp());
        user.applyInitialNickname();
        return user;
    }

    /** {@code users(ARTIST)} → {@code companies} → {@code artists} FK 체인. */
    private Integer newArtist() {
        User artistUser = newUser();
        ReflectionTestUtils.setField(artistUser, "role", UserRole.ARTIST);
        userRepository.flush();

        String companyName = "t-" + UUID.randomUUID().toString().substring(0, 20);
        entityManager.createNativeQuery(
                        "insert into motif_core.companies (company_name, reg_usr_seq) values (:name, :regUsrSeq)")
                .setParameter("name", companyName)
                .setParameter("regUsrSeq", artistUser.getUserSeq())
                .executeUpdate();
        Integer companySeq = ((Number) entityManager.createNativeQuery(
                        "select company_seq from motif_core.companies where company_name = :name")
                .setParameter("name", companyName)
                .getSingleResult()).intValue();

        entityManager.createNativeQuery("""
                        insert into motif_core.artists (user_seq, company_seq, reg_usr_seq)
                        values (:userSeq, :companySeq, :regUsrSeq)
                        """)
                .setParameter("userSeq", artistUser.getUserSeq())
                .setParameter("companySeq", companySeq)
                .setParameter("regUsrSeq", artistUser.getUserSeq())
                .executeUpdate();

        return ((Number) entityManager.createNativeQuery(
                        "select artist_seq from motif_core.artists where user_seq = :userSeq")
                .setParameter("userSeq", artistUser.getUserSeq())
                .getSingleResult()).intValue();
    }
}
