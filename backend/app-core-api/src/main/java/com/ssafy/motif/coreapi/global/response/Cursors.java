package com.ssafy.motif.coreapi.global.response;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 커서 토큰 인코딩 (API 명세 §1.9).
 *
 * <p>§1.9 는 커서를 "서버 생성 opaque 토큰(base64 — 정렬 키 조합)" 으로 정하고 클라이언트는
 * 해석·조작하지 않는다고 못박는다. 정렬 키가 <b>PK 하나</b>인 목록(§5.5 아티스트 공지,
 * §7.1 챌린지 피드)은 {@link #encode}·{@link #decode} 를, 스냅샷 순서를 페이지하는
 * 목록(§6.1 HOT)은 {@link #encodeSnapshot}·{@link #decodeSnapshot} 을 쓴다.
 *
 * <p><b>두 형식을 하나로 합치지 않는다.</b> 단일 PK 토큰에 버전 칸을 더하면 §5.5·§7.1
 * 이 쓰지 않는 값을 채워야 하고, 이미 발급돼 클라이언트가 들고 있는 토큰이 디코드되지
 * 않는다(무한 스크롤 중인 화면이 400 을 만난다). 형식을 늘리는 쪽이 싸다.
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

    /** 조합 토큰의 구분자. 담는 값이 전부 숫자라 충돌하지 않는다. */
    private static final String DELIMITER = ":";

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

    /** 스냅샷 순서 목록의 커서 위치 — {@code (버전, 이번 페이지 마지막 순위)} (§6.1). */
    public record Snapshot(long version, int lastRank) {
    }

    /**
     * 스냅샷 순서 목록의 다음 페이지 토큰 (§6.1).
     *
     * <p><b>버전이 함께 들어가는 것이 요점이다.</b> rank 만 담으면 배치가 순위를 갈아
     * 끼운 뒤 같은 rank 가 다른 영상을 가리켜, 진행 중인 무한 스크롤에 중복·누락이
     * 생긴다 — 예외도 로그도 없이 목록만 이상해지는 종류다.
     */
    public static String encodeSnapshot(long version, int lastRank) {
        return ENCODER.encodeToString(
                (version + DELIMITER + lastRank).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 토큰 → {@code (버전, 마지막 순위)}. {@code null}·빈 문자열은 "최초 호출" 이라
     * {@code null} 을 준다.
     *
     * <p>{@link #decode} 와 같은 이유로 망가진 토큰은 400 이다. <b>보존 기간이 지난
     * 버전은 여기서 걸러내지 않는다</b> — 형식은 멀쩡하고 그 버전이 Redis 에 없을 뿐이라
     * 판단은 스냅샷을 읽는 쪽이 한다(§6.1: 빈 목록 200).
     */
    public static Snapshot decodeSnapshot(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(DECODER.decode(cursor), StandardCharsets.UTF_8);
            int at = decoded.indexOf(DELIMITER);
            if (at < 0) {
                throw new IllegalArgumentException("구분자가 없다");
            }
            return new Snapshot(
                    Long.parseLong(decoded.substring(0, at)),
                    Integer.parseInt(decoded.substring(at + 1)));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER, "cursor: " + cursor);
        }
    }
}
