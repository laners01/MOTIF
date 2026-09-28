package com.ssafy.motif.coreapi.domain.composite;

import com.ssafy.motif.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 합성 파이프라인 에러 코드 (API 명세 §13 · §15 표).
 *
 * <p><b>대상이 없는 것은 여기 없다.</b> {@code CHALLENGE_NOT_FOUND}·
 * {@code EDITED_CHALLENGE_NOT_FOUND} 는 {@code ChallengeErrorCode} 가,
 * {@code ASSET_NOT_FOUND}·{@code ASSET_ACCESS_DENIED} 는 {@code AssetErrorCode} 가
 * 소유한다 — {@code code} 는 전역 유일이고 도메인 소유자가 갖는다(README §3).
 * 여기 있는 것은 <b>잡 자체의 상태</b>에 대한 코드뿐이다.
 *
 * <p>{@code JOB_NOT_FOUND} 하나로 미존재와 <b>타인 잡</b>을 함께 답한다(§1.8 존재 은닉) —
 * 가르면 남의 {@code jobUuid} 를 넣어 보고 존재 여부를 읽을 수 있다.
 */
@Getter
@RequiredArgsConstructor
public enum CompositeErrorCode implements ErrorCode {

    /** 잡 없음·타인 잡 (§13.2·§13.3·§13.4). */
    JOB_NOT_FOUND(404, "JOB_NOT_FOUND", "존재하지 않는 합성 잡입니다."),

    /** S3 원본 부재 — 업로드 미완료 또는 presign 만료 (§13.2). */
    RAW_NOT_UPLOADED(409, "RAW_NOT_UPLOADED", "원본 업로드가 완료되지 않았습니다."),

    /** 상태가 {@code UPLOADED} 가 아님 — 중복 요청 포함 (§13.2). */
    JOB_NOT_REQUESTABLE(409, "JOB_NOT_REQUESTABLE", "합성을 요청할 수 없는 상태입니다."),

    /** 상태가 {@code FAILED} 가 아님 (§13.4). */
    JOB_NOT_RETRYABLE(409, "JOB_NOT_RETRYABLE", "재시도할 수 없는 상태입니다."),

    /**
     * 원본이 수명주기로 삭제됨 (§13.4) — 클라이언트는 재시도 버튼을 숨기고 재촬영을
     * 안내한다. 409 가 아니라 410 인 것은 "있었지만 이제 없다" 이기 때문이다.
     */
    RAW_VIDEO_EXPIRED(410, "RAW_VIDEO_EXPIRED", "원본 영상이 만료되었습니다. 다시 촬영해 주세요.");

    private final int status;
    private final String code;
    private final String message;
}
