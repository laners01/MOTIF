package com.ssafy.motif.coreapi.admin.auth.dto;

import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;

/**
 * 현재 어드민 (어드민 명세 §3.1.3).
 *
 * <p>토큰 클레임을 그대로 돌려준다 — {@code admins} 를 다시 읽지 않는다. 화면이
 * 헤더에 이름을 찍으려고 부르는 값이고, 클레임과 다른 값을 줄 이유가 없다. 계정이
 * 비활성으로 바뀌어도 만료(12시간) 전까지 토큰이 유효한 것은 stateless 설계의
 * 결과다(§1.2 — 서버 측 폐기 목록 없음).
 */
public record AdminMeResponse(int adminSeq, String name, int userSeq) {

    public static AdminMeResponse from(AuthenticatedAdmin admin) {
        return new AdminMeResponse(admin.adminSeq(), admin.name(), admin.userSeq());
    }
}
