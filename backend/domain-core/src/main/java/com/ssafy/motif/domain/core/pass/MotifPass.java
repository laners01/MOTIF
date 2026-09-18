package com.ssafy.motif.domain.core.pass;

import com.ssafy.motif.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 회원이 보유한 모티프 패스 (DB SPEC v0.15 §35, changeSet 0009-06).
 *
 * <p><b>(회원, 아티스트)당 한 행이다</b>({@code uq_motif_passes_user_artist}). 최초 결제가
 * {@code PAID} 가 될 때 {@link #issue} 로 만들고, 만료·환불 뒤 재구매는 새 행이 아니라
 * <b>같은 행을 {@link #reactivate} 로 되살린다</b>(§35) — 그래서 {@code order_seq} 가
 * "현재 이용 기간을 만든 주문"으로 바뀐다. 둘 다 결제 승인(API 명세 §12.2)만 부른다.
 *
 * <p><b>이용 판정은 {@code status} 만으로 하지 않는다.</b> 만료 전이 배치가 아직 없어
 * 기간이 지난 패스도 {@code ACTIVE} 로 남아 있다 — {@code status='ACTIVE'} 이고
 * {@code expires_dttm} 이 아직 지나지 않은 것이 유효한 패스다
 * ({@link MotifPassRepository#findActive}, {@code MotifPassPriceRepository.findActivePassExpiry}).
 * 재활성 전에 "지금 유효한가"를 묻는 것도 그 조회의 몫이라 여기서는 다시 판정하지 않는다.
 *
 * <p>{@code canceledDttm} 은 <b>취소 의사 기록일 뿐 이용 판정에 불참한다</b>(DB §35) —
 * 다음 갱신을 하지 않겠다는 뜻이지 지금 못 쓴다는 뜻이 아니다. 재활성은 이 값을 지운다.
 *
 * <p>{@code REFUNDED}·{@code EXPIRED} 전이는 어드민 환불·만료 배치의 몫이라 아직 메서드가 없다.
 */
@Entity
@Table(name = "motif_passes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MotifPass extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pass_seq")
    private Integer passSeq;

    @Column(name = "user_seq", nullable = false, updatable = false)
    private Integer userSeq;

    @Column(name = "artist_seq", nullable = false, updatable = false)
    private Integer artistSeq;

    /** 현재 이용 기간을 만든 PAID 주문 — 재활성마다 바뀐다 (§35). */
    @Column(name = "order_seq", nullable = false)
    private Long orderSeq;

    @Column(name = "duration_months", nullable = false)
    private Short durationMonths;

    @Column(name = "started_dttm", nullable = false)
    private Instant startedDttm;

    @Column(name = "expires_dttm", nullable = false)
    private Instant expiresDttm;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private PassStatus status;

    @Column(name = "canceled_dttm")
    private Instant canceledDttm;

    /**
     * 최초 발급 (§12.2 7단계) — 이 회원이 이 아티스트의 패스 행을 아직 갖고 있지 않을 때.
     *
     * @param expiresDttm {@code startedDttm + durationMonths 개월} — 개월 계산은 호출부가 한다
     *                    (시간대가 필요해 엔티티가 정하지 않는다)
     */
    public static MotifPass issue(Integer userSeq, Integer artistSeq, Long orderSeq, short durationMonths,
                                  Instant startedDttm, Instant expiresDttm) {
        MotifPass pass = new MotifPass();
        pass.userSeq = userSeq;
        pass.artistSeq = artistSeq;
        pass.renew(orderSeq, durationMonths, startedDttm, expiresDttm);
        return pass;
    }

    /**
     * 만료·환불 뒤 재구매 (§35) — 같은 행을 새 주문으로 되살린다.
     *
     * <p>유효한 패스를 다시 살 수 없다는 판정({@code PASS_ALREADY_ACTIVE})은 승인 서비스가
     * DB 시각으로 먼저 끝내고 부른다 — 여기서 앱 시계로 다시 재면 두 판정이 갈릴 수 있다.
     */
    public void reactivate(Long orderSeq, short durationMonths, Instant startedDttm, Instant expiresDttm) {
        renew(orderSeq, durationMonths, startedDttm, expiresDttm);
    }

    private void renew(Long orderSeq, short durationMonths, Instant startedDttm, Instant expiresDttm) {
        this.orderSeq = orderSeq;
        this.durationMonths = durationMonths;
        this.startedDttm = startedDttm;
        this.expiresDttm = expiresDttm;
        this.status = PassStatus.ACTIVE;
        this.canceledDttm = null;
    }
}
