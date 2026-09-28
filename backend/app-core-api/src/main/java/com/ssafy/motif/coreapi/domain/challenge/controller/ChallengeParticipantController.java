package com.ssafy.motif.coreapi.domain.challenge.controller;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.domain.challenge.docs.ChallengeParticipantControllerDocs;
import com.ssafy.motif.coreapi.domain.challenge.dto.EditedVideoResponse;
import com.ssafy.motif.coreapi.domain.challenge.service.ChallengeQueryService;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팬 앱 슬롯 API (명세 §7.6) — 편집본 영상 (0020).
 *
 * <p>경로가 {@code /challenges} 아래가 아닌 이유는 <b>슬롯이 독립 리소스</b>이기 때문이다 —
 * 앱은 §7.5 가 준 {@code challengeParticipantSeq} 하나만 들고 오고, 그 슬롯이 어느 챌린지
 * 것인지는 서버가 안다. 어드민의 {@code /admin/api/v1/challenge-participants} 와 경로가
 * 비슷하지만 접두사가 갈려 충돌하지 않는다.
 *
 * <p>Swagger 문서는 {@link ChallengeParticipantControllerDocs} 가 갖는다.
 */
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/challenge-participants")
public class ChallengeParticipantController implements ChallengeParticipantControllerDocs {

    private final ChallengeQueryService challengeQueryService;

    /**
     * §7.6 편집본 영상 — <b>통짜 MP4 의 서명 URL</b>.
     *
     * <p>촬영 화면에 들어갈 때 부른다. 서명이 만료되면 같은 요청을 다시 보내면 된다 —
     * 그래서 만료를 짧게 잡을 수 있고, 응답의 {@code expiresIn} 이 그 판단 근거다.
     */
    @Override
    @GetMapping("/{challengeParticipantSeq}/video")
    public ApiResponse<EditedVideoResponse> editedVideo(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer challengeParticipantSeq) {
        return ApiResponse.success(challengeQueryService.editedVideo(challengeParticipantSeq));
    }
}
