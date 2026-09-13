package com.ssafy.motif.coreapi.domain.challenge;

import com.ssafy.motif.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 챌린지 도메인 에러 코드 (API 명세 §15 표).
 *
 * <p><b>{@code AdminErrorCode} 가 아니다.</b> 어드민 명세 §5 는 어드민 <b>전용</b> 코드만
 * 담고, §3.7 실패표의 {@code CHALLENGE_NOT_FOUND}·{@code EDITED_CHALLENGE_NOT_FOUND} 는
 * 사용자 API(§7.2·§13.1)가 먼저 쓰는 도메인 코드다. code 는 전역 유일해야 하므로
 * {@code ArtistErrorCode} 와 같은 방식으로 챌린지 도메인이 소유하고 어드민이 참조한다.
 */
@Getter
@RequiredArgsConstructor
public enum ChallengeErrorCode implements ErrorCode {

    /** 미존재·삭제 챌린지 (API 명세 §7.2·§13.1, 어드민 §3.7). */
    CHALLENGE_NOT_FOUND(404, "CHALLENGE_NOT_FOUND", "존재하지 않는 챌린지입니다."),

    /** 선택한 슬롯 조합의 편집본 없음 (API 명세 §13.1, 어드민 §3.7.6). */
    EDITED_CHALLENGE_NOT_FOUND(404, "EDITED_CHALLENGE_NOT_FOUND", "존재하지 않는 편집본입니다.");

    private final int status;
    private final String code;
    private final String message;
}
