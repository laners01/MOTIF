package com.ssafy.motif.domain.core.user;

/**
 * 회원 상세의 통계 (어드민 명세 §3.14.2) — {@link UserRepository#stats} 투영.
 *
 * <p><b>엔티티가 아니라 네이티브 집계다.</b> {@code asset_purchases}·{@code reports}·
 * 댓글 3종은 테이블만 있고 JPA 엔티티가 없다. 숫자 하나씩을 위해 엔티티 4개를 만드는
 * 대신 서브쿼리 한 방으로 센다 — 상세 화면 1건당 1회다. 그 테이블을 소유할 티켓
 * (구매 §12 · 신고 §3.13)이 엔티티를 만들면 해당 줄을 그리로 옮긴다
 * ({@code MotifPassRepository.countActivePasses} 와 같은 규칙).
 *
 * <p>유효 패스는 여기 없다 — {@code MotifPass} 읽기 전용 엔티티가 생겨
 * {@code MotifPassRepository.findActive} 가 JPQL 로 가져온다.
 *
 * <p>별칭을 큰따옴표로 감싸는 이유는 Postgres 가 따옴표 없는 별칭을 소문자로 접기
 * 때문이다. 접히면 {@code videocount} 가 되어 이 인터페이스의 getter 와 짝이 안 맞는다.
 */
public interface UserStatsRow {

    long getVideoCount();

    long getSubscriptionCount();

    long getActivePurchaseCount();

    /** 이 사용자의 콘텐츠(참여 영상·댓글 3종)가 신고당한 건수. */
    long getReportedCount();

    /** 이 사용자가 신고한 건수. */
    long getReportCount();
}
