package com.ssafy.motif.coreapi.domain.challenge.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.challenge.ChallengeErrorCode;
import com.ssafy.motif.coreapi.domain.challenge.dto.ChallengeDetailResponse;
import com.ssafy.motif.domain.core.artist.Artist;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.artist.Subscription;
import com.ssafy.motif.domain.core.artist.SubscriptionRepository;
import com.ssafy.motif.domain.core.challenge.Challenge;
import com.ssafy.motif.domain.core.challenge.ChallengeParticipant;
import com.ssafy.motif.domain.core.challenge.ChallengeParticipantRepository;
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
 * §7.2 챌린지 상세를 실제 DB에 대고 검증한다 (S15P21D104-138).
 *
 * <p><b>로컬 compose DB 는 세 레인이 공유한다</b> — 단언은 이 테스트가 만든 챌린지로만 좁힌다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class ChallengeDetailLocalDbTest {

    @Autowired
    private ChallengeQueryService challengeQueryService;
    @Autowired
    private ChallengeRepository challengeRepository;
    @Autowired
    private ChallengeParticipantRepository participantRepository;
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
    private Integer artistSeq;
    private Integer challengeSeq;

    @BeforeEach
    void setUp() {
        adminUserSeq = newUser().getUserSeq();
        fanSeq = newUser().getUserSeq();
        artistSeq = newArtist("카리나");
        Integer songSeq = songRepository.saveAndFlush(
                Song.register("Supernova", "aespa", adminUserSeq)).getSongSeq();
        String uuid = UUID.randomUUID().toString();
        challengeSeq = challengeRepository.saveAndFlush(Challenge.register(
                artistSeq, songSeq, "같이 춰요!",
                "challenges/" + uuid + ".mp4",
                "challenges/" + uuid + "/thumb.jpg", 15000)).getChallengeSeq();
    }

    @Test
    @DisplayName("아티스트·곡·소개글이 §7.1 항목 + description 형태로 나온다")
    void detailCarriesFeedItemPlusDescription() {
        ChallengeDetailResponse detail = challengeQueryService.detail(fanSeq, challengeSeq);

        assertThat(detail.challengeSeq()).isEqualTo(challengeSeq);
        assertThat(detail.description()).isEqualTo("같이 춰요!");
        assertThat(detail.durationMs()).isEqualTo(15000);
        assertThat(detail.artist().artistSeq()).isEqualTo(artistSeq);
        assertThat(detail.artist().name()).isEqualTo("카리나");
        assertThat(detail.song().title()).isEqualTo("Supernova");
        assertThat(detail.song().singerName()).isEqualTo("aespa");
    }

    @Test
    @DisplayName("슬롯은 slotOrder 오름차순 = 화면 좌→우 순서이고 anchor 가 함께 온다")
    void participantsComeInSlotOrderWithAnchor() {
        Integer winter = newArtist("윈터");
        Integer ningning = newArtist("닝닝");
        // 일부러 뒤 슬롯을 먼저 넣는다 — 정렬이 삽입 순서가 아니라 slotOrder 라야 한다
        participantRepository.save(ChallengeParticipant.of(challengeSeq, ningning, 1,
                new ChallengeParticipant.Anchor(0.72, 0.36, 0.0995),
                "edited/1/n.mp4", "edited/1/n.jpg", "edited/1/np.jpg"));
        participantRepository.save(ChallengeParticipant.of(challengeSeq, winter, 0,
                new ChallengeParticipant.Anchor(0.22, 0.36, 0.0995),
                "edited/1/w.mp4", "edited/1/w.jpg", null));
        entityManager.flush();

        ChallengeDetailResponse detail = challengeQueryService.detail(fanSeq, challengeSeq);

        assertThat(detail.participants())
                .extracting(ChallengeDetailResponse.Participant::slotOrder)
                .containsExactly(0, 1);
        assertThat(detail.participants())
                .extracting(ChallengeDetailResponse.Participant::participantMask)
                .containsExactly(1, 2);
        assertThat(detail.participants())
                .extracting(ChallengeDetailResponse.Participant::name)
                .containsExactly("윈터", "닝닝");
        assertThat(detail.participants().getFirst().anchor())
                .isEqualTo(new ChallengeDetailResponse.Anchor(0.22, 0.36, 0.0995));
    }

    /**
     * 0019 — 편집본이 슬롯에 붙으면서 {@code editedChallenges[]} 배열이 없어졌다.
     * 고를 수 있는지는 슬롯마다 {@code selectable} 이 답한다.
     */
    @Test
    @DisplayName("편집본이 없는 슬롯은 selectable=false 다 — 화면이 미리 잠근다")
    void slotWithoutEditedIsNotSelectable() {
        Integer winter = newArtist("윈터");
        Integer ningning = newArtist("닝닝");
        participantRepository.save(ChallengeParticipant.of(challengeSeq, winter, 0,
                new ChallengeParticipant.Anchor(0.22, 0.36, 0.0995),
                "edited/1/w.mp4", "edited/1/w.jpg", "edited/1/wp.jpg"));
        participantRepository.save(ChallengeParticipant.of(challengeSeq, ningning, 1,
                new ChallengeParticipant.Anchor(0.72, 0.36, 0.0995), null, null, null));
        entityManager.flush();

        ChallengeDetailResponse detail = challengeQueryService.detail(fanSeq, challengeSeq);

        assertThat(detail.participants())
                .extracting(ChallengeDetailResponse.Participant::selectable)
                .containsExactly(true, false);
        // 선택 화면 이미지도 슬롯에 붙는다 — 키가 없으면 null 이고 클라이언트가 썸네일로
        // 폴백한다. 키가 있는 쪽의 URL 은 CDN 설정에 달려 있어 여기서 값을 보지 않는다
        assertThat(detail.participants().getLast().previewImageUrl()).isNull();
    }

    @Test
    @DisplayName("슬롯이 없으면 빈 배열이다 — 교체 인물 없는 챌린지")
    void noSlotsYieldsEmptyList() {
        assertThat(challengeQueryService.detail(fanSeq, challengeSeq).participants()).isEmpty();
    }

    @Test
    @DisplayName("isLiked·isSubscribed 는 호출자 기준이다")
    void myStateIsPerCaller() {
        ChallengeDetailResponse before = challengeQueryService.detail(fanSeq, challengeSeq);
        assertThat(before.isLiked()).isFalse();
        assertThat(before.isSubscribed()).isFalse();

        like(challengeSeq, fanSeq);
        subscriptionRepository.saveAndFlush(Subscription.of(fanSeq, artistSeq));

        ChallengeDetailResponse after = challengeQueryService.detail(fanSeq, challengeSeq);
        assertThat(after.isLiked()).isTrue();
        assertThat(after.isSubscribed()).isTrue();
    }

    @Test
    @DisplayName("남이 누른 좋아요는 내 isLiked 를 올리지 않는다")
    void othersLikeDoesNotLeakIntoMyState() {
        like(challengeSeq, newUser().getUserSeq());

        assertThat(challengeQueryService.detail(fanSeq, challengeSeq).isLiked()).isFalse();
    }

    @Test
    @DisplayName("삭제된 챌린지는 없는 것으로 답한다 — §1.8")
    void deletedChallengeIsNotFound() {
        entityManager.createNativeQuery(
                        "update motif_core.challenges set is_deleted = true where challenge_seq = :seq")
                .setParameter("seq", challengeSeq)
                .executeUpdate();
        entityManager.clear();

        assertThatThrownBy(() -> challengeQueryService.detail(fanSeq, challengeSeq))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ChallengeErrorCode.CHALLENGE_NOT_FOUND);
    }

    @Test
    @DisplayName("없는 챌린지도 CHALLENGE_NOT_FOUND 다")
    void missingChallengeIsNotFound() {
        assertThatThrownBy(() -> challengeQueryService.detail(fanSeq, Integer.MAX_VALUE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ChallengeErrorCode.CHALLENGE_NOT_FOUND);
    }

    // --- 픽스처 -------------------------------------------------------------

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
