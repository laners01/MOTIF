package com.ssafy.motif.coreapi.domain.composite.worker;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.domain.composite.dto.WorkerDoneRequest;
import com.ssafy.motif.coreapi.domain.composite.dto.WorkerFailedRequest;
import com.ssafy.motif.coreapi.domain.composite.service.CompositeJobCompletionService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * AI Job → 우리 콜백 (AI 서버 명세 §3.3·§3.4 · 작업명세 §4.3).
 *
 * <p><b>{@code @Hidden} 이다.</b> Swagger 는 앱 클라이언트와의 계약 문서이고, 이 경로의
 * 계약서는 AI 서버 명세다. 문서에 실으면 앱 개발자가 부를 수 있는 API 로 읽힌다.
 *
 * <p>인증은 {@link WorkerTokenFilter} 가 끝낸다 — 공유 비밀 하나이고 사용자 JWT 가
 * 아니다. 그래서 {@code @AuthenticationPrincipal} 이 없다.
 *
 * <p><b>응답은 전부 200 이다</b>(멱등 — AI 명세 §3.5). 이미 처리된 잡이어도 200 을 주고
 * AI 는 exit 0 한다. 404(모르는 잡)·401(토큰 불일치)에도 AI 는 재시도하지 않는다 —
 * 5xx·타임아웃만 3회 재시도한다.
 *
 * <p>{@code X-Request-Id} 는 {@code RequestIdFilter} 가 승계한다 — AI 를 한 바퀴 돌아
 * 온 값이라 로그가 접수부터 이어진다.
 */
@Hidden
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/worker/v1/composite-jobs")
public class WorkerCallbackController {

    private final CompositeJobCompletionService completionService;

    /**
     * 합성 성공 — S3 PUT 이 끝난 뒤에 온다.
     *
     * <p>여기서 {@code DONE} 이 되지 않는다. HLS 가공(app-media)이 남아 있어
     * {@code job.media} 를 발행하고 상태는 {@code PROCESSING} 이다.
     */
    @PostMapping("/{jobUuid}/done")
    public ApiResponse<Void> done(@PathVariable UUID jobUuid,
                                  @Valid @RequestBody WorkerDoneRequest request) {
        completionService.aiDone(jobUuid, request);
        return ApiResponse.success();
    }

    /** 합성 실패 — 잡을 {@code FAILED} 로 닫고 {@code composite.finished(FAILED)} 를 낸다. */
    @PostMapping("/{jobUuid}/failed")
    public ApiResponse<Void> failed(@PathVariable UUID jobUuid,
                                    @Valid @RequestBody WorkerFailedRequest request) {
        completionService.aiFailed(jobUuid, request);
        return ApiResponse.success();
    }
}
