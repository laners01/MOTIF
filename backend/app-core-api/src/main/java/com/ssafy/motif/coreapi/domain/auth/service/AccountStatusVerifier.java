package com.ssafy.motif.coreapi.domain.auth.service;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.domain.core.auth.UserAccount;
import com.ssafy.motif.domain.core.user.User;
import org.springframework.stereotype.Component;

/**
 * 토큰 발급 전 계정 상태 검사 (명세 §1.5 — "로그인·리프레시 시점에 모두 검증").
 *
 * <p>로그인(§2.1)과 재발급(§2.2)이 같은 판정을 써야 한다. 한쪽만 고쳐 규칙이
 * 갈라지면 정지된 계정이 재발급으로 서비스를 계속 쓰게 된다.
 *
 * <p>삭제를 정지보다 먼저 본다 — 두 상태가 겹칠 때 사용자에게 더 정확한 쪽은
 * "삭제된 계정"이다.
 */
@Component
public class AccountStatusVerifier {

    /**
     * @param account {@code user} 가 초기화된 상태로 넘긴다(fetch join).
     * @throws BusinessException 403 {@code ACCOUNT_DELETED} / {@code ACCOUNT_SUSPENDED}
     */
    public void verifyActive(UserAccount account) {
        User user = account.getUser();
        if (user.isDeleted()) {
            throw new BusinessException(CommonErrorCode.ACCOUNT_DELETED,
                    "삭제된 사용자: userSeq=" + user.getUserSeq());
        }
        if (account.isSuspended()) {
            throw new BusinessException(CommonErrorCode.ACCOUNT_SUSPENDED,
                    "정지된 계정: userSeq=" + user.getUserSeq());
        }
    }
}
