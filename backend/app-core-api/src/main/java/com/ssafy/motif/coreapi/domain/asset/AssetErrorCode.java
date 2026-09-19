package com.ssafy.motif.coreapi.domain.asset;

import com.ssafy.motif.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 에셋 도메인 에러 코드 (API 명세 §15 · 어드민 명세 §5).
 *
 * <p>{@code ASSET_NOT_FOUND} 는 어드민 전용이 아니라 상점(§11)·합성 요청(§13.2)도 쓰는
 * 사용자 도메인 코드라 {@code AdminErrorCode} 가 아니라 여기가 소유한다 —
 * {@code ArtistErrorCode} 와 같은 근거(code 전역 유일).
 */
@Getter
@RequiredArgsConstructor
public enum AssetErrorCode implements ErrorCode {

    /** 미존재 에셋 (어드민 §3.8, API §11·§13.2). 에셋에는 삭제 플래그가 없다(DB §30). */
    ASSET_NOT_FOUND(404, "ASSET_NOT_FOUND", "존재하지 않는 에셋입니다."),

    /**
     * MOTIF 합성 잡 생성에 이용 권한이 없음 (API 명세 §13.1 · §15 표).
     *
     * <p><b>404 로 숨기지 않는다.</b> §11.2 상세는 존재 은닉(§1.8)이라 안 보이는 에셋을
     * 전부 404 로 접지만, 여기서는 클라이언트가 <b>구매 화면으로 유도</b>해야 해서
     * "존재하지만 권한이 없다"를 구분해 줘야 한다(§13.1 실패표). 선결제 원칙이라 촬영
     * 자체가 시작되지 않는다.
     */
    ASSET_ACCESS_DENIED(403, "ASSET_ACCESS_DENIED", "구매 후 이용할 수 있습니다.");

    private final int status;
    private final String code;
    private final String message;
}
