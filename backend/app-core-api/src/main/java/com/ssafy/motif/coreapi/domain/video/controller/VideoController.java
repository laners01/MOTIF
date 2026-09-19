package com.ssafy.motif.coreapi.domain.video.controller;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.domain.video.docs.VideoControllerDocs;
import com.ssafy.motif.coreapi.domain.video.dto.VideoItem;
import com.ssafy.motif.coreapi.domain.video.dto.VideoPublishRequest;
import com.ssafy.motif.coreapi.domain.video.dto.VideoPublishResponse;
import com.ssafy.motif.coreapi.domain.video.dto.VideoReportRequest;
import com.ssafy.motif.coreapi.domain.video.dto.VideoUpdateRequest;
import com.ssafy.motif.coreapi.domain.video.service.VideoCommandService;
import com.ssafy.motif.coreapi.domain.video.service.VideoLikeService;
import com.ssafy.motif.coreapi.domain.video.service.VideoQueryService;
import com.ssafy.motif.coreapi.domain.video.service.VideoReportService;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 참여 영상 API (명세 §8.2 · §8.3 · §8.6 · §8.7 · §8.9 · §8.10). Base URL 은 {@code /api/v1}.
 *
 * <p>챌린지 경로 아래인 §8.1 피드는 {@code ChallengeVideoController} 가 따로 갖는다 —
 * 경로가 달라 한 {@code @RequestMapping} 에 묶이지 않는다.
 *
 * <p><b>인증이 필요하다</b>(🔓 아님) — 응답의 {@code isLiked}·{@code isMine} 과
 * 타인 {@code PRIVATE} 은닉이 전부 호출자 기준이다.
 *
 * <p>§8.5 조회 · §8.8 게시는 아직 매핑이 없다.
 *
 * <p>Swagger 문서는 {@link VideoControllerDocs} 가 갖는다.
 */
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/videos")
public class VideoController implements VideoControllerDocs {

    private final VideoQueryService videoQueryService;
    private final VideoLikeService videoLikeService;
    private final VideoCommandService videoCommandService;
    private final VideoReportService videoReportService;

    /**
     * §8.8 게시 — <b>201 Created</b>. 합성이 끝난 잡을 참여 영상으로 만든다.
     *
     * <p>제목의 "업로드" 는 바이트가 아니라 <b>게시</b>다 — 영상은 §13.1 presign 으로
     * 이미 S3 에 있다.
     *
     * <p>{@code AuthenticatedUser} 를 통째로 넘기는 것은 <b>role 로 거르기 때문</b>이다
     * (ARTIST 는 403). 다른 메서드는 {@code userSeq} 만 쓴다.
     */
    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VideoPublishResponse> publish(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody VideoPublishRequest request) {
        return ApiResponse.success(videoCommandService.publish(user, request));
    }

    /** §8.2 참여 영상 단건 — 뷰어 오버레이. */
    @Override
    @GetMapping("/{videoSeq}")
    public ApiResponse<VideoItem> detail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long videoSeq) {
        return ApiResponse.success(videoQueryService.detail(user.userSeq(), videoSeq));
    }

    /**
     * §8.6 관심 없음 — 요청 본문이 없다.
     *
     * <p>숨김 해제 매핑은 두지 않는다(명세 §8.6 기본안) — 되돌리는 화면이 없다.
     */
    @Override
    @PostMapping("/{videoSeq}/hide")
    public ApiResponse<Void> hide(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long videoSeq) {
        videoCommandService.hide(user.userSeq(), videoSeq);
        return ApiResponse.success();
    }

    /**
     * §8.9 게시 정보 수정 — 부분 수정이라 PUT 이 아니라 PATCH 다.
     *
     * <p>응답은 §8.2 항목(수정 반영본)이다.
     */
    @Override
    @PatchMapping("/{videoSeq}")
    public ApiResponse<VideoItem> update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long videoSeq,
            @Valid @RequestBody VideoUpdateRequest request) {
        return ApiResponse.success(videoCommandService.update(user.userSeq(), videoSeq, request));
    }

    /**
     * §8.10 게시 영상 삭제 — 소프트 삭제라 200 이고 {@code data} 는 없다.
     *
     * <p>204 를 쓰지 않는 이유는 봉투 규약이다(§1.4) — 모든 성공 응답이
     * {@code result}·{@code code}·{@code message} 를 갖는다.
     */
    @Override
    @DeleteMapping("/{videoSeq}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long videoSeq) {
        videoCommandService.delete(user.userSeq(), videoSeq);
        return ApiResponse.success();
    }

    /**
     * §8.7 영상 신고 — 접수만 하고 {@code data} 는 없다.
     *
     * <p>201 이 아니라 200 이다(명세 §8.7). 신고는 클라이언트가 다시 가리킬 리소스를
     * 만들지 않는다 — 접수 번호를 돌려주지도 않는다.
     */
    @Override
    @PostMapping("/{videoSeq}/report")
    public ApiResponse<Void> report(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long videoSeq,
            @Valid @RequestBody VideoReportRequest request) {
        videoReportService.report(user.userSeq(), videoSeq, request.reasonCode());
        return ApiResponse.success();
    }

    /**
     * §8.3 좋아요 — <b>204 No Content</b>. 응답 본문이 없다(§1.2 봉투 예외).
     *
     * <p>§9.5 댓글 · §10.2 공지 좋아요와 같은 계약이다 — 클라이언트가 낙관적으로 반영하고
     * 실패 시 롤백한다.
     */
    @Override
    @PostMapping("/{videoSeq}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void like(@AuthenticationPrincipal AuthenticatedUser user,
                     @PathVariable Long videoSeq) {
        videoLikeService.like(user.userSeq(), videoSeq);
    }

    /** §8.3 좋아요 해제 — 204. 누르지 않은 상태에서 불러도 204 다(멱등). */
    @Override
    @DeleteMapping("/{videoSeq}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlike(@AuthenticationPrincipal AuthenticatedUser user,
                       @PathVariable Long videoSeq) {
        videoLikeService.unlike(user.userSeq(), videoSeq);
    }
}
