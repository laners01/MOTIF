package com.ssafy.motif.coreapi.admin.user;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.audit.AdminAuditLogger;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.admin.user.dto.UserSuspendResponse;
import com.ssafy.motif.coreapi.admin.user.dto.UserUnsuspendResponse;
import com.ssafy.motif.coreapi.domain.auth.service.RefreshTokenRevoker;
import com.ssafy.motif.coreapi.domain.user.UserErrorCode;
import com.ssafy.motif.domain.core.auth.AccountStatus;
import com.ssafy.motif.domain.core.auth.UserAccount;
import com.ssafy.motif.domain.core.auth.UserAccountRepository;
import com.ssafy.motif.domain.core.user.User;
import com.ssafy.motif.domain.core.user.UserRepository;
import com.ssafy.motif.domain.core.user.UserRole;
import com.ssafy.motif.event.contract.UserSuspendedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * 회원 정지·해제 (어드민 명세 §3.14.3~4) — S15P21D104-582.
 *
 * <p>조회(§3.14.1~2)는 {@link AdminUserQueryService} 다 — 사용자 도메인의
 * {@code UserQueryService}·{@code UserCommandService} 와 같은 가름이다.
 *
 * <p><b>정지는 두 가지를 한 트랜잭션에 한다</b> — 계정 상태 전이와 refresh 토큰 전량
 * 폐기다. 나누면 "정지는 됐는데 토큰은 살아 있는" 창이 생기고, 그 창에서 재발급 한 번이면
 * 정지가 무의미해진다. 기기 토큰 비활성은 커밋 후 {@code user.suspended} 로 넘긴다(583).
 *
 * <p><b>이미 발급된 access token 은 만료까지 산다.</b> JWT 라 서버 상태가 없다 —
 * 명세 §1.5 가 수용한 동작이고, 재진입은 {@code AccountStatusVerifier} 가 로그인·재발급
 * 양쪽에서 막는다. 인증 필터에 상태 검사를 넣지 않는 이유는 매 요청 계정 조회가 붙어서다.
 */
@Service
@RequiredArgsConstructor
public class AdminUserCommandService {

    private final UserRepository userRepository;
    private final UserAccountRepository userAccountRepository;
    private final RefreshTokenRevoker refreshTokenRevoker;
    private final ApplicationEventPublisher eventPublisher;
    private final AdminAuditLogger auditLogger;

    /**
     * §3.14.3 — 정지. 상태 전이 + refresh 토큰 전량 폐기, 커밋 후 {@code user.suspended}.
     *
     * <p>{@code reason} 은 저장하지 않는다 — {@code user_accounts} 에 칸이 없다. 감사
     * 로그에만 남으므로 여기서 빠뜨리면 "왜 정지했는가"가 어디에도 남지 않는다(§1.3).
     */
    @Transactional
    public UserSuspendResponse suspend(AuthenticatedAdmin admin, Integer userSeq, String reason) {
        UserAccount account = suspendableAccount(userSeq);
        if (account.isSuspended()) {
            throw new BusinessException(AdminErrorCode.USER_ALREADY_SUSPENDED,
                    "이미 정지된 회원: userSeq=" + userSeq);
        }

        account.suspend();
        int revokedTokenCount = refreshTokenRevoker.revokeAll(userSeq);
        eventPublisher.publishEvent(new UserSuspendedEvent(
                UUID.randomUUID().toString(), Instant.now(), userSeq));
        auditLogger.write(admin, "SUSPEND_USER", "USER", userSeq, AdminAuditLogger.RESULT_OK, reason);

        return new UserSuspendResponse(userSeq, AccountStatus.SUSPENDED, revokedTokenCount);
    }

    /**
     * §3.14.4 — 해제. {@code ACTIVE} 전이만 하고 토큰은 복구하지 않는다.
     *
     * <p>해제 이벤트가 없다 — 기기 토큰은 재로그인 시 재등록으로 살아난다(명세 §4).
     */
    @Transactional
    public UserUnsuspendResponse unsuspend(AuthenticatedAdmin admin, Integer userSeq) {
        UserAccount account = suspendableAccount(userSeq);
        if (!account.isSuspended()) {
            throw new BusinessException(AdminErrorCode.USER_NOT_SUSPENDED,
                    "정지 상태가 아닌 회원: userSeq=" + userSeq);
        }

        account.unsuspend();
        auditLogger.write(admin, "UNSUSPEND_USER", "USER", userSeq, AdminAuditLogger.RESULT_OK, null);

        return new UserUnsuspendResponse(userSeq, AccountStatus.ACTIVE);
    }

    /**
     * 정지·해제의 공통 선행 검사 — 404(미존재) → 400(대상 아님) 순이다.
     *
     * <p><b>{@code role} 을 먼저 본다.</b> 아티스트·어드민은 {@code user_accounts} 행
     * 자체가 없어(아티스트는 로그인하지 않는다) 계정부터 찾으면 "없음"과 "대상 아님"이
     * 구분되지 않는다. 명세 표의 400 {@code INVALID_PARAMETER} 가 이 자리다.
     *
     * <p>삭제된 회원은 정지할 수 있다 — 탈퇴 후에도 콘텐츠가 남아 신고 처리 대상이 된다.
     */
    private UserAccount suspendableAccount(Integer userSeq) {
        User user = userRepository.findById(userSeq)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND,
                        "존재하지 않는 사용자: userSeq=" + userSeq));
        if (user.getRole() != UserRole.USER) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER,
                    "정지 대상이 아닌 역할: userSeq=" + userSeq + " role=" + user.getRole());
        }
        return userAccountRepository.findWithUserByUserSeq(userSeq)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND,
                        "로그인 계정이 없는 사용자: userSeq=" + userSeq));
    }
}
