package com.ssafy.motif.coreapi.admin.artist;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.artist.dto.ArtistCreateRequest;
import com.ssafy.motif.coreapi.admin.artist.dto.ArtistCreateResponse;
import com.ssafy.motif.coreapi.admin.artist.dto.ArtistDeleteResponse;
import com.ssafy.motif.coreapi.admin.artist.dto.ArtistDetailResponse;
import com.ssafy.motif.coreapi.admin.artist.dto.ArtistSummaryResponse;
import com.ssafy.motif.coreapi.admin.artist.dto.ArtistUpdateRequest;
import com.ssafy.motif.coreapi.admin.media.AdminMediaService;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.domain.artist.ArtistErrorCode;
import com.ssafy.motif.coreapi.domain.media.MediaErrorCode;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.global.response.PageResponses;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.asset.AssetType;
import com.ssafy.motif.domain.core.asset.MotifAsset;
import com.ssafy.motif.domain.core.asset.MotifAssetPrice;
import com.ssafy.motif.domain.core.asset.MotifAssetPriceRepository;
import com.ssafy.motif.domain.core.asset.MotifAssetRepository;
import com.ssafy.motif.domain.core.pass.MotifPassPrice;
import com.ssafy.motif.domain.core.pass.MotifPassPriceRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * §3.5 를 실제 DB에 대고 검증한다 (S15P21D104-385·558).
 *
 * <p>핵심 세 가지 — ① {@code users(ARTIST)} + {@code artists} 원자 생성
 * ② "그룹 소속 아티스트의 소속사 = 그룹 소속사" 불변식(등록 시 400, 수정 시 409)
 * ③ 삭제 연쇄(§3.5.5, S15P21D104-558) — 에셋·가격까지 같은 트랜잭션에 꺼진다.
 *
 * <p><b>로컬 DB 는 여러 세션이 공유한다.</b> 단언은 이 테스트가 만든 seq 로만 좁힌다 —
 * 전체 건수에 기대면 남의 데이터로 깨진다.
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
    @Autowired
    private MotifAssetRepository motifAssetRepository;
    @Autowired
    private MotifAssetPriceRepository motifAssetPriceRepository;
    @Autowired
    private MotifPassPriceRepository motifPassPriceRepository;
    @PersistenceContext
    private EntityManager entityManager;

    /** HEAD 는 실제 S3 왕복이라 버킷·자격증명이 필요해진다 — 여기서 볼 것은 키의 저장 경로다. */
    @MockitoBean
    private AdminMediaService adminMediaService;

    private AuthenticatedAdmin admin;
    private Integer companySeq;
    private Integer otherCompanySeq;
    private Integer groupSeq;

    @BeforeEach
    void setUp() {
        given(adminMediaService.exists(anyString())).willReturn(true);
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
                new ArtistCreateRequest("카리나", groupSeq, companySeq, "aespa의 카리나입니다.", null, null));
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
                new ArtistCreateRequest("카리나", null, companySeq, null, null, null));
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
                new ArtistCreateRequest("카리나", groupSeq, otherCompanySeq, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_PARAMETER);

        assertThat(artistRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("그룹만 주면 소속사는 그룹을 따라간다")
    void companyFollowsGroupWhenOmitted() {
        ArtistCreateResponse response = adminArtistService.create(admin,
                new ArtistCreateRequest("카리나", groupSeq, null, null, null, null));

        assertThat(artistRepository.findById(response.artistSeq()).orElseThrow().getCompanySeq())
                .isEqualTo(companySeq);
    }

    @Test
    @DisplayName("솔로인데 companySeq 가 없으면 400")
    void soloNeedsCompany() {
        assertThatThrownBy(() -> adminArtistService.create(admin,
                new ArtistCreateRequest("카리나", null, null, null, null, null)))
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
                new ArtistCreateRequest("카리나", groupSeq, companySeq, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.GROUP_NOT_FOUND);

        assertThatThrownBy(() -> adminArtistService.create(admin,
                new ArtistCreateRequest("카리나", null, 999_999, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.COMPANY_NOT_FOUND);
    }

    @Test
    @DisplayName("그룹 소속 아티스트의 소속사 단독 변경은 409 ARTIST_COMPANY_LOCKED")
    void companyOnlyChangeIsLockedWhileInGroup() {
        Integer artistSeq = adminArtistService.create(admin,
                new ArtistCreateRequest("카리나", groupSeq, companySeq, null, null, null)).artistSeq();

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
                new ArtistCreateRequest("카리나", groupSeq, companySeq, null, null, null)).artistSeq();

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
                new ArtistCreateRequest("카리나", null, companySeq, null, null, null)).artistSeq();

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
                new ArtistCreateRequest("카리나", null, companySeq, null, null, null)).artistSeq();

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
                new ArtistCreateRequest("카리나", null, companySeq, null, null, null));

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
        adminArtistService.create(admin, new ArtistCreateRequest("카리나", groupSeq, companySeq, null, null, null));
        adminArtistService.create(admin, new ArtistCreateRequest("윈터", groupSeq, companySeq, null, null, null));
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
    @DisplayName("q 가 없거나 공백뿐이면 전체 조회다 — null 을 쿼리로 넘기면 lower(bytea) 로 죽는다")
    void listWithoutQueryReturnsAll() {
        // 공유 로컬 DB 라 기존 행이 있다 — 절대 개수가 아니라 증분으로 본다.
        long before = adminArtistService.list(null, null, null, false, 0, 1).totalElements();

        adminArtistService.create(admin, new ArtistCreateRequest("카리나", groupSeq, companySeq, null, null, null));
        adminArtistService.create(admin, new ArtistCreateRequest("윈터", groupSeq, companySeq, null, null, null));
        entityManager.flush();

        // 2026-09-13 운영 500 회귀 방어. 기존 목록 테스트가 전부 q 를 넘겨서 이 경로가
        // 한 번도 실행되지 않았고, 배포 후에야 lower(bytea) 로 드러났다.
        assertThat(adminArtistService.list(null, null, null, false, 0, 20).totalElements())
                .isEqualTo(before + 2);
        assertThat(adminArtistService.list("   ", null, null, false, 0, 20).totalElements())
                .isEqualTo(before + 2);
    }

    @Test
    @DisplayName("삭제된 아티스트는 기본 목록에서 빠지고 includeDeleted 로만 보인다")
    void deletedAreHiddenByDefault() {
        Integer artistSeq = adminArtistService.create(admin,
                new ArtistCreateRequest("카리나", null, companySeq, null, null, null)).artistSeq();
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
        adminArtistService.create(admin, new ArtistCreateRequest("솔로가수", null, companySeq, null, null, null));
        entityManager.flush();

        assertThat(adminArtistService.list("솔로가수", null, null, false, 0, 20).list())
                .singleElement()
                .satisfies(item -> assertThat(item.group()).isNull());
    }

    @Test
    @DisplayName("상세는 패스 가격표와 최신 챌린지를 함께 준다")
    void detailIncludesPassPricesAndChallenges() {
        Integer artistSeq = adminArtistService.create(admin,
                new ArtistCreateRequest("카리나", groupSeq, companySeq, "소개", null, null)).artistSeq();
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

    // --- 386 이미지 키 ------------------------------------------------------

    @Test
    @DisplayName("프로필은 users, 모티프 배경은 artists 에 저장된다 — 업로드 API 는 없다")
    void imageKeysLandInTheirOwnTables() {
        ArtistCreateResponse response = adminArtistService.create(admin,
                new ArtistCreateRequest("카리나", null, companySeq, null,
                        "profiles/3f9c0000-0000-0000-0000-000000000001.jpg",
                        "artists/a1b20000-0000-0000-0000-000000000002.jpg"));
        entityManager.flush();

        assertThat(userRepository.findById(response.userSeq()).orElseThrow().getProfileImageKey())
                .isEqualTo("profiles/3f9c0000-0000-0000-0000-000000000001.jpg");
        assertThat(artistRepository.findById(response.artistSeq()).orElseThrow().getMotifBgImageKey())
                .isEqualTo("artists/a1b20000-0000-0000-0000-000000000002.jpg");
    }

    @Test
    @DisplayName("이미지 키는 선택이다 — 없으면 둘 다 null")
    void imageKeysAreOptional() {
        ArtistCreateResponse response = adminArtistService.create(admin,
                new ArtistCreateRequest("카리나", null, companySeq, null, null, null));
        entityManager.flush();

        assertThat(userRepository.findById(response.userSeq()).orElseThrow().getProfileImageKey()).isNull();
        assertThat(artistRepository.findById(response.artistSeq()).orElseThrow().getMotifBgImageKey()).isNull();
    }

    @Test
    @DisplayName("올라오지 않은 키는 409 IMAGE_NOT_UPLOADED — 아티스트를 만들지 않는다")
    void missingImageBlocksCreate() {
        given(adminMediaService.exists("profiles/missing.jpg")).willReturn(false);
        long before = artistRepository.count();

        assertThatThrownBy(() -> adminArtistService.create(admin,
                new ArtistCreateRequest("카리나", null, companySeq, null, "profiles/missing.jpg", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(MediaErrorCode.IMAGE_NOT_UPLOADED);

        assertThat(artistRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("수정으로 이미지를 교체하고, 키를 생략하면 기존 이미지가 남는다")
    void updateReplacesOrKeepsImages() {
        ArtistCreateResponse created = adminArtistService.create(admin,
                new ArtistCreateRequest("카리나", null, companySeq, null,
                        "profiles/old.jpg", "artists/old.jpg"));

        ArtistUpdateRequest replace = new ArtistUpdateRequest();
        replace.setProfileImageKey("profiles/new.jpg");
        adminArtistService.update(admin, created.artistSeq(), replace);
        entityManager.flush();

        assertThat(userRepository.findById(created.userSeq()).orElseThrow().getProfileImageKey())
                .isEqualTo("profiles/new.jpg");
        // motifBgImageKey 는 보내지 않았으므로 그대로다
        assertThat(artistRepository.findById(created.artistSeq()).orElseThrow().getMotifBgImageKey())
                .isEqualTo("artists/old.jpg");
    }

    @Test
    @DisplayName("상세·목록은 키가 아니라 CDN URL 로 준다")
    void responsesExposeUrlsNotKeys() {
        Integer artistSeq = adminArtistService.create(admin,
                new ArtistCreateRequest("카리나", null, companySeq, null,
                        "profiles/x.jpg", "artists/x.jpg")).artistSeq();
        entityManager.flush();

        ArtistDetailResponse detail = adminArtistService.detail(artistSeq);

        // 로컬은 public-base-url 이 비어 있어 resolver 가 null 을 준다(PublicMediaUrlResolver).
        // 요점은 응답에 S3 키가 그대로 새지 않는다는 것이다
        assertThat(detail.summary().profileImageUrl()).isNotEqualTo("profiles/x.jpg");
        assertThat(detail.motifBgImageUrl()).isNotEqualTo("artists/x.jpg");
    }

    // --- 558 삭제 연쇄 (§3.5.5) ---------------------------------------------

    @Test
    @DisplayName("삭제는 artists·users 와 에셋·에셋 가격·패스 가격을 한 트랜잭션에 내린다")
    void deleteCascadesToAssetsAndPrices() {
        ArtistCreateResponse created = adminArtistService.create(admin,
                new ArtistCreateRequest("카리나", null, companySeq, null, null, null));
        int artistSeq = created.artistSeq();
        Integer assetA = newAsset(artistSeq, true);
        Integer assetB = newAsset(artistSeq, true);
        Integer priceA = newAssetPrice(assetA, true);
        Integer priceB = newAssetPrice(assetB, true);
        Integer passA = newPassPrice(artistSeq, (short) 1, true);
        Integer passB = newPassPrice(artistSeq, (short) 12, true);
        entityManager.flush();

        ArtistDeleteResponse response = adminArtistService.delete(admin, artistSeq);

        assertThat(response).isEqualTo(new ArtistDeleteResponse(2, 2, 2));
        // 벌크 UPDATE 가 컨텍스트를 비웠으므로 아래 조회는 전부 DB 를 다시 읽는다
        assertThat(artistRepository.findById(artistSeq).orElseThrow().isDeleted()).isTrue();
        assertThat(userRepository.findById(created.userSeq()).orElseThrow())
                .satisfies(user -> {
                    assertThat(user.isDeleted()).isTrue();
                    assertThat(user.getModUsrSeq()).isEqualTo(admin.userSeq());
                });
        assertThat(motifAssetRepository.findAllById(List.of(assetA, assetB)))
                .hasSize(2)
                .allSatisfy(asset -> assertThat(asset.isActive()).isFalse());
        assertThat(motifAssetPriceRepository.findAllById(List.of(priceA, priceB)))
                .hasSize(2)
                .allSatisfy(price -> {
                    assertThat(price.isActive()).isFalse();
                    assertThat(price.getModUsrSeq()).isEqualTo(admin.userSeq());
                });
        assertThat(motifPassPriceRepository.findAllById(List.of(passA, passB)))
                .hasSize(2)
                .allSatisfy(price -> assertThat(price.isActive()).isFalse());
    }

    @Test
    @DisplayName("반환 건수는 이번에 실제 UPDATE 된 행 수다 — 이미 꺼진 행은 세지 않는다")
    void deleteCountsOnlyRowsItTurnedOff() {
        int artistSeq = adminArtistService.create(admin,
                new ArtistCreateRequest("카리나", null, companySeq, null, null, null)).artistSeq();
        Integer active = newAsset(artistSeq, true);
        newAssetPrice(active, true);
        Integer alreadyOff = newAsset(artistSeq, false);
        newAssetPrice(alreadyOff, false);           // 가격도 이미 꺼져 있다
        newPassPrice(artistSeq, (short) 1, false);  // 활성 패스 가격은 하나도 없다
        entityManager.flush();

        // 총 보유량(에셋 2·가격 2)이 아니라 이번에 꺼진 수(1·1·0)여야 한다
        assertThat(adminArtistService.delete(admin, artistSeq))
                .isEqualTo(new ArtistDeleteResponse(1, 1, 0));
    }

    @Test
    @DisplayName("다른 아티스트의 에셋·가격은 건드리지 않는다")
    void deleteIsScopedToItsOwnArtist() {
        int target = adminArtistService.create(admin,
                new ArtistCreateRequest("카리나", null, companySeq, null, null, null)).artistSeq();
        int bystander = adminArtistService.create(admin,
                new ArtistCreateRequest("윈터", null, companySeq, null, null, null)).artistSeq();
        newAssetPrice(newAsset(target, true), true);
        Integer otherAsset = newAsset(bystander, true);
        Integer otherPrice = newAssetPrice(otherAsset, true);
        Integer otherPass = newPassPrice(bystander, (short) 1, true);
        entityManager.flush();

        adminArtistService.delete(admin, target);

        assertThat(artistRepository.findById(bystander).orElseThrow().isDeleted()).isFalse();
        assertThat(motifAssetRepository.findById(otherAsset).orElseThrow().isActive()).isTrue();
        assertThat(motifAssetPriceRepository.findById(otherPrice).orElseThrow().isActive()).isTrue();
        assertThat(motifPassPriceRepository.findById(otherPass).orElseThrow().isActive()).isTrue();
    }

    @Test
    @DisplayName("이미 삭제된 아티스트를 다시 삭제하면 404 — 존재를 숨긴다 (티켓 558 멱등 규칙)")
    void deletingTwiceIsNotFound() {
        int artistSeq = adminArtistService.create(admin,
                new ArtistCreateRequest("카리나", null, companySeq, null, null, null)).artistSeq();
        entityManager.flush();
        adminArtistService.delete(admin, artistSeq);

        assertThatThrownBy(() -> adminArtistService.delete(admin, artistSeq))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ArtistErrorCode.ARTIST_NOT_FOUND);
    }

    @Test
    @DisplayName("소프트 삭제다 — 행은 남고 includeDeleted 로만 보인다")
    void deleteIsSoft() {
        String name = "카리나-" + UUID.randomUUID();
        int artistSeq = adminArtistService.create(admin,
                new ArtistCreateRequest(name, null, companySeq, null, null, null)).artistSeq();
        entityManager.flush();

        adminArtistService.delete(admin, artistSeq);

        // 행이 사라지면 과거 챌린지·주문의 FK 가 끊긴다 — 보존이 목적이다(티켓 558)
        assertThat(artistRepository.findById(artistSeq)).isPresent();
        assertThat(adminArtistService.list(name, null, null, false, 0, 20).totalElements()).isZero();
        assertThat(adminArtistService.list(name, null, null, true, 0, 20).totalElements()).isEqualTo(1);
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

    /** 삭제 연쇄용 에셋. {@code motif_assets} 에는 삭제 플래그가 없어 활성 여부만 있다. */
    private Integer newAsset(int artistSeq, boolean active) {
        MotifAsset asset = MotifAsset.register(artistSeq, "t-" + UUID.randomUUID(), null,
                "assets/" + UUID.randomUUID() + ".mp4", "assets/" + UUID.randomUUID() + ".jpg",
                5_000, AssetType.NORMAL);
        if (!active) {
            asset.changeActive(false);
        }
        return motifAssetRepository.saveAndFlush(asset).getAssetSeq();
    }

    /** 국가는 KR 한 행뿐이고 (에셋, 국가) 가 UNIQUE 라 에셋당 가격은 하나다. */
    private Integer newAssetPrice(Integer assetSeq, boolean active) {
        return motifAssetPriceRepository.saveAndFlush(MotifAssetPrice.of(assetSeq, countrySeq(),
                new BigDecimal("1900.00"), "KRW", active, admin.userSeq())).getAssetPriceSeq();
    }

    /** (아티스트, 국가, 개월) 이 UNIQUE 라 국가가 하나여도 개월로 여러 행을 만든다. */
    private Integer newPassPrice(int artistSeq, short durationMonths, boolean active) {
        return motifPassPriceRepository.saveAndFlush(MotifPassPrice.of(artistSeq, countrySeq(),
                durationMonths, new BigDecimal("9900.00"), "KRW", active, admin.userSeq()))
                .getPassPriceSeq();
    }

    private Integer countrySeq() {
        return ((Number) entityManager.createNativeQuery(
                        "select country_seq from motif_core.countries where country_code = 'KR'")
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
