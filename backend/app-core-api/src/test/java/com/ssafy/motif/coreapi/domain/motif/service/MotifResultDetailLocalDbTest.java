package com.ssafy.motif.coreapi.domain.motif.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.motif.MotifErrorCode;
import com.ssafy.motif.coreapi.domain.motif.dto.MotifResultDetailResponse;
import com.ssafy.motif.coreapi.global.media.MediaSigningTestSupport;
import com.ssafy.motif.domain.core.artist.Artist;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.asset.AssetType;
import com.ssafy.motif.domain.core.asset.MotifAsset;
import com.ssafy.motif.domain.core.asset.MotifAssetRepository;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §11.6 결과물 상세를 실제 DB 에 대고 검증한다 (S15P21D104-228).
 *
 * <p><b>두 서명이 섞이지 않는지를 본다.</b> {@code videoUrl} 은 프리픽스 서명(Policy),
 * {@code thumbnailUrl} 은 단일 파일 서명(Expires)이다(ADR-0012). 영상에 단일 파일 서명이
 *붙으면 재생목록만 열리고 <b>조각 요청이 전부 403</b> 인데, 응답만 보면 멀쩡해 보여서
 * 실기기에서야 드러난다.
 *
 * <p>404 세 갈래(미존재 · 삭제 · 타인 소유)가 한 코드로 모이는 것도 여기서 고정한다 —
 * 조회 메서드가 {@code userSeq} 를 걸고 있다는 계약이 그 근거다.
 *
 * <p><b>로컬 compose DB 는 세 레인이 공유한다</b> — 이 테스트는 자기가 만든 사용자로만
 * 조회한다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class MotifResultDetailLocalDbTest {

    @DynamicPropertySource
    static void media(DynamicPropertyRegistry registry) {
        MediaSigningTestSupport.register(registry);
    }

    @Autowired
    private MotifResultQueryService motifResultQueryService;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private MotifAssetRepository motifAssetRepository;
    @Autowired
    private UserRepository userRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private Integer adminUserSeq;
    private Integer fanSeq;
    private Integer artistSeq;
    private Integer assetSeq;
    private UUID jobUuid;

    @BeforeEach
    void setUp() {
        adminUserSeq = newUser().getUserSeq();
        fanSeq = newUser().getUserSeq();
        artistSeq = newArtist("카리나");
        assetSeq = newAsset(artistSeq);
    }

    // --- 성공 ------------------------------------------------------------------

    @Test
    @DisplayName("상세가 명세 형태 그대로 나온다")
    void detailCarriesEveryField() {
        Long resultSeq = newResult();

        MotifResultDetailResponse detail = detail(resultSeq);

        assertThat(detail.resultSeq()).isEqualTo(resultSeq);
        assertThat(detail.durationMs()).isEqualTo(9_800);
        assertThat(detail.description()).isNull();
        assertThat(detail.isRep()).isFalse();
        assertThat(detail.regDttm()).isNotNull();
        assertThat(detail.asset().assetSeq()).isEqualTo(assetSeq);
        assertThat(detail.asset().title()).isNotBlank();
        assertThat(detail.artist().artistSeq()).isEqualTo(artistSeq);
        assertThat(detail.artist().name()).isEqualTo("카리나");
        assertThat(detail.artist().groupName()).isNull();
    }

    @Test
    @DisplayName("문구를 채운 결과물은 그대로 나온다 — null 만 다루는 게 아니다")
    void descriptionIsReturnedWhenPresent() {
        Long resultSeq = newResult();
        setDescription(resultSeq, "첫 모티프");

        assertThat(detail(resultSeq).description()).isEqualTo("첫 모티프");
    }

    // --- 두 서명 ----------------------------------------------------------------

    @Test
    @DisplayName("videoUrl 은 프리픽스 서명이다 — 조각까지 이 한 벌로 통한다")
    void videoUrlCarriesPrefixSignature() {
        String url = detail(newResult()).videoUrl();

        assertThat(url)
                .startsWith(MediaSigningTestSupport.CDN + "/private/motif/" + jobUuid + "/master.m3u8?")
                .contains("Policy=", "Signature=", "Key-Pair-Id=");
        // core-api 재생목록 경로로 돌아가지 않는다 (ADR-0012 로 전환됐다)
        assertThat(url).doesNotContain("/api/v1/media/");
    }

    @Test
    @DisplayName("thumbnailUrl 은 단일 파일 서명이다 — 둘이 섞이지 않는다")
    void thumbnailUrlCarriesCannedSignature() {
        MotifResultDetailResponse detail = detail(newResult());

        assertThat(detail.thumbnailUrl())
                .startsWith(MediaSigningTestSupport.CDN + "/private/motif/" + jobUuid + "/thumb.jpg?")
                .contains("Expires=", "Signature=", "Key-Pair-Id=")
                .doesNotContain("Policy=");
    }

    // --- isRep ------------------------------------------------------------------

    @Test
    @DisplayName("대표로 지정한 결과물은 isRep 이 true 다")
    void representativeIsFlagged() {
        Long resultSeq = newResult();
        setRepMotif(fanSeq, resultSeq);

        assertThat(detail(resultSeq).isRep()).isTrue();
    }

    @Test
    @DisplayName("남이 대표로 지정해도 내 응답에서는 false 다")
    void representativeIsPerCaller() {
        Long resultSeq = newResult();
        setRepMotif(newUser().getUserSeq(), resultSeq);

        assertThat(detail(resultSeq).isRep()).isFalse();
    }

    // --- 404 --------------------------------------------------------------------

    @Test
    @DisplayName("없는 결과물은 404 다")
    void unknownResultIsNotFound() {
        assertNotFound(9_999_999_999L);
    }

    @Test
    @DisplayName("삭제한 결과물은 404 다")
    void deletedResultIsNotFound() {
        Long resultSeq = newResult();
        softDelete(resultSeq);

        assertNotFound(resultSeq);
    }

    @Test
    @DisplayName("타인 소유는 403 이 아니라 404 다 — 존재 은닉")
    void resultOfAnotherUserIsNotFound() {
        Integer stranger = newUser().getUserSeq();

        assertNotFound(newResult(stranger, artistSeq));
    }

    // --- 목록과 어긋나지 않는다 ---------------------------------------------------

    @Test
    @DisplayName("삭제된 아티스트의 결과물도 열린다 — 이름도 그대로다")
    void resultOfDeletedArtistStillOpens() {
        Long resultSeq = newResult();
        entityManager.createNativeQuery(
                        "update motif_core.artists set is_deleted = true where artist_seq = :seq")
                .setParameter("seq", artistSeq)
                .executeUpdate();
        entityManager.clear();

        assertThat(detail(resultSeq).artist().name()).isEqualTo("카리나");
    }

    @Test
    @DisplayName("판매 중단된 에셋의 결과물도 열린다 — 에셋 제목도 그대로다")
    void resultOfInactiveAssetStillOpens() {
        Long resultSeq = newResult();
        entityManager.createNativeQuery(
                        "update motif_core.motif_assets set is_active = false where asset_seq = :seq")
                .setParameter("seq", assetSeq)
                .executeUpdate();
        entityManager.clear();

        assertThat(detail(resultSeq).asset().title()).isNotBlank();
    }

    // --- 픽스처 -------------------------------------------------------------

    private MotifResultDetailResponse detail(Long resultSeq) {
        return motifResultQueryService.detail(fanSeq, resultSeq);
    }

    private void assertNotFound(Long resultSeq) {
        assertThatThrownBy(() -> detail(resultSeq))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(MotifErrorCode.MOTIF_RESULT_NOT_FOUND);
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

    private Integer newAsset(Integer artistSeq) {
        String uuid = UUID.randomUUID().toString();
        return motifAssetRepository.saveAndFlush(MotifAsset.register(
                        artistSeq, "에셋 " + uuid.substring(0, 8), null,
                        "assets/" + uuid + ".mp4", "assets/" + uuid + "/thumb.jpg", 9_800,
                        AssetType.NORMAL))
                .getAssetSeq();
    }

    private Long newResult() {
        return newResult(fanSeq, artistSeq);
    }

    /** 결과물은 합성 잡이 끝날 때 서버가 만든다(§11.7) — 그 경로가 없어 행을 직접 넣는다. */
    private Long newResult(Integer userSeq, Integer artistSeq) {
        UUID uuid = UUID.randomUUID();
        jobUuid = uuid;
        long jobSeq = newJob(userSeq, uuid);
        entityManager.createNativeQuery("""
                        insert into motif_core.motif_results
                               (user_seq, asset_seq, artist_seq, job_seq,
                                video_key, thumbnail_key, duration_ms)
                        values (:userSeq, :assetSeq, :artistSeq, :jobSeq,
                                :videoKey, :thumbnailKey, 9800)
                        """)
                .setParameter("userSeq", userSeq)
                .setParameter("assetSeq", assetSeq)
                .setParameter("artistSeq", artistSeq)
                .setParameter("jobSeq", jobSeq)
                .setParameter("videoKey", "private/motif/" + uuid + "/master.m3u8")
                .setParameter("thumbnailKey", "private/motif/" + uuid + "/thumb.jpg")
                .executeUpdate();
        entityManager.clear();
        return ((Number) entityManager.createNativeQuery(
                        "select result_seq from motif_core.motif_results where job_seq = :jobSeq")
                .setParameter("jobSeq", jobSeq)
                .getSingleResult()).longValue();
    }

    /** {@code status='DONE'} 은 산출물 키 두 개를 함께 요구한다. */
    private long newJob(Integer userSeq, UUID uuid) {
        entityManager.createNativeQuery("""
                        insert into motif_core.composite_jobs
                               (job_uuid, job_type, user_seq, asset_seq, raw_video_key, status,
                                result_video_key, result_thumbnail_key)
                        values (cast(:jobUuid as uuid), 'MOTIF', :userSeq, :assetSeq, :rawKey, 'DONE',
                                :videoKey, :thumbnailKey)
                        """)
                .setParameter("jobUuid", uuid.toString())
                .setParameter("userSeq", userSeq)
                .setParameter("assetSeq", assetSeq)
                .setParameter("rawKey", "uploads/" + uuid + ".mp4")
                .setParameter("videoKey", "private/motif/" + uuid + "/master.m3u8")
                .setParameter("thumbnailKey", "private/motif/" + uuid + "/thumb.jpg")
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery(
                        "select job_seq from motif_core.composite_jobs where job_uuid = cast(:jobUuid as uuid)")
                .setParameter("jobUuid", uuid.toString())
                .getSingleResult()).longValue();
    }

    private void setDescription(Long resultSeq, String description) {
        entityManager.createNativeQuery(
                        "update motif_core.motif_results set description = :description where result_seq = :seq")
                .setParameter("description", description)
                .setParameter("seq", resultSeq)
                .executeUpdate();
        entityManager.clear();
    }

    private void softDelete(Long resultSeq) {
        entityManager.createNativeQuery(
                        "update motif_core.motif_results set is_deleted = true where result_seq = :seq")
                .setParameter("seq", resultSeq)
                .executeUpdate();
        entityManager.clear();
    }

    /** 대표 설정 API(§11.8)가 아직 없어 칼럼을 직접 쓴다. */
    private void setRepMotif(Integer userSeq, Long resultSeq) {
        entityManager.createNativeQuery(
                        "update motif_core.users set rep_motif_result_seq = :resultSeq where user_seq = :userSeq")
                .setParameter("resultSeq", resultSeq)
                .setParameter("userSeq", userSeq)
                .executeUpdate();
        entityManager.clear();
    }
}
