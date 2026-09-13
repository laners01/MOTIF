package com.ssafy.motif.coreapi.domain.challenge.service;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.common.response.CursorResponse;
import com.ssafy.motif.coreapi.domain.challenge.dto.ChallengeFeedItem;
import com.ssafy.motif.coreapi.global.response.Cursors;
import com.ssafy.motif.domain.core.artist.Artist;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.artist.Subscription;
import com.ssafy.motif.domain.core.artist.SubscriptionRepository;
import com.ssafy.motif.domain.core.challenge.Challenge;
import com.ssafy.motif.domain.core.challenge.ChallengeRepository;
import com.ssafy.motif.domain.core.song.Song;
import com.ssafy.motif.domain.core.song.SongRepository;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §7.1 숏폼 피드를 실제 DB에 대고 검증한다.
 *
 * <p><b>피드는 전역 목록이다</b> — 다른 세션이 만든 챌린지가 같은 페이지에 섞인다.
 * 그래서 단언은 전부 <b>이 테스트가 만든 seq 로 걸러</b> 한다. "첫 항목이 내 것" 같은
 * 단언은 쓰지 않는다.
 *
 * <p><b>커서를 생략한 호출을 반드시 테스트한다.</b> 커서·검색어를 한 쿼리에
 * {@code :cursor is null or …} 로 합치면 타입 없는 {@code null} 을 Postgres 가 추론하다
 * 운영에서 500 이 난다 — 이 프로젝트가 그 함정을 두 번 밟았고(389·385), 두 번 다
 * 테스트가 전부 인자를 넘겨서 드러나지 않았다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class ChallengeFeedLocalDbTest {

    @Autowired
    private ChallengeQueryService challengeQueryService;
    @Autowired
    private ChallengeRepository challengeRepository;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private SongRepository songRepository;
    @Autowired
    private UserRepository userRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private Integer adminUserSeq;
    private Integer fanSeq;
    private Integer otherFanSeq;
    private Integer artistSeq;
    private List<Integer> mine;      // 오래된 → 최신 순서로 만든 3건
    private String anchor;           // 내 최신 seq 바로 위 — 여기서부터 읽으면 내 것이 먼저 온다

    @BeforeEach
    void setUp() {
        adminUserSeq = newUser().getUserSeq();
        fanSeq = newUser().getUserSeq();
        otherFanSeq = newUser().getUserSeq();
        artistSeq = newArtist("카리나");
        Integer songSeq = songRepository.saveAndFlush(
                Song.register("Supernova", "aespa", adminUserSeq)).getSongSeq();

        mine = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            mine.add(newChallenge(songSeq));
        }
        anchor = Cursors.encode(mine.getLast() + 1);
    }

    @Test
    @DisplayName("커서를 생략하면 첫 페이지다 — null 이 쿼리에 닿지 않는다")
    void firstPageWithoutCursor() {
        CursorResponse<ChallengeFeedItem> page = challengeQueryService.feed(fanSeq, null, 20);

        assertThat(page.list()).isNotEmpty();
        assertThat(page.prevCursor()).isNull();
        assertThat(page.hasPrevious()).isFalse();
    }

    @Test
    @DisplayName("빈 문자열 커서도 첫 페이지로 본다")
    void blankCursorIsFirstPage() {
        assertThat(challengeQueryService.feed(fanSeq, "", 20).list()).isNotEmpty();
        assertThat(challengeQueryService.feed(fanSeq, "   ", 20).list()).isNotEmpty();
    }

    @Test
    @DisplayName("커서로 이어 읽으면 중복도 누락도 없다 — 최신순")
    void cursorPagesWithoutOverlapOrGap() {
        CursorResponse<ChallengeFeedItem> first = challengeQueryService.feed(fanSeq, anchor, 2);
        assertThat(seqs(first)).containsExactly(mine.get(2), mine.get(1));
        assertThat(first.hasNext()).isTrue();
        assertThat(first.nextCursor()).isNotNull();

        CursorResponse<ChallengeFeedItem> second =
                challengeQueryService.feed(fanSeq, first.nextCursor(), 2);
        assertThat(seqs(second)).startsWith(mine.get(0));
    }

    @Test
    @DisplayName("isLiked·isSubscribed 는 호출자 기준이다")
    void myStateIsPerCaller() {
        like(mine.getLast(), fanSeq);
        subscriptionRepository.saveAndFlush(Subscription.of(fanSeq, artistSeq));
        entityManager.clear();

        ChallengeFeedItem forFan = itemOf(challengeQueryService.feed(fanSeq, anchor, 3), mine.getLast());
        ChallengeFeedItem forOther =
                itemOf(challengeQueryService.feed(otherFanSeq, anchor, 3), mine.getLast());

        assertThat(forFan.isLiked()).isTrue();
        assertThat(forFan.isSubscribed()).isTrue();
        assertThat(forOther.isLiked()).isFalse();
        assertThat(forOther.isSubscribed()).isFalse();
    }

    @Test
    @DisplayName("삭제된 챌린지는 피드에 없다 (§1.8)")
    void deletedIsExcluded() {
        Challenge deleted = challengeRepository.findById(mine.getLast()).orElseThrow();
        deleted.softDelete();
        challengeRepository.saveAndFlush(deleted);
        entityManager.clear();

        assertThat(seqs(challengeQueryService.feed(fanSeq, anchor, 10)))
                .doesNotContain(mine.getLast())
                .contains(mine.get(0), mine.get(1));
    }

    @Test
    @DisplayName("항목이 §7.1 계약대로 채워진다")
    void itemCarriesContract() {
        ChallengeFeedItem item = itemOf(challengeQueryService.feed(fanSeq, anchor, 3), mine.getLast());

        assertThat(item.artist().artistSeq()).isEqualTo(artistSeq);
        assertThat(item.artist().name()).isEqualTo("카리나");
        assertThat(item.song().title()).isEqualTo("Supernova");
        assertThat(item.song().singerName()).isEqualTo("aespa");
        assertThat(item.durationMs()).isEqualTo(15000);
        assertThat(item.viewCount()).isZero();
        assertThat(item.participantCount()).isZero();
    }

    @Test
    @DisplayName("망가진 커서는 400 이다 — 조용히 첫 페이지로 되돌리지 않는다")
    void brokenCursorIsRejected() {
        assertThatThrownBy(() -> challengeQueryService.feed(fanSeq, "!!not-base64!!", 20))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_PARAMETER);
    }

    @Test
    @DisplayName("size 는 1~100 으로 잘린다")
    void sizeIsClamped() {
        assertThat(challengeQueryService.feed(fanSeq, anchor, 0).list()).hasSize(1);
        assertThat(challengeQueryService.feed(fanSeq, anchor, 1000).list()).isNotEmpty();
    }

    // --- 픽스처 -------------------------------------------------------------

    private List<Integer> seqs(CursorResponse<ChallengeFeedItem> page) {
        return page.list().stream().map(ChallengeFeedItem::challengeSeq).toList();
    }

    private ChallengeFeedItem itemOf(CursorResponse<ChallengeFeedItem> page, Integer seq) {
        return page.list().stream()
                .filter(i -> i.challengeSeq().equals(seq))
                .findFirst()
                .orElseThrow(() -> new AssertionError("피드에 " + seq + " 가 없다"));
    }

    private Integer newChallenge(Integer songSeq) {
        String uuid = UUID.randomUUID().toString();
        return challengeRepository.saveAndFlush(Challenge.register(
                artistSeq, songSeq, "같이 춰요!",
                "challenges/" + uuid + ".mp4",
                "challenges/" + uuid + "/thumb.jpg", 15000)).getChallengeSeq();
    }

    private User newUser() {
        User user = userRepository.saveAndFlush(User.signUp());
        user.applyInitialNickname();
        return user;
    }

    private Integer newArtist(String name) {
        User artistUser = userRepository.saveAndFlush(User.createArtist(name));
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

    /** 좋아요 토글 API(§7.3)가 아직 없어 행을 직접 넣는다 — 검증 대상은 조회 쪽이다. */
    private void like(Integer seq, Integer userSeq) {
        entityManager.createNativeQuery(
                        "insert into motif_core.challenge_likes (challenge_seq, user_seq) values (:seq, :userSeq)")
                .setParameter("seq", seq)
                .setParameter("userSeq", userSeq)
                .executeUpdate();
        entityManager.clear();
    }
}
