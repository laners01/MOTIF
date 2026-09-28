package com.ssafy.motif.coreapi.domain.video;

import com.ssafy.motif.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 참여 영상 도메인 에러 코드 (API 명세 §15 표).
 *
 * <p><b>코드 하나짜리 enum 이다.</b> §8 의 실패 분기가 거의 전부 이 하나로 모인다 —
 * §8.2 단건 · §8.3 좋아요 · §8.5 조회 · §8.6 관심 없음 · §8.7 신고 · §8.9 수정 ·
 * §8.10 삭제가 모두 {@code VIDEO_NOT_FOUND} 를 쓴다({@code ALREADY_REPORTED} 처럼
 * 각 API 고유인 것은 그 티켓이 여기 더한다).
 *
 * <p><b>404 로 모으는 것이 의도다.</b> 타인의 {@code PRIVATE} 영상을 403 으로 가르면
 * "그 영상은 존재하고 당신 것이 아니다" 가 응답에서 읽힌다 — {@code videoSeq} 를 찍어
 * 볼 수 있는 자리라 §1.8 존재 은닉이 여기에 걸린다.
 */
@Getter
@RequiredArgsConstructor
public enum VideoErrorCode implements ErrorCode {

    /**
     * 미존재 · 삭제 영상 · 삭제 사용자의 콘텐츠 · <b>타인의 {@code PRIVATE}</b> ·
     * 삭제된 챌린지 소속 (API 명세 §8.2).
     */
    VIDEO_NOT_FOUND(404, "VIDEO_NOT_FOUND", "존재하지 않는 영상입니다."),

    /** 게시하려는 잡이 {@code DONE} 이 아님 — 진행 중·실패 (API 명세 §8.8). */
    JOB_NOT_DONE(409, "JOB_NOT_DONE", "합성이 완료되지 않았습니다."),

    /**
     * 이미 게시된 잡 (§8.8).
     *
     * <p>판정은 {@code uq_participation_videos_job} 이 한다 — 미리 조회해 막으면 동시
     * 요청 둘이 모두 통과한 뒤 두 번째가 제약 위반 500 이 된다.
     */
    ALREADY_PUBLISHED(409, "ALREADY_PUBLISHED", "이미 게시된 영상입니다."),

    /** 게시 시점에 원본 챌린지가 삭제됨 (§8.8). 클라이언트는 "삭제된 챌린지" 를 안내한다. */
    CHALLENGE_DELETED(409, "CHALLENGE_DELETED", "삭제된 챌린지입니다.");

    private final int status;
    private final String code;
    private final String message;
}
