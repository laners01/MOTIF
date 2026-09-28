package com.ssafy.motif.coreapi.domain.composite.dto;

import com.ssafy.motif.domain.core.job.JobStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * 합성 요청 응답 (API 명세 §13.2) — <b>202 Accepted</b>.
 *
 * <p>접수했다는 뜻이고 합성은 아직 시작도 하지 않았다. 이후 진행은 FCM 이 알리고
 * 앱은 §13.3 을 한 번 부른다 — <b>폴링하지 않는다</b>(§1.6).
 */
@Schema(description = "합성 요청 응답 (§13.2) — 큐에 들어갔다")
public record CompositeJobRequestResponse(

        @Schema(description = "잡 식별자", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID jobUuid,

        @Schema(description = "전이 후 상태 — 언제나 QUEUED", example = "QUEUED")
        JobStatus status) {
}
