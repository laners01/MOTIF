package com.ssafy.motif.coreapi.domain.onboarding;

import com.ssafy.motif.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 온보딩 도메인 에러 코드 (API 명세 §3.3, §15).
 *
 * <p>{@code ARTIST_NOT_FOUND} 는 여기 두지 않는다 — 아티스트 도메인이 소유한다
 * ({@link com.ssafy.motif.coreapi.domain.artist.ArtistErrorCode}).
 */
@Getter
@RequiredArgsConstructor
public enum OnboardingErrorCode implements ErrorCode {

    /**
     * 이미 온보딩을 마친 계정의 재호출 (명세 §3.3).
     *
     * <p>판정 기준은 "구독 1건 이상 존재"다 — 완료 플래그 컬럼을 두지 않기로 한
     * 결정(원본 §16 #16)에서 파생된 규칙이다.
     */
    ALREADY_ONBOARDED(409, "ALREADY_ONBOARDED", "이미 온보딩을 완료한 계정입니다.");

    private final int status;
    private final String code;
    private final String message;
}
