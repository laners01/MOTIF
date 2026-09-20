package com.ssafy.motif.coreapi.admin.artist;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.alias.dto.AliasRequest;
import com.ssafy.motif.coreapi.admin.alias.dto.AliasResponse;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.domain.artist.ArtistErrorCode;
import com.ssafy.motif.domain.core.artist.Artist;
import com.ssafy.motif.domain.core.artist.ArtistAliasRepository;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.artist.Company;
import com.ssafy.motif.domain.core.artist.CompanyRepository;
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
 * 어드민 아티스트 별칭 CRUD (어드민 명세 §3.5.6~9) — S15P21D104-358.
 *
 * <p>핵심 넷 — ① 중복을 막지 <b>않는다</b> ② 남의 별칭은 {@code aliasSeq} 를 알아도 못 만진다
 * ③ 대상은 <b>비삭제만</b>이다 ④ 별칭 삭제는 하드지만 아티스트를 건드리지 않는다.
 *
 * <p><b>공유 로컬 DB 다.</b> 전체 건수에 기대지 않는다 — 내가 만든 아티스트의 별칭만 본다.
 *
 * <p>형식 검증({@code languageCode} 2자·{@code alias} 100자)은 Bean Validation 이라
 * 컨트롤러 단계다. 이 레포에 {@code @WebMvcTest} 선례가 없어 여기서는 다루지 않고
 * Swagger 스키마로 드러낸다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class AdminArtistAliasServiceLocalDbTest {

    @Autowired
    private AdminArtistAliasService adminArtistAliasService;
    @Autowired
    private ArtistAliasRepository artistAliasRepository;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private UserRepository userRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private AuthenticatedAdmin admin;
    private Integer companySeq;
    private Integer artistSeq;

    @BeforeEach
    void setUp() {
        User user = userRepository.saveAndFlush(User.signUp());
        user.applyInitialNickname();
        admin = new AuthenticatedAdmin(1, user.getUserSeq(), "운영자");
        companySeq = companyRepository.saveAndFlush(
                Company.register(unique("c"), admin.userSeq())).getCompanySeq();
        artistSeq = newArtist();
    }

    @Test
    @DisplayName("등록하면 201 seq 가 나오고 목록에 감사 칼럼과 함께 뜬다")
    void create() {
        Integer aliasSeq = adminArtistAliasService
                .create(admin, artistSeq, new AliasRequest("en", "Karina")).aliasSeq();
        entityManager.flush();

        assertThat(aliasSeq).isNotNull();
        assertThat(adminArtistAliasService.list(artistSeq).list())
                .singleElement()
                .satisfies(alias -> {
                    assertThat(alias.aliasSeq()).isEqualTo(aliasSeq);
                    assertThat(alias.languageCode()).isEqualTo("en");
                    assertThat(alias.alias()).isEqualTo("Karina");
                    assertThat(alias.regDttm()).isNotNull();
                });
        assertThat(artistAliasRepository.findById(aliasSeq).orElseThrow().getRegUsrSeq())
                .isEqualTo(admin.userSeq());
    }

    @Test
    @DisplayName("앞뒤 공백은 잘라서 저장한다")
    void aliasIsTrimmed() {
        adminArtistAliasService.create(admin, artistSeq, new AliasRequest("ko", "  카리나  "));
        entityManager.flush();

        assertThat(adminArtistAliasService.list(artistSeq).list())
                .singleElement()
                .extracting(AliasResponse::alias)
                .isEqualTo("카리나");
    }

    /**
     * ADR-0014 D3 의 회귀 방어 — DB 에 UNIQUE 가 없고 서비스도 막지 않는다.
     *
     * <p>"정리" 차원으로 중복 검사를 붙이면 한 언어에 표기 변형을 못 넣게 되는데
     * ({@code aespa}·{@code æspa}) 그게 곧 기능 손실이다.
     */
    @Test
    @DisplayName("같은 언어에 여러 개 — 완전히 같은 값도 409 가 아니다")
    void duplicatesAreAllowed() {
        adminArtistAliasService.create(admin, artistSeq, new AliasRequest("en", "aespa"));
        adminArtistAliasService.create(admin, artistSeq, new AliasRequest("en", "æspa"));
        adminArtistAliasService.create(admin, artistSeq, new AliasRequest("en", "aespa"));
        entityManager.flush();

        assertThat(adminArtistAliasService.list(artistSeq).list())
                .extracting(AliasResponse::alias)
                .containsExactly("aespa", "æspa", "aespa");
    }

    @Test
    @DisplayName("수정은 전체 치환 — 두 칸이 함께 바뀐다")
    void update() {
        Integer aliasSeq = adminArtistAliasService
                .create(admin, artistSeq, new AliasRequest("en", "Karina")).aliasSeq();

        AliasResponse updated = adminArtistAliasService
                .update(admin, artistSeq, aliasSeq, new AliasRequest("ja", "カリナ"));
        entityManager.flush();

        assertThat(updated.languageCode()).isEqualTo("ja");
        assertThat(updated.alias()).isEqualTo("カリナ");
        assertThat(artistAliasRepository.findById(aliasSeq).orElseThrow().getModDttm()).isNotNull();
    }

    @Test
    @DisplayName("삭제는 하드 — 행이 사라지고 아티스트는 그대로다")
    void deleteIsHard() {
        Integer aliasSeq = adminArtistAliasService
                .create(admin, artistSeq, new AliasRequest("ko", "카리나")).aliasSeq();

        adminArtistAliasService.delete(admin, artistSeq, aliasSeq);
        entityManager.flush();

        assertThat(artistAliasRepository.findById(aliasSeq)).isEmpty();
        assertThat(adminArtistAliasService.list(artistSeq).list()).isEmpty();
        assertThat(artistRepository.findByArtistSeqAndDeletedFalse(artistSeq)).isPresent();
    }

    @Test
    @DisplayName("남의 별칭은 aliasSeq 를 알아도 수정·삭제할 수 없다 — 404")
    void otherArtistsAliasIsHidden() {
        Integer aliasSeq = adminArtistAliasService
                .create(admin, artistSeq, new AliasRequest("ko", "카리나")).aliasSeq();
        Integer otherSeq = newArtist();
        entityManager.flush();

        assertThatThrownBy(() -> adminArtistAliasService
                .update(admin, otherSeq, aliasSeq, new AliasRequest("ko", "윈터")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.ALIAS_NOT_FOUND);
        assertThatThrownBy(() -> adminArtistAliasService.delete(admin, otherSeq, aliasSeq))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.ALIAS_NOT_FOUND);

        assertThat(artistAliasRepository.findById(aliasSeq)).isPresent();   // 남아 있다
    }

    @Test
    @DisplayName("없는 별칭은 404 ALIAS_NOT_FOUND")
    void unknownAliasIsNotFound() {
        assertThatThrownBy(() -> adminArtistAliasService.delete(admin, artistSeq, 999_999))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.ALIAS_NOT_FOUND);
    }

    /**
     * 대상은 비삭제만이다(2026-09-21 확정).
     *
     * <p>별칭 행 자체는 소프트 삭제를 따라가지 않으므로 DB 에 남아 있다(DB §41) — 지워지지
     * 않았다는 것까지 확인한다. 복구하면 검색이 그대로 살아난다.
     */
    @Test
    @DisplayName("삭제된 아티스트는 등록·목록 모두 404 지만 기존 별칭 행은 남아 있다")
    void deletedArtistIsNotFound() {
        Integer aliasSeq = adminArtistAliasService
                .create(admin, artistSeq, new AliasRequest("ko", "카리나")).aliasSeq();
        softDeleteArtist(artistSeq);

        assertThatThrownBy(() -> adminArtistAliasService.list(artistSeq))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ArtistErrorCode.ARTIST_NOT_FOUND);
        assertThatThrownBy(() -> adminArtistAliasService
                .create(admin, artistSeq, new AliasRequest("en", "Karina")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ArtistErrorCode.ARTIST_NOT_FOUND);

        assertThat(artistAliasRepository.findById(aliasSeq)).isPresent();
    }

    @Test
    @DisplayName("없는 아티스트는 404 ARTIST_NOT_FOUND")
    void unknownArtistIsNotFound() {
        assertThatThrownBy(() -> adminArtistAliasService.list(999_999))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ArtistErrorCode.ARTIST_NOT_FOUND);
    }

    // --- 픽스처 -------------------------------------------------------------

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 18);
    }

    private Integer newArtist() {
        User user = userRepository.save(User.createArtist(unique("a")));
        return artistRepository.save(Artist.register(
                user.getUserSeq(), null, companySeq, null, null, admin.userSeq())).getArtistSeq();
    }

    private void softDeleteArtist(Integer seq) {
        entityManager.flush();
        entityManager.createNativeQuery(
                        "update motif_core.artists set is_deleted = true where artist_seq = :seq")
                .setParameter("seq", seq)
                .executeUpdate();
        entityManager.clear();
    }
}
