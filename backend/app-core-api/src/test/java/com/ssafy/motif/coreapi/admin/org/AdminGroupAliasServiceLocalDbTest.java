package com.ssafy.motif.coreapi.admin.org;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.alias.dto.AliasRequest;
import com.ssafy.motif.coreapi.admin.alias.dto.AliasResponse;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.domain.core.artist.ArtistGroup;
import com.ssafy.motif.domain.core.artist.ArtistGroupRepository;
import com.ssafy.motif.domain.core.artist.Company;
import com.ssafy.motif.domain.core.artist.CompanyRepository;
import com.ssafy.motif.domain.core.artist.GroupAliasRepository;
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
 * 어드민 그룹 별칭 CRUD (어드민 명세 §3.4.5~8) — S15P21D104-358.
 *
 * <p>계약이 아티스트 별칭(§3.5.6~9)과 같아 검증도 같은 넷이다 — 중복 허용, 소유 확인,
 * 비삭제만, 하드 삭제. 두 서비스가 <b>별개 클래스</b>라 한쪽만 규칙이 어긋날 수 있어
 * 그대로 다시 본다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class AdminGroupAliasServiceLocalDbTest {

    @Autowired
    private AdminGroupAliasService adminGroupAliasService;
    @Autowired
    private GroupAliasRepository groupAliasRepository;
    @Autowired
    private ArtistGroupRepository artistGroupRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private UserRepository userRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private AuthenticatedAdmin admin;
    private Integer companySeq;
    private Integer groupSeq;

    @BeforeEach
    void setUp() {
        User user = userRepository.saveAndFlush(User.signUp());
        user.applyInitialNickname();
        admin = new AuthenticatedAdmin(1, user.getUserSeq(), "운영자");
        companySeq = companyRepository.saveAndFlush(
                Company.register(unique("c"), admin.userSeq())).getCompanySeq();
        groupSeq = newGroup();
    }

    @Test
    @DisplayName("등록·목록·수정·삭제 한 바퀴")
    void crud() {
        Integer aliasSeq = adminGroupAliasService
                .create(admin, groupSeq, new AliasRequest("en", " aespa ")).aliasSeq();
        entityManager.flush();

        assertThat(adminGroupAliasService.list(groupSeq).list())
                .singleElement()
                .satisfies(alias -> {
                    assertThat(alias.languageCode()).isEqualTo("en");
                    assertThat(alias.alias()).isEqualTo("aespa");   // trim 됐다
                });

        adminGroupAliasService.update(admin, groupSeq, aliasSeq, new AliasRequest("ko", "에스파"));
        entityManager.flush();
        assertThat(adminGroupAliasService.list(groupSeq).list())
                .singleElement()
                .extracting(AliasResponse::alias)
                .isEqualTo("에스파");

        adminGroupAliasService.delete(admin, groupSeq, aliasSeq);
        entityManager.flush();
        assertThat(groupAliasRepository.findById(aliasSeq)).isEmpty();
        assertThat(artistGroupRepository.findByGroupSeqAndDeletedFalse(groupSeq)).isPresent();
    }

    @Test
    @DisplayName("같은 언어에 여러 개 — 중복도 막지 않는다")
    void duplicatesAreAllowed() {
        adminGroupAliasService.create(admin, groupSeq, new AliasRequest("en", "aespa"));
        adminGroupAliasService.create(admin, groupSeq, new AliasRequest("en", "æspa"));
        entityManager.flush();

        assertThat(adminGroupAliasService.list(groupSeq).list()).hasSize(2);
    }

    @Test
    @DisplayName("남의 별칭은 aliasSeq 를 알아도 수정·삭제할 수 없다 — 404")
    void otherGroupsAliasIsHidden() {
        Integer aliasSeq = adminGroupAliasService
                .create(admin, groupSeq, new AliasRequest("en", "aespa")).aliasSeq();
        Integer otherSeq = newGroup();
        entityManager.flush();

        assertThatThrownBy(() -> adminGroupAliasService
                .update(admin, otherSeq, aliasSeq, new AliasRequest("en", "NCT")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.ALIAS_NOT_FOUND);

        assertThat(groupAliasRepository.findById(aliasSeq)).isPresent();
    }

    @Test
    @DisplayName("삭제된 그룹은 등록·목록 모두 404 지만 기존 별칭 행은 남아 있다")
    void deletedGroupIsNotFound() {
        Integer aliasSeq = adminGroupAliasService
                .create(admin, groupSeq, new AliasRequest("en", "aespa")).aliasSeq();
        softDeleteGroup(groupSeq);

        assertThatThrownBy(() -> adminGroupAliasService.list(groupSeq))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.GROUP_NOT_FOUND);

        assertThat(groupAliasRepository.findById(aliasSeq)).isPresent();
    }

    @Test
    @DisplayName("없는 그룹은 404 GROUP_NOT_FOUND")
    void unknownGroupIsNotFound() {
        assertThatThrownBy(() -> adminGroupAliasService.list(999_999))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.GROUP_NOT_FOUND);
    }

    // --- 픽스처 -------------------------------------------------------------

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 18);
    }

    private Integer newGroup() {
        return artistGroupRepository.save(
                ArtistGroup.register(companySeq, unique("g"), admin.userSeq())).getGroupSeq();
    }

    private void softDeleteGroup(Integer seq) {
        entityManager.flush();
        entityManager.createNativeQuery(
                        "update motif_core.artist_groups set is_deleted = true where group_seq = :seq")
                .setParameter("seq", seq)
                .executeUpdate();
        entityManager.clear();
    }
}
