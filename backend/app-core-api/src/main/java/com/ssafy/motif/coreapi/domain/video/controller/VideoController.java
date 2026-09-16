package com.ssafy.motif.coreapi.domain.video.controller;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.domain.video.docs.VideoControllerDocs;
import com.ssafy.motif.coreapi.domain.video.dto.VideoItem;
import com.ssafy.motif.coreapi.domain.video.service.VideoQueryService;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 참여 영상 API (명세 §8.2). Base URL 은 {@code /api/v1}.
 *
 * <p>챌린지 경로 아래인 §8.1 피드는 {@code ChallengeVideoController} 가 따로 갖는다 —
 * 경로가 달라 한 {@code @RequestMapping} 에 묶이지 않는다.
 *
 * <p><b>인증이 필요하다</b>(🔓 아님) — 응답의 {@code isLiked}·{@code isMine} 과
 * 타인 {@code PRIVATE} 은닉이 전부 호출자 기준이다.
 *
 * <p>§8.3 좋아요 · §8.5 조회 · §8.6 관심 없음 · §8.7 신고 · §8.8~§8.10 게시·수정·삭제는
 * 아직 매핑이 없다.
 *
 * <p>Swagger 문서는 {@link VideoControllerDocs} 가 갖는다.
 */
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/videos")
public class VideoController implements VideoControllerDocs {

    private final VideoQueryService videoQueryService;

    /** §8.2 참여 영상 단건 — 뷰어 오버레이. */
    @Override
    @GetMapping("/{videoSeq}")
    public ApiResponse<VideoItem> detail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long videoSeq) {
        return ApiResponse.success(videoQueryService.detail(user.userSeq(), videoSeq));
    }
}
