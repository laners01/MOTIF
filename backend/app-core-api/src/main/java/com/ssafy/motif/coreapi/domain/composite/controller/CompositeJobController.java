package com.ssafy.motif.coreapi.domain.composite.controller;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.domain.composite.docs.CompositeJobControllerDocs;
import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobCreateRequest;
import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobCreateResponse;
import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobRequestBody;
import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobRequestResponse;
import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobRetryResponse;
import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobStatusResponse;
import com.ssafy.motif.coreapi.domain.composite.service.CompositeJobCommandService;
import com.ssafy.motif.coreapi.domain.composite.service.CompositeJobQueryService;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 합성 파이프라인 API (명세 §13). Base URL 은 {@code /api/v1}.
 *
 * <p><b>챌린지와 모티프가 같은 엔드포인트를 쓴다</b> — {@code jobType} 이 가른다. 별도
 * API 를 만들지 않는 이유는 잡 행·원본 키·상태 전이·FCM 이 전부 같기 때문이다.
 *
 * <p><b>전부 인증이 필요하다</b> — 잡은 호출자 것이고 MOTIF 의 권한 판정도 호출자 기준이다.
 *
 * <p>경로가 {@code /api/v1/**} 이고 {@code /api/v1/admin/**} 이 아니므로 springdoc
 * {@code user} 그룹 문서에 자동으로 잡힌다({@code OpenApiConfig}).
 *
 * <p>Swagger 문서는 {@link CompositeJobControllerDocs} 가 갖는다.
 */
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/composite-jobs")
public class CompositeJobController implements CompositeJobControllerDocs {

    private final CompositeJobCommandService compositeJobCommandService;
    private final CompositeJobQueryService compositeJobQueryService;

    /**
     * §13.1 잡 생성 + 촬영본 업로드 URL 발급 — <b>201 Created</b>.
     *
     * <p>{@code @Valid} 는 {@code jobType} 필수·{@code participantMask} 음수 금지만 본다.
     * 형태(CHALLENGE 면 {@code challengeSeq}, MOTIF 면 {@code assetSeq})는 서비스가 판정해
     * 같은 400 봉투로 낸다.
     */
    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CompositeJobCreateResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CompositeJobCreateRequest request) {
        return ApiResponse.success(compositeJobCommandService.create(user.userSeq(), request));
    }

    /**
     * §13.2 합성 요청 — <b>202 Accepted</b>. 업로드를 마친 뒤 부른다.
     *
     * <p>접수했다는 뜻이고 합성은 시작도 하지 않았다. <b>AI 호출은 여기서 일어나지
     * 않는다</b> — 디스패처가 {@code QUEUED} 행을 집어 커밋 뒤에 보낸다.
     *
     * <p>본문이 없어도 된다 — MOTIF 는 보낼 값이 없다({@code required = false}).
     */
    @Override
    @PostMapping("/{jobUuid}/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<CompositeJobRequestResponse> request(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID jobUuid,
            @RequestBody(required = false) CompositeJobRequestBody body) {
        return ApiResponse.success(
                compositeJobCommandService.request(user.userSeq(), jobUuid, body));
    }

    /**
     * §13.3 잡 상태·결과 — FCM 을 받고 <b>1회</b> 호출한다. 폴링 금지(§1.6).
     *
     * <p>경로의 {@code jobUuid} 가 UUID 형식이 아니면 타입 불일치로 400
     * {@code INVALID_PARAMETER} 다 — 존재하지 않는 잡(404)과 다르다.
     */
    @Override
    @GetMapping("/{jobUuid}")
    public ApiResponse<CompositeJobStatusResponse> status(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID jobUuid) {
        return ApiResponse.success(compositeJobQueryService.status(user.userSeq(), jobUuid));
    }

    /**
     * §13.4 재시도 — <b>202 Accepted</b>. 실패한 잡을 같은 행으로 재큐한다.
     *
     * <p>본문이 없다. 무엇을 다시 할지는 잡 행에 다 적혀 있다.
     *
     * <p>{@code jobUuid} 가 바뀌지 않으므로 클라이언트는 들고 있던 식별자로 §13.3 을
     * 계속 조회하면 된다.
     */
    @Override
    @PostMapping("/{jobUuid}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<CompositeJobRetryResponse> retry(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID jobUuid) {
        return ApiResponse.success(compositeJobCommandService.retry(user.userSeq(), jobUuid));
    }
}
