package com.ssafy.motif.coreapi.domain.user.dto;

import com.ssafy.motif.coreapi.global.validation.Nickname;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 닉네임 수정 요청 (API 명세 §4.2).
 *
 * <p><b>형식 검증만 한다. 중복 확인은 하지 않는다</b>(C1 회의 확정 —
 * {@code users.nickname} 에 UNIQUE 가 없다). 규칙은 온보딩(§3.3)과 <b>같은
 * {@link Nickname}</b> 이다 — 두 곳이 갈리면 온보딩에서 정한 닉네임을 수정 화면에서
 * 못 바꾸는 상태가 생긴다.
 *
 * <p>앞뒤 공백을 <b>다듬지 않는다.</b> 패턴에 공백이 없으므로 {@code " 닉"} 은 400 이다 —
 * 서버가 조용히 고쳐 주면 사용자가 보낸 값과 저장된 값이 달라진다.
 */
public record NicknameUpdateRequest(

        @Nickname
        @Schema(description = "새 닉네임. 영문·한글·숫자·마침표·언더바 2~20자. 중복 허용",
                example = "새닉네임", requiredMode = Schema.RequiredMode.REQUIRED)
        String nickname
) {
}
