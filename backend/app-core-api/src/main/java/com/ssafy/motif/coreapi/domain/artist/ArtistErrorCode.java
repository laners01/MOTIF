package com.ssafy.motif.coreapi.domain.artist;

import com.ssafy.motif.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 아티스트 도메인 에러 코드 (API 명세 §15).
 *
 * <p>{@code ARTIST_NOT_FOUND} 는 온보딩(§3.3)뿐 아니라 아티스트 상세(§5.3)·구독(§5.6)·
 * 패스(§11.4)·주문(§12.1) 등이 함께 쓴다. code 는 전역 유일해야 하므로 아티스트
 * 도메인이 소유하고 각 도메인이 참조한다 — 쓰는 쪽마다 만들면 중복 코드가 생긴다.
 */
@Getter
@RequiredArgsConstructor
public enum ArtistErrorCode implements ErrorCode {

    /** 미존재·삭제 아티스트 (명세 §3.3, §5.3, §5.6, §11.4, §12.1). */
    ARTIST_NOT_FOUND(404, "ARTIST_NOT_FOUND", "존재하지 않는 아티스트입니다.");

    private final int status;
    private final String code;
    private final String message;
}
