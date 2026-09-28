package com.ssafy.motif.coreapi.domain.artist.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.artist.ArtistErrorCode;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistChallengeResponse;
import com.ssafy.motif.domain.core.artist.Artist;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
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
 * §5.4 아티스트 챌린지 그리드를 실제 DB에 대고 검증한다 (S15P21D104-76).
 *
 * <p><b>로컬 compose DB 는 세 레인이 공유한다</b> — 단언은 이 테스트가 만든 아티스트의
 * 챌린지로만 좁힌다. 전량 반환 API 라 "전체 몇 건" 단언은 특히 위험하다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class ArtistChallengesLocalDbTest {

    @Autowired
    private ArtistQueryService artistQueryService;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private ChallengeRepository challengeRepository;
    @Autowired
    private SongRepository songRepository;
    @Autowired
    private UserRepository userRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private Integer adminUserSeq;
    private Integer artistSeq;
    private Integer songSeq;

    @BeforeEach
    void setUp() {
        adminUserSeq = newUser().getUserSeq();
        artistSeq = newArtist();
        songSeq = songRepository.saveAndFlush(
                Song.register("Supernova", "aespa", adminUserSeq)).getSongSeq();
    }

    @Test
    @DisplayName("최신순(PK 역순) 전량 반환 — 페이지 칸이 없다")
    void returnsEveryChallengeNewestFirst() {
        Integer first = save().getChallengeSeq();
        Integer second = save().getChallengeSeq();
        Integer third = save().getChallengeSeq();

        assertThat(artistQueryService.challenges(artistSeq).list())
                .extracting(ArtistChallengeResponse::challengeSeq)
                .containsExactly(third, second, first);
    }

    @Test
    @DisplayName("삭제된 챌린지는 빠진다 — §1.8")
    void deletedChallengesAreHidden() {
        Integer kept = save().getChallengeSeq();
        Integer removed = save().getChallengeSeq();
        softDelete(removed);

        assertThat(artistQueryService.challenges(artistSeq).list())
                .extracting(ArtistChallengeResponse::challengeSeq)
                .containsExactly(kept);
    }

    @Test
    @DisplayName("챌린지가 없으면 빈 배열이다 — 404 가 아니다")
    void noChallengesIsAnEmptyList() {
        assertThat(artistQueryService.challenges(artistSeq).list()).isEmpty();
    }

    @Test
    @DisplayName("다른 아티스트의 챌린지는 섞이지 않는다")
    void otherArtistsChallengesAreNotIncluded() {
        Integer mine = save().getChallengeSeq();
        Integer otherArtistSeq = newArtist();
        challengeRepository.saveAndFlush(challenge(otherArtistSeq));

        assertThat(artistQueryService.challenges(artistSeq).list())
                .extracting(ArtistChallengeResponse::challengeSeq)
                .containsExactly(mine);
    }

    @Test
    @DisplayName("삭제된 아티스트는 404 다 — 목록도 상세와 같은 비노출을 따른다")
    void deletedArtistIsNotFound() {
        entityManager.createNativeQuery(
                        "update motif_core.artists set is_deleted = true where artist_seq = :seq")
                .setParameter("seq", artistSeq)
                .executeUpdate();
        entityManager.clear();

        assertThatThrownBy(() -> artistQueryService.challenges(artistSeq))
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
        User artistUser = userRepository.saveAndFlush(User.createArtist("아티스트"));
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

    private Challenge save() {
        return challengeRepository.saveAndFlush(challenge(artistSeq));
    }

    private Challenge challenge(Integer owner) {
        String uuid = UUID.randomUUID().toString();
        return Challenge.register(owner, songSeq, "소개",
                "challenges/" + uuid + ".mp4", "challenges/" + uuid + "/thumb.jpg", 15000);
    }

    /** 소프트 삭제 경로가 아직 서비스에 없어 컬럼을 직접 올린다 — 검증 대상은 조회 쪽이다. */
    private void softDelete(Integer challengeSeq) {
        entityManager.createNativeQuery(
                        "update motif_core.challenges set is_deleted = true where challenge_seq = :seq")
                .setParameter("seq", challengeSeq)
                .executeUpdate();
        entityManager.clear();
    }
}
