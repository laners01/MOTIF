package com.ssafy.motif.coreapi.domain.dev;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 로컬 전용 발행 스모크 (격리 모드 — ADR-0003).
 *
 * <pre>
 * POST /api/dev/jobs {"jobType":"MOTIF"}
 *   → job.composite 발행(AFTER_COMMIT) → 스텁 워커 2초 처리 → composite.done
 *   → app-notification 수신. 세 로그의 requestId가 같아야 한다.
 * </pre>
 *
 * <p>{@code @Hidden} — Swagger 는 클라이언트(안드로이드) 계약 문서다. 이 엔드포인트는
 * 로컬 스모크 도구이므로 문서에 싣지 않는다.
 */
@Hidden
@Profile("local")
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dev/jobs")
public class DevJobController {

    private final DevJobService devJobService;

    @PostMapping
    public ApiResponse<Map<String, String>> createJob(@Valid @RequestBody DevJobRequest request) {
        String jobUuid = devJobService.requestCompositeJob(request.jobType());
        return ApiResponse.success(Map.of("jobUuid", jobUuid));
    }
}
