package com.ssafy.motif.coreapi.domain.composite.dto;

import com.ssafy.motif.domain.core.job.JobStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * 재시도 응답 (API 명세 §13.4) — <b>202 Accepted</b>.
 *
 * <p>{@code CompositeJobRequestResponse}(§13.2)와 모양이 거의 같지만 {@code attempt} 가
 * 하나 더 있다 — 재시도는 <b>몇 번째인지가 화면에 필요한</b> 유일한 자리다. 한 레코드로
 * 합치면 §13.2 응답에 없어야 할 칸이 생긴다.
 *
 * <p><b>{@code jobUuid} 가 그대로다.</b> 새 잡을 만들지 않고 같은 행을 재큐하므로
 * 클라이언트가 들고 있던 식별자를 계속 쓴다.
 */
@Schema(description = "재시도 응답 (§13.4) — 같은 잡이 다시 큐에 들어갔다")
public record CompositeJobRetryResponse(

        @Schema(description = "잡 식별자 — 재시도해도 바뀌지 않는다",
                example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID jobUuid,

        @Schema(description = "전이 후 상태 — 언제나 QUEUED", example = "QUEUED")
        JobStatus status,

        @Schema(description = "증가한 시도 횟수", example = "2")
        short attempt) {
}
