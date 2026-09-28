package com.ssafy.motif.coreapi.admin.org;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.admin.org.dto.CompanyNameRequest;
import com.ssafy.motif.coreapi.admin.org.dto.CompanySummaryResponse;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.domain.core.artist.Artist;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §3.3 소속사 CRUD 를 실제 DB에 대고 검증한다 (S15P21D104-383).
 *
 * <p>핵심 둘 — ① 활성 회사명 부분 UNIQUE(삭제분은 이름을 다시 쓸 수 있다)
 * ② 비삭제 그룹·아티스트가 참조 중이면 삭제가 409 다.
 *
 * <p><b>공유 로컬 DB 다.</b> 세 레인이 같은 compose DB 를 쓰므로 전체 건수에 기대지 않는다 —
 * 회사명에 UUID 를 박아 내가 만든 행만 골라 보고, 전체를 봐야 하는 곳(검색어 없는 목록)은
 * <b>증분</b>으로 본다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class AdminCompanyServiceLocalDbTest {

    @Autowired
    private AdminCompanyService adminCompanyService;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private UserRepository userRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private AuthenticatedAdmin admin;

    @BeforeEach
    void setUp() {
        User user = userRepository.saveAndFlush(User.signUp());
        user.applyInitialNickname();
        admin = new AuthenticatedAdmin(1, user.getUserSeq(), "운영자");
    }

    @Test
    @DisplayName("등록하면 감사 칼럼이 채워지고 목록에 참조 수 0 으로 뜬다")
    void create() {
        String name = uniqueName("c");

        Integer companySeq = newCompany(name);
        entityManager.flush();

        assertThat(companyRepository.findById(companySeq).orElseThrow())
                .satisfies(company -> {
                    assertThat(company.getCompanyName()).isEqualTo(name);
                    assertThat(company.getRegUsrSeq()).isEqualTo(admin.userSeq());
                    assertThat(company.isDeleted()).isFalse();
                    assertThat(company.getRegDttm()).isNotNull();
                });
        assertThat(only(name)).satisfies(item -> {
            assertThat(item.groupCount()).isZero();
            assertThat(item.artistCount()).isZero();
            assertThat(item.isDeleted()).isFalse();
        });
    }

    @Test
    @DisplayName("활성 회사명 중복은 409 COMPANY_NAME_DUPLICATE")
    void duplicateActiveNameIsRejected() {
        String name = uniqueName("c");
        newCompany(name);

        assertThatThrownBy(() -> newCompany(name))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.COMPANY_NAME_DUPLICATE);
    }

    @Test
    @DisplayName("삭제된 회사명은 다시 쓸 수 있다 — UNIQUE 가 부분 인덱스다")
    void deletedNameCanBeReused() {
        String name = uniqueName("c");
        adminCompanyService.delete(admin, newCompany(name));

        Integer reused = newCompany(name);
        entityManager.flush();

        assertThat(companyRepository.findById(reused).orElseThrow().isDeleted()).isFalse();
        assertThat(adminCompanyService.list(name, true, 0, 20).totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("이름을 그대로 둔 수정은 중복이 아니다 — 자기 자신은 제외한다")
    void renameToSameNameIsAllowed() {
        String name = uniqueName("c");
        Integer companySeq = newCompany(name);

        CompanySummaryResponse updated =
                adminCompanyService.update(admin, companySeq, new CompanyNameRequest(name));

        assertThat(updated.companyName()).isEqualTo(name);
    }

    @Test
    @DisplayName("다른 활성 회사의 이름으로 바꾸면 409 이고 mod_usr_seq 도 남지 않는다")
    void renameToTakenNameIsRejected() {
        String taken = uniqueName("c");
        newCompany(taken);
        Integer companySeq = newCompany(uniqueName("c"));

        assertThatThrownBy(() ->
                adminCompanyService.update(admin, companySeq, new CompanyNameRequest(taken)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.COMPANY_NAME_DUPLICATE);

        assertThat(companyRepository.findById(companySeq).orElseThrow().getModUsrSeq()).isNull();
    }

    @Test
    @DisplayName("수정하면 회사명과 mod_usr_seq 가 바뀐다")
    void rename() {
        Integer companySeq = newCompany(uniqueName("c"));
        String renamed = uniqueName("c");

        CompanySummaryResponse updated =
                adminCompanyService.update(admin, companySeq, new CompanyNameRequest(renamed));
        entityManager.flush();

        assertThat(updated.companyName()).isEqualTo(renamed);
        assertThat(companyRepository.findById(companySeq).orElseThrow().getModUsrSeq())
                .isEqualTo(admin.userSeq());
    }

    @Test
    @DisplayName("참조가 없으면 소프트 삭제 — 행은 남고 기본 목록에서만 빠진다")
    void deleteIsSoft() {
        String name = uniqueName("c");
        Integer companySeq = newCompany(name);

        adminCompanyService.delete(admin, companySeq);
        entityManager.flush();

        assertThat(companyRepository.findById(companySeq).orElseThrow())
                .satisfies(company -> {
                    assertThat(company.isDeleted()).isTrue();
                    assertThat(company.getModUsrSeq()).isEqualTo(admin.userSeq());
                });
        assertThat(adminCompanyService.list(name, false, 0, 20).totalElements()).isZero();
        assertThat(adminCompanyService.list(name, true, 0, 20).totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("그룹이 참조 중이면 409 COMPANY_IN_USE 이고 data 에 참조 건수가 실린다")
    void deleteBlockedByGroup() {
        Integer companySeq = newCompany(uniqueName("c"));
        newGroup(companySeq);

        assertThatThrownBy(() -> adminCompanyService.delete(admin, companySeq))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException ex = (BusinessException) e;
                    assertThat(ex.getErrorCode()).isEqualTo(AdminErrorCode.COMPANY_IN_USE);
                    assertThat(ex.getData()).isEqualTo(Map.of("groupCount", 1L, "artistCount", 0L));
                });
        assertThat(companyRepository.findById(companySeq).orElseThrow().isDeleted()).isFalse();
    }

    @Test
    @DisplayName("솔로 아티스트가 참조 중이어도 409 — 그룹 수가 0 이어도 막는다")
    void deleteBlockedBySoloArtist() {
        Integer companySeq = newCompany(uniqueName("c"));
        newArtist(companySeq);

        assertThatThrownBy(() -> adminCompanyService.delete(admin, companySeq))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getData())
                        .isEqualTo(Map.of("groupCount", 0L, "artistCount", 1L)));
    }

    @Test
    @DisplayName("삭제된 아티스트는 참조로 세지 않는다 — 비삭제만 본다")
    void deletedArtistDoesNotBlock() {
        Integer companySeq = newCompany(uniqueName("c"));
        softDeleteArtist(newArtist(companySeq));

        adminCompanyService.delete(admin, companySeq);
        entityManager.flush();

        assertThat(companyRepository.findById(companySeq).orElseThrow().isDeleted()).isTrue();
    }

    @Test
    @DisplayName("없는·이미 삭제된 소속사는 수정·삭제 모두 404")
    void unknownIsNotFound() {
        Integer companySeq = newCompany(uniqueName("c"));
        adminCompanyService.delete(admin, companySeq);

        assertThatThrownBy(() -> adminCompanyService.delete(admin, companySeq))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.COMPANY_NOT_FOUND);
        assertThatThrownBy(() ->
                adminCompanyService.update(admin, 999_999, new CompanyNameRequest(uniqueName("c"))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.COMPANY_NOT_FOUND);
    }

    @Test
    @DisplayName("목록은 그룹·아티스트 참조 수를 비삭제 기준으로 센다")
    void listCountsReferences() {
        String name = uniqueName("c");
        Integer companySeq = newCompany(name);
        Integer groupSeq = newGroup(companySeq);
        newArtist(companySeq, groupSeq);
        newArtist(companySeq);
        softDeleteArtist(newArtist(companySeq));
        entityManager.flush();

        assertThat(only(name)).satisfies(item -> {
            assertThat(item.groupCount()).isEqualTo(1);
            assertThat(item.artistCount()).isEqualTo(2);   // 삭제분 1건은 빠진다
        });
    }

    /**
     * 2026-09-13 운영 500 회귀 방어 — 385·389 가 각각 배포 후에야 만난 함정이다.
     *
     * <p>목록 테스트가 전부 검색어를 넘기면 {@code q} 없는 경로가 한 번도 실행되지 않는다.
     * 그 경로에 타입 없는 {@code null} 이 들어가면 Postgres 가 {@code bytea} 로 추론해
     * {@code function lower(bytea) does not exist} 로 죽는다.
     */
    @Test
    @DisplayName("검색어 없는 목록 — null·공백 모두 전체 조회다")
    void listWithoutQuery() {
        String mine = uniqueName("c");
        String alsoMine = uniqueName("c");
        newCompany(mine);
        newCompany(alsoMine);
        entityManager.flush();

        // 공유 DB 다 — 증분조차 남의 커밋에 깨진다(실제로 깨졌다). 내가 만든 행이
        // 검색어 없는 조회에 섞여 나오는지만 본다. 정렬이 companySeq DESC 라 맨 앞이다.
        for (String q : new String[]{null, "   "}) {
            assertThat(adminCompanyService.list(q, false, 0, 100).list())
                    .extracting(CompanySummaryResponse::companyName)
                    .contains(mine, alsoMine);
        }
    }

    // --- 픽스처 -------------------------------------------------------------

    /** 공유 DB 라 이름이 겹치면 안 된다 — 회사명은 UNIQUE 라 특히 그렇다(칼럼 50자). */
    private static String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 18);
    }

    private Integer newCompany(String name) {
        return adminCompanyService.create(admin, new CompanyNameRequest(name)).companySeq();
    }

    /** 그룹 등록 API 는 §3.4(384) 다 — 참조를 만들기만 하면 되므로 행을 직접 넣는다. */
    private Integer newGroup(Integer companySeq) {
        String name = uniqueName("g");
        entityManager.flush();
        entityManager.createNativeQuery("""
                        insert into motif_core.artist_groups (company_seq, group_name, reg_usr_seq)
                        values (:companySeq, :name, :regUsrSeq)
                        """)
                .setParameter("companySeq", companySeq)
                .setParameter("name", name)
                .setParameter("regUsrSeq", admin.userSeq())
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery(
                        "select group_seq from motif_core.artist_groups where group_name = :name")
                .setParameter("name", name)
                .getSingleResult()).intValue();
    }

    private Integer newArtist(Integer companySeq) {
        return newArtist(companySeq, null);
    }

    private Integer newArtist(Integer companySeq, Integer groupSeq) {
        User user = userRepository.save(User.createArtist(uniqueName("a")));
        return artistRepository.save(Artist.register(
                user.getUserSeq(), groupSeq, companySeq, null, null, admin.userSeq())).getArtistSeq();
    }

    private void softDeleteArtist(Integer artistSeq) {
        entityManager.flush();
        entityManager.createNativeQuery(
                        "update motif_core.artists set is_deleted = true where artist_seq = :seq")
                .setParameter("seq", artistSeq)
                .executeUpdate();
        entityManager.clear();
    }

    private CompanySummaryResponse only(String name) {
        PageResponse<CompanySummaryResponse> page = adminCompanyService.list(name, true, 0, 20);
        assertThat(page.list()).hasSize(1);
        return page.list().getFirst();
    }
}
