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
 * <p><b>생성자가 없다 — 읽기 전용이다.</b> 발급은 결제 승인(§12)이 하고 만료 전이는
 * 배치(§3.16)가 한다. 둘 다 이 티켓 범위 밖이라 여기서는 어드민 회원 상세(§3.14.2)가
 * 읽기만 한다 ({@code ParticipationVideo}·{@code CompositeJob} 과 같은 규칙).
 *
 * <p><b>이용 판정은 {@code status} 만으로 하지 않는다.</b> 만료 전이 배치가 아직 없어
 * 기간이 지난 패스도 {@code ACTIVE} 로 남아 있다 — {@code status='ACTIVE'} 이고
 * {@code expires_dttm} 이 아직 지나지 않은 것이 유효한 패스다
 * ({@link MotifPassRepository#findActive}).
 *
 * <p>{@code canceledDttm} 은 <b>취소 의사 기록일 뿐 이용 판정에 불참한다</b>(DB §35) —
 * 다음 갱신을 하지 않겠다는 뜻이지 지금 못 쓴다는 뜻이 아니다.
 *
 * <p>{@code uq_motif_passes_user_artist} 로 (회원, 아티스트)당 한 행이다.
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

    @Column(name = "order_seq", nullable = false, updatable = false)
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
}
