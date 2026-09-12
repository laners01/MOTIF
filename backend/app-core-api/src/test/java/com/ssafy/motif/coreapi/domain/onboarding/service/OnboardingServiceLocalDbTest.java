package com.ssafy.motif.coreapi.domain.onboarding.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.artist.ArtistErrorCode;
import com.ssafy.motif.coreapi.domain.onboarding.OnboardingErrorCode;
import com.ssafy.motif.coreapi.domain.onboarding.dto.OnboardingRequest;
import com.ssafy.motif.coreapi.domain.onboarding.dto.OnboardingResponse;
import com.ssafy.motif.domain.core.artist.Artist;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.artist.SubscriptionRepository;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 명세 §3.3 온보딩을 실제 DB에 대고 검증한다 (ADR-0003 격리 모드).
 *
 * <p><b>아티스트 픽스처를 네이티브 SQL로 만든다.</b> 시드에 아티스트가 없고
 * ({@code 0013-seed.sql} 은 oauth_providers·countries 뿐), {@link Artist} 엔티티는
 * §3.3 에 필요한 컬럼만 부분 매핑이라 JPA 로는 NOT NULL 인 {@code user_seq}·
 * {@code company_seq}·{@code reg_usr_seq} 를 채울 수 없다. §5 에서 연관이 매핑되면
 * 이 픽스처는 엔티티 방식으로 바꿀 수 있다.
 *
 * <p>{@code @Transactional}로 매 테스트를 롤백한다. 실행 전
 * {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class OnboardingServiceLocalDbTest {

    @Autowired
    private OnboardingService onboardingService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private Integer userSeq;

    @BeforeEach
    void setUp() {
        userSeq = newUser().getUserSeq();
    }

    @Test
    @DisplayName("정상 온보딩 — 구독이 생기고 닉네임이 확정된다")
    void completeCreatesSubscriptionsAndNickname() {
        Integer artistA = newArtist();
        Integer artistB = newArtist();

        OnboardingResponse response = onboardingService.complete(
                userSeq, new OnboardingRequest(List.of(artistA, artistB), "모티프러버"));

        assertThat(response.subscribedCount()).isEqualTo(2);
        assertThat(response.nickname()).isEqualTo("모티프러버");
        assertThat(subscriptionRepository.existsByIdUserSeq(userSeq)).isTrue();
        assertThat(userRepository.findById(userSeq).orElseThrow().getNickname()).isEqualTo("모티프러버");
    }

    @Test
    @DisplayName("구독자 수가 아티스트마다 1씩 증가한다")
    void subscriberCountIncreases() {
        Integer artistSeq = newArtist();
        int before = artistRepository.findById(artistSeq).orElseThrow().getSubscriberCount();

        onboardingService.complete(userSeq, new OnboardingRequest(List.of(artistSeq), "모티프러버"));

        assertThat(artistRepository.findById(artistSeq).orElseThrow().getSubscriberCount())
                .isEqualTo(before + 1);
    }

    @Test
    @DisplayName("중복 artistSeq 는 한 번만 반영된다 — 구독도 카운터도 1")
    void duplicateArtistSeqCountsOnce() {
        Integer artistSeq = newArtist();
        int before = artistRepository.findById(artistSeq).orElseThrow().getSubscriberCount();

        OnboardingResponse response = onboardingService.complete(
                userSeq, new OnboardingRequest(List.of(artistSeq, artistSeq, artistSeq), "모티프러버"));

        assertThat(response.subscribedCount()).isEqualTo(1);
        assertThat(artistRepository.findById(artistSeq).orElseThrow().getSubscriberCount())
                .isEqualTo(before + 1);
    }

    @Test
    @DisplayName("이미 온보딩한 사용자의 재호출은 409")
    void secondCallIsRejected() {
        Integer artistSeq = newArtist();
        onboardingService.complete(userSeq, new OnboardingRequest(List.of(artistSeq), "모티프러버"));

        assertThatThrownBy(() -> onboardingService.complete(
                userSeq, new OnboardingRequest(List.of(artistSeq), "다른닉네임")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(OnboardingErrorCode.ALREADY_ONBOARDED);
    }

    @Test
    @DisplayName("존재하지 않는 아티스트가 섞이면 404 — 닉네임도 구독도 남지 않는다")
    void unknownArtistRejectsEverything() {
        Integer artistSeq = newArtist();
        String nicknameBefore = userRepository.findById(userSeq).orElseThrow().getNickname();

        assertThatThrownBy(() -> onboardingService.complete(
                userSeq, new OnboardingRequest(List.of(artistSeq, 999_999), "모티프러버")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ArtistErrorCode.ARTIST_NOT_FOUND);

        assertThat(subscriptionRepository.existsByIdUserSeq(userSeq)).isFalse();
        assertThat(userRepository.findById(userSeq).orElseThrow().getNickname()).isEqualTo(nicknameBefore);
    }

    @Test
    @DisplayName("삭제된 아티스트도 404 로 막는다")
    void deletedArtistIsRejected() {
        Integer deleted = newArtist();
        entityManager.createNativeQuery(
                        "update motif_core.artists set is_deleted = true where artist_seq = :seq")
                .setParameter("seq", deleted)
                .executeUpdate();
        entityManager.clear();

        assertThatThrownBy(() -> onboardingService.complete(
                userSeq, new OnboardingRequest(List.of(deleted), "모티프러버")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ArtistErrorCode.ARTIST_NOT_FOUND);

        assertThat(subscriptionRepository.existsByIdUserSeq(userSeq)).isFalse();
    }

    // --- 픽스처 -------------------------------------------------------------

    private User newUser() {
        User user = userRepository.saveAndFlush(User.signUp());
        user.applyInitialNickname();
        return user;
    }

    /** {@code users(ARTIST)} → {@code companies} → {@code artists} FK 체인을 만든다. */
    private Integer newArtist() {
        User artistUser = newUser();
        ReflectionTestUtils.setField(artistUser, "role", UserRole.ARTIST);
        userRepository.flush();

        Integer companySeq = newCompany(artistUser.getUserSeq());

        entityManager.createNativeQuery("""
                        insert into motif_core.artists (user_seq, company_seq, reg_usr_seq)
                        values (:userSeq, :companySeq, :regUsrSeq)
                        """)
                .setParameter("userSeq", artistUser.getUserSeq())
                .setParameter("companySeq", companySeq)
                .setParameter("regUsrSeq", artistUser.getUserSeq())
                .executeUpdate();

        return ((Number) entityManager.createNativeQuery(
                        "select artist_seq from motif_core.artists where user_seq = :userSeq")
                .setParameter("userSeq", artistUser.getUserSeq())
                .getSingleResult()).intValue();
    }

    private Integer newCompany(Integer regUsrSeq) {
        // uq_companies_name_active 때문에 활성 회사명은 유일해야 한다
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
}
