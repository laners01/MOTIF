package com.ssafy.motif.domain.core.user;

import com.ssafy.motif.domain.core.auth.AccountStatus;

import java.time.Instant;

/**
 * 회원 목록 한 줄 (어드민 명세 §3.14.1) — {@link UserRepository#search} 투영.
 *
 * <p>{@code accountStatus} 는 {@code null} 일 수 있다 — 아티스트는 로그인하지 않아
 * {@code user_accounts} 행이 없다({@code AdminArtistService} javadoc "user_accounts 는
 * 만들지 않는다"). 정지 대상도 아니므로 화면에서는 상태 배지를 비운다.
 *
 * <p>통계 6종은 여기 없다 — 행마다 세면 N+1 이다. 상세(§3.14.2)에서만 한 번 집계한다.
 */
public record UserListRow(
        Integer userSeq, String nickname, String profileImageKey, UserRole role,
        AccountStatus accountStatus, boolean deleted, Instant regDttm
) {
}
