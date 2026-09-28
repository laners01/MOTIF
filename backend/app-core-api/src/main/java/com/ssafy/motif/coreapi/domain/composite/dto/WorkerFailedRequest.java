package com.ssafy.motif.coreapi.domain.composite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * 합성 실패 콜백 본문 (AI 서버 명세 §3.4) — AI → {@code /failed}.
 *
 * @param failCode    §6 표의 값. <b>패턴을 여기서 강제한다</b> —
 *                    {@code composite_jobs.fail_code} 가 {@code VARCHAR(30)} 이라
 *                    긴 값이 오면 INSERT 에서 잘리는 게 아니라 터진다. 400 으로 먼저
 *                    막는 편이 원인이 보인다
 * @param failMessage 로그용 자유 문장. <b>사용자에게 보이지 않는다</b> — 사용자 문구는
 *                    앱이 {@code failCode} 로 고른다(AI 명세 §6). 그래서 저장하지 않고
 *                    로그로만 남긴다
 * @param processingMs 기록용
 * @param attempt      우리 값을 덮지 않는다 ({@link WorkerDoneRequest} 와 같은 이유)
 * @param workerId     {@code CLOUD_RUN_EXECUTION}
 */
@JsonNaming(SnakeCaseStrategy.class)
public record WorkerFailedRequest(
        @NotBlank @Pattern(regexp = "^[A-Z_]{1,30}$") String failCode,
        @Size(max = 500) String failMessage,
        @PositiveOrZero Integer processingMs,
        Integer attempt,
        String workerId) {
}
