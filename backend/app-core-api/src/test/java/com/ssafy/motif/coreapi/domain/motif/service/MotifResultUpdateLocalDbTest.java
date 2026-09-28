package com.ssafy.motif.coreapi.domain.motif.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.motif.MotifErrorCode;
import com.ssafy.motif.coreapi.domain.motif.dto.MotifResultDetailResponse;
import com.ssafy.motif.coreapi.domain.motif.dto.MotifResultUpdateRequest;
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
 * §11.8 결과물 수정·삭제를 실제 DB 에 대고 검증한다.
 *
 * <p><b>대표 단일성이 핵심이다.</b> 대표는 사용자당 하나인데 그 보장이 코드가 아니라
 * {@code users} 칸 하나라는 구조에 있다 — 새 대표를 덮어쓰면 이전 대표가 자동으로
 * 풀린다. 그 구조가 실제로 성립하는지는 DB 없이 확인할 수 없다.
 *
 * <p><b>삭제 시 대표 정리도 여기서만 잡힌다.</b> {@code rep_motif_result_seq} 에 FK 가
 * 없어(순환 참조 회피) DB 가 정리해 주지 않는다 — 빠뜨리면 마이페이지(§4.1)가 삭제된
 * 결과물을 대표로 가리키는데, 그 증상은 이 API 가 아니라 다른 화면에서 나타난다.
 *
 * <p><b>로컬 compose DB 는 세 레인이 공유한다</b> — 이 테스트는 자기가 만든 사용자로만
 * 조회한다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class MotifResultUpdateLocalDbTest {

    @DynamicPropertySource
    static void media(DynamicPropertyRegistry registry) {
        MediaSigningTestSupport.register(registry);
    }

    @Autowired
    private MotifResultCommandService motifResultCommandService;
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

    @BeforeEach
    void setUp() {
        adminUserSeq = newUser().getUserSeq();
        fanSeq = newUser().getUserSeq();
        artistSeq = newArtist();
        assetSeq = newAsset();
    }

    // --- 부분 수정 ---------------------------------------------------------------

    @Test
    @DisplayName("보낸 필드만 반영한다 — description 만 보내면 대표 지정은 그대로다")
    void updatesOnlyGivenFields() {
        Long resultSeq = newResult();
        update(resultSeq, new MotifResultUpdateRequest(null, true));

        MotifResultDetailResponse updated =
                update(resultSeq, new MotifResultUpdateRequest("첫 모티프", null));

        assertThat(updated.description()).isEqualTo("첫 모티프");
        assertThat(updated.isRep()).isTrue();
    }

    @Test
    @DisplayName("빈 문자열은 지움이다 — 생략과 다르다")
    void blankDescriptionClearsIt() {
        Long resultSeq = newResult();
        update(resultSeq, new MotifResultUpdateRequest("채웠다", null));

        assertThat(update(resultSeq, new MotifResultUpdateRequest("", null)).description())
                .isEmpty();
        assertThat(update(resultSeq, new MotifResultUpdateRequest(null, null)).description())
                .isEmpty();
    }

    @Test
    @DisplayName("둘 다 생략하면 현재 상태를 그대로 돌려준다")
    void emptyRequestIsNoOp() {
        Long resultSeq = newResult();
        MotifResultDetailResponse before = detail(resultSeq);

        MotifResultDetailResponse after = update(resultSeq, new MotifResultUpdateRequest(null, null));

        assertThat(withoutSignature(after)).isEqualTo(withoutSignature(before));
    }

    @Test
    @DisplayName("응답이 §11.6 상세와 같다 — 조립 지점이 하나다")
    void responseMatchesDetail() {
        Long resultSeq = newResult();

        MotifResultDetailResponse updated =
                update(resultSeq, new MotifResultUpdateRequest("문구", true));

        assertThat(withoutSignature(updated)).isEqualTo(withoutSignature(detail(resultSeq)));
    }

    // --- 대표 단일성 --------------------------------------------------------------

    @Test
    @DisplayName("isRep=true 가 이전 대표를 자동으로 해제한다 — 대표는 언제나 하나다")
    void newRepresentativeReplacesTheOldOne() {
        Long first = newResult();
        Long second = newResult();
        update(first, new MotifResultUpdateRequest(null, true));

        update(second, new MotifResultUpdateRequest(null, true));

        assertThat(detail(first).isRep()).isFalse();
        assertThat(detail(second).isRep()).isTrue();
        assertThat(repMotifSeq(fanSeq)).isEqualTo(second);
    }

    @Test
    @DisplayName("isRep=false 는 현재 대표를 해제한다")
    void falseClearsTheRepresentative() {
        Long resultSeq = newResult();
        update(resultSeq, new MotifResultUpdateRequest(null, true));

        update(resultSeq, new MotifResultUpdateRequest(null, false));

        assertThat(detail(resultSeq).isRep()).isFalse();
        assertThat(repMotifSeq(fanSeq)).isNull();
    }

    @Test
    @DisplayName("대표가 아닌 결과물에 false 를 보내면 아무 일도 없다 — 남의 지정을 지우지 않는다")
    void falseOnNonRepresentativeIsNoOp() {
        Long rep = newResult();
        Long other = newResult();
        update(rep, new MotifResultUpdateRequest(null, true));

        update(other, new MotifResultUpdateRequest(null, false));

        assertThat(repMotifSeq(fanSeq)).isEqualTo(rep);
        assertThat(detail(rep).isRep()).isTrue();
    }

    @Test
    @DisplayName("내 대표 지정이 남의 대표를 건드리지 않는다")
    void representativeIsPerUser() {
        Integer otherFan = newUser().getUserSeq();
        Long mine = newResult();
        Long theirs = newResult(otherFan);
        setRepMotif(otherFan, theirs);

        update(mine, new MotifResultUpdateRequest(null, true));

        assertThat(repMotifSeq(fanSeq)).isEqualTo(mine);
        assertThat(repMotifSeq(otherFan)).isEqualTo(theirs);
    }

    // --- 삭제 ---------------------------------------------------------------------

    @Test
    @DisplayName("삭제하면 목록·상세에서 사라진다")
    void deletedResultDisappears() {
        Long resultSeq = newResult();

        motifResultCommandService.delete(fanSeq, resultSeq);
        entityManager.flush();
        entityManager.clear();

        assertThat(motifResultQueryService.results(fanSeq, null, null, 20).list()).isEmpty();
        assertNotFound(resultSeq);
    }

    @Test
    @DisplayName("대표였다면 삭제와 함께 users.rep_motif_result_seq 가 비워진다")
    void deletingTheRepresentativeClearsThePointer() {
        Long resultSeq = newResult();
        update(resultSeq, new MotifResultUpdateRequest(null, true));

        motifResultCommandService.delete(fanSeq, resultSeq);
        entityManager.flush();
        entityManager.clear();

        assertThat(repMotifSeq(fanSeq)).isNull();
    }

    @Test
    @DisplayName("대표가 아닌 것을 지워도 대표 지정은 그대로다")
    void deletingOtherResultKeepsTheRepresentative() {
        Long rep = newResult();
        Long other = newResult();
        update(rep, new MotifResultUpdateRequest(null, true));

        motifResultCommandService.delete(fanSeq, other);
        entityManager.flush();
        entityManager.clear();

        assertThat(repMotifSeq(fanSeq)).isEqualTo(rep);
    }

    @Test
    @DisplayName("이미 삭제한 결과물을 다시 지우면 404 다 — 멱등으로 200 을 주지 않는다")
    void deletingTwiceIsNotFound() {
        Long resultSeq = newResult();
        motifResultCommandService.delete(fanSeq, resultSeq);
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> motifResultCommandService.delete(fanSeq, resultSeq))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(MotifErrorCode.MOTIF_RESULT_NOT_FOUND);
    }

    // --- 404 ----------------------------------------------------------------------

    @Test
    @DisplayName("없는 결과물은 PATCH·DELETE 둘 다 404 다 — 빈 요청이어도 그렇다")
    void unknownResultIsNotFound() {
        assertThatThrownBy(() -> update(9_999_999_999L, new MotifResultUpdateRequest(null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(MotifErrorCode.MOTIF_RESULT_NOT_FOUND);
        assertThatThrownBy(() -> motifResultCommandService.delete(fanSeq, 9_999_999_999L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("타인 소유는 403 이 아니라 404 다 — 대표로 가로챌 수 없다")
    void resultOfAnotherUserIsNotFound() {
        Long theirs = newResult(newUser().getUserSeq());

        assertThatThrownBy(() -> update(theirs, new MotifResultUpdateRequest(null, true)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(MotifErrorCode.MOTIF_RESULT_NOT_FOUND);
        assertThat(repMotifSeq(fanSeq)).isNull();
    }

    @Test
    @DisplayName("타인 소유는 삭제되지 않는다")
    void resultOfAnotherUserIsNotDeleted() {
        Integer otherFan = newUser().getUserSeq();
        Long theirs = newResult(otherFan);

        assertThatThrownBy(() -> motifResultCommandService.delete(fanSeq, theirs))
                .isInstanceOf(BusinessException.class);
        entityManager.clear();
        assertThat(motifResultQueryService.results(otherFan, null, null, 20).list())
                .hasSize(1);
    }

    // --- 픽스처 -------------------------------------------------------------

    private MotifResultDetailResponse update(Long resultSeq, MotifResultUpdateRequest request) {
        MotifResultDetailResponse response =
                motifResultCommandService.update(fanSeq, resultSeq, request);
        entityManager.flush();
        entityManager.clear();
        return response;
    }

    private MotifResultDetailResponse detail(Long resultSeq) {
        return motifResultQueryService.detail(fanSeq, resultSeq);
    }

    /**
     * 서명 쿼리를 떼고 비교한다.
     *
     * <p><b>서명은 발급 시각을 담는다</b>(만료 epoch). 두 응답을 통째로 비교하면 초가
     * 바뀌는 순간 실패하는, 원인을 찾기 어려운 깜빡이 테스트가 된다 — 여기서 확인하려는
     * 것은 "같은 조립 지점을 쓰는가" 이지 서명 값이 아니다.
     */
    private static MotifResultDetailResponse withoutSignature(MotifResultDetailResponse response) {
        return new MotifResultDetailResponse(
                response.resultSeq(),
                stripQuery(response.videoUrl()),
                stripQuery(response.thumbnailUrl()),
                response.description(),
                response.durationMs(),
                response.asset(),
                response.artist(),
                response.isRep(),
                response.regDttm());
    }

    private static String stripQuery(String url) {
        if (url == null) {
            return null;
        }
        int mark = url.indexOf('?');
        return mark < 0 ? url : url.substring(0, mark);
    }

    private void assertNotFound(Long resultSeq) {
        assertThatThrownBy(() -> detail(resultSeq))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(MotifErrorCode.MOTIF_RESULT_NOT_FOUND);
    }

    private Long repMotifSeq(Integer userSeq) {
        Number seq = (Number) entityManager.createNativeQuery(
                        "select rep_motif_result_seq from motif_core.users where user_seq = :seq")
                .setParameter("seq", userSeq)
                .getSingleResult();
        return seq == null ? null : seq.longValue();
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

    private Integer newAsset() {
        String uuid = UUID.randomUUID().toString();
        return motifAssetRepository.saveAndFlush(MotifAsset.register(
                        artistSeq, "에셋 " + uuid.substring(0, 8), null,
                        "assets/" + uuid + ".mp4", "assets/" + uuid + "/thumb.jpg", 9_800,
                        AssetType.NORMAL))
                .getAssetSeq();
    }

    private Long newResult() {
        return newResult(fanSeq);
    }

    /** 결과물은 합성 잡이 끝날 때 서버가 만든다(§11.7) — 그 경로가 없어 행을 직접 넣는다. */
    private Long newResult(Integer userSeq) {
        UUID uuid = UUID.randomUUID();
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

    /** 대표 설정을 API 가 아니라 칼럼으로 심는다 — 남의 계정에 값을 넣어야 할 때 쓴다. */
    private void setRepMotif(Integer userSeq, Long resultSeq) {
        entityManager.createNativeQuery(
                        "update motif_core.users set rep_motif_result_seq = :resultSeq where user_seq = :userSeq")
                .setParameter("resultSeq", resultSeq)
                .setParameter("userSeq", userSeq)
                .executeUpdate();
        entityManager.clear();
    }
}
