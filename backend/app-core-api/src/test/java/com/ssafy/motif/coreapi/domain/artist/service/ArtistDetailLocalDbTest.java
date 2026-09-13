package com.ssafy.motif.coreapi.domain.artist.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.artist.ArtistErrorCode;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistDetailResponse;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §5.3 아티스트 상세를 실제 DB에 대고 검증한다 (S15P21D104-67).
 *
 * <p><b>로컬 compose DB 는 세 레인이 공유한다</b> — 단언은 이 테스트가 만든 아티스트로만
 * 좁힌다. "전체 몇 건" 같은 단언을 두면 옆 레인이 데이터를 넣는 순간 깨진다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class ArtistDetailLocalDbTest {

    @Autowired
    private ArtistQueryService artistQueryService;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private ChallengeRepository challengeRepository;
    @Autowired
    private SongRepository songRepository;
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
        artistSeq = newArtist("카리나");
    }

    @Test
    @DisplayName("미구독이면 isSubscribed=false 이고 subscribedDays 는 null 이다")
    void unsubscribedHasNullSubscribedDays() {
        ArtistDetailResponse detail = artistQueryService.detail(fanSeq, artistSeq);

        assertThat(detail.artistSeq()).isEqualTo(artistSeq);
        assertThat(detail.name()).isEqualTo("카리나");
        assertThat(detail.isSubscribed()).isFalse();
        assertThat(detail.subscribedDays()).isNull();
    }

    @Test
    @DisplayName("구독 중이면 subscribedDays 가 채워진다 — 구독 당일은 0")
    void subscribedDaysIsZeroOnTheDayOfSubscription() {
        subscriptionRepository.saveAndFlush(Subscription.of(fanSeq, artistSeq));

        ArtistDetailResponse detail = artistQueryService.detail(fanSeq, artistSeq);

        assertThat(detail.isSubscribed()).isTrue();
        assertThat(detail.subscribedDays()).isZero();
    }

    @Test
    @DisplayName("challengeCount 는 비삭제 챌린지만 센다")
    void challengeCountExcludesDeleted() {
        Integer songSeq = newSong();
        challengeRepository.save(newChallenge(songSeq));
        challengeRepository.save(newChallenge(songSeq));
        Challenge deleted = challengeRepository.save(newChallenge(songSeq));
        entityManager.flush();
        softDeleteChallenge(deleted.getChallengeSeq());

        assertThat(artistQueryService.detail(fanSeq, artistSeq).challengeCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("패스가 없으면 pass.active=false 이고 만료 시각은 null 이다")
    void passIsInactiveWithoutPurchase() {
        ArtistDetailResponse detail = artistQueryService.detail(fanSeq, artistSeq);

        assertThat(detail.pass().active()).isFalse();
        assertThat(detail.pass().expiresDttm()).isNull();
    }

    @Test
    @DisplayName("myMotifVideos 는 빈 배열이다 — signed 쿠키가 없어 보호 등급 URL 을 만들 수 없다")
    void myMotifVideosIsEmptyUntilSignedCookiesExist() {
        assertThat(artistQueryService.detail(fanSeq, artistSeq).myMotifVideos()).isEmpty();
    }

    @Test
    @DisplayName("삭제된 아티스트는 없는 것으로 답한다 — §1.8")
    void deletedArtistIsNotFound() {
        softDeleteArtist(artistSeq);

        assertThatThrownBy(() -> artistQueryService.detail(fanSeq, artistSeq))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ArtistErrorCode.ARTIST_NOT_FOUND);
    }

    @Test
    @DisplayName("없는 아티스트도 ARTIST_NOT_FOUND 다")
    void missingArtistIsNotFound() {
        assertThatThrownBy(() -> artistQueryService.detail(fanSeq, Integer.MAX_VALUE))
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

    private Integer newArtist(String name) {
        User artistUser = userRepository.saveAndFlush(User.createArtist(name));
        Artist artist = artistRepository.saveAndFlush(Artist.register(
                artistUser.getUserSeq(), null, newCompany(), "소개글", null, adminUserSeq));
        return artist.getArtistSeq();
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

    private Integer newSong() {
        return songRepository.saveAndFlush(
                Song.register("Supernova", "aespa", adminUserSeq)).getSongSeq();
    }

    private Challenge newChallenge(Integer songSeq) {
        String uuid = UUID.randomUUID().toString();
        return Challenge.register(artistSeq, songSeq, "소개",
                "challenges/" + uuid + ".mp4", "challenges/" + uuid + "/thumb.jpg", 15000);
    }

    /** 소프트 삭제 경로가 아직 서비스에 없어 컬럼을 직접 올린다 — 검증 대상은 조회 쪽이다. */
    private void softDeleteChallenge(Integer challengeSeq) {
        entityManager.createNativeQuery(
                        "update motif_core.challenges set is_deleted = true where challenge_seq = :seq")
                .setParameter("seq", challengeSeq)
                .executeUpdate();
        entityManager.clear();
    }

    private void softDeleteArtist(Integer seq) {
        entityManager.createNativeQuery(
                        "update motif_core.artists set is_deleted = true where artist_seq = :seq")
                .setParameter("seq", seq)
                .executeUpdate();
        entityManager.clear();
    }
}
