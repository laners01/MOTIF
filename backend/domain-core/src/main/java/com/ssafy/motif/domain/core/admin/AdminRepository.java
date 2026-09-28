package com.ssafy.motif.domain.core.admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin, Integer> {

    /**
     * 로그인 조회 (§3.1.1).
     *
     * <p>{@code is_active} 를 조건에 넣지 않는다 — 비활성 계정은 401 이 아니라 403
     * {@code ADMIN_INACTIVE} 로 구분해 응답해야 하므로(§3.1.1 표) 행을 읽어 온 뒤
     * 서비스가 판정한다.
     */
    Optional<Admin> findByLoginId(String loginId);
}
