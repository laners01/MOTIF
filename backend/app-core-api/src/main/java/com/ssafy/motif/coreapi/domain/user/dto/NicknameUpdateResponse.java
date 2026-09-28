package com.ssafy.motif.coreapi.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 닉네임 수정 응답 (API 명세 §4.2).
 *
 * <p>확정된 닉네임 하나만 돌려준다. 마이페이지 전체를 다시 그려야 하면 §4.1 을 부른다 —
 * 수정 응답에 화면 전체를 실으면 §4.1 과 같은 것을 두 군데서 조립하게 된다.
 */
@Schema(description = "닉네임 수정 결과 (§4.2)")
public record NicknameUpdateResponse(String nickname) {
}
