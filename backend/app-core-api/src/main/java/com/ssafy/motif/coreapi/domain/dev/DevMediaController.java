package com.ssafy.motif.coreapi.domain.dev;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.domain.media.MediaJobPublisher;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 로컬 전용 미디어 후처리 트리거 (격리 모드 — ADR-0003).
 *
 * <pre>
 * POST /api/dev/media {"sourceType":"CHALLENGE_ORIGIN","sourceSeq":1,
 *                      "sourceKey":"challenges/{uuid}.mp4","outputPrefix":"challenges/{uuid}"}
 *   → job.media 발행(AFTER_COMMIT) → app-media 가 렌디션·HLS·썸네일 생성
 *   → S3 업로드 → challenges.video_key 갱신
 * </pre>
 *
 * <p>어드민 챌린지 업로드 API 가 생기면 {@link MediaJobPublisher} 를 그쪽에서 부르고
 * 이 엔드포인트는 스모크 용도로만 남는다.
 *
 * <p>{@code @Hidden} — Swagger 는 클라이언트 계약 문서다. 로컬 도구는 싣지 않는다.
 */
@Hidden
@Profile("local")
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dev/media")
public class DevMediaController {

    private final DevMediaService devMediaService;

    @PostMapping
    public ApiResponse<Map<String, String>> requestMediaJob(@Valid @RequestBody Request request) {
        String uuid = devMediaService.request(request.sourceType(), request.sourceSeq(),
                request.sourceKey(), request.outputPrefix());
        return ApiResponse.success(Map.of("mediaJobUuid", uuid));
    }

    public record Request(
            @NotBlank String sourceType,
            @Positive long sourceSeq,
            @NotBlank String sourceKey,
            @NotBlank String outputPrefix) {
    }

    /** 발행은 커밋 후에 일어난다 — 트랜잭션 경계가 있어야 relay 가 동작한다. */
    @Service
    @RequiredArgsConstructor
    static class DevMediaService {

        private final MediaJobPublisher publisher;

        @Transactional
        String request(String sourceType, long sourceSeq, String sourceKey, String outputPrefix) {
            return publisher.publish(sourceType, sourceSeq, sourceKey, outputPrefix);
        }
    }
}
