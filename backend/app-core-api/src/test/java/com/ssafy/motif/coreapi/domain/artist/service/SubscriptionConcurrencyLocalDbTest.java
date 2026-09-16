package com.ssafy.motif.coreapi.domain.artist.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 구독 동시성 (S15P21D104-59 · 명세 §5.6 · §5.7) — 연타로 카운터가 어긋나지 않는지.
 *
 * <p><b>{@code @Transactional} 을 붙이지 않는다.</b> 테스트 트랜잭션을 걸면 모든 스레드가
 * 그 트랜잭션을 공유하거나 서로의 미커밋 데이터를 못 봐서 <b>동시성이 재현되지 않는다</b> —
 * 통과해도 아무것도 증명하지 못하는 테스트가 된다. 대신 실제로 커밋하고
 * {@link #tearDown} 에서 직접 치운다.
 *
 * <p>여기서 지키려는 것은 하나다: <b>삽입·삭제가 실제로 행을 건드렸을 때만 카운터를
 * 움직인다.</b> {@code existsById} 로 확인하고 갈라졌다면 구독은 카운터가 2 오르고,
 * 해제는 {@code ck_artists_subscriber_count CHECK (subscriber_count >= 0)} 위반으로
 * DB 가 예외를 던진다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
class SubscriptionConcurrencyLocalDbTest {

    /** 연타 한 번이 만드는 동시 요청 수 — 디바운스를 뚫고 들어온 상황을 흉내 낸다. */
    private static final int THREADS = 8;

    @Autowired
    private SubscriptionCommandService subscriptionCommandService;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @PersistenceContext
    private EntityManager entityManager;

    private Integer me;
    private Integer artistSeq;
    private Integer artistUserSeq;
    private Integer companySeq;

    @BeforeEach
    void setUp() {
        inTransaction(() -> {
            me = newUser();
            newArtist();
        });
    }

    /** 커밋한 행을 직접 치운다 — 롤백이 없다. FK 역순으로 지운다. */
    @AfterEach
    void tearDown() {
        inTransaction(() -> {
            execute("delete from motif_core.subscriptions where artist_seq = ?1", artistSeq);
            execute("delete from motif_core.artists where artist_seq = ?1", artistSeq);
            execute("delete from motif_core.companies where company_seq = ?1", companySeq);
            execute("delete from motif_core.users where user_seq in (?1, ?2)", me, artistUserSeq);
        });
    }

    /**
     * 픽스처를 <b>실제로 커밋</b>한다.
     *
     * <p>테스트 클래스의 메서드에 {@code @Transactional} 을 붙여 봐야 같은 클래스 안에서
     * 부르면 프록시를 타지 않아 {@code TransactionRequiredException} 이 난다. 명시적으로
     * 템플릿을 쓴다.
     */
    private void inTransaction(Runnable work) {
        transactionTemplate.executeWithoutResult(status -> work.run());
    }

    private <T> T inTransaction(java.util.function.Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }

    @Test
    @DisplayName("동시에 8번 구독해도 카운터는 1만 오른다 — 행도 1개다")
    void 동시_구독() throws Exception {
        runConcurrently(() -> subscriptionCommandService.subscribe(me, artistSeq));

        assertThat(subscriptionRows()).isEqualTo(1);
        assertThat(subscriberCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("동시에 8번 해제해도 카운터가 음수가 되지 않는다 — CHECK 위반이 없다")
    void 동시_해제() throws Exception {
        subscriptionCommandService.subscribe(me, artistSeq);

        runConcurrently(() -> subscriptionCommandService.unsubscribe(me, artistSeq));

        assertThat(subscriptionRows()).isZero();
        assertThat(subscriberCount()).isZero();
    }

    @Test
    @DisplayName("구독·해제가 뒤섞여도 카운터는 행 수와 일치한다")
    void 구독_해제_혼재() throws Exception {
        List<Callable<Object>> tasks = new java.util.ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            boolean subscribe = i % 2 == 0;
            tasks.add(() -> subscribe
                    ? subscriptionCommandService.subscribe(me, artistSeq)
                    : subscriptionCommandService.unsubscribe(me, artistSeq));
        }
        runAll(tasks);

        // 최종 상태가 구독인지 해제인지는 실행 순서에 달렸다 — 고정할 수 없고 고정해서도
        // 안 된다. 지켜야 할 불변식은 "거울 카운터가 실제 행 수와 같다" 하나뿐이다
        assertThat(subscriberCount()).isEqualTo(subscriptionRows());
    }

    private void runConcurrently(Runnable action) throws Exception {
        List<Callable<Object>> tasks = new java.util.ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> {
                action.run();
                return null;
            });
        }
        runAll(tasks);
    }

    /**
     * 모든 스레드를 <b>같은 순간에</b> 풀어놓는다.
     *
     * <p>{@code invokeAll} 만 쓰면 스레드가 순차로 시작돼 경합이 안 생길 수 있다.
     * 래치로 출발선을 맞춘다.
     */
    private void runAll(List<Callable<Object>> tasks) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(tasks.size())) {
            List<Future<Object>> futures = tasks.stream()
                    .map(task -> pool.submit(() -> {
                        start.await();
                        return task.call();
                    }))
                    .toList();
            start.countDown();

            for (Future<Object> future : futures) {
                // 예외가 났으면 여기서 터진다 — 카운터 CHECK 위반이 조용히 묻히지 않는다
                future.get();
            }
        }
    }

    // --- 픽스처 (전부 inTransaction 안에서 부른다) ---------------------------

    private int subscriptionRows() {
        return inTransaction(() -> ((Number) entityManager.createNativeQuery("""
                select count(*) from motif_core.subscriptions
                 where user_seq = ?1 and artist_seq = ?2
                """).setParameter(1, me).setParameter(2, artistSeq).getSingleResult()).intValue());
    }

    private int subscriberCount() {
        return inTransaction(() -> ((Number) entityManager.createNativeQuery(
                        "select subscriber_count from motif_core.artists where artist_seq = ?1")
                .setParameter(1, artistSeq).getSingleResult()).intValue());
    }

    private Integer newUser() {
        String nickname = "t-" + UUID.randomUUID().toString().substring(0, 20);
        execute("insert into motif_core.users (nickname, role) values (?1, 'USER')", nickname);
        return scalar("select user_seq from motif_core.users where nickname = ?1", nickname);
    }

    private void newArtist() {
        artistUserSeq = newUser();
        String companyName = "t-" + UUID.randomUUID().toString().substring(0, 20);
        execute("insert into motif_core.companies (company_name, reg_usr_seq) values (?1, ?2)",
                companyName, artistUserSeq);
        companySeq = scalar(
                "select company_seq from motif_core.companies where company_name = ?1", companyName);

        execute("insert into motif_core.artists (user_seq, company_seq, reg_usr_seq) values (?1, ?2, ?1)",
                artistUserSeq, companySeq);
        artistSeq = scalar(
                "select artist_seq from motif_core.artists where user_seq = ?1", artistUserSeq);
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
