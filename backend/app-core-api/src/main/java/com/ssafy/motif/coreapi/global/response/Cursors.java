package com.ssafy.motif.coreapi.global.response;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 커서 토큰 인코딩 (API 명세 §1.9).
 *
 * <p>§1.9 는 커서를 "서버 생성 opaque 토큰(base64 — 정렬 키 조합)" 으로 정하고 클라이언트는
 * 해석·조작하지 않는다고 못박는다. 지금 커서를 쓰는 목록(§5.5 아티스트 공지)의 정렬 키는
 * <b>PK 하나</b>라 토큰도 PK 하나를 담는다. 정렬 키가 여럿인 목록(6.1 HOT 의 스냅샷
 * 버전 등)이 생기면 그때 이 클래스가 구분자 있는 조합으로 넓어진다 — <b>지금 없는
 * 필드를 미리 넣지 않는다.</b>
 *
 * <p><b>base64 는 암호가 아니다.</b> 디코드하면 seq 가 보인다 — 그래도 되는 이유는
 * 커서가 담는 값이 응답 항목에 이미 들어 있는 공개 식별자({@code noticeSeq})이기
 * 때문이다. base64 의 목적은 은닉이 아니라 "이건 파싱하지 말라"는 신호이고, 그래서
 * 서명도 붙이지 않는다 — 조작된 커서로 얻을 수 있는 것은 같은 목록의 다른 구간뿐이라
 * 권한 경계를 넘지 못한다. 권한이 걸린 커서가 생기면 서명이 필요해진다.
 *
 * <p>URL 쿼리로 오가므로 {@code +}·{@code /} 가 없는 <b>URL-safe</b> 알파벳을 쓰고,
 * 패딩({@code =})도 뗀다 — 인코딩이 덜 된 클라이언트가 보내도 깨지지 않는다.
 */
public final class Cursors {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private Cursors() {
    }

    /** 다음 페이지 토큰 — 이번 페이지 <b>마지막</b> 항목의 정렬 키를 담는다. */
    public static String encode(Integer lastSeq) {
        return ENCODER.encodeToString(String.valueOf(lastSeq).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 토큰 → 정렬 키. {@code null}·빈 문자열은 "최초 호출" 이라 {@code null} 을 준다.
     *
     * <p>망가진 토큰은 <b>400 으로 돌려준다</b>. 조용히 첫 페이지로 되돌리면 클라이언트는
     * 무한 스크롤이 맨 위로 점프하는 현상을 원인 없이 겪고, 그대로 던지면
     * {@code NumberFormatException} 이 500 이 된다 — 잘못된 것은 요청이다.
     */
    public static Integer decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(new String(DECODER.decode(cursor), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER, "cursor: " + cursor);
        }
    }
}
