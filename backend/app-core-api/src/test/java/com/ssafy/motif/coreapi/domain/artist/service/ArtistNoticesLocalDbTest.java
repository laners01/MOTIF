package com.ssafy.motif.coreapi.domain.artist.service;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.common.response.CursorResponse;
import com.ssafy.motif.coreapi.domain.artist.ArtistErrorCode;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistNoticeResponse;
import com.ssafy.motif.domain.core.artist.Artist;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.notice.Notice;
import com.ssafy.motif.domain.core.notice.NoticeImage;
import com.ssafy.motif.domain.core.notice.NoticeImageRepository;
import com.ssafy.motif.domain.core.notice.NoticeRepository;
import com.ssafy.motif.domain.core.user.User;
import com.ssafy.motif.domain.core.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §5.5 아티스트 공지 목록(커서)을 실제 DB에 대고 검증한다 (S15P21D104-79).
 *
 * <p><b>커서 없는 최초 호출을 반드시 덮는다.</b> 이 API 에는 검색어가 없지만 커서가
 * 같은 함정을 갖는다 — {@code :cursor is null or …} 로 한 쿼리에 합쳤다면 타입 없는
 * {@code null} 을 Postgres 가 {@code bytea} 로 추론해 운영 500 이 났을 자리다
 * ({@code ArtistRepository.search} 의 {@code lower(bytea)} 사고와 같은 원인).
 * 파생 메서드를 둘로 나눠 {@code null} 이 쿼리에 닿지 않게 했고, 그 계약을 여기서 고정한다.
 *
 * <p><b>로컬 compose DB 는 세 레인이 공유한다</b> — 단언은 이 테스트가 만든 아티스트의
 * 공지로만 좁힌다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class ArtistNoticesLocalDbTest {

    @Autowired
    private ArtistQueryService artistQueryService;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private NoticeRepository noticeRepository;
    @Autowired
    private NoticeImageRepository noticeImageRepository;
    @Autowired
    private UserRepository userRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private Integer adminUserSeq;
    private Integer fanSeq;
    private Integer artistSeq;

    @BeforeEach
    void setUp() {
        adminUserSeq = newUser().getUserSeq();
        fanSeq = newUser().getUserSeq();
        artistSeq = newArtist();
    }

    @Test
    @DisplayName("커서를 생략한 최초 호출이 500 이 아니라 첫 페이지를 준다 — bytea 추론 함정 방어")
    void firstCallWithoutCursorWorks() {
        Integer first = newNotice().getNoticeSeq();
        Integer second = newNotice().getNoticeSeq();

        CursorResponse<ArtistNoticeResponse> page =
                artistQueryService.notices(fanSeq, artistSeq, null, 20);

        assertThat(page.list())
                .extracting(ArtistNoticeResponse::noticeSeq)
                .containsExactly(second, first);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @DisplayName("빈 문자열 커서도 최초 호출과 같다 — 클라가 빈 값을 붙여 보내도 깨지지 않는다")
    void blankCursorIsTreatedAsFirstCall() {
        Integer only = newNotice().getNoticeSeq();

        assertThat(artistQueryService.notices(fanSeq, artistSeq, "", 20).list())
                .extracting(ArtistNoticeResponse::noticeSeq)
                .containsExactly(only);
    }

    @Test
    @DisplayName("nextCursor 로 이어 받으면 겹침도 누락도 없다")
    void cursorWalksThePagesWithoutOverlap() {
        Integer n1 = newNotice().getNoticeSeq();
        Integer n2 = newNotice().getNoticeSeq();
        Integer n3 = newNotice().getNoticeSeq();

        CursorResponse<ArtistNoticeResponse> first =
                artistQueryService.notices(fanSeq, artistSeq, null, 2);
        assertThat(first.list())
                .extracting(ArtistNoticeResponse::noticeSeq)
                .containsExactly(n3, n2);
        assertThat(first.hasNext()).isTrue();
        assertThat(first.nextCursor()).isNotNull();

        CursorResponse<ArtistNoticeResponse> second =
                artistQueryService.notices(fanSeq, artistSeq, first.nextCursor(), 2);
        assertThat(second.list())
                .extracting(ArtistNoticeResponse::noticeSeq)
                .containsExactly(n1);
        assertThat(second.hasNext()).isFalse();
        assertThat(second.nextCursor()).isNull();
    }

    @Test
    @DisplayName("역방향은 아직 계약이 아니다 — prevCursor 는 null, hasPrevious 는 false")
    void backwardPagingIsNotPartOfTheContractYet() {
        newNotice();

        CursorResponse<ArtistNoticeResponse> page =
                artistQueryService.notices(fanSeq, artistSeq, null, 20);

        assertThat(page.prevCursor()).isNull();
        assertThat(page.hasPrevious()).isFalse();
    }

    @Test
    @DisplayName("망가진 커서는 400 이다 — 조용히 첫 페이지로 되돌리지 않는다")
    void brokenCursorIsRejected() {
        assertThatThrownBy(() -> artistQueryService.notices(fanSeq, artistSeq, "!!not-base64!!", 20))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_PARAMETER);
    }

    @Test
    @DisplayName("images 는 displayOrder 오름차순 전체 배열이다")
    void imagesComeInDisplayOrder() {
        Notice notice = newNotice();
        noticeImageRepository.saveAll(java.util.List.of(
                NoticeImage.of(notice.getNoticeSeq(), "notices/b.jpg", (short) 1),
                NoticeImage.of(notice.getNoticeSeq(), "notices/a.jpg", (short) 0)));
        entityManager.flush();

        ArtistNoticeResponse item =
                artistQueryService.notices(fanSeq, artistSeq, null, 20).list().getFirst();

        assertThat(item.images())
                .extracting(ArtistNoticeResponse.Image::displayOrder)
                .containsExactly((short) 0, (short) 1);
    }

    @Test
    @DisplayName("isLiked 는 호출자 기준이다 — 남이 누른 좋아요는 내 응답에서 false")
    void isLikedIsPerCaller() {
        Notice mine = newNotice();
        Integer otherFan = newUser().getUserSeq();
        like(mine.getNoticeSeq(), otherFan);

        assertThat(artistQueryService.notices(fanSeq, artistSeq, null, 20).list().getFirst().isLiked())
                .isFalse();

        like(mine.getNoticeSeq(), fanSeq);

        assertThat(artistQueryService.notices(fanSeq, artistSeq, null, 20).list().getFirst().isLiked())
                .isTrue();
    }

    @Test
    @DisplayName("artist 블록에 groupName 이 없다 — §5.5 명시")
    void artistBlockHasNoGroupName() {
        newNotice();

        ArtistNoticeResponse.Artist writer =
                artistQueryService.notices(fanSeq, artistSeq, null, 20).list().getFirst().artist();

        assertThat(writer.artistSeq()).isEqualTo(artistSeq);
        assertThat(writer.name()).isEqualTo("카리나");
    }

    @Test
    @DisplayName("삭제된 공지는 빠진다 — §1.8")
    void deletedNoticesAreHidden() {
        Integer kept = newNotice().getNoticeSeq();
        Notice removed = newNotice();
        removed.softDelete();
        entityManager.flush();

        assertThat(artistQueryService.notices(fanSeq, artistSeq, null, 20).list())
                .extracting(ArtistNoticeResponse::noticeSeq)
                .containsExactly(kept);
    }

    @Test
    @DisplayName("공지가 없으면 빈 목록이고 커서도 없다")
    void emptyArtistYieldsEmptyPage() {
        CursorResponse<ArtistNoticeResponse> page =
                artistQueryService.notices(fanSeq, artistSeq, null, 20);

        assertThat(page.list()).isEmpty();
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @DisplayName("size 는 100 을 넘지 못한다 — §1.9 상한")
    void sizeIsCappedAtHundred() {
        newNotice();

        // 상한을 넘겨도 예외가 아니라 잘려서 정상 동작한다
        assertThat(artistQueryService.notices(fanSeq, artistSeq, null, 5_000).list()).hasSize(1);
    }

    @Test
    @DisplayName("삭제된 아티스트는 404 다")
    void deletedArtistIsNotFound() {
        entityManager.createNativeQuery(
                        "update motif_core.artists set is_deleted = true where artist_seq = :seq")
                .setParameter("seq", artistSeq)
                .executeUpdate();
        entityManager.clear();

        assertThatThrownBy(() -> artistQueryService.notices(fanSeq, artistSeq, null, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ArtistErrorCode.ARTIST_NOT_FOUND);
    }

    // --- 픽스처 -------------------------------------------------------------

    private User newUser() {
        User user = userRepository.saveAndFlush(User.signUp());
        user.applyInitialNickname();
        return user;
    }

    private Integer newArtist() {
        User artistUser = userRepository.saveAndFlush(User.createArtist("카리나"));
        return artistRepository.saveAndFlush(Artist.register(
                artistUser.getUserSeq(), null, newCompany(), null, null, adminUserSeq)).getArtistSeq();
    }

    private Integer newCompany() {
        String name = "t-" + UUID.randomUUID().toString().substring(0, 20);
        entityManager.createNativeQuery(
                        "insert into motif_core.companies (company_name, reg_usr_seq) values (:name, :regUsrSeq)")
                .setParameter("name", name)
                .setParameter("regUsrSeq", adminUserSeq)
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery(
                        "select company_seq from motif_core.companies where company_name = :name")
                .setParameter("name", name)
                .getSingleResult()).intValue();
    }

    private Notice newNotice() {
        return noticeRepository.saveAndFlush(Notice.register(artistSeq, "본문 " + UUID.randomUUID()));
    }

    /** 좋아요 토글 API(§10.2)가 아직 없어 행을 직접 넣는다 — 검증 대상은 조회 쪽이다. */
    private void like(Integer noticeSeq, Integer userSeq) {
        entityManager.createNativeQuery(
                        "insert into motif_core.notice_likes (notice_seq, user_seq) values (:noticeSeq, :userSeq)")
                .setParameter("noticeSeq", noticeSeq)
                .setParameter("userSeq", userSeq)
                .executeUpdate();
        entityManager.clear();
    }
}
