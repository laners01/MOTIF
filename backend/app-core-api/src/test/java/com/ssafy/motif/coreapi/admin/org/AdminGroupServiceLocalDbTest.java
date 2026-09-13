package com.ssafy.motif.coreapi.admin.org;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.admin.org.dto.CompanyNameRequest;
import com.ssafy.motif.coreapi.admin.org.dto.GroupCreateRequest;
import com.ssafy.motif.coreapi.admin.org.dto.GroupSummaryResponse;
import com.ssafy.motif.coreapi.admin.org.dto.GroupUpdateRequest;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.domain.core.artist.Artist;
import com.ssafy.motif.domain.core.artist.ArtistGroupRepository;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
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
 * §3.4 그룹 CRUD 를 실제 DB에 대고 검증한다 (S15P21D104-384).
 *
 * <p>핵심은 <b>"그룹 소속 아티스트의 소속사 = 그룹 소속사"</b> 불변식이다 — 그룹의 소속사를
 * 옮기면 비삭제 멤버가 같은 트랜잭션에서 따라 옮겨져야 한다(DB §8).
 *
 * <p><b>공유 로컬 DB 다.</b> 그룹명·회사명에 UUID 를 박아 내가 만든 행만 골라 보고, 전체를
 * 봐야 하는 곳(검색어 없는 목록)은 <b>증분</b>으로 본다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class AdminGroupServiceLocalDbTest {

    @Autowired
    private AdminGroupService adminGroupService;
    @Autowired
    private AdminCompanyService adminCompanyService;
    @Autowired
    private ArtistGroupRepository artistGroupRepository;
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
    @DisplayName("등록하면 감사 칼럼이 채워지고 소속사를 함께 준다. 그룹명 중복은 허용한다")
    void create() {
        Integer companySeq = newCompany();
        String name = uniqueName("g");

        Integer groupSeq = newGroup(companySeq, name);
        newGroup(companySeq, name);           // 같은 이름 두 번 — UNIQUE 가 없다
        entityManager.flush();

        assertThat(artistGroupRepository.findById(groupSeq).orElseThrow())
                .satisfies(group -> {
                    assertThat(group.getRegUsrSeq()).isEqualTo(admin.userSeq());
                    assertThat(group.getRegDttm()).isNotNull();
                    assertThat(group.isDeleted()).isFalse();
                });
        PageResponse<GroupSummaryResponse> page = adminGroupService.list(name, null, false, 0, 20);
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.list()).allSatisfy(item -> {
            assertThat(item.company().companySeq()).isEqualTo(companySeq);
            assertThat(item.memberCount()).isZero();
        });
    }

    @Test
    @DisplayName("없는·삭제된 소속사로는 그룹을 만들 수 없다 — 404 COMPANY_NOT_FOUND")
    void createNeedsLiveCompany() {
        Integer companySeq = newCompany();
        adminCompanyService.delete(admin, companySeq);

        assertThatThrownBy(() -> newGroup(companySeq, uniqueName("g")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.COMPANY_NOT_FOUND);
    }

    @Test
    @DisplayName("그룹명만 수정하면 소속사는 그대로다")
    void renameOnly() {
        Integer companySeq = newCompany();
        Integer groupSeq = newGroup(companySeq, uniqueName("g"));
        String renamed = uniqueName("g");

        GroupSummaryResponse updated =
                adminGroupService.update(admin, groupSeq, new GroupUpdateRequest(renamed, null));

        assertThat(updated.groupName()).isEqualTo(renamed);
        assertThat(updated.company().companySeq()).isEqualTo(companySeq);
    }

    /** §3.4.3 · DB §8 — 이 티켓의 핵심 불변식이다. */
    @Test
    @DisplayName("소속사를 바꾸면 비삭제 멤버의 company_seq 가 같은 트랜잭션에서 따라 옮겨진다")
    void changingCompanyMovesMembers() {
        Integer from = newCompany();
        Integer to = newCompany();
        Integer groupSeq = newGroup(from, uniqueName("g"));
        Integer memberSeq = newArtist(from, groupSeq);
        Integer otherSeq = newArtist(from, null);        // 같은 소속사지만 그룹 밖 — 남아야 한다
        entityManager.flush();

        GroupSummaryResponse updated =
                adminGroupService.update(admin, groupSeq, new GroupUpdateRequest(null, to));

        assertThat(updated.company().companySeq()).isEqualTo(to);
        assertThat(artistGroupRepository.findById(groupSeq).orElseThrow().getCompanySeq()).isEqualTo(to);
        assertThat(artistRepository.findById(memberSeq).orElseThrow().getCompanySeq()).isEqualTo(to);
        assertThat(artistRepository.findById(otherSeq).orElseThrow().getCompanySeq()).isEqualTo(from);
    }

    @Test
    @DisplayName("이름과 소속사를 같이 바꿔도 둘 다 반영된다 — 벌크 UPDATE 가 이름 변경을 삼키지 않는다")
    void renameAndMoveTogether() {
        Integer from = newCompany();
        Integer to = newCompany();
        Integer groupSeq = newGroup(from, uniqueName("g"));
        Integer memberSeq = newArtist(from, groupSeq);
        String renamed = uniqueName("g");
        entityManager.flush();

        GroupSummaryResponse updated =
                adminGroupService.update(admin, groupSeq, new GroupUpdateRequest(renamed, to));

        // flushAutomatically 가 없으면 clearAutomatically 가 이름 변경을 통째로 버린다
        assertThat(updated.groupName()).isEqualTo(renamed);
        assertThat(artistGroupRepository.findById(groupSeq).orElseThrow())
                .satisfies(group -> {
                    assertThat(group.getGroupName()).isEqualTo(renamed);
                    assertThat(group.getCompanySeq()).isEqualTo(to);
                });
        assertThat(artistRepository.findById(memberSeq).orElseThrow().getCompanySeq()).isEqualTo(to);
    }

    @Test
    @DisplayName("삭제된 멤버는 따라 옮기지 않는다")
    void deletedMembersAreNotMoved() {
        Integer from = newCompany();
        Integer to = newCompany();
        Integer groupSeq = newGroup(from, uniqueName("g"));
        Integer goneSeq = newArtist(from, groupSeq);
        softDeleteArtist(goneSeq);

        adminGroupService.update(admin, groupSeq, new GroupUpdateRequest(null, to));

        assertThat(artistRepository.findById(goneSeq).orElseThrow().getCompanySeq()).isEqualTo(from);
    }

    @Test
    @DisplayName("그룹을 없는 소속사로 옮기면 404 이고 멤버도 그대로다")
    void moveToUnknownCompanyIsRejected() {
        Integer from = newCompany();
        Integer groupSeq = newGroup(from, uniqueName("g"));
        Integer memberSeq = newArtist(from, groupSeq);
        entityManager.flush();

        assertThatThrownBy(() ->
                adminGroupService.update(admin, groupSeq, new GroupUpdateRequest(null, 999_999)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.COMPANY_NOT_FOUND);
        assertThat(artistRepository.findById(memberSeq).orElseThrow().getCompanySeq()).isEqualTo(from);
    }

    @Test
    @DisplayName("멤버가 없으면 소프트 삭제")
    void deleteIsSoft() {
        String name = uniqueName("g");
        Integer groupSeq = newGroup(newCompany(), name);

        adminGroupService.delete(admin, groupSeq);
        entityManager.flush();

        assertThat(artistGroupRepository.findById(groupSeq).orElseThrow())
                .satisfies(group -> {
                    assertThat(group.isDeleted()).isTrue();
                    assertThat(group.getModUsrSeq()).isEqualTo(admin.userSeq());
                });
        assertThat(adminGroupService.list(name, null, false, 0, 20).totalElements()).isZero();
        assertThat(adminGroupService.list(name, null, true, 0, 20).totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("비삭제 멤버가 있으면 409 GROUP_IN_USE 이고 data 에 memberCount 가 실린다")
    void deleteBlockedByMember() {
        Integer companySeq = newCompany();
        Integer groupSeq = newGroup(companySeq, uniqueName("g"));
        newArtist(companySeq, groupSeq);

        assertThatThrownBy(() -> adminGroupService.delete(admin, groupSeq))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException ex = (BusinessException) e;
                    assertThat(ex.getErrorCode()).isEqualTo(AdminErrorCode.GROUP_IN_USE);
                    assertThat(ex.getData()).isEqualTo(Map.of("memberCount", 1L));
                });
        assertThat(artistGroupRepository.findById(groupSeq).orElseThrow().isDeleted()).isFalse();
    }

    @Test
    @DisplayName("없는·이미 삭제된 그룹은 수정·삭제 모두 404")
    void unknownIsNotFound() {
        Integer groupSeq = newGroup(newCompany(), uniqueName("g"));
        adminGroupService.delete(admin, groupSeq);

        assertThatThrownBy(() -> adminGroupService.delete(admin, groupSeq))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.GROUP_NOT_FOUND);
        assertThatThrownBy(() ->
                adminGroupService.update(admin, 999_999, new GroupUpdateRequest("x", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.GROUP_NOT_FOUND);
    }

    @Test
    @DisplayName("목록은 멤버 수를 비삭제 기준으로 세고 companySeq 로 걸러진다")
    void listCountsAndFilters() {
        Integer mine = newCompany();
        Integer other = newCompany();
        String name = uniqueName("g");
        Integer groupSeq = newGroup(mine, name);
        newGroup(other, name);
        newArtist(mine, groupSeq);
        softDeleteArtist(newArtist(mine, groupSeq));
        entityManager.flush();

        assertThat(adminGroupService.list(name, mine, false, 0, 20).list())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.company().companySeq()).isEqualTo(mine);
                    assertThat(item.memberCount()).isEqualTo(1);   // 삭제분은 빠진다
                });
    }

    /** 소속사 목록과 같은 회귀 방어다 — 목록 API 두 개가 각각 이 경로를 갖는다. */
    @Test
    @DisplayName("검색어 없는 목록 — null·공백 모두 전체 조회다")
    void listWithoutQuery() {
        Integer companySeq = newCompany();
        String mine = uniqueName("g");
        String alsoMine = uniqueName("g");
        newGroup(companySeq, mine);
        newGroup(companySeq, alsoMine);
        entityManager.flush();

        // 공유 DB 다 — 증분조차 남의 커밋에 깨진다. 내가 만든 행이 검색어 없는 조회에
        // 섞여 나오는지만 본다. 정렬이 groupSeq DESC 라 맨 앞이다.
        for (String q : new String[]{null, "   "}) {
            assertThat(adminGroupService.list(q, null, false, 0, 100).list())
                    .extracting(GroupSummaryResponse::groupName)
                    .contains(mine, alsoMine);
        }
        // 소속사 필터와 함께여도 검색어 없는 경로는 같다 — 이쪽은 내 소속사라 건수가 확정이다
        assertThat(adminGroupService.list(null, companySeq, false, 0, 20).totalElements())
                .isEqualTo(2);
    }

    // --- 픽스처 -------------------------------------------------------------

    /** 공유 DB 라 이름이 겹치면 안 된다 — 회사명은 UNIQUE 라 특히 그렇다(칼럼 50자). */
    private static String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 18);
    }

    private Integer newCompany() {
        return adminCompanyService.create(admin, new CompanyNameRequest(uniqueName("c"))).companySeq();
    }

    private Integer newGroup(Integer companySeq, String name) {
        return adminGroupService.create(admin, new GroupCreateRequest(companySeq, name)).groupSeq();
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
}
