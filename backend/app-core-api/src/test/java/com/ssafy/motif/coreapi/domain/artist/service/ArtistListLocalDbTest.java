package com.ssafy.motif.coreapi.domain.artist.service;

import com.ssafy.motif.coreapi.domain.artist.dto.ArtistCardResponse;
import com.ssafy.motif.domain.core.artist.Artist;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
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
import static org.assertj.core.api.Assertions.tuple;

/**
 * §5.10 전체 아티스트 목록을 실제 DB에 대고 검증한다 (S15P21D104-34).
 *
 * <p><b>로컬 compose DB 는 세 레인이 공유한다.</b> 전량 반환 API 라 "전체 몇 건" 단언은
 * 쓸 수 없다 — 단언은 이 테스트가 만든 아티스트로 좁히고, <b>그 안에서의 상대 순서</b>를
 * 본다. 다른 레인이 아티스트를 더해도 상대 순서는 바뀌지 않는다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class ArtistListLocalDbTest {

    @Autowired
    private ArtistQueryService artistQueryService;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private UserRepository userRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private Integer adminUserSeq;
    private Integer viewerSeq;
    private Integer companySeq;

    @BeforeEach
    void setUp() {
        adminUserSeq = newUser().getUserSeq();
        viewerSeq = newUser().getUserSeq();
        companySeq = newCompany();
    }

    @Test
    @DisplayName("구독자 수 내림차순이다 — 등록 순서와 무관하다")
    void ordersBySubscriberCountDesc() {
        Integer few = newArtist(1);
        Integer many = newArtist(9);
        Integer some = newArtist(5);

        assertThat(mine(List.of(few, many, some))).containsExactly(many, some, few);
    }

    @Test
    @DisplayName("동점이면 artistSeq 오름차순이다 — 구독자 0인 신인들의 순서가 고정된다")
    void breaksTiesByArtistSeqAsc() {
        Integer first = newArtist(0);
        Integer second = newArtist(0);
        Integer third = newArtist(0);

        assertThat(mine(List.of(third, second, first))).containsExactly(first, second, third);
    }

    @Test
    @DisplayName("삭제된 아티스트는 빠진다 — §1.8")
    void deletedArtistsAreHidden() {
        Integer kept = newArtist(3);
        Integer removed = newArtist(100);
        softDelete(removed);

        assertThat(mine(List.of(kept, removed))).containsExactly(kept);
    }

    @Test
    @DisplayName("isSubscribed 는 호출자 기준이다 — 남의 구독은 붙지 않는다")
    void isSubscribedIsPerCaller() {
        Integer subscribed = newArtist(2);
        Integer other = newArtist(1);
        subscriptionRepository.saveAndFlush(Subscription.of(viewerSeq, subscribed));
        subscriptionRepository.saveAndFlush(Subscription.of(adminUserSeq, other));

        List<ArtistCardResponse> rows = rowsOf(List.of(subscribed, other));

        assertThat(rows).extracting(ArtistCardResponse::artistSeq, ArtistCardResponse::isSubscribed)
                .containsExactly(tuple(subscribed, true), tuple(other, false));
    }

    @Test
    @DisplayName("이름은 users.nickname 이고 솔로 아티스트는 groupName 이 null 이다")
    void nameComesFromUserAndSoloHasNoGroup() {
        Integer soloSeq = newArtist(0);

        ArtistCardResponse solo = rowsOf(List.of(soloSeq)).getFirst();

        assertThat(solo.name()).isEqualTo("아티스트");
        assertThat(solo.groupName()).isNull();
        assertThat(solo.profileImageUrl()).isNull();
    }

    // --- 단언 보조 -----------------------------------------------------------

    /** 이 테스트가 만든 아티스트만 남긴 응답 — 공유 DB 의 다른 행을 걸러낸다. */
    private List<ArtistCardResponse> rowsOf(List<Integer> artistSeqs) {
        return artistQueryService.list(viewerSeq).list().stream()
                .filter(row -> artistSeqs.contains(row.artistSeq()))
                .toList();
    }

    private List<Integer> mine(List<Integer> artistSeqs) {
        return rowsOf(artistSeqs).stream().map(ArtistCardResponse::artistSeq).toList();
    }

    // --- 픽스처 -------------------------------------------------------------

    private User newUser() {
        User user = userRepository.saveAndFlush(User.signUp());
        user.applyInitialNickname();
        return user;
    }

    /** 구독자 수는 §5.6 경로 없이 칼럼을 직접 세운다 — 검증 대상은 정렬이다. */
    private Integer newArtist(int subscriberCount) {
        User artistUser = userRepository.saveAndFlush(User.createArtist("아티스트"));
        Artist artist = artistRepository.saveAndFlush(Artist.register(
                artistUser.getUserSeq(), null, companySeq, null, null, adminUserSeq));
        setSubscriberCount(artist.getArtistSeq(), subscriberCount);
        return artist.getArtistSeq();
    }

    private void setSubscriberCount(Integer artistSeq, int count) {
        entityManager.createNativeQuery(
                        "update motif_core.artists set subscriber_count = :count where artist_seq = :seq")
                .setParameter("count", count)
                .setParameter("seq", artistSeq)
                .executeUpdate();
        entityManager.clear();
    }

    private void softDelete(Integer artistSeq) {
        entityManager.createNativeQuery(
                        "update motif_core.artists set is_deleted = true where artist_seq = :seq")
                .setParameter("seq", artistSeq)
                .executeUpdate();
        entityManager.clear();
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
}
