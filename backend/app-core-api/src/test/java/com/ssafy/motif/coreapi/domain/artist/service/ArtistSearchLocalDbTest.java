package com.ssafy.motif.coreapi.domain.artist.service;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistSearchResponse;
import com.ssafy.motif.domain.core.artist.Artist;
import com.ssafy.motif.domain.core.artist.ArtistAlias;
import com.ssafy.motif.domain.core.artist.ArtistAliasRepository;
import com.ssafy.motif.domain.core.artist.ArtistGroup;
import com.ssafy.motif.domain.core.artist.ArtistGroupRepository;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.artist.Company;
import com.ssafy.motif.domain.core.artist.CompanyRepository;
import com.ssafy.motif.domain.core.artist.GroupAlias;
import com.ssafy.motif.domain.core.artist.GroupAliasRepository;
import com.ssafy.motif.domain.core.artist.Subscription;
import com.ssafy.motif.domain.core.artist.SubscriptionRepository;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §5.2 아티스트 검색 (자동완성 겸용) — S15P21D104-359.
 *
 * <p><b>공유 로컬 DB 다.</b> 0018-04 백필이 기존 아티스트 800여 명에 `ko` 별칭을 깔아 놨고
 * 다른 레인도 같은 DB 를 쓴다. 그래서 전체 건수에 기대지 않는다 — 검색어에 UUID 조각을
 * 박아 <b>내가 만든 별칭만</b> 걸리게 하고, 그 안에서 순서를 본다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class ArtistSearchLocalDbTest {

    @Autowired
    private ArtistQueryService artistQueryService;
    @Autowired
    private ArtistAliasRepository artistAliasRepository;
    @Autowired
    private GroupAliasRepository groupAliasRepository;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private ArtistGroupRepository artistGroupRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private UserRepository userRepository;
    @PersistenceContext
    private EntityManager entityManager;

    /** 이 테스트만 걸리게 하는 검색어 조각 — 공유 DB 의 남의 별칭과 섞이지 않는다. */
    private String token;
    private Integer userSeq;
    private Integer companySeq;

    @BeforeEach
    void setUp() {
        token = "zq" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        User user = userRepository.saveAndFlush(User.signUp());
        user.applyInitialNickname();
        userSeq = user.getUserSeq();
        companySeq = companyRepository.saveAndFlush(
                Company.register(unique("c"), userSeq)).getCompanySeq();
    }

    @Test
    @DisplayName("본인 별칭으로 찾으면 표시 이름은 nickname 이다 — 별칭을 싣지 않는다")
    void findsByOwnAlias() {
        Integer artistSeq = newArtist("카리나", null);
        alias(artistSeq, "en", token);
        flush();

        assertThat(search(token))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.artistSeq()).isEqualTo(artistSeq);
                    assertThat(item.name()).isEqualTo("카리나");   // 별칭(token)이 아니다
                    assertThat(item.groupName()).isNull();          // 솔로
                    assertThat(item.isSubscribed()).isFalse();
                });
    }

    @Test
    @DisplayName("대소문자를 가리지 않는다")
    void isCaseInsensitive() {
        Integer artistSeq = newArtist("카리나", null);
        alias(artistSeq, "en", "Karina" + token);
        flush();

        assertThat(search("karina" + token)).singleElement()
                .extracting(ArtistSearchResponse::artistSeq).isEqualTo(artistSeq);
        assertThat(search("KARINA" + token.toUpperCase())).singleElement()
                .extracting(ArtistSearchResponse::artistSeq).isEqualTo(artistSeq);
    }

    @Test
    @DisplayName("그룹 별칭에 걸리면 그 그룹의 비삭제 멤버 전원이 결과에 들어온다")
    void groupAliasExpandsToMembers() {
        Integer groupSeq = newGroup("aespa");
        Integer karina = newArtist("카리나", groupSeq);
        Integer winter = newArtist("윈터", groupSeq);
        Integer deletedMember = newArtist("탈퇴", groupSeq);
        softDelete("artists", "artist_seq", deletedMember);
        groupAlias(groupSeq, "en", token);
        flush();

        assertThat(search(token))
                .extracting(ArtistSearchResponse::artistSeq)
                .containsExactlyInAnyOrder(karina, winter);   // 삭제 멤버는 빠진다
        assertThat(search(token))
                .allSatisfy(item -> assertThat(item.groupName()).isEqualTo("aespa"));
    }

    @Test
    @DisplayName("본인 매치가 그룹 확장 멤버보다 항상 앞이다")
    void ownMatchOutranksGroupExpansion() {
        Integer groupSeq = newGroup("aespa");
        // 구독자 수를 뒤집어 놓는다 — 정렬이 구독자 수가 아니라 매치 종류로 갈리는지 본다
        Integer member = newArtist("윈터", groupSeq, 9_999);
        Integer solo = newArtist("카리나", null, 0);
        groupAlias(groupSeq, "en", token);
        alias(solo, "en", token);
        flush();

        assertThat(search(token))
                .extracting(ArtistSearchResponse::artistSeq)
                .containsExactly(solo, member);
    }

    @Test
    @DisplayName("별칭 여러 개로 걸려도 1건이다 — 가장 높은 매치가 정렬 키다")
    void duplicateMatchesCollapseToOne() {
        Integer exactOnly = newArtist("완전", null);
        Integer many = newArtist("여러", null);
        alias(exactOnly, "en", token + "-tail");           // 부분 일치만
        alias(many, "en", token);                           // 완전 일치
        alias(many, "ko", token + "-b");                    // 부분 일치
        alias(many, "ja", token + "-ccc");                  // 부분 일치
        flush();

        List<ArtistSearchResponse> found = search(token);
        assertThat(found).extracting(ArtistSearchResponse::artistSeq)
                .containsExactly(many, exactOnly);          // 완전 일치가 앞, 각 1건
        assertThat(found).hasSize(2);
    }

    @Test
    @DisplayName("정렬 — 완전 일치 → 접두 → 별칭 길이 → 구독자 수 → artistSeq")
    void ordering() {
        Integer exact = newArtist("완전", null, 0);
        Integer prefix = newArtist("접두", null, 0);
        Integer shorter = newArtist("짧은부분", null, 0);
        Integer longer = newArtist("긴부분", null, 0);
        Integer popular = newArtist("인기", null, 500);
        Integer quiet = newArtist("한산", null, 1);

        alias(exact, "en", token);                       // 완전 일치
        alias(prefix, "en", token + "X");                // 접두 일치
        alias(shorter, "en", "a" + token);               // 부분, 길이 짧음
        alias(longer, "en", "aaaa" + token);             // 부분, 길이 김
        // 같은 별칭 길이로 맞춰 구독자 수 tie-break 를 본다
        alias(popular, "en", "bb" + token);
        alias(quiet, "en", "cc" + token);
        flush();

        assertThat(search(token))
                .extracting(ArtistSearchResponse::artistSeq)
                .containsExactly(exact, prefix, shorter, popular, quiet, longer);
    }

    /**
     * {@code size} 는 정렬·중복 제거를 마친 뒤의 단순 상한이다 — 그룹이 쪼개져도 자른다.
     *
     * <p>그룹을 통째로 넣는 규칙으로 바꾸면 {@code size} 가 상한이 아니게 되고,
     * 자동완성이 요청한 것보다 긴 목록을 받는다.
     */
    @Test
    @DisplayName("size 상한에서 그룹 멤버가 잘려 나간다")
    void sizeCutsThroughGroup() {
        Integer groupSeq = newGroup("aespa");
        newArtist("멤버1", groupSeq, 30);
        newArtist("멤버2", groupSeq, 20);
        newArtist("멤버3", groupSeq, 10);
        groupAlias(groupSeq, "en", token);
        flush();

        assertThat(search(token)).hasSize(3);
        assertThat(artistQueryService.search(userSeq, token, 2).list()).hasSize(2);
    }

    @Test
    @DisplayName("삭제된 아티스트·그룹은 별칭이 남아 있어도 빠진다")
    void deletedAreExcluded() {
        Integer live = newArtist("살아있음", null);
        Integer dead = newArtist("삭제됨", null);
        alias(live, "en", token);
        alias(dead, "en", token);
        softDelete("artists", "artist_seq", dead);

        Integer deadGroup = newGroup("해체");
        Integer memberOfDeadGroup = newArtist("해체멤버", deadGroup);
        groupAlias(deadGroup, "en", token);
        softDelete("artist_groups", "group_seq", deadGroup);
        flush();

        assertThat(search(token))
                .extracting(ArtistSearchResponse::artistSeq)
                .containsExactly(live)
                .doesNotContain(dead, memberOfDeadGroup);
    }

    @Test
    @DisplayName("구독 여부는 요청자 기준이다")
    void isSubscribedIsPerCaller() {
        Integer artistSeq = newArtist("카리나", null);
        alias(artistSeq, "en", token);
        subscriptionRepository.save(Subscription.of(userSeq, artistSeq));
        flush();

        assertThat(search(token)).singleElement()
                .extracting(ArtistSearchResponse::isSubscribed).isEqualTo(true);

        User other = userRepository.saveAndFlush(User.signUp());
        other.applyInitialNickname();
        assertThat(artistQueryService.search(other.getUserSeq(), token, 20).list())
                .singleElement()
                .extracting(ArtistSearchResponse::isSubscribed).isEqualTo(false);
    }

    /**
     * LIKE 메타문자 이스케이프 — 사용자가 친 글자를 <b>패턴이 아니라 값으로</b> 찾는다.
     *
     * <p>이스케이프가 빠지면 {@code %} 가 모든 별칭에 걸려 검색어와 무관한 상위
     * {@code size} 건이 나온다. {@code _} 는 아무 글자 1개가 된다.
     */
    @Test
    @DisplayName("%·_ 는 와일드카드가 아니라 찾을 글자다")
    void likeMetaCharactersAreEscaped() {
        Integer withPercent = newArtist("퍼센트", null);
        Integer plain = newArtist("보통", null);
        alias(withPercent, "en", token + "50%off");
        alias(plain, "en", token + "50Xoff");
        flush();

        // 진짜 % 가 든 별칭만 걸린다 — % 를 값으로 찾는다
        assertThat(search(token + "50%off"))
                .extracting(ArtistSearchResponse::artistSeq)
                .containsExactly(withPercent);

        // % 가 와일드카드였다면 "50%off"·"50Xoff" 둘 다 걸린다. 값이라 둘 다 아니다
        assertThat(search(token + "%off")).isEmpty();

        // _ 가 와일드카드였다면 "50%off"·"50Xoff" 둘 다 걸린다
        assertThat(search(token + "50_off")).isEmpty();
    }

    @Test
    @DisplayName("0건도 200 빈 배열이다")
    void emptyResultIsOk() {
        assertThat(search(token)).isEmpty();
    }

    @Test
    @DisplayName("빈 검색어·공백뿐인 검색어는 400 INVALID_PARAMETER")
    void blankQueryIsRejected() {
        for (String q : new String[]{"", "   ", null}) {
            assertThatThrownBy(() -> artistQueryService.search(userSeq, q, 20))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(CommonErrorCode.INVALID_PARAMETER);
        }
    }

    @Test
    @DisplayName("앞뒤 공백은 잘라서 찾는다")
    void queryIsTrimmed() {
        Integer artistSeq = newArtist("카리나", null);
        alias(artistSeq, "en", token);
        flush();

        assertThat(search("  " + token + "  ")).singleElement()
                .extracting(ArtistSearchResponse::artistSeq).isEqualTo(artistSeq);
    }

    // --- 픽스처 -------------------------------------------------------------

    private List<ArtistSearchResponse> search(String q) {
        return artistQueryService.search(userSeq, q, 20).list();
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 18);
    }

    private Integer newArtist(String name, Integer groupSeq) {
        return newArtist(name, groupSeq, 0);
    }

    /**
     * {@code subscriberCount} 는 거울 카운터라 세터가 없다 — 정렬 tie-break 를 보려면
     * 값이 필요하므로 행을 직접 갱신한다(구독을 실제로 만드는 것보다 싸다).
     */
    private Integer newArtist(String name, Integer groupSeq, int subscriberCount) {
        User user = userRepository.save(User.createArtist(name));
        Integer artistSeq = artistRepository.save(Artist.register(
                user.getUserSeq(), groupSeq, companySeq, null, null, userSeq)).getArtistSeq();
        if (subscriberCount > 0) {
            entityManager.flush();
            entityManager.createNativeQuery("""
                            update motif_core.artists set subscriber_count = :count
                             where artist_seq = :seq
                            """)
                    .setParameter("count", subscriberCount)
                    .setParameter("seq", artistSeq)
                    .executeUpdate();
            entityManager.clear();
        }
        return artistSeq;
    }

    private Integer newGroup(String name) {
        return artistGroupRepository.save(
                ArtistGroup.register(companySeq, name, userSeq)).getGroupSeq();
    }

    private void alias(Integer artistSeq, String lang, String value) {
        artistAliasRepository.save(ArtistAlias.register(artistSeq, lang, value, userSeq));
    }

    private void groupAlias(Integer groupSeq, String lang, String value) {
        groupAliasRepository.save(GroupAlias.register(groupSeq, lang, value, userSeq));
    }

    private void softDelete(String table, String idColumn, Integer seq) {
        entityManager.flush();
        entityManager.createNativeQuery(
                        "update motif_core." + table + " set is_deleted = true"
                                + " where " + idColumn + " = :seq")
                .setParameter("seq", seq)
                .executeUpdate();
        entityManager.clear();
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }
}
