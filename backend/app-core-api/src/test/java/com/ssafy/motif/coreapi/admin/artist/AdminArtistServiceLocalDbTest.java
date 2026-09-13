package com.ssafy.motif.coreapi.admin.artist;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.artist.dto.ArtistCreateRequest;
import com.ssafy.motif.coreapi.admin.artist.dto.ArtistCreateResponse;
import com.ssafy.motif.coreapi.admin.artist.dto.ArtistDetailResponse;
import com.ssafy.motif.coreapi.admin.artist.dto.ArtistSummaryResponse;
import com.ssafy.motif.coreapi.admin.artist.dto.ArtistUpdateRequest;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.domain.artist.ArtistErrorCode;
import com.ssafy.motif.coreapi.global.response.PageResponse;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.user.User;
import com.ssafy.motif.domain.core.user.UserRepository;
import com.ssafy.motif.domain.core.user.UserRole;
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
 * §3.5.1~§3.5.4 를 실제 DB에 대고 검증한다 (S15P21D104-385).
 *
 * <p>핵심 두 가지 — ① {@code users(ARTIST)} + {@code artists} 원자 생성
 * ② "그룹 소속 아티스트의 소속사 = 그룹 소속사" 불변식(등록 시 400, 수정 시 409).
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class AdminArtistServiceLocalDbTest {

    @Autowired
    private AdminArtistService adminArtistService;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private UserRepository userRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private AuthenticatedAdmin admin;
    private Integer companySeq;
    private Integer otherCompanySeq;
    private Integer groupSeq;

    @BeforeEach
    void setUp() {
        Integer adminUserSeq = newUser().getUserSeq();
        admin = new AuthenticatedAdmin(1, adminUserSeq, "운영자");
        companySeq = newCompany(adminUserSeq);
        otherCompanySeq = newCompany(adminUserSeq);
        groupSeq = newGroup(companySeq, adminUserSeq);
    }

    @Test
    @DisplayName("등록하면 users(ARTIST) 와 artists 가 같은 트랜잭션에 생긴다")
    void createMakesUserAndArtist() {
        ArtistCreateResponse response = adminArtistService.create(admin,
                new ArtistCreateRequest("카리나", groupSeq, companySeq, "aespa의 카리나입니다."));
        entityManager.flush();

        User user = userRepository.findById(response.userSeq()).orElseThrow();
        assertThat(user.getNickname()).isEqualTo("카리나");
        assertThat(user.getRole()).isEqualTo(UserRole.ARTIST);
        assertThat(artistRepository.findById(response.artistSeq()).orElseThrow())
                .satisfies(artist -> {
                    assertThat(artist.getUserSeq()).isEqualTo(response.userSeq());
                    assertThat(artist.getGroupSeq()).isEqualTo(groupSeq);
                    assertThat(artist.getCompanySeq()).isEqualTo(companySeq);
                    assertThat(artist.getRegUsrSeq()).isEqualTo(admin.userSeq());
                });
    }

    @Test
    @DisplayName("아티스트에게는 user_accounts 를 만들지 않는다 — 로그인하지 않는다")
    void createDoesNotMakeAccount() {
        ArtistCreateResponse response = adminArtistService.create(admin,
                new ArtistCreateRequest("카리나", null, companySeq, null));
        entityManager.flush();

        assertThat(((Number) entityManager.createNativeQuery(
                        "select count(*) from motif_core.user_accounts where user_seq = :seq")
                .setParameter("seq", response.userSeq())
                .getSingleResult()).longValue())
                .isZero();
    }

    @Test
    @DisplayName("그룹 소속사와 companySeq 가 다르면 400 — 아무것도 만들지 않는다")
    void companyMismatchIsRejected() {
        long before = artistRepository.count();

        assertThatThrownBy(() -> adminArtistService.create(admin,
                new ArtistCreateRequest("카리나", groupSeq, otherCompanySeq, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_PARAMETER);

        assertThat(artistRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("그룹만 주면 소속사는 그룹을 따라간다")
    void companyFollowsGroupWhenOmitted() {
        ArtistCreateResponse response = adminArtistService.create(admin,
                new ArtistCreateRequest("카리나", groupSeq, null, null));

        assertThat(artistRepository.findById(response.artistSeq()).orElseThrow().getCompanySeq())
                .isEqualTo(companySeq);
    }

    @Test
    @DisplayName("솔로인데 companySeq 가 없으면 400")
    void soloNeedsCompany() {
        assertThatThrownBy(() -> adminArtistService.create(admin,
                new ArtistCreateRequest("카리나", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_PARAMETER);
    }

    @Test
    @DisplayName("삭제된 그룹·소속사는 404")
    void deletedReferencesAreRejected() {
        entityManager.createNativeQuery(
                        "update motif_core.artist_groups set is_deleted = true where group_seq = :seq")
                .setParameter("seq", groupSeq)
                .executeUpdate();
        entityManager.clear();

        assertThatThrownBy(() -> adminArtistService.create(admin,
                new ArtistCreateRequest("카리나", groupSeq, companySeq, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.GROUP_NOT_FOUND);

        assertThatThrownBy(() -> adminArtistService.create(admin,
                new ArtistCreateRequest("카리나", null, 999_999, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.COMPANY_NOT_FOUND);
    }

    @Test
    @DisplayName("그룹 소속 아티스트의 소속사 단독 변경은 409 ARTIST_COMPANY_LOCKED")
    void companyOnlyChangeIsLockedWhileInGroup() {
        Integer artistSeq = adminArtistService.create(admin,
                new ArtistCreateRequest("카리나", groupSeq, companySeq, null)).artistSeq();

        ArtistUpdateRequest request = new ArtistUpdateRequest();
        request.setCompanySeq(otherCompanySeq);   // groupSeq 키 자체를 보내지 않았다

        assertThatThrownBy(() -> adminArtistService.update(admin, artistSeq, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.ARTIST_COMPANY_LOCKED);
    }

    @Test
    @DisplayName("groupSeq: null 과 함께 오면 솔로 전환이라 소속사가 바뀐다")
    void explicitNullGroupTurnsSolo() {
        Integer artistSeq = adminArtistService.create(admin,
                new ArtistCreateRequest("카리나", groupSeq, companySeq, null)).artistSeq();

        ArtistUpdateRequest request = new ArtistUpdateRequest();
        request.setGroupSeq(null);                // 키는 있고 값이 null — 솔로 전환
        request.setCompanySeq(otherCompanySeq);
        adminArtistService.update(admin, artistSeq, request);
        entityManager.flush();

        assertThat(artistRepository.findById(artistSeq).orElseThrow())
                .satisfies(artist -> {
                    assertThat(artist.getGroupSeq()).isNull();
                    assertThat(artist.getCompanySeq()).isEqualTo(otherCompanySeq);
                });
    }

    @Test
    @DisplayName("솔로 아티스트의 소속사 변경은 막지 않는다")
    void soloCompanyChangeIsAllowed() {
        Integer artistSeq = adminArtistService.create(admin,
                new ArtistCreateRequest("카리나", null, companySeq, null)).artistSeq();

        ArtistUpdateRequest request = new ArtistUpdateRequest();
        request.setCompanySeq(otherCompanySeq);
        adminArtistService.update(admin, artistSeq, request);
        entityManager.flush();

        assertThat(artistRepository.findById(artistSeq).orElseThrow().getCompanySeq())
                .isEqualTo(otherCompanySeq);
    }

    @Test
    @DisplayName("그룹 이동 시에도 소속사 불일치는 400 이다")
    void groupMoveChecksCompanyToo() {
        Integer artistSeq = adminArtistService.create(admin,
                new ArtistCreateRequest("카리나", null, companySeq, null)).artistSeq();

        ArtistUpdateRequest request = new ArtistUpdateRequest();
        request.setGroupSeq(groupSeq);
        request.setCompanySeq(otherCompanySeq);

        assertThatThrownBy(() -> adminArtistService.update(admin, artistSeq, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_PARAMETER);
    }

    @Test
    @DisplayName("이름 변경은 users.nickname 과 mod_usr_seq 를 갱신한다")
    void nameChangeUpdatesUser() {
        ArtistCreateResponse created = adminArtistService.create(admin,
                new ArtistCreateRequest("카리나", null, companySeq, null));

        ArtistUpdateRequest request = new ArtistUpdateRequest();
        request.setName("KARINA");
        adminArtistService.update(admin, created.artistSeq(), request);
        entityManager.flush();

        assertThat(userRepository.findById(created.userSeq()).orElseThrow())
                .satisfies(user -> {
                    assertThat(user.getNickname()).isEqualTo("KARINA");
                    assertThat(user.getModUsrSeq()).isEqualTo(admin.userSeq());
                });
    }

    @Test
    @DisplayName("목록은 이름 부분 일치로 걸러지고 그룹·소속사를 함께 준다")
    void listFiltersByName() {
        adminArtistService.create(admin, new ArtistCreateRequest("카리나", groupSeq, companySeq, null));
        adminArtistService.create(admin, new ArtistCreateRequest("윈터", groupSeq, companySeq, null));
        entityManager.flush();

        PageResponse<ArtistSummaryResponse> page =
                adminArtistService.list("카리", null, null, false, 0, 20);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.list()).singleElement().satisfies(item -> {
            assertThat(item.name()).isEqualTo("카리나");
            assertThat(item.group().groupSeq()).isEqualTo(groupSeq);
            assertThat(item.company().companySeq()).isEqualTo(companySeq);
            assertThat(item.challengeCount()).isZero();
        });
    }

    @Test
    @DisplayName("삭제된 아티스트는 기본 목록에서 빠지고 includeDeleted 로만 보인다")
    void deletedAreHiddenByDefault() {
        Integer artistSeq = adminArtistService.create(admin,
                new ArtistCreateRequest("카리나", null, companySeq, null)).artistSeq();
        entityManager.flush();
        entityManager.createNativeQuery(
                        "update motif_core.artists set is_deleted = true where artist_seq = :seq")
                .setParameter("seq", artistSeq)
                .executeUpdate();
        entityManager.clear();

        assertThat(adminArtistService.list("카리나", null, null, false, 0, 20).totalElements()).isZero();
        assertThat(adminArtistService.list("카리나", null, null, true, 0, 20).totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("솔로는 목록에서 group 이 null 이다 — left join")
    void soloHasNullGroup() {
        adminArtistService.create(admin, new ArtistCreateRequest("솔로가수", null, companySeq, null));
        entityManager.flush();

        assertThat(adminArtistService.list("솔로가수", null, null, false, 0, 20).list())
                .singleElement()
                .satisfies(item -> assertThat(item.group()).isNull());
    }

    @Test
    @DisplayName("상세는 패스 가격표와 최신 챌린지를 함께 준다")
    void detailIncludesPassPricesAndChallenges() {
        Integer artistSeq = adminArtistService.create(admin,
                new ArtistCreateRequest("카리나", groupSeq, companySeq, "소개")).artistSeq();
        entityManager.flush();

        ArtistDetailResponse detail = adminArtistService.detail(artistSeq);

        assertThat(detail.summary().name()).isEqualTo("카리나");
        assertThat(detail.bio()).isEqualTo("소개");
        assertThat(detail.passPrices()).isEmpty();
        assertThat(detail.recentChallenges()).isEmpty();
    }

    @Test
    @DisplayName("없는 아티스트는 404")
    void unknownArtistIsNotFound() {
        assertThatThrownBy(() -> adminArtistService.detail(999_999))
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

    private Integer newCompany(Integer regUsrSeq) {
        String name = "t-" + UUID.randomUUID().toString().substring(0, 20);
        entityManager.createNativeQuery(
                        "insert into motif_core.companies (company_name, reg_usr_seq) values (:name, :regUsrSeq)")
                .setParameter("name", name)
                .setParameter("regUsrSeq", regUsrSeq)
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery(
                        "select company_seq from motif_core.companies where company_name = :name")
                .setParameter("name", name)
                .getSingleResult()).intValue();
    }

    private Integer newGroup(Integer companySeq, Integer regUsrSeq) {
        String name = "g-" + UUID.randomUUID().toString().substring(0, 20);
        entityManager.createNativeQuery("""
                        insert into motif_core.artist_groups (company_seq, group_name, reg_usr_seq)
                        values (:companySeq, :name, :regUsrSeq)
                        """)
                .setParameter("companySeq", companySeq)
                .setParameter("name", name)
                .setParameter("regUsrSeq", regUsrSeq)
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery(
                        "select group_seq from motif_core.artist_groups where group_name = :name")
                .setParameter("name", name)
                .getSingleResult()).intValue();
    }
}
