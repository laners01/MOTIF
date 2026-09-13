package com.ssafy.motif.coreapi.admin.song;

import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.admin.song.dto.SongCreateRequest;
import com.ssafy.motif.coreapi.admin.song.dto.SongResponse;
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

/**
 * 곡 목록·등록 (S15P21D104-389 — 명세 §3.6).
 *
 * <p>{@code @Transactional} 로 매 테스트를 롤백한다 — 이 API 는 이벤트도 커밋 후 동작도
 * 없어서 트랜잭션 경계 자체가 검증 대상이 아니다(챌린지 등록과 다른 점이다).
 * 실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 *
 * <p>곡 테이블은 시드가 비어 있지 않을 수 있다(다른 테스트·수동 확인이 남긴 행). 그래서
 * 전체 개수가 아니라 <b>이 테스트가 만든 곡</b>만 걸리는 고유 토큰으로 검색해 확인한다.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class AdminSongServiceLocalDbTest {

    @Autowired
    private AdminSongService adminSongService;
    @PersistenceContext
    private EntityManager entityManager;

    private AuthenticatedAdmin admin;
    /** 이 테스트가 만든 행만 골라내는 검색 토큰 — 곡명·가수명에 함께 심는다. */
    private String token;

    @BeforeEach
    void setUp() {
        admin = new AuthenticatedAdmin(1, newUser(), "테스트어드민");
        token = "tk" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    @DisplayName("등록 — songSeq 를 돌려주고 reg_usr_seq 는 adminSeq 가 아니라 userSeq 다")
    void 등록() {
        Integer songSeq = adminSongService.create(admin,
                new SongCreateRequest(token + " Supernova", "aespa")).songSeq();

        assertThat(songSeq).isNotNull();
        assertThat(scalar("select reg_usr_seq from motif_core.songs where song_seq = ?1", songSeq))
                .isEqualTo(admin.userSeq());
    }

    @Test
    @DisplayName("중복 곡명·가수명을 막지 않는다 — 리믹스·재녹음이 같은 제목을 쓴다")
    void 중복_허용() {
        SongCreateRequest 같은_곡 = new SongCreateRequest(token + " Supernova", "aespa");

        Integer first = adminSongService.create(admin, 같은_곡).songSeq();
        Integer second = adminSongService.create(admin, 같은_곡).songSeq();

        assertThat(second).isNotEqualTo(first);
        assertThat(adminSongService.list(token, 0, 20).totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("목록은 최신 등록순이다")
    void 최신순() {
        Integer 먼저 = adminSongService.create(admin, new SongCreateRequest(token + " 먼저", "가수")).songSeq();
        Integer 나중 = adminSongService.create(admin, new SongCreateRequest(token + " 나중", "가수")).songSeq();

        assertThat(adminSongService.list(token, 0, 20).list())
                .extracting(SongResponse::songSeq)
                .containsExactly(나중, 먼저);
    }

    @Test
    @DisplayName("q 는 곡명·가수명 둘 다 본다 — 대소문자를 무시한다")
    void 검색() {
        adminSongService.create(admin, new SongCreateRequest(token + " Supernova", "aespa"));
        adminSongService.create(admin, new SongCreateRequest(token + " Ditto", "NewJeans"));

        assertThat(adminSongService.list(token + " supernova", 0, 20).list())
                .extracting(SongResponse::title).containsExactly(token + " Supernova");
        assertThat(adminSongService.list("NEWJEANS", 0, 20).list())
                .extracting(SongResponse::singerName).contains("NewJeans");
    }

    @Test
    @DisplayName("q 가 없거나 공백뿐이면 전체 조회다")
    void 빈_검색어() {
        adminSongService.create(admin, new SongCreateRequest(token + " Supernova", "aespa"));

        assertThat(adminSongService.list(null, 0, 20).totalElements()).isPositive();
        assertThat(adminSongService.list("   ", 0, 20).totalElements()).isPositive();
    }

    @Test
    @DisplayName("challengeCount 는 삭제되지 않은 챌린지만 센다")
    void 챌린지_수() {
        Integer 쓰이는_곡 = adminSongService.create(admin,
                new SongCreateRequest(token + " 쓰이는곡", "가수")).songSeq();
        Integer 안_쓰이는_곡 = adminSongService.create(admin,
                new SongCreateRequest(token + " 안쓰이는곡", "가수")).songSeq();
        Integer artistSeq = newArtist();
        newChallenge(artistSeq, 쓰이는_곡, false);
        newChallenge(artistSeq, 쓰이는_곡, true);      // 소프트 삭제 — 세지 않는다

        PageResponse<SongResponse> page = adminSongService.list(token, 0, 20);

        assertThat(page.list()).filteredOn(s -> s.songSeq().equals(쓰이는_곡))
                .singleElement().extracting(SongResponse::challengeCount).isEqualTo(1L);
        assertThat(page.list()).filteredOn(s -> s.songSeq().equals(안_쓰이는_곡))
                .singleElement().extracting(SongResponse::challengeCount).isEqualTo(0L);
    }

    @Test
    @DisplayName("검색 결과가 없어도 터지지 않는다 — 빈 목록에 challengeCount 를 묻지 않는다")
    void 빈_페이지() {
        PageResponse<SongResponse> page = adminSongService.list("존재하지않는곡" + UUID.randomUUID(), 0, 20);

        assertThat(page.list()).isEmpty();
        assertThat(page.totalElements()).isZero();
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    @DisplayName("size 는 100 으로 잘리고 0 이하는 1 이 된다 — page 음수는 0")
    void 페이지_파라미터_보정() {
        adminSongService.create(admin, new SongCreateRequest(token + " Supernova", "aespa"));

        assertThat(adminSongService.list(token, 0, 1000).size()).isEqualTo(100);
        assertThat(adminSongService.list(token, -1, 0).page()).isZero();
        assertThat(adminSongService.list(token, -1, 0).size()).isEqualTo(1);
    }

    @Test
    @DisplayName("페이지가 넘어가면 hasNext·totalPages 가 Page 계산 그대로 실린다")
    void 페이지_계산() {
        adminSongService.create(admin, new SongCreateRequest(token + " 1", "가수"));
        adminSongService.create(admin, new SongCreateRequest(token + " 2", "가수"));
        adminSongService.create(admin, new SongCreateRequest(token + " 3", "가수"));

        PageResponse<SongResponse> first = adminSongService.list(token, 0, 2);
        PageResponse<SongResponse> last = adminSongService.list(token, 1, 2);

        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.hasNext()).isTrue();
        assertThat(first.hasPrevious()).isFalse();
        assertThat(last.list()).hasSize(1);
        assertThat(last.hasNext()).isFalse();
        assertThat(last.hasPrevious()).isTrue();
    }

    // --- 픽스처 -------------------------------------------------------------

    private Integer newUser() {
        String nickname = "t-" + UUID.randomUUID().toString().substring(0, 20);
        execute("insert into motif_core.users (nickname, role) values (?1, 'ADMIN')", nickname);
        return scalar("select user_seq from motif_core.users where nickname = ?1", nickname);
    }

    /** {@code users(ARTIST)} → {@code companies} → {@code artists} FK 체인. */
    private Integer newArtist() {
        Integer userSeq = newUser();
        String companyName = "t-" + UUID.randomUUID().toString().substring(0, 20);
        execute("insert into motif_core.companies (company_name, reg_usr_seq) values (?1, ?2)",
                companyName, userSeq);
        Integer companySeq = scalar(
                "select company_seq from motif_core.companies where company_name = ?1", companyName);

        execute("insert into motif_core.artists (user_seq, company_seq, reg_usr_seq) values (?1, ?2, ?1)",
                userSeq, companySeq);
        return scalar("select artist_seq from motif_core.artists where user_seq = ?1", userSeq);
    }

    private void newChallenge(Integer artistSeq, Integer songSeq, boolean deleted) {
        String uuid = UUID.randomUUID().toString();
        execute("""
                insert into motif_core.challenges
                    (artist_seq, song_seq, description, video_key, thumbnail_key, duration_ms, is_deleted)
                values (?1, ?2, '소개글', ?3, ?4, 15000, ?5)
                """, artistSeq, songSeq,
                "challenges/" + uuid + ".mp4", "challenges/" + uuid + "/thumb.jpg", deleted);
    }

    private void execute(String sql, Object... params) {
        var query = entityManager.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) {
            query.setParameter(i + 1, params[i]);
        }
        query.executeUpdate();
    }

    private Integer scalar(String sql, Object param) {
        return ((Number) entityManager.createNativeQuery(sql)
                .setParameter(1, param)
                .getSingleResult()).intValue();
    }
}
