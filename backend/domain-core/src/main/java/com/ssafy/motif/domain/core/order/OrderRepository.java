package com.ssafy.motif.domain.core.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * 외부 식별자로 주문을 집는다 — {@code uq_orders_order_uuid} 가 유일성을 보장한다.
     *
     * <p>클라이언트와 토스는 {@code orderUuid} 만 안다({@link Order} javadoc). 승인·취소
     * (§12.2·§12.3)는 {@link #findByOrderUuidAndUserSeq} 로 <b>본인 조건</b>을 함께 건다.
     */
    Optional<Order> findByOrderUuid(UUID orderUuid);

    /**
     * 본인 주문 (API 명세 §12.2·§12.3) — 없는 주문과 타인 주문을 가르지 않는다.
     *
     * <p>둘을 가르면 "그 주문은 존재한다"가 응답에서 읽히므로 같은 404
     * {@code ORDER_NOT_FOUND} 다(§1.8 존재 은닉과 같은 규칙).
     */
    Optional<Order> findByOrderUuidAndUserSeq(UUID orderUuid, Integer userSeq);

    /**
     * 이 회원의 결제 승인을 <b>트랜잭션 끝까지 직렬화</b>한다 (API 명세 §12.2).
     *
     * <p>12.1 이 같은 상품의 {@code PENDING} 주문을 여러 개 허용하므로, 같은 회원의 두 주문이
     * 동시에 승인되면 둘 다 재검증을 통과해 토스 승인까지 간 뒤 두 번째가
     * {@code uq_asset_purchases_active}·{@code uq_motif_passes_user_artist} 에서 터진다 —
     * 돈은 빠졌는데 권한은 없는 상태다. 회원 단위 advisory lock 으로 승인을 한 줄로 세우면
     * 두 번째는 첫 번째가 커밋한 뒤 재검증에서 {@code ALREADY_OWNED} ·
     * {@code PASS_ALREADY_ACTIVE} 로 걸린다.
     *
     * <p>{@code pg_advisory_xact_lock} 은 트랜잭션이 끝나면 저절로 풀린다 — 토스 호출 중
     * 예외가 나도 남지 않는다. 첫 인자 {@code 1} 은 이 용도의 네임스페이스다(다른 advisory
     * lock 사용처가 생기면 다른 값을 쓴다).
     *
     * @return 항상 1 — 잠금 함수가 void 라 행 수를 세는 형태로 감쌌다
     */
    @Query(value = "select count(*) from (select pg_advisory_xact_lock(1, :userSeq)) t", nativeQuery = true)
    long lockUserOrders(@Param("userSeq") Integer userSeq);

    /**
     * {@code PENDING → PAID} 조건부 UPDATE (API 명세 §12.2 4단계 · DB §33).
     *
     * <p><b>이 UPDATE 가 만료·중복 승인 방어다.</b> 만료 판정을 {@code expires_dttm > now()}
     * 로 DB 시각에 두고, 영향 행이 0이면 호출부가 만료인지 중복인지를 다시 읽어 가른다.
     * 네이티브로 쓴 이유는 명세의 SQL 을 그대로 옮기기 위해서고, {@code now()} 를 한
     * 트랜잭션 안에서 일관되게 쓰기 위해서다.
     *
     * <p>{@code approved_dttm} 도 {@code now()} 다 — 토스 승인 <b>전</b>에 이 UPDATE 를 치고
     * 같은 트랜잭션에서 승인을 부르므로(승인 서비스 javadoc), 커밋 전에는 밖에서 PAID 가
     * 보이지 않는다. 승인이 거절되면 트랜잭션이 통째로 되돌아간다.
     *
     * <p>두 플래그는 {@code MotifAssetPriceRepository.deactivateAll} 과 같은 이유다 —
     * 벌크 UPDATE 는 영속성 컨텍스트를 우회하므로, 이어서 읽는 주문이 옛 값을 들고 있으면
     * 안 된다.
     *
     * @return 1 이면 전이됨, 0 이면 만료 또는 이미 PENDING 이 아님
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update motif_core.orders
               set status = 'PAID',
                   payment_provider = :provider,
                   payment_key = :paymentKey,
                   approved_dttm = now(),
                   mod_dttm = now()
             where order_seq = :orderSeq
               and status = 'PENDING'
               and expires_dttm > now()
            """, nativeQuery = true)
    int markPaid(@Param("orderSeq") Long orderSeq,
                 @Param("provider") String provider,
                 @Param("paymentKey") String paymentKey);

    /**
     * {@code PENDING → EXPIRED} (API 명세 §12.2 2단계 · DB §33).
     *
     * <p>승인 직전 재검증에서 판매 중단·만료가 드러난 주문을 정리한다. 배치가 하는 일과
     * 같지만 발견한 자리에서 바로 바꾸는 이유는, 그대로 두면 클라이언트가 만료된 주문을
     * 다시 승인하려 들 때마다 같은 재검증을 반복하기 때문이다.
     *
     * @return 1 이면 전이됨, 0 이면 이미 PENDING 이 아님
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update motif_core.orders
               set status = 'EXPIRED', mod_dttm = now()
             where order_seq = :orderSeq and status = 'PENDING'
            """, nativeQuery = true)
    int markExpired(@Param("orderSeq") Long orderSeq);

    /**
     * {@code PENDING → FAILED} — 토스가 승인을 <b>확정 거절</b>했을 때 (API 명세 §12.2 3단계).
     *
     * <p>{@code ck_orders_status_shape} 가 FAILED 에 {@code payment_provider}·{@code fail_reason}
     * 을 요구하고 {@code approved_dttm} 은 NULL 이어야 한다. {@code payment_key} 도 남긴다 —
     * 토스 콘솔에서 이 거절 건을 찾는 열쇠다. 실패한 주문은 되살리지 않는다(재시도는 새 주문).
     *
     * <p>PAID 전이가 되돌아간 <b>뒤</b>에 별도 트랜잭션으로 불러야 한다 — 같은 트랜잭션 안에서
     * 부르면 롤백과 함께 사라진다.
     *
     * @param reason 200자 이내 — 호출부가 자른다
     * @return 1 이면 전이됨, 0 이면 이미 PENDING 이 아님
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update motif_core.orders
               set status = 'FAILED',
                   payment_provider = :provider,
                   payment_key = :paymentKey,
                   fail_reason = :reason,
                   mod_dttm = now()
             where order_seq = :orderSeq and status = 'PENDING'
            """, nativeQuery = true)
    int markFailed(@Param("orderSeq") Long orderSeq,
                   @Param("provider") String provider,
                   @Param("paymentKey") String paymentKey,
                   @Param("reason") String reason);
}
