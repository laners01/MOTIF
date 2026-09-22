package com.ssafy.motif.coreapi.domain.challenge.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.challenge.ChallengeErrorCode;
import com.ssafy.motif.coreapi.domain.challenge.dto.EditedVideoResponse;
import com.ssafy.motif.domain.core.artist.Artist;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
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
 * §7.6 편집본 영상 (0020).
 *
 * <p><b>URL 문자열을 단언하지 않는다.</b> CDN 주소와 서명 키가 로컬에 없어 값이
 * 비거나 던진다 — 여기서 고정할 것은 "어느 슬롯을 어떤 조건으로 내주는가" 이지 주소
 * 형태가 아니다. 서명 자체는 {@code CloudFrontUrlSigner} 의 몫이고, 등급 판정은 키
 * 프리픽스가 한다({@code MediaKeys.isProtected}).
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class EditedVideoLocalDbTest {

    private static final int DURATION_MS = 15_020;

    @Autowired
    private ChallengeQueryService challengeQueryService;
    @Autowired
    private ChallengeRepository challengeRepository;
    @Autowired
    private ChallengeParticipantRepository participantRepository;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private SongRepository songRepository;
    @Autowired
    private UserRepository userRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private Integer adminUserSeq;
    private Integer artistSeq;
    private Integer challengeSeq;

    @BeforeEach
    void setUp() {
        adminUserSeq = userRepository.saveAndFlush(User.signUp()).getUserSeq();
        artistSeq = newArtist();
        challengeSeq = newChallenge();
    }

    @Test
    @DisplayName("편집본이 있는 슬롯은 길이와 만료를 함께 준다")
    void editedVideoCarriesDurationAndExpiry() {
        Integer slotSeq = newSlot(0, true);

        EditedVideoResponse response = challengeQueryService.editedVideo(slotSeq);

        assertThat(response.challengeParticipantSeq()).isEqualTo(slotSeq);
        assertThat(response.challengeSeq()).isEqualTo(challengeSeq);
        assertThat(response.artistSeq()).isNotNull();
        // MP4 라 플레이어가 moov 를 읽기 전에는 길이를 모른다 — 서버가 주는 값이다
        assertThat(response.durationMs()).isEqualTo(DURATION_MS);
        // 만료가 있어야 앱이 "다시 부를 시점" 을 안다
        assertThat(response.expiresIn()).isPositive();
    }

    /**
     * 0019 로 "슬롯은 있는데 편집본이 없는" 상태가 정상이 됐다. 그 자리는 합성 베이스가
     * 없으므로 영상을 줄 수 없다 — §7.5 의 {@code initialSceneUrl == null} 이 화면에서
     * 미리 잠그고, 여기는 그걸 통과해 온 요청을 막는 방어선이다.
     */
    @Test
    @DisplayName("편집본이 없는 슬롯은 404")
    void slotWithoutEditedIsNotFound() {
        Integer slotSeq = newSlot(0, false);

        assertFails(slotSeq);
    }

    @Test
    @DisplayName("없는 슬롯은 404")
    void unknownSlotIsNotFound() {
        assertFails(-1);
    }

    /**
     * 삭제된 챌린지의 슬롯도 404 다 — 챌린지가 앱에서 사라졌는데 그 편집본만 계속
     * 받아지면 §1.8 비노출이 뚫린다.
     */
    @Test
    @DisplayName("삭제된 챌린지의 슬롯은 404")
    void slotOfDeletedChallengeIsNotFound() {
        Integer slotSeq = newSlot(0, true);
        challengeRepository.findById(challengeSeq).orElseThrow().softDelete();
        entityManager.flush();

        assertFails(slotSeq);
    }

    private void assertFails(Integer slotSeq) {
        assertThatThrownBy(() -> challengeQueryService.editedVideo(slotSeq))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ChallengeErrorCode.EDITED_CHALLENGE_NOT_FOUND);
    }

    // --- 픽스처 -------------------------------------------------------------

    /** 슬롯을 만들고, 편집본이 있으면 §3.7.6 처럼 뒤이어 채운다 — 실제 순서와 같다. */
    private Integer newSlot(int slotOrder, boolean withEdited) {
        String uuid = UUID.randomUUID().toString();
        ChallengeParticipant slot = ChallengeParticipant.of(
                challengeSeq, newArtist(), slotOrder,
                new ChallengeParticipant.Anchor(0.22, 0.36, 0.0995));
        if (withEdited) {
            // private/ 프리픽스라야 서명 경로를 탄다(0020)
            slot.replaceEditedKeys("private/edited/" + uuid + "/e.mp4",
                    "private/edited/" + uuid + "/s.jpg",
                    "private/edited/" + uuid + "/p.jpg", DURATION_MS);
        }
        return participantRepository.saveAndFlush(slot).getChallengeParticipantSeq();
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

    private Integer newChallenge() {
        String uuid = UUID.randomUUID().toString();
        Integer songSeq = songRepository.saveAndFlush(
                Song.register("곡 " + uuid.substring(0, 8), "카리나", adminUserSeq)).getSongSeq();
        return challengeRepository.saveAndFlush(Challenge.register(
                        artistSeq, songSeq, "소개",
                        "challenges/" + uuid + "/master.m3u8",
                        "challenges/" + uuid + "/thumb.jpg", 15_000))
                .getChallengeSeq();
    }
}
