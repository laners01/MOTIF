package com.ssafy.motif.coreapi.admin.auth;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.auth.dto.AdminLoginRequest;
import com.ssafy.motif.coreapi.admin.auth.dto.AdminLoginResponse;
import com.ssafy.motif.coreapi.admin.security.AdminJwtProvider;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.domain.core.admin.Admin;
import com.ssafy.motif.domain.core.admin.AdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 어드민 로그인 (어드민 명세 §3.1.1).
 *
 * <p>토큰 발급은 {@link AdminJwtProvider#issue} 가 한다 — 여기서 JWT 를 조립하지
 * 않는다. 서명키·클레임·만료가 두 곳에 생기면 검증({@code parse})과 어긋나는 순간
 * 로그인은 되는데 모든 요청이 401 이 되는 식으로 갈라진다.
 *
 * <p><b>로그인 실패를 하나로 합친다.</b> 아이디가 없든 비밀번호가 틀리든 같은 401
 * {@code ADMIN_LOGIN_FAILED} 다(§3.1.1 "구분하지 않음") — 응답을 나누면 어떤
 * login_id 가 실재하는지 알려주는 계정 열거 창구가 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminJwtProvider jwtProvider;

    /**
     * @throws BusinessException {@code ADMIN_LOGIN_FAILED}(401) 또는
     *                           {@code ADMIN_INACTIVE}(403)
     */
    @Transactional
    public AdminLoginResponse login(AdminLoginRequest request) {
        Admin admin = adminRepository.findByLoginId(request.loginId())
                .orElse(null);

        // 계정이 없어도 해시 대조를 돌린다 — 존재하는 아이디만 BCrypt 비용(수십 ms)을
        // 치르면 응답 시간 차이로 계정 존재가 드러난다. 에러 코드를 합친 의미가 없어진다.
        boolean matched = passwordEncoder.matches(
                request.password(),
                Optional.ofNullable(admin).map(Admin::getPasswordHash).orElse(DUMMY_HASH));

        if (admin == null || !matched) {
            log.warn("어드민 로그인 실패 — loginId={}", request.loginId());
            throw new BusinessException(AdminErrorCode.ADMIN_LOGIN_FAILED,
                    "로그인 정보가 올바르지 않습니다.");
        }
        if (!admin.isActive()) {
            log.warn("비활성 어드민 로그인 시도 — adminSeq={}", admin.getAdminSeq());
            throw new BusinessException(AdminErrorCode.ADMIN_INACTIVE,
                    "비활성화된 어드민 계정입니다.");
        }

        admin.markLoggedIn(OffsetDateTime.now());

        AuthenticatedAdmin principal =
                new AuthenticatedAdmin(admin.getAdminSeq(), admin.getUserSeq(), admin.getName());
        log.info("어드민 로그인 — adminSeq={} usr={}", principal.adminSeq(), principal.userSeq());
        return new AdminLoginResponse(
                principal.adminSeq(), principal.name(), principal.userSeq(),
                jwtProvider.issue(principal), jwtProvider.ttlSeconds());
    }

    /**
     * 존재하지 않는 계정에도 같은 시간을 쓰기 위한 대조용 해시.
     *
     * <p>어떤 평문과도 일치하지 않는다 — 임의 문자열을 BCrypt(cost 10) 로 한 번 돌린
     * 값이고, 원본 평문은 버렸다. 값 자체는 비밀이 아니며 비교 비용만이 목적이다.
     */
    private static final String DUMMY_HASH =
            "$2a$10$JhaeUYA8B.13AY0td.wMVOL0mhgESbMbojQMOQ3zAN0PEefS08ksi";
}
