package com.ssafy.motif.coreapi.admin.security;

/**
 * 인증된 어드민 요청의 principal — 어드민 JWT claims 에서만 만든다 (명세 §1.2).
 *
 * @param adminSeq {@code admins.admin_seq} — 감사 <b>로그</b>의 수행자(§1.3)
 * @param userSeq  {@code admins.user_seq} → {@code users(role='ADMIN')} —
 *                 감사 <b>컬럼</b>({@code reg_usr_seq}·{@code mod_usr_seq}·
 *                 {@code processed_usr_seq})에 들어가는 값이다(§1.3). 둘은 다른 값이라
 *                 토큰이 함께 싣는다 — 매 요청 {@code admins} 조회를 없앤다
 */
public record AuthenticatedAdmin(int adminSeq, int userSeq, String name) {
}
