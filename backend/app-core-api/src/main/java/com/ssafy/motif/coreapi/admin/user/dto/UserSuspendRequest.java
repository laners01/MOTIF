package com.ssafy.motif.coreapi.admin.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 회원 정지 요청 (어드민 명세 §3.14.3).
 *
 * @param reason <b>저장되지 않는다</b> — {@code user_accounts} 에 사유 칸이 없다.
 *               감사 로그({@code AdminAuditLogger})에만 남는다. 그래도 필수로 받는 것은
 *               "왜 정지했는가"가 남지 않는 정지를 만들지 않기 위해서다(§1.3)
 */
public record UserSuspendRequest(
        @NotBlank @Size(max = 200) String reason
) {
}
