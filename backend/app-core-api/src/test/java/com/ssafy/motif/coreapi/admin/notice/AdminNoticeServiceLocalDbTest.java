package com.ssafy.motif.coreapi.admin.notice;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.media.MediaObjectChecker;
import com.ssafy.motif.coreapi.admin.notice.dto.NoticeDetailResponse;
import com.ssafy.motif.coreapi.admin.notice.dto.NoticeImageResponse;
import com.ssafy.motif.coreapi.admin.notice.dto.NoticeUpdateRequest;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.domain.media.MediaErrorCode;
import com.ssafy.motif.coreapi.domain.notice.NoticeErrorCode;
import com.ssafy.motif.domain.core.notice.NoticeImageRepository;
import com.ssafy.motif.domain.core.notice.NoticeRepository;
import com.ssafy.motif.domain.core.user.User;
import com.ssafy.motif.domain.core.user.UserRepository;
import com.ssafy.motif.domain.core.user.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * §3.10.4·§3.10.5 를 실제 DB에 대고 검증한다 (S15P21D104-571 DoD).
 *
 * <p>DoD 두 가지 — ① 이미지 3→2장 교체 후 {@code display_order} 가 0,1 로 다시 깔리는가
 * ② 수정이 알림을 발송하지 않는가.
 *
 * <p>②는 {@link RabbitTemplate} 스파이로 본다. 공지 등록(402·404)이 아직 없어 발행
 * 경로 자체가 없지만, 그 티켓이 {@code notice.created} 를 붙일 때 <b>수정 경로에도</b>
 * 걸면 여기서 깨진다 — 지금 없는 것을 나중에 생기지 않게 막는 것이 이 검증의 목적이다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class AdminNoticeServiceLocalDbTest {

    @Autowired
    private AdminNoticeService adminNoticeService;
    @Autowired
    private NoticeRepository noticeRepository;
    @Autowired
    private NoticeImageRepository noticeImageRepository;
    @Autowired
    private UserRepository userRepository;
    @PersistenceContext
    private EntityManager entityManager;

    @MockitoBean
    private MediaObjectChecker mediaObjectChecker;
    @MockitoSpyBean
    private RabbitTemplate rabbitTemplate;

    private AuthenticatedAdmin admin;
    private Integer noticeSeq;

    @BeforeEach
    void setUp() {
        given(mediaObjectChecker.exists(anyString())).willReturn(true);
        admin = new AuthenticatedAdmin(1, newUser().getUserSeq(), "운영자");
        noticeSeq = newNotice(List.of("notices/a.jpg", "notices/b.jpg", "notices/c.jpg"));
    }

    @Test
    @DisplayName("이미지 3→2장 교체 — 기존 행은 사라지고 순서가 0,1 로 다시 깔린다")
    void imagesAreFullyReplaced() {
        NoticeDetailResponse response = adminNoticeService.update(admin, noticeSeq,
                new NoticeUpdateRequest(null, List.of("notices/x.jpg", "notices/y.jpg")));
        entityManager.flush();

        assertThat(response.images())
                .extracting(NoticeImageResponse::displayOrder)
                .containsExactly((short) 0, (short) 1);
        assertThat(noticeImageRepository.findByNoticeSeqOrderByDisplayOrderAsc(noticeSeq))
                .extracting(image -> image.getImageKey())
                .containsExactly("notices/x.jpg", "notices/y.jpg");
    }

    @Test
    @DisplayName("수정은 알림을 다시 보내지 않는다 — 발송 0건")
    void updateSendsNoNotification() {
        adminNoticeService.update(admin, noticeSeq,
                new NoticeUpdateRequest("고친 본문", List.of("notices/x.jpg")));

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("imageKeys 를 생략하면 이미지는 그대로 — 본문만 바뀐다")
    void omittedImageKeysLeaveImagesAlone() {
        adminNoticeService.update(admin, noticeSeq, new NoticeUpdateRequest("고친 본문", null));
        entityManager.flush();

        assertThat(noticeImageRepository.findByNoticeSeqOrderByDisplayOrderAsc(noticeSeq)).hasSize(3);
        assertThat(noticeRepository.findById(noticeSeq).orElseThrow().getContent()).isEqualTo("고친 본문");
    }

    @Test
    @DisplayName("빈 배열은 전부 지우라는 뜻이다")
    void emptyImageKeysClearImages() {
        adminNoticeService.update(admin, noticeSeq, new NoticeUpdateRequest(null, List.of()));
        entityManager.flush();

        assertThat(noticeImageRepository.findByNoticeSeqOrderByDisplayOrderAsc(noticeSeq)).isEmpty();
    }

    @Test
    @DisplayName("이미지만 바꿔도 mod_dttm 이 올라간다")
    void imageOnlyUpdateTouchesModDttm() {
        assertThat(noticeRepository.findById(noticeSeq).orElseThrow().getModDttm()).isNull();

        adminNoticeService.update(admin, noticeSeq, new NoticeUpdateRequest(null, List.of("notices/x.jpg")));

        assertThat(noticeRepository.findById(noticeSeq).orElseThrow().getModDttm()).isNotNull();
    }

    @Test
    @DisplayName("올라오지 않은 이미지 키는 409 — 기존 이미지를 지우기 전에 막는다")
    void missingImageKeepsExistingImages() {
        given(mediaObjectChecker.exists("notices/x.jpg")).willReturn(false);

        assertThatThrownBy(() -> adminNoticeService.update(admin, noticeSeq,
                new NoticeUpdateRequest(null, List.of("notices/x.jpg", "notices/y.jpg"))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(MediaErrorCode.IMAGE_NOT_UPLOADED);

        assertThat(noticeImageRepository.findByNoticeSeqOrderByDisplayOrderAsc(noticeSeq)).hasSize(3);
    }

    @Test
    @DisplayName("삭제는 소프트 — 좋아요·이미지 행은 남는다")
    void deleteIsSoftAndKeepsChildren() {
        newLike(noticeSeq);

        adminNoticeService.delete(admin, noticeSeq);
        entityManager.flush();

        assertThat(noticeRepository.findById(noticeSeq).orElseThrow().isDeleted()).isTrue();
        assertThat(noticeImageRepository.findByNoticeSeqOrderByDisplayOrderAsc(noticeSeq)).hasSize(3);
        assertThat(countLikes(noticeSeq)).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 삭제된 공지는 404 — 수정도 재삭제도 막는다")
    void deletedNoticeIsNotFound() {
        adminNoticeService.delete(admin, noticeSeq);
        entityManager.flush();

        assertThatThrownBy(() -> adminNoticeService.update(admin, noticeSeq,
                new NoticeUpdateRequest("고친 본문", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(NoticeErrorCode.NOTICE_NOT_FOUND);

        assertThatThrownBy(() -> adminNoticeService.delete(admin, noticeSeq))
                .isInstanceOf(BusinessException.class);
    }

    // --- 픽스처 -------------------------------------------------------------

    private Integer newNotice(List<String> imageKeys) {
        Integer artistSeq = newArtist();
        Integer seq = ((Number) entityManager.createNativeQuery("""
                        insert into motif_core.notices (artist_seq, content)
                        values (:artistSeq, '컴백 소식 전체 본문')
                        returning notice_seq
                        """)
                .setParameter("artistSeq", artistSeq)
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

    private void newLike(Integer noticeSeq) {
        entityManager.createNativeQuery(
                        "insert into motif_core.notice_likes (notice_seq, user_seq) values (:noticeSeq, :userSeq)")
                .setParameter("noticeSeq", noticeSeq)
                .setParameter("userSeq", newUser().getUserSeq())
                .executeUpdate();
    }

    private long countLikes(Integer noticeSeq) {
        return ((Number) entityManager.createNativeQuery(
                        "select count(*) from motif_core.notice_likes where notice_seq = :seq")
                .setParameter("seq", noticeSeq)
                .getSingleResult()).longValue();
    }

    private User newUser() {
        User user = userRepository.saveAndFlush(User.signUp());
        user.applyInitialNickname();
        return user;
    }

    /** {@code users(ARTIST)} → {@code companies} → {@code artists} FK 체인 (온보딩 테스트와 같은 방식). */
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
