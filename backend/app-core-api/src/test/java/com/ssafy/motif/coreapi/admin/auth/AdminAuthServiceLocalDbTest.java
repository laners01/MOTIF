package com.ssafy.motif.coreapi.admin.auth;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.auth.dto.AdminLoginRequest;
import com.ssafy.motif.coreapi.admin.auth.dto.AdminLoginResponse;
import com.ssafy.motif.coreapi.admin.security.AdminJwtProvider;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.domain.core.admin.Admin;
import com.ssafy.motif.domain.core.admin.AdminRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 어드민 로그인 §3.1.1 을 실제 DB 에 대고 검증한다 (S15P21D104-608 DoD).
 *
 * <p>0014(`admins.user_seq` NOT NULL FK)와 0015(시드)가 함께 적용된 스키마가 있어야
 * 의미가 있는 테스트라 {@code local-db} 태그를 단다 — Jenkins 에이전트에는 DB 가 없다.
 *
 * <p>계정은 매번 새로 만든다. 시드 계정({@code ops_admin})에 로그인해 버리면
 * {@code last_login_dttm} 이 바뀌고, 그 값을 보는 다음 테스트가 순서에 따라 흔들린다.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class AdminAuthServiceLocalDbTest {

    @Autowired
    private AdminAuthService adminAuthService;
    @Autowired
    private AdminRepository adminRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AdminJwtProvider jwtProvider;

    @PersistenceContext
    private EntityManager em;

    private static final String PASSWORD = "pw-for-test-1234";

    @Test
    @DisplayName("아이디·비밀번호가 맞으면 토큰을 주고 last_login_dttm 을 갱신한다")
    void 로그인_성공() {
        Admin admin = persistAdmin(true);

        AdminLoginResponse response =
                adminAuthService.login(new AdminLoginRequest(admin.getLoginId(), PASSWORD));

        assertThat(response.adminSeq()).isEqualTo(admin.getAdminSeq());
        assertThat(response.userSeq()).isEqualTo(admin.getUserSeq());
        assertThat(response.expiresIn()).isEqualTo(jwtProvider.ttlSeconds());

        // 발급한 토큰이 검증기를 그대로 통과해야 한다 — 여기가 갈라지면 로그인은
        // 되는데 이후 모든 어드민 요청이 401 이 된다.
        AuthenticatedAdmin parsed = jwtProvider.parse(response.accessToken());
        assertThat(parsed).isEqualTo(
                new AuthenticatedAdmin(admin.getAdminSeq(), admin.getUserSeq(), admin.getName()));

        em.flush();
        assertThat(admin.getLastLoginDttm()).as("§3.1.1 last_login_dttm 갱신").isNotNull();
    }

    @Test
    @DisplayName("is_active=FALSE 면 403 ADMIN_INACTIVE — 비밀번호가 맞아도 거부한다")
    void 비활성_거부() {
        Admin admin = persistAdmin(false);

        assertThatThrownBy(() ->
                adminAuthService.login(new AdminLoginRequest(admin.getLoginId(), PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.ADMIN_INACTIVE);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 401 ADMIN_LOGIN_FAILED")
    void 비밀번호_불일치() {
        Admin admin = persistAdmin(true);

        assertThatThrownBy(() ->
                adminAuthService.login(new AdminLoginRequest(admin.getLoginId(), "wrong-password")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.ADMIN_LOGIN_FAILED);
    }

    @Test
    @DisplayName("없는 아이디도 같은 401 이다 — 계정 존재를 구분해 알려주지 않는다")
    void 없는_아이디도_같은_코드() {
        assertThatThrownBy(() ->
                adminAuthService.login(new AdminLoginRequest("no-such-admin", PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.ADMIN_LOGIN_FAILED);
    }

    @Test
    @DisplayName("시드 계정(ops_admin)이 들어와 있고 users(ADMIN) 과 이어져 있다 — 부록 B 5항")
    void 시드_계정() {
        Admin seeded = adminRepository.findByLoginId("ops_admin").orElseThrow();

        assertThat(seeded.isActive()).isTrue();
        assertThat(seeded.getUserSeq()).isNotNull();
        String role = (String) em.createNativeQuery(
                        "SELECT role FROM users WHERE user_seq = :seq")
                .setParameter("seq", seeded.getUserSeq())
                .getSingleResult();
        assertThat(role).isEqualTo("ADMIN");
    }

    /**
     * {@code admins} 는 가입 경로가 없어 엔티티에 생성자가 없다(의도). 테스트 계정은
     * 시드와 같은 순서로 네이티브 INSERT 한다 — users(ADMIN) 먼저, 그다음 admins.
     */
    private Admin persistAdmin(boolean active) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String loginId = "t_" + suffix;

        em.createNativeQuery("INSERT INTO users (nickname, role) VALUES (:nick, 'ADMIN')")
                .setParameter("nick", "admin_" + loginId)
                .executeUpdate();
        Integer userSeq = (Integer) em.createNativeQuery(
                        "SELECT user_seq FROM users WHERE nickname = :nick")
                .setParameter("nick", "admin_" + loginId)
                .getSingleResult();

        em.createNativeQuery("""
                        INSERT INTO admins (login_id, password_hash, name, is_active, user_seq)
                        VALUES (:loginId, :hash, :name, :active, :userSeq)
                        """)
                .setParameter("loginId", loginId)
                .setParameter("hash", passwordEncoder.encode(PASSWORD))
                .setParameter("name", "테스트운영자")
                .setParameter("active", active)
                .setParameter("userSeq", userSeq)
                .executeUpdate();
        em.clear();

        return adminRepository.findByLoginId(loginId).orElseThrow();
    }
}
