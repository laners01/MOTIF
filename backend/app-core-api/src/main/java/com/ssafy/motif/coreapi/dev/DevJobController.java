package com.ssafy.motif.coreapi.dev;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
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
 */
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
