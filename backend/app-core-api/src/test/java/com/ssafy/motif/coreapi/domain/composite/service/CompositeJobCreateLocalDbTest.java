package com.ssafy.motif.coreapi.domain.composite.service;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.error.ErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.asset.AssetErrorCode;
import com.ssafy.motif.coreapi.domain.challenge.ChallengeErrorCode;
import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobCreateRequest;
import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobCreateResponse;
import com.ssafy.motif.coreapi.global.media.MediaObjectStore;
import com.ssafy.motif.domain.core.artist.Artist;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.asset.AssetType;
import com.ssafy.motif.domain.core.asset.MotifAsset;
import com.ssafy.motif.domain.core.asset.MotifAssetRepository;
import com.ssafy.motif.domain.core.challenge.Challenge;
import com.ssafy.motif.domain.core.challenge.ChallengeParticipant;
import com.ssafy.motif.domain.core.challenge.ChallengeParticipantRepository;
import com.ssafy.motif.domain.core.challenge.ChallengeRepository;
import com.ssafy.motif.domain.core.job.CompositeJob;
import com.ssafy.motif.domain.core.job.CompositeJobRepository;
import com.ssafy.motif.domain.core.job.JobStatus;
import com.ssafy.motif.domain.core.job.JobType;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;

/**
 * §13.1 합성 잡 생성을 실제 DB 에 대고 검증한다 (S15P21D104-186 · 246).
 *
 * <p><b>DB 없이는 확인되지 않는 것이 둘이다.</b> 하나는 {@code ck_composite_jobs_type_shape}
 * — CHALLENGE 와 MOTIF 가 서로 다른 칸을 채워야 하고 MOTIF 는
 * {@code challenge_participant_seq} 가 NULL 이어야 한다. 다른 하나는 MOTIF 권한 판정으로,
 * 구매·패스·에셋 타입이 전부 쿼리와 {@code MyAccess} 규칙에 걸쳐 있다.
 *
 * <p><b>S3 는 목이다.</b> presign 은 로컬 서명 계산이라 자격증명이 없으면 터지고, 이
 * 테스트가 볼 것은 "어떤 키·타입·만료로 서명을 요청했나" 지 서명 문자열이 아니다.
 *
 * <p><b>로컬 compose DB 는 세 레인이 공유한다</b> — 테스트 트랜잭션이 롤백되므로 행이
 * 남지 않는다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class CompositeJobCreateLocalDbTest {

    private static final String UPLOAD_URL = "https://motif-media.s3.test/raw/x.mp4?X-Amz-Signature=stub";

    /** 20MB — 폰 촬영본 정도. 상한(300MB) 안이다. */
    private static final long UPLOAD_SIZE = 20L * 1024 * 1024;

    @Autowired
    private CompositeJobCommandService compositeJobCommandService;
    @Autowired
    private CompositeJobRepository compositeJobRepository;
    @Autowired
    private ChallengeRepository challengeRepository;
    @Autowired
    private ChallengeParticipantRepository participantRepository;
    @Autowired
    private MotifAssetRepository motifAssetRepository;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private SongRepository songRepository;
    @Autowired
    private UserRepository userRepository;
    @PersistenceContext
    private EntityManager entityManager;

    @MockitoBean
    private MediaObjectStore objectStore;

    private Integer adminUserSeq;
    private Integer fanSeq;
    private Integer artistSeq;

    @BeforeEach
    void setUp() {
        given(objectStore.presignPut(anyString(), anyString(), anyLong(), any(Duration.class)))
                .willReturn(UPLOAD_URL);
        adminUserSeq = newUser().getUserSeq();
        fanSeq = newUser().getUserSeq();
        artistSeq = newArtist();
    }

    // --- CHALLENGE (186) ---------------------------------------------------------

    @Test
    @DisplayName("슬롯을 지정하면 그 슬롯이 잡에 박히고 UPLOADED 행이 생긴다")
    void challengeWithSlotRecordsIt() {
        Integer challengeSeq = newChallenge();
        Integer slotSeq = newSlot(challengeSeq, 1, true);

        CompositeJobCreateResponse response = create(challengeRequest(challengeSeq, slotSeq));

        CompositeJob job = job(response.jobUuid());
        assertThat(job.getJobType()).isEqualTo(JobType.CHALLENGE);
        assertThat(job.getStatus()).isEqualTo(JobStatus.UPLOADED);
        assertThat(job.getUserSeq()).isEqualTo(fanSeq);
        assertThat(job.getChallengeSeq()).isEqualTo(challengeSeq);
        assertThat(job.getAssetSeq()).isNull();
        assertThat(job.getChallengeParticipantSeq()).isEqualTo(slotSeq);
        assertThat(job.getAttempt()).isEqualTo((short) 1);
        assertThat(job.getRawVideoKey()).isEqualTo("raw/" + response.jobUuid() + ".mp4");
    }

    @Test
    @DisplayName("슬롯 생략 — 무교체다. challenge_participant_seq 가 NULL 로 남는다")
    void challengeWithoutSlotLeavesItNull() {
        Integer challengeSeq = newChallenge();

        assertThat(job(create(challengeRequest(challengeSeq, null)).jobUuid())
                .getChallengeParticipantSeq()).isNull();
    }

    @Test
    @DisplayName("없는 슬롯 seq 는 404")
    void unknownSlotIsNotFound() {
        Integer challengeSeq = newChallenge();
        newSlot(challengeSeq, 0, true);

        assertFails(challengeRequest(challengeSeq, -1),
                ChallengeErrorCode.EDITED_CHALLENGE_NOT_FOUND);
    }

    /**
     * 0019 — 전조합 완결성 강제가 없어져 "슬롯은 있는데 편집본이 없는" 상태가 정상이 됐다.
     * 그 슬롯을 고르면 합성 베이스가 없으므로 여기서 막는다.
     */
    @Test
    @DisplayName("슬롯은 있는데 편집본이 없으면 404 — §7.5 가 화면에서 미리 잠그는 자리다")
    void slotWithoutEditedIsNotFound() {
        Integer challengeSeq = newChallenge();
        Integer slotSeq = newSlot(challengeSeq, 0, false);

        assertFails(challengeRequest(challengeSeq, slotSeq),
                ChallengeErrorCode.EDITED_CHALLENGE_NOT_FOUND);
    }

    /**
     * 0020 — 잡 행이 {@code challenge_seq} 와 슬롯 seq 를 함께 들고 있어 둘이 어긋나면
     * 합성 베이스와 챌린지가 다른 잡이 만들어진다. FK 는 각자만 보므로 서비스가 대조한다.
     */
    @Test
    @DisplayName("다른 챌린지의 슬롯을 보내면 404 — 존재를 알려주지 않는다(§1.8)")
    void slotOfAnotherChallengeIsNotFound() {
        Integer mine = newChallenge();
        Integer other = newChallenge();
        Integer otherSlot = newSlot(other, 0, true);

        assertFails(challengeRequest(mine, otherSlot),
                ChallengeErrorCode.EDITED_CHALLENGE_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제된 챌린지는 슬롯을 보기 전에 404 다")
    void deletedChallengeIsNotFound() {
        Integer challengeSeq = newChallenge();
        Integer slotSeq = newSlot(challengeSeq, 0, true);
        challengeRepository.findById(challengeSeq).orElseThrow().softDelete();
        entityManager.flush();

        assertFails(challengeRequest(challengeSeq, slotSeq), ChallengeErrorCode.CHALLENGE_NOT_FOUND);
    }

    @Test
    @DisplayName("presign 은 잡의 raw 키에 video/mp4 · 600초로 요청한다 — 조건이 서명에 박힌다")
    void presignCarriesTheRawKeyAndItsConditions() {
        CompositeJobCreateResponse response = create(challengeRequest(newChallenge(), null));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> size = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(objectStore).presignPut(key.capture(), org.mockito.ArgumentMatchers.eq("video/mp4"),
                size.capture(), ttl.capture());

        assertThat(key.getValue()).isEqualTo("raw/" + response.jobUuid() + ".mp4");
        // 상한이 아니라 클라이언트가 보낸 크기여야 한다 — 서명된 Content-Length 는
        // "이하" 가 아니라 "정확히" 라서, 상한을 박으면 그 크기 파일만 올라간다
        assertThat(size.getValue()).isEqualTo(UPLOAD_SIZE);
        assertThat(ttl.getValue()).isEqualTo(Duration.ofSeconds(600));
        assertThat(response.expiresIn()).isEqualTo(600);
        assertThat(response.uploadUrl()).isEqualTo(UPLOAD_URL);
    }

    @Test
    @DisplayName("반대 형태의 칸은 무시하지 않고 400 으로 거절한다 — DB CHECK 와 같은 모양")
    void wrongShapeIsRejected() {
        assertFails(new CompositeJobCreateRequest(JobType.CHALLENGE, null, 1, null, UPLOAD_SIZE),
                CommonErrorCode.INVALID_PARAMETER);
        assertFails(new CompositeJobCreateRequest(JobType.MOTIF, 1, 1, null, UPLOAD_SIZE),
                CommonErrorCode.INVALID_PARAMETER);
    }

    @Test
    @DisplayName("상한을 넘는 크기는 400 — data.maxBytes 로 한도를 알려주고 잡을 만들지 않는다")
    void oversizedUploadIsRejected() {
        long tooBig = 301L * 1024 * 1024;

        assertThatThrownBy(() -> create(new CompositeJobCreateRequest(
                JobType.CHALLENGE, newChallenge(), null, null, tooBig)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException business = (BusinessException) e;
                    assertThat(business.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_PARAMETER);
                    assertThat(business.getData()).isNotNull();
                });
        verify(objectStore, org.mockito.Mockito.never())
                .presignPut(anyString(), anyString(), anyLong(), any(Duration.class));
    }

    // --- MOTIF (246) -------------------------------------------------------------

    @Test
    @DisplayName("권한이 없으면 403 — 선결제 원칙이라 촬영을 시작할 수 없다")
    void motifWithoutAccessIsForbidden() {
        assertFails(motifRequest(newAsset(AssetType.NORMAL, true).getAssetSeq()),
                AssetErrorCode.ASSET_ACCESS_DENIED);
    }

    @Test
    @DisplayName("유효 패스 보유자는 201 — NORMAL 은 패스로 열린다")
    void motifWithPassIsCreated() {
        MotifAsset asset = newAsset(AssetType.NORMAL, true);
        grantPass(Instant.now().plus(30, ChronoUnit.DAYS));

        CompositeJob job = job(create(motifRequest(asset.getAssetSeq())).jobUuid());

        assertThat(job.getJobType()).isEqualTo(JobType.MOTIF);
        assertThat(job.getStatus()).isEqualTo(JobStatus.UPLOADED);
        assertThat(job.getAssetSeq()).isEqualTo(asset.getAssetSeq());
        assertThat(job.getChallengeSeq()).isNull();
        assertThat(job.getChallengeParticipantSeq()).isNull();
    }

    @Test
    @DisplayName("PREMIUM 은 패스로 열리지 않는다 — 패스만 있으면 403 (§11 권한 표)")
    void motifPremiumIsNotCoveredByPass() {
        MotifAsset asset = newAsset(AssetType.PREMIUM, true);
        grantPass(Instant.now().plus(30, ChronoUnit.DAYS));

        assertFails(motifRequest(asset.getAssetSeq()), AssetErrorCode.ASSET_ACCESS_DENIED);
    }

    @Test
    @DisplayName("판매 중단 에셋이어도 구매자는 201 — 어제 산 것이 오늘 막히면 안 된다")
    void motifInactiveAssetOpensForOwner() {
        MotifAsset asset = newAsset(AssetType.PREMIUM, false);
        purchase(asset.getAssetSeq());

        assertThat(job(create(motifRequest(asset.getAssetSeq())).jobUuid()).getAssetSeq())
                .isEqualTo(asset.getAssetSeq());
    }

    @Test
    @DisplayName("없는 에셋은 403 이 아니라 404 다 — 권한 판정 전에 존재를 본다")
    void motifUnknownAssetIsNotFound() {
        assertFails(motifRequest(-1), AssetErrorCode.ASSET_NOT_FOUND);
    }

    // --- 픽스처 -------------------------------------------------------------------

    private CompositeJobCreateResponse create(CompositeJobCreateRequest request) {
        return compositeJobCommandService.create(fanSeq, request);
    }

    private void assertFails(CompositeJobCreateRequest request, ErrorCode expected) {
        assertThatThrownBy(() -> create(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    /** @param slotSeq 교체할 슬롯. {@code null} 이면 무교체(원본 위 합성) */
    private static CompositeJobCreateRequest challengeRequest(Integer challengeSeq, Integer slotSeq) {
        return new CompositeJobCreateRequest(JobType.CHALLENGE, challengeSeq, null, slotSeq, UPLOAD_SIZE);
    }

    private static CompositeJobCreateRequest motifRequest(Integer assetSeq) {
        return new CompositeJobCreateRequest(JobType.MOTIF, null, assetSeq, null, UPLOAD_SIZE);
    }

    private CompositeJob job(UUID jobUuid) {
        entityManager.flush();
        entityManager.clear();
        return compositeJobRepository.findByJobUuid(jobUuid).orElseThrow();
    }

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

    /**
     * 슬롯 하나. {@code withEdited=false} 면 편집본이 없는 슬롯이다 — 0019 이후 정상 상태다.
     *
     * <p>아티스트는 매번 새로 만든다. {@code uq_challenge_participants_artist} 가 한 챌린지
     * 안에서 같은 사람이 두 자리를 차지하는 것을 막는다.
     */
    private Integer newSlot(Integer challengeSeq, int slotOrder, boolean withEdited) {
        ChallengeParticipant slot = participantRepository.saveAndFlush(ChallengeParticipant.of(
                challengeSeq, newArtist(), slotOrder,
                new ChallengeParticipant.Anchor(0.22, 0.36, 0.0995)));
        if (withEdited) {
            // 슬롯이 먼저 나고 편집본이 뒤에 붙는다(§3.7.2 → §3.7.6) — 키가 슬롯 seq 를 품는다
            String prefix = "private/edited/" + challengeSeq + "/"
                    + slot.getChallengeParticipantSeq() + "/";
            slot.replaceEditedKeys(prefix + UUID.randomUUID() + ".mp4",
                    prefix + UUID.randomUUID() + ".jpg",
                    prefix + UUID.randomUUID() + ".jpg", 15_000);
            participantRepository.saveAndFlush(slot);
        }
        return slot.getChallengeParticipantSeq();
    }

    private MotifAsset newAsset(AssetType type, boolean active) {
        String uuid = UUID.randomUUID().toString();
        MotifAsset asset = MotifAsset.register(artistSeq, "에셋 " + uuid.substring(0, 8), "소개 문구",
                "assets/" + uuid + ".mp4", "assets/" + uuid + "/thumb.jpg", 9_800, type);
        if (!active) {
            asset.changeActive(false);
        }
        return motifAssetRepository.saveAndFlush(asset);
    }

    /** 구매·패스 행은 직접 넣는다 — 검증 대상은 §13.1 의 판정이지 §12 의 발급이 아니다. */
    private void purchase(Integer assetSeq) {
        long orderSeq = newOrder("ASSET", assetSeq, null);
        entityManager.createNativeQuery("""
                        insert into motif_core.asset_purchases (user_seq, asset_seq, order_seq, status)
                        values (:userSeq, :assetSeq, :orderSeq, 'ACTIVE')
                        """)
                .setParameter("userSeq", fanSeq)
                .setParameter("assetSeq", assetSeq)
                .setParameter("orderSeq", orderSeq)
                .executeUpdate();
        entityManager.clear();
    }

    private void grantPass(Instant expiresDttm) {
        long orderSeq = newOrder("PASS", null, artistSeq);
        entityManager.createNativeQuery("""
                        insert into motif_core.motif_passes
                               (user_seq, artist_seq, order_seq, duration_months,
                                started_dttm, expires_dttm, status)
                        values (:userSeq, :artistSeq, :orderSeq, 1,
                                :startedDttm, :expiresDttm, 'ACTIVE')
                        """)
                .setParameter("userSeq", fanSeq)
                .setParameter("artistSeq", artistSeq)
                .setParameter("orderSeq", orderSeq)
                .setParameter("startedDttm", expiresDttm.minus(30, ChronoUnit.DAYS))
                .setParameter("expiresDttm", expiresDttm)
                .executeUpdate();
        entityManager.clear();
    }

    private long newOrder(String productType, Integer assetSeq, Integer orderArtistSeq) {
        UUID orderUuid = UUID.randomUUID();
        entityManager.createNativeQuery("""
                        insert into motif_core.orders
                               (order_uuid, user_seq, product_type, asset_seq, artist_seq,
                                country_code, pass_duration_months, amount, currency, status,
                                payment_provider, payment_key, approved_dttm, expires_dttm)
                        values (cast(:orderUuid as uuid), :userSeq, :productType, :assetSeq, :artistSeq,
                                'KR', :passMonths, 3900.00, 'KRW', 'PAID',
                                'TEST', :paymentKey, current_timestamp,
                                current_timestamp + interval '1 day')
                        """)
                .setParameter("orderUuid", orderUuid.toString())
                .setParameter("userSeq", fanSeq)
                .setParameter("productType", productType)
                .setParameter("assetSeq", assetSeq)
                .setParameter("artistSeq", orderArtistSeq)
                .setParameter("passMonths", "PASS".equals(productType) ? (short) 1 : null)
                .setParameter("paymentKey", "pk-" + orderUuid)
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery(
                        "select order_seq from motif_core.orders where order_uuid = cast(:orderUuid as uuid)")
                .setParameter("orderUuid", orderUuid.toString())
                .getSingleResult()).longValue();
    }
}
