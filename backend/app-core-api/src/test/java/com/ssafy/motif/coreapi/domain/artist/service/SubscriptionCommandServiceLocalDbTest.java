package com.ssafy.motif.coreapi.domain.artist.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.artist.ArtistErrorCode;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistDetailResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.SubscriptionResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 구독 / 구독 취소 (S15P21D104-59 · 명세 §5.6 · §5.7).
 *
 * <p><b>DB 가 필요하다.</b> 이 API 의 핵심이 "삽입·삭제가 실제로 몇 행을 건드렸는가"이고
 * 그 판정을 복합 PK 가 한다 — 목으로는 검증되는 것이 없다.
 *
 * <p>동시성은 트랜잭션을 공유하면 재현되지 않으므로
 * {@code SubscriptionConcurrencyLocalDbTest} 가 따로 본다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class SubscriptionCommandServiceLocalDbTest {

    @Autowired
    private SubscriptionCommandService subscriptionCommandService;
    @Autowired
    private ArtistQueryService artistQueryService;
    @PersistenceContext
    private EntityManager entityManager;

    private Integer me;
    private Integer artistSeq;

    @BeforeEach
    void setUp() {
        me = newUser();
        artistSeq = newArtist();
    }

    @Nested
    @DisplayName("구독 (§5.6)")
    class Subscribe {

        @Test
        @DisplayName("구독하면 행이 생기고 카운터가 1 오른 값으로 응답한다")
        void 구독() {
            SubscriptionResponse response = subscriptionCommandService.subscribe(me, artistSeq);

            assertThat(response.isSubscribed()).isTrue();
            assertThat(response.subscriberCount()).isEqualTo(1);
            assertThat(subscriptionRows()).isEqualTo(1);
        }

        @Test
        @DisplayName("이미 구독 중이면 200 이고 카운터가 그대로다 — 멱등")
        void 멱등() {
            subscriptionCommandService.subscribe(me, artistSeq);
            SubscriptionResponse again = subscriptionCommandService.subscribe(me, artistSeq);

            assertThat(again.isSubscribed()).isTrue();
            assertThat(again.subscriberCount()).isEqualTo(1);
            assertThat(subscriptionRows()).isEqualTo(1);
        }

        @Test
        @DisplayName("세 번을 불러도 카운터는 1이다")
        void 여러_번() {
            subscriptionCommandService.subscribe(me, artistSeq);
            subscriptionCommandService.subscribe(me, artistSeq);

            assertThat(subscriptionCommandService.subscribe(me, artistSeq).subscriberCount())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("다른 사용자의 구독은 따로 센다")
        void 사용자별() {
            Integer other = newUser();

            subscriptionCommandService.subscribe(me, artistSeq);
            SubscriptionResponse response = subscriptionCommandService.subscribe(other, artistSeq);

            assertThat(response.subscriberCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("존재하지 않는 아티스트는 404 다")
        void 미존재_404() {
            assertThatThrownBy(() -> subscriptionCommandService.subscribe(me, 99_999_999))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ArtistErrorCode.ARTIST_NOT_FOUND);
        }

        @Test
        @DisplayName("삭제된 아티스트는 구독할 수 없다 — §1.8 없는 것으로 답한다")
        void 삭제_아티스트_404() {
            softDelete(artistSeq);

            assertThatThrownBy(() -> subscriptionCommandService.subscribe(me, artistSeq))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ArtistErrorCode.ARTIST_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("구독 취소 (§5.7)")
    class Unsubscribe {

        @Test
        @DisplayName("해제하면 행이 사라지고 카운터가 1 내린 값으로 응답한다 — 하드 삭제")
        void 해제() {
            subscriptionCommandService.subscribe(me, artistSeq);

            SubscriptionResponse response = subscriptionCommandService.unsubscribe(me, artistSeq);

            assertThat(response.isSubscribed()).isFalse();
            assertThat(response.subscriberCount()).isZero();
            assertThat(subscriptionRows()).isZero();
        }

        @Test
        @DisplayName("구독하지 않은 상태에서 해제해도 200 이고 카운터가 그대로다 — 멱등")
        void 멱등() {
            SubscriptionResponse response = subscriptionCommandService.unsubscribe(me, artistSeq);

            assertThat(response.isSubscribed()).isFalse();
            assertThat(response.subscriberCount()).isZero();
        }

        @Test
        @DisplayName("미구독 해제를 두 번 해도 CHECK (subscriber_count >= 0) 을 위반하지 않는다")
        void 음수_방지() {
            subscriptionCommandService.unsubscribe(me, artistSeq);
            subscriptionCommandService.unsubscribe(me, artistSeq);
            entityManager.flush();

            assertThat(subscriberCount()).isZero();
        }

        @Test
        @DisplayName("남의 구독은 건드리지 않는다")
        void 사용자별() {
            Integer other = newUser();
            subscriptionCommandService.subscribe(me, artistSeq);
            subscriptionCommandService.subscribe(other, artistSeq);

            assertThat(subscriptionCommandService.unsubscribe(me, artistSeq).subscriberCount())
                    .isEqualTo(1);
            assertThat(subscriptionRows()).isZero();
        }

        @Test
        @DisplayName("존재하지 않는 아티스트는 404 다")
        void 미존재_404() {
            assertThatThrownBy(() -> subscriptionCommandService.unsubscribe(me, 99_999_999))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ArtistErrorCode.ARTIST_NOT_FOUND);
        }

        @Test
        @DisplayName("삭제된 아티스트도 해제할 수 있다 — 막으면 그 구독을 영원히 정리할 수 없다")
        void 삭제_아티스트도_해제_가능() {
            subscriptionCommandService.subscribe(me, artistSeq);
            softDelete(artistSeq);

            SubscriptionResponse response = subscriptionCommandService.unsubscribe(me, artistSeq);

            assertThat(response.isSubscribed()).isFalse();
            assertThat(subscriptionRows()).isZero();
        }
    }

    @Test
    @DisplayName("구독 직후 §5.3 상세가 isSubscribed=true · subscribedDays=0 을 준다")
    void 상세와_연동() {
        subscriptionCommandService.subscribe(me, artistSeq);

        ArtistDetailResponse detail = artistQueryService.detail(me, artistSeq);

        assertThat(detail.isSubscribed()).isTrue();
        assertThat(detail.subscribedDays()).isZero();
        assertThat(detail.subscriberCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("해제 직후 §5.3 상세가 isSubscribed=false · subscribedDays=null 을 준다")
    void 해제_후_상세() {
        subscriptionCommandService.subscribe(me, artistSeq);
        subscriptionCommandService.unsubscribe(me, artistSeq);

        ArtistDetailResponse detail = artistQueryService.detail(me, artistSeq);

        assertThat(detail.isSubscribed()).isFalse();
        assertThat(detail.subscribedDays()).isNull();
    }

    // --- 픽스처 -------------------------------------------------------------

    private int subscriptionRows() {
        return ((Number) entityManager.createNativeQuery("""
                select count(*) from motif_core.subscriptions
                 where user_seq = ?1 and artist_seq = ?2
                """).setParameter(1, me).setParameter(2, artistSeq).getSingleResult()).intValue();
    }

    private int subscriberCount() {
        return ((Number) entityManager.createNativeQuery(
                        "select subscriber_count from motif_core.artists where artist_seq = ?1")
                .setParameter(1, artistSeq).getSingleResult()).intValue();
    }

    private void softDelete(Integer target) {
        execute("update motif_core.artists set is_deleted = true where artist_seq = ?1", target);
        entityManager.flush();
        entityManager.clear();
    }

    private Integer newUser() {
        String nickname = "t-" + UUID.randomUUID().toString().substring(0, 20);
        execute("insert into motif_core.users (nickname, role) values (?1, 'USER')", nickname);
        return scalar("select user_seq from motif_core.users where nickname = ?1", nickname);
    }

    /** {@code users(ARTIST)} → {@code companies} → {@code artists} FK 체인. */
    private Integer newArtist() {
        Integer userSeq = newUser();
        String companyName = "t-" + UUID.randomUUID().toString().substring(0, 20);
        execute("insert into motif_core.companies (company_name, reg_usr_seq) values (?1, ?2)",
                companyName, userSeq);
        Integer companySeq = scalar(
                "select company_seq from motif_core.companies where company_name = ?1", companyName);

        execute("insert into motif_core.artists (user_seq, company_seq, reg_usr_seq) values (?1, ?2, ?1)",
                userSeq, companySeq);
        return scalar("select artist_seq from motif_core.artists where user_seq = ?1", userSeq);
    }

    private void execute(String sql, Object... params) {
        var query = entityManager.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) {
            query.setParameter(i + 1, params[i]);
        }
        query.executeUpdate();
    }

    private Integer scalar(String sql, Object param) {
        return ((Number) entityManager.createNativeQuery(sql)
                .setParameter(1, param)
                .getSingleResult()).intValue();
    }
}
