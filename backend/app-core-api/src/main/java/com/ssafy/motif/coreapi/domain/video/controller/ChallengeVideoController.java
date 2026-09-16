package com.ssafy.motif.coreapi.domain.video.controller;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.CursorResponse;
import com.ssafy.motif.coreapi.domain.video.docs.ChallengeVideoControllerDocs;
import com.ssafy.motif.coreapi.domain.video.dto.VideoItem;
import com.ssafy.motif.coreapi.domain.video.service.VideoFeedQueryService;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 챌린지별 참여 영상 API (명세 §8.1). Base URL 은 {@code /api/v1}.
 *
 * <p><b>경로는 {@code /challenges} 아래인데 컨트롤러는 §8 도메인에 있다.</b> 이 목록의
 * 항목이 §8.2 참여 영상 항목이고, 이어지는 §8.3~§8.10 과 같은 DTO·서비스를 쓴다 —
 * 챌린지 자체를 다루는 §7 과는 바뀌는 이유가 다르다. 경로가 아니라 <b>무엇이 바뀌면
 * 같이 바뀌는가</b>로 묶는다.
 *
 * <p><b>인증이 필요하다</b>(🔓 아님) — 응답의 {@code isLiked}·{@code isMine} 과
 * 관심 없음 제외가 전부 호출자 기준이다.
 *
 * <p>경로가 {@code /api/v1/**} 이고 {@code /api/v1/admin/**} 이 아니므로 springdoc
 * {@code user} 그룹 문서에 자동으로 잡힌다({@code OpenApiConfig}).
 *
 * <p>Swagger 문서는 {@link ChallengeVideoControllerDocs} 가 갖는다.
 */
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/challenges")
public class ChallengeVideoController implements ChallengeVideoControllerDocs {

    private final VideoFeedQueryService videoFeedQueryService;

    /** §8.1 참여 영상 피드 — 커서, 추천 정렬. */
    @Override
    @GetMapping("/{challengeSeq}/videos")
    public ApiResponse<CursorResponse<VideoItem>> feed(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer challengeSeq,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                videoFeedQueryService.feed(user.userSeq(), challengeSeq, cursor, size));
    }
}
