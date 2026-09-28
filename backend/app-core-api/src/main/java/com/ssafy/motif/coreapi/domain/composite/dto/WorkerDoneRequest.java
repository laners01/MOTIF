package com.ssafy.motif.coreapi.domain.composite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * 합성 성공 콜백 본문 (AI 서버 명세 §3.3) — AI → {@code /done}.
 *
 * <p><b>snake_case 다</b>(작업명세 §3.1) — AI 가 만지는 JSON 은 전부 그렇다.
 *
 * @param resultVideoKey AI 가 실제로 PUT 한 키. <b>그대로 저장하지 않는다</b> — 서버가
 *                       {@code MediaKeys.forWorkerOutput} 으로 계산한 값과 <b>대조만</b>
 *                       한다. 콜백은 인터넷에서 오는 입력이라, 믿고 쓰면 남의 잡 결과나
 *                       엉뚱한 프리픽스를 가공 대상으로 넣을 수 있다
 * @param processingMs   컨테이너 시작부터 업로드 끝까지. 기록용이고 없어도 처리된다
 * @param attempt        AI 가 본 시도 횟수. <b>우리 값을 덮지 않는다</b> — 재시도 카운터의
 *                       주인은 잡 행이다(§13.4). 로그 대조용으로만 받는다
 * @param workerId       {@code CLOUD_RUN_EXECUTION}. 없어도 처리된다
 */
@JsonNaming(SnakeCaseStrategy.class)
public record WorkerDoneRequest(
        @NotBlank String resultVideoKey,
        @PositiveOrZero Integer processingMs,
        Integer attempt,
        String workerId) {
}
