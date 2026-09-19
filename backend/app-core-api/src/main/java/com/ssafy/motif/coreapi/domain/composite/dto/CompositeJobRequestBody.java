package com.ssafy.motif.coreapi.domain.composite.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 합성 요청 본문 (API 명세 §13.2) — CHALLENGE 만 값을 갖는다.
 *
 * <p><b>MOTIF 는 {@code {}} 를 보낸다.</b> 보정을 쓰지 않으며 서버 내부 값은 {@code false}
 * 고정이라, 값이 와도 무시한다(S15P21D104-247).
 *
 * @param correctionEnabled 촬영 후 사용자가 고른 보정 여부. <b>생략하면 {@code false}</b>
 *                          <i>(기본안)</i> — §13.1 이 만든 행의 값과 같아, 본문을 비워 보낸
 *                          클라이언트가 보정을 우연히 켜게 되지 않는다
 */
@Schema(description = "합성 요청 본문 (§13.2) — MOTIF 는 빈 객체를 보낸다")
public record CompositeJobRequestBody(

        @Schema(description = "보정 여부 (CHALLENGE 전용). MOTIF 는 보내지 않는다 — 와도 무시",
                example = "true")
        Boolean correctionEnabled) {

    /** 본문이 통째로 없을 수도 있다 — {@code @RequestBody(required = false)} 가 {@code null} 을 준다. */
    public boolean correctionEnabledOrFalse() {
        return Boolean.TRUE.equals(correctionEnabled);
    }
}
