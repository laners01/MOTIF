package com.ssafy.motif.coreapi.domain.motif;

import com.ssafy.motif.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 모티프 결과물 도메인 에러 코드 (API 명세 §11.6 · §11.8).
 *
 * <p>{@code AssetErrorCode}·{@code ArtistErrorCode} 와 같은 자리다 — {@code code} 는 전역
 * 유일이라 도메인마다 자기 것을 갖는다.
 */
@Getter
@RequiredArgsConstructor
public enum MotifErrorCode implements ErrorCode {

    /**
     * 열 수 없는 결과물 (§11.6 · §11.8).
     *
     * <p><b>타인 소유도 여기로 합친다.</b> 403 으로 가르면 "그 결과물은 존재하고 당신
     * 것이 아니다" 가 응답에서 읽히는데, {@code resultSeq} 는 찍어 볼 수 있는 값이다
     * (§1.8 존재 은닉). 삭제한 것도 같다.
     */
    MOTIF_RESULT_NOT_FOUND(404, "MOTIF_RESULT_NOT_FOUND", "존재하지 않는 결과물입니다.");

    private final int status;
    private final String code;
    private final String message;
}
