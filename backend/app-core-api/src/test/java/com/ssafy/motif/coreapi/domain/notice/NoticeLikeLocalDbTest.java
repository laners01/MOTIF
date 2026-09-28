package com.ssafy.motif.coreapi.domain.notice;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistNoticeResponse;
import com.ssafy.motif.coreapi.domain.artist.service.ArtistQueryService;
import com.ssafy.motif.coreapi.domain.notice.service.NoticeLikeService;
import com.ssafy.motif.coreapi.domain.video.VideoTestData;
import com.ssafy.motif.domain.core.notice.Notice;
import com.ssafy.motif.domain.core.notice.NoticeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §10.2 공지 좋아요 토글을 실제 DB 에 대고 검증한다 (S15P21D104-96).
 *
 * <p><b>멱등이 이 API 의 핵심이다.</b> 같은 요청을 두 번 받아도 카운터가 한 번만 움직여야
 * 하고, 해제를 두 번 받아도 음수가 되면 안 된다 — 후자는 {@code ck_notices_like_count}
 * 위반으로 터지는 자리라 단위 테스트로는 드러나지 않는다.
 *
 * <p>결과는 <b>읽기 API 로</b> 확인한다(§5.5 {@code isLiked}·{@code likeCount}). 204 라
 * 응답에 아무것도 없어서, 목록에 반영되는지가 이 API 의 관측 가능한 전부다 — 공지 상세
 * API 는 없다(구 §10.1, v0.8 삭제).
 *
 * <p><b>로컬 compose DB 는 여러 레인이 공유한다</b> — 단언은 이 테스트가 만든 아티스트의
 * 공지로만 좁힌다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class NoticeLikeLocalDbTest {

    @Autowired
    private NoticeLikeService noticeLikeService;
    @Autowired
    private ArtistQueryService artistQueryService;
    @Autowired
    private NoticeRepository noticeRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private VideoTestData videos;

    private Integer artistSeq;
    private Integer me;
    private Integer fan;

    @BeforeEach
    void setUp() {
        videos = new VideoTestData(entityManager);

        artistSeq = videos.newArtist();
        me = videos.newUser();
        fan = videos.newUser();
    }

    @Test
    @DisplayName("좋아요는 멱등 — 두 번 눌러도 카운터는 한 번만 오르고 행도 하나다")
    void likeIsIdempotent() {
        Integer notice = newNotice();

        noticeLikeService.like(me, notice);
        noticeLikeService.like(me, notice);

        ArtistNoticeResponse item = firstNotice();
        assertThat(item.isLiked()).isTrue();
        assertThat(item.likeCount()).isEqualTo(1);
        assertThat(likeRows(notice)).isEqualTo(1);
    }

    @Test
    @DisplayName("해제도 멱등 — 누르지 않은 상태에서 두 번 해제해도 0 이고 음수가 되지 않는다")
    void unlikeIsIdempotentAndNeverNegative() {
        Integer notice = newNotice();

        noticeLikeService.like(me, notice);
        noticeLikeService.unlike(me, notice);
        noticeLikeService.unlike(me, notice);
        noticeLikeService.unlike(me, notice);

        ArtistNoticeResponse item = firstNotice();
        assertThat(item.isLiked()).isFalse();
        assertThat(item.likeCount()).isZero();
        // 해제는 하드 삭제다 — 행이 남지 않는다
        assertThat(likeRows(notice)).isZero();
    }

    @Test
    @DisplayName("토글 왕복 — 다시 누르면 올라간다")
    void toggleRoundTrip() {
        Integer notice = newNotice();

        noticeLikeService.like(me, notice);
        noticeLikeService.unlike(me, notice);
        noticeLikeService.like(me, notice);

        ArtistNoticeResponse item = firstNotice();
        assertThat(item.isLiked()).isTrue();
        assertThat(item.likeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("남의 좋아요는 내 isLiked 가 아니다 — 카운터는 함께 오른다")
    void likeIsPerUser() {
        Integer notice = newNotice();

        noticeLikeService.like(fan, notice);
        assertThat(firstNotice().isLiked()).isFalse();
        assertThat(firstNotice().likeCount()).isEqualTo(1);

        noticeLikeService.like(me, notice);
        assertThat(firstNotice().isLiked()).isTrue();
        assertThat(firstNotice().likeCount()).isEqualTo(2);

        // 내 해제가 남의 행까지 지우면 안 된다
        noticeLikeService.unlike(me, notice);
        assertThat(firstNotice().isLiked()).isFalse();
        assertThat(firstNotice().likeCount()).isEqualTo(1);
        assertThat(likeRows(notice)).isEqualTo(1);
    }

    @Test
    @DisplayName("미존재 공지는 404 — 생성·해제 양쪽 다")
    void unknownNoticeIsNotFound() {
        assertNotFound(-1);
    }

    @Test
    @DisplayName("삭제된 공지는 없는 것으로 답한다 — 눌러 둔 뒤 삭제돼도 해제가 404")
    void deletedNoticeIsNotFound() {
        Integer notice = newNotice();
        noticeLikeService.like(me, notice);

        softDelete(notice);

        assertNotFound(notice);
    }

    private Integer newNotice() {
        Notice notice = noticeRepository.save(Notice.register(artistSeq, "공지 본문"));
        entityManager.flush();
        entityManager.clear();
        return notice.getNoticeSeq();
    }

    private ArtistNoticeResponse firstNotice() {
        return artistQueryService.notices(me, artistSeq, null, 20).list().getFirst();
    }

    private void assertNotFound(Integer noticeSeq) {
        assertThatThrownBy(() -> noticeLikeService.like(me, noticeSeq))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(NoticeErrorCode.NOTICE_NOT_FOUND);
        assertThatThrownBy(() -> noticeLikeService.unlike(me, noticeSeq))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(NoticeErrorCode.NOTICE_NOT_FOUND);
    }

    private void softDelete(Integer noticeSeq) {
        entityManager.createNativeQuery(
                        "update motif_core.notices set is_deleted = true where notice_seq = ?1")
                .setParameter(1, noticeSeq)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    private int likeRows(Integer noticeSeq) {
        return ((Number) entityManager
                .createNativeQuery("select count(*) from motif_core.notice_likes"
                        + " where notice_seq = ?1")
                .setParameter(1, noticeSeq)
                .getSingleResult()).intValue();
    }
}
