package com.ssafy.motif.coreapi.domain.motif.service;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.common.response.CursorResponse;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.admin.motif.AdminMotifResultService;
import com.ssafy.motif.coreapi.admin.motif.dto.AdminMotifResultItem;
import com.ssafy.motif.coreapi.domain.motif.dto.MotifResultItem;
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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §11.5 보관함 목록을 실제 DB 에 대고 검증한다 (S15P21D104-227).
 *
 * <p><b>커서 최초 호출을 반드시 덮는다</b> — {@code cursor} 가 없는 호출이 이 API 의
 * 기본이라, 리포지토리를 한 메서드로 합쳐 {@code null} 을 흘렸다면 <b>가장 흔한 요청</b>이
 * 500 이 되는 자리다(§5.5 공지 목록과 같은 함정).
 *
 * <p><b>서명 키를 즉석에서 만들어 주입한다.</b> 로컬·CI 에는
 * {@code motif.media.signing.private-key} 가 없어 보호 등급 URL 발급이 던지는데,
 * 모티프 결과물은 전량 보호 등급이라 그대로 두면 이 API 를 한 줄도 검증할 수 없다.
 *
 * <p><b>로컬 compose DB 는 세 레인이 공유한다</b> — 이 테스트는 자기가 만든 사용자로만
 * 조회한다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class MotifResultLocalDbTest {

    private static final String CDN = "https://cdn.test";

    /**
     * 테스트용 RSA 키페어 — 서명 <b>경로</b>를 태우는 것이 목적이라 값 자체는 의미가 없다.
     *
     * <p>고정 PEM 을 소스에 박지 않는다. 진짜 키처럼 보이는 문자열이 레포에 남으면
     * 언젠가 운영 키와 헷갈린다.
     */
    @DynamicPropertySource
    static void signingKey(DynamicPropertyRegistry registry) {
        registry.add("motif.media.public-base-url", () -> CDN);
        registry.add("motif.media.signing.private-key", MotifResultLocalDbTest::generatePkcs8Pem);
    }

    private static String generatePkcs8Pem() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return "-----BEGIN PRIVATE KEY-----\n"
                    + Base64.getMimeEncoder().encodeToString(pair.getPrivate().getEncoded())
                    + "\n-----END PRIVATE KEY-----";
        } catch (Exception e) {
            throw new IllegalStateException("테스트 키 생성 실패", e);
        }
    }

    @Autowired
    private MotifResultQueryService motifResultQueryService;
    @Autowired
    private AdminMotifResultService adminMotifResultService;
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

    @BeforeEach
    void setUp() {
        adminUserSeq = newUser().getUserSeq();
        fanSeq = newUser().getUserSeq();
        artistSeq = newArtist("카리나");
        assetSeq = newAsset(artistSeq);
    }

    // --- 커서 -------------------------------------------------------------------

    @Test
    @DisplayName("커서를 생략한 최초 호출이 500 이 아니라 첫 페이지를 준다")
    void firstCallWithoutCursorWorks() {
        Long older = newResult();
        Long newer = newResult();

        CursorResponse<MotifResultItem> page = results(null, null, 20);

        assertThat(page.list())
                .extracting(MotifResultItem::resultSeq)
                .containsExactly(newer, older);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @DisplayName("빈 문자열 커서도 최초 호출과 같다")
    void blankCursorIsTreatedAsFirstCall() {
        Long only = newResult();

        assertThat(results(null, "", 20).list())
                .extracting(MotifResultItem::resultSeq)
                .containsExactly(only);
    }

    @Test
    @DisplayName("nextCursor 로 이어 받으면 겹침도 누락도 없다")
    void cursorWalksThePagesWithoutOverlap() {
        Long first = newResult();
        Long second = newResult();
        Long third = newResult();

        CursorResponse<MotifResultItem> page1 = results(null, null, 2);
        assertThat(page1.list()).extracting(MotifResultItem::resultSeq)
                .containsExactly(third, second);
        assertThat(page1.hasNext()).isTrue();
        assertThat(page1.nextCursor()).isNotNull();

        CursorResponse<MotifResultItem> page2 = results(null, page1.nextCursor(), 2);
        assertThat(page2.list()).extracting(MotifResultItem::resultSeq).containsExactly(first);
        assertThat(page2.hasNext()).isFalse();
        assertThat(page2.nextCursor()).isNull();
    }

    @Test
    @DisplayName("역방향은 아직 계약이 아니다 — prevCursor 는 null, hasPrevious 는 false")
    void backwardPagingIsNotPartOfTheContractYet() {
        newResult();

        CursorResponse<MotifResultItem> page = results(null, null, 20);

        assertThat(page.prevCursor()).isNull();
        assertThat(page.hasPrevious()).isFalse();
    }

    @Test
    @DisplayName("망가진 커서는 400 이다 — 조용히 첫 페이지로 되돌리지 않는다")
    void brokenCursorIsRejected() {
        assertThatThrownBy(() -> results(null, "!!not-base64!!", 20))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_PARAMETER);
    }

    @Test
    @DisplayName("size 는 100 을 넘지 못한다 — §1.9 상한")
    void sizeIsCappedAtHundred() {
        newResult();

        assertThat(results(null, null, 5_000).list()).hasSize(1);
    }

    @Test
    @DisplayName("결과물이 없으면 빈 목록이고 커서도 없다")
    void emptyLibraryYieldsEmptyPage() {
        CursorResponse<MotifResultItem> page = results(null, null, 20);

        assertThat(page.list()).isEmpty();
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    // --- 대상 -------------------------------------------------------------------

    @Test
    @DisplayName("남의 결과물은 내 보관함에 없다")
    void resultsOfAnotherUserAreHidden() {
        Integer stranger = newUser().getUserSeq();
        newResult(stranger, artistSeq);
        Long mine = newResult();

        assertThat(results(null, null, 20).list())
                .extracting(MotifResultItem::resultSeq)
                .containsExactly(mine);
    }

    @Test
    @DisplayName("삭제한 결과물은 빠진다")
    void deletedResultIsHidden() {
        Long kept = newResult();
        softDelete(newResult());

        assertThat(results(null, null, 20).list())
                .extracting(MotifResultItem::resultSeq)
                .containsExactly(kept);
    }

    @Test
    @DisplayName("artistSeq 를 주면 그 아티스트 결과물만 남는다 — §5.8 진입점")
    void artistFilterNarrowsTheLibrary() {
        Integer other = newArtist("윈터");
        Long mine = newResult();
        newResult(fanSeq, other);

        assertThat(results(artistSeq, null, 20).list())
                .extracting(MotifResultItem::resultSeq)
                .containsExactly(mine);
    }

    @Test
    @DisplayName("artistSeq 필터에서도 커서가 이어진다 — 최초 호출의 null 바인딩 포함")
    void artistFilterWalksWithCursor() {
        Long first = newResult();
        Long second = newResult();

        CursorResponse<MotifResultItem> page1 = results(artistSeq, null, 1);
        assertThat(page1.list()).extracting(MotifResultItem::resultSeq).containsExactly(second);
        assertThat(page1.hasNext()).isTrue();

        CursorResponse<MotifResultItem> page2 = results(artistSeq, page1.nextCursor(), 1);
        assertThat(page2.list()).extracting(MotifResultItem::resultSeq).containsExactly(first);
    }

    @Test
    @DisplayName("삭제된 아티스트의 결과물도 남고 이름이 붙는다 — 내가 만든 것이다")
    void resultOfDeletedArtistSurvives() {
        newResult();
        entityManager.createNativeQuery(
                        "update motif_core.artists set is_deleted = true where artist_seq = :seq")
                .setParameter("seq", artistSeq)
                .executeUpdate();
        entityManager.clear();

        List<MotifResultItem> list = results(null, null, 20).list();

        assertThat(list).hasSize(1);
        assertThat(list.getFirst().artist().name()).isEqualTo("카리나");
    }

    // --- 항목 --------------------------------------------------------------------

    @Test
    @DisplayName("썸네일은 서명된 보호 등급 주소다 — 무서명으로 새지 않는다")
    void thumbnailIsSigned() {
        newResult();

        String url = results(null, null, 20).list().getFirst().thumbnailUrl();

        assertThat(url).startsWith(CDN + "/private/motif/");
        assertThat(url).contains("Signature=").contains("Key-Pair-Id=").contains("Expires=");
    }

    @Test
    @DisplayName("artist 는 README §3.5 요약이다 — 솔로면 groupName 이 null")
    void itemCarriesArtistSummary() {
        newResult();

        MotifResultItem.Artist artist = results(null, null, 20).list().getFirst().artist();

        assertThat(artist.artistSeq()).isEqualTo(artistSeq);
        assertThat(artist.name()).isEqualTo("카리나");
        assertThat(artist.groupName()).isNull();
        assertThat(artist.profileImageUrl()).isNull();
    }

    @Test
    @DisplayName("isRep 은 대표 모티프 하나만 true 다")
    void onlyTheRepresentativeIsFlagged() {
        Long plain = newResult();
        Long rep = newResult();
        setRepMotif(rep);

        assertThat(results(null, null, 20).list())
                .extracting(MotifResultItem::resultSeq, MotifResultItem::isRep)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple(rep, true),
                        org.assertj.core.api.Assertions.tuple(plain, false));
    }

    @Test
    @DisplayName("남이 대표로 지정한 결과물은 내 목록에서 isRep 이 아니다")
    void representativeIsPerCaller() {
        Long mine = newResult();
        Integer stranger = newUser().getUserSeq();
        setRepMotif(stranger, mine);

        assertThat(results(null, null, 20).list().getFirst().isRep()).isFalse();
    }

    // --- 어드민 목록 (어드민 명세 §3.17.1) ---------------------------------------

    @Test
    @DisplayName("어드민 — 필터를 전부 비운 호출이 500 이 아니고 서명된 재생 주소를 준다")
    void adminListWithoutFilters() {
        Long mine = newResult();

        PageResponse<AdminMotifResultItem> page =
                adminMotifResultService.list(null, null, null, false, 0, 100);

        assertThat(page.list()).extracting(AdminMotifResultItem::resultSeq).contains(mine);
        AdminMotifResultItem item = page.list().stream()
                .filter(i -> i.resultSeq().equals(mine)).findFirst().orElseThrow();
        assertThat(item.userNickname()).isNotBlank();
        assertThat(item.assetTitle()).startsWith("에셋 ");
        assertThat(item.videoUrl()).startsWith(CDN + "/private/motif/").contains("Signature=");
    }

    @Test
    @DisplayName("어드민 — 삭제분은 includeDeleted=true 일 때만 보인다")
    void adminListIncludesDeletedOnlyWhenAsked() {
        Long kept = newResult();
        Long gone = newResult();
        softDelete(gone);

        assertThat(adminMotifResultService.list(fanSeq, null, null, false, 0, 20).list())
                .extracting(AdminMotifResultItem::resultSeq).containsExactly(kept);
        assertThat(adminMotifResultService.list(fanSeq, artistSeq, assetSeq, true, 0, 20).list())
                .extracting(AdminMotifResultItem::resultSeq).containsExactly(gone, kept);
    }

    // --- 픽스처 -------------------------------------------------------------

    private CursorResponse<MotifResultItem> results(Integer artistSeq, String cursor, int size) {
        return motifResultQueryService.results(fanSeq, artistSeq, cursor, size);
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

    /**
     * 결과물은 합성 잡이 끝날 때 서버가 만든다(§11.7) — 그 경로가 아직 없어 행을 직접
     * 넣는다. 검증 대상은 조회 쪽이다.
     */
    private Long newResult(Integer userSeq, Integer artistSeq) {
        UUID jobUuid = UUID.randomUUID();
        long jobSeq = newJob(userSeq, jobUuid);
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
                .setParameter("videoKey", "private/motif/" + jobUuid + "/master.m3u8")
                .setParameter("thumbnailKey", "private/motif/" + jobUuid + "/thumb.jpg")
                .executeUpdate();
        entityManager.clear();
        return ((Number) entityManager.createNativeQuery(
                        "select result_seq from motif_core.motif_results where job_seq = :jobSeq")
                .setParameter("jobSeq", jobSeq)
                .getSingleResult()).longValue();
    }

    /**
     * {@code status='DONE'} 은 산출물 키 두 개를 함께 요구한다
     * ({@code ck_composite_jobs_status_shape}) — 결과물이 생기는 시점이 곧 잡이 끝난
     * 시점이라 그렇다(§11.7).
     */
    private long newJob(Integer userSeq, UUID jobUuid) {
        entityManager.createNativeQuery("""
                        insert into motif_core.composite_jobs
                               (job_uuid, job_type, user_seq, asset_seq, raw_video_key, status,
                                result_video_key, result_thumbnail_key)
                        values (cast(:jobUuid as uuid), 'MOTIF', :userSeq, :assetSeq, :rawKey, 'DONE',
                                :videoKey, :thumbnailKey)
                        """)
                .setParameter("jobUuid", jobUuid.toString())
                .setParameter("userSeq", userSeq)
                .setParameter("assetSeq", assetSeq)
                .setParameter("rawKey", "uploads/" + jobUuid + ".mp4")
                .setParameter("videoKey", "private/motif/" + jobUuid + "/master.m3u8")
                .setParameter("thumbnailKey", "private/motif/" + jobUuid + "/thumb.jpg")
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery(
                        "select job_seq from motif_core.composite_jobs where job_uuid = cast(:jobUuid as uuid)")
                .setParameter("jobUuid", jobUuid.toString())
                .getSingleResult()).longValue();
    }

    private void softDelete(Long resultSeq) {
        entityManager.createNativeQuery(
                        "update motif_core.motif_results set is_deleted = true where result_seq = :seq")
                .setParameter("seq", resultSeq)
                .executeUpdate();
        entityManager.clear();
    }

    private void setRepMotif(Long resultSeq) {
        setRepMotif(fanSeq, resultSeq);
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
