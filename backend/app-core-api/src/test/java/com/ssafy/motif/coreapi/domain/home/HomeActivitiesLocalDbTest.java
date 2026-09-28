package com.ssafy.motif.coreapi.domain.home;

import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.domain.home.dto.ActivityItem;
import com.ssafy.motif.coreapi.domain.home.service.HomeActivityQueryService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 홈 구독 활동 피드 — DB 통합 (S15P21D104-116 · 명세 §6.3).
 *
 * <p>단위 테스트가 닿지 못하는 곳만 본다 — <b>UNION ALL 네이티브 쿼리가 실제로 도는지</b>.
 * 이종 정렬·count 합·페이지 경계·따옴표 별칭은 문법이 맞아도 런타임에 깨지고, 그때
 * 증상은 "목록 순서가 이상하다" 라 원인을 찾기 어렵다. 항목 조립은
 * {@code HomeActivityQueryServiceTest} 가 이미 고정한다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 *
 * <p><b>구독한 아티스트만 본다.</b> 매 테스트가 새 사용자를 만들어 구독을 새로 걸므로
 * 로컬 DB 에 남아 있는 다른 데이터가 결과에 섞이지 않는다 — 목록 전체를 단언할 수 있는
 * 이유다.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class HomeActivitiesLocalDbTest {

    @Autowired
    private HomeActivityQueryService service;
    @PersistenceContext
    private EntityManager entityManager;

    private Integer me;
    private Integer artistSeq;

    @BeforeEach
    void setUp() {
        me = newUser();
        artistSeq = newArtist();
        subscribe(me, artistSeq);
    }

    @Test
    @DisplayName("UNION 이 실제로 돈다 — 에셋과 공지가 한 목록에 섞여 최신순이다")
    void unionQueryRuns() {
        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Integer 오래된_에셋 = newAsset(artistSeq, "9월 신규 에셋", true, base.minusSeconds(300));
        Integer 중간_공지 = newNotice(artistSeq, "컴백 소식", false, base.minusSeconds(200));
        Integer 최신_에셋 = newAsset(artistSeq, "10월 에셋", true, base.minusSeconds(100));

        PageResponse<ActivityItem> response = service.activities(me, 0, 20);

        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.list()).extracting(ActivityItem::type).containsExactly(
                ActivityItem.ActivityType.MOTIF_ASSET,
                ActivityItem.ActivityType.NOTICE,
                ActivityItem.ActivityType.MOTIF_ASSET);
        assertThat(response.list().get(0).asset().assetSeq()).isEqualTo(최신_에셋);
        assertThat(response.list().get(1).notice().noticeSeq()).isEqualTo(중간_공지);
        assertThat(response.list().get(2).asset().assetSeq()).isEqualTo(오래된_에셋);
    }

    @Test
    @DisplayName("등록 시각이 같으면 type 오름차순이다 — MOTIF_ASSET 이 NOTICE 보다 앞")
    void tieBreaksByTypeThenSeq() {
        Instant same = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        newNotice(artistSeq, "공지", false, same);
        newAsset(artistSeq, "에셋", true, same);

        PageResponse<ActivityItem> response = service.activities(me, 0, 20);

        assertThat(response.list()).extracting(ActivityItem::type).containsExactly(
                ActivityItem.ActivityType.MOTIF_ASSET,
                ActivityItem.ActivityType.NOTICE);
    }

    @Test
    @DisplayName("비활성 에셋과 삭제 공지는 빠진다 — count 에서도 빠진다")
    void excludesInactiveAssetAndDeletedNotice() {
        Instant base = Instant.now();
        newAsset(artistSeq, "판매 중단", false, base);
        newNotice(artistSeq, "삭제된 공지", true, base);
        Integer 살아있는_공지 = newNotice(artistSeq, "살아있는 공지", false, base);

        PageResponse<ActivityItem> response = service.activities(me, 0, 20);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.list()).singleElement()
                .extracting(item -> item.notice().noticeSeq()).isEqualTo(살아있는_공지);
    }

    @Test
    @DisplayName("구독하지 않은 아티스트의 활동은 나오지 않는다")
    void excludesUnsubscribedArtists() {
        Integer 남의_아티스트 = newArtist();
        newAsset(남의_아티스트, "남의 에셋", true, Instant.now());
        Integer 내_공지 = newNotice(artistSeq, "내 공지", false, Instant.now());

        PageResponse<ActivityItem> response = service.activities(me, 0, 20);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.list()).singleElement()
                .extracting(item -> item.notice().noticeSeq()).isEqualTo(내_공지);
    }

    @Test
    @DisplayName("구독이 0건이면 빈 페이지다 — 쿼리가 터지지 않는다")
    void noSubscriptionGivesEmptyPage() {
        Integer 구독_없는_사용자 = newUser();

        PageResponse<ActivityItem> response = service.activities(구독_없는_사용자, 0, 20);

        assertThat(response.list()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
    }

    @Test
    @DisplayName("페이지 경계가 맞는다 — 겹치지도 빠지지도 않는다")
    void pagesWithoutGapsOrDuplicates() {
        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        for (int i = 0; i < 5; i++) {
            newAsset(artistSeq, "에셋" + i, true, base.minusSeconds(i * 10L));
        }

        PageResponse<ActivityItem> first = service.activities(me, 0, 2);
        PageResponse<ActivityItem> second = service.activities(me, 1, 2);

        assertThat(first.totalElements()).isEqualTo(5);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.hasNext()).isTrue();
        assertThat(second.hasPrevious()).isTrue();
        assertThat(second.list()).extracting(item -> item.asset().assetSeq())
                .doesNotContainAnyElementsOf(
                        first.list().stream().map(item -> item.asset().assetSeq()).toList());
    }

    /**
     * <b>URL 내용은 단언하지 않는다.</b> 로컬·CI 에는 {@code motif.media.public-base-url}
     * 이 없어 {@code PublicMediaUrlResolver} 가 설계대로 {@code null} 을 준다(prod 에서만
     * 설정한다). 키 → URL 변환은 CDN 주소를 주입한 단위 테스트가 본다.
     */
    @Test
    @DisplayName("공지 이미지는 displayOrder 순으로 오고 좋아요 상태가 반영된다")
    void noticeCarriesImagesAndLikeState() {
        Integer noticeSeq = newNotice(artistSeq, "이미지 있는 공지", false, Instant.now());
        // 삽입 순서를 displayOrder 와 반대로 둔다 — 정렬이 실제로 걸리는지 보려고
        newNoticeImage(noticeSeq, "notices/second.jpg", (short) 1);
        newNoticeImage(noticeSeq, "notices/first.jpg", (short) 0);
        like(noticeSeq, me);

        ActivityItem item = service.activities(me, 0, 20).list().getFirst();

        assertThat(item.notice().isLiked()).isTrue();
        assertThat(item.notice().images()).extracting(ActivityItem.Image::displayOrder)
                .containsExactly((short) 0, (short) 1);
    }

    @Test
    @DisplayName("두 타입의 artist 요약이 같은 모양이다 — groupName 포함")
    void bothTypesCarryTheSameArtistSummary() {
        newAsset(artistSeq, "에셋", true, Instant.now());
        newNotice(artistSeq, "공지", false, Instant.now().minusSeconds(10));

        List<ActivityItem> list = service.activities(me, 0, 20).list();

        assertThat(list).hasSize(2);
        assertThat(list.get(0).artist()).isEqualTo(list.get(1).artist());
        assertThat(list.getFirst().artist().name()).isNotBlank();
    }

    // --- 픽스처 -------------------------------------------------------------

    private Integer newUser() {
        String nickname = "t-" + UUID.randomUUID().toString().substring(0, 20);
        execute("insert into motif_core.users (nickname, role) values (?1, 'USER')", nickname);
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

    private void subscribe(Integer userSeq, Integer artistSeq) {
        execute("insert into motif_core.subscriptions (user_seq, artist_seq) values (?1, ?2)",
                userSeq, artistSeq);
        flush();
    }

    private Integer newAsset(Integer artistSeq, String title, boolean active, Instant regDttm) {
        String uuid = UUID.randomUUID().toString();
        execute("""
                insert into motif_core.motif_assets
                    (artist_seq, title, video_key, thumbnail_key, duration_ms, is_active, reg_dttm)
                values (?1, ?2, ?3, ?4, 9800, ?5, ?6)
                """, artistSeq, title, "assets/" + uuid + ".mp4",
                "assets/" + uuid + "/thumb.jpg", active, regDttm);
        flush();
        return scalar("select asset_seq from motif_core.motif_assets where video_key = ?1",
                "assets/" + uuid + ".mp4");
    }

    private Integer newNotice(Integer artistSeq, String content, boolean deleted, Instant regDttm) {
        String marked = content + " " + UUID.randomUUID();
        execute("""
                insert into motif_core.notices (artist_seq, content, reg_dttm, is_deleted)
                values (?1, ?2, ?3, ?4)
                """, artistSeq, marked, regDttm, deleted);
        flush();
        return scalar("select notice_seq from motif_core.notices where content = ?1", marked);
    }

    private void newNoticeImage(Integer noticeSeq, String imageKey, short displayOrder) {
        execute("""
                insert into motif_core.notice_images (notice_seq, image_key, display_order)
                values (?1, ?2, ?3)
                """, noticeSeq, imageKey, displayOrder);
        flush();
    }

    private void like(Integer noticeSeq, Integer userSeq) {
        execute("insert into motif_core.notice_likes (notice_seq, user_seq) values (?1, ?2)",
                noticeSeq, userSeq);
        flush();
    }

    /** 네이티브 쿼리는 영속성 컨텍스트를 보지 않는다 — 읽기 전에 밀어 넣는다. */
    private void flush() {
        entityManager.flush();
        entityManager.clear();
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
