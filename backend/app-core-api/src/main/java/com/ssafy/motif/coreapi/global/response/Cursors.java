package com.ssafy.motif.coreapi.global.response;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.domain.core.user.ActivityTargetType;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * 커서 토큰 인코딩 (API 명세 §1.9).
 *
 * <p>§1.9 는 커서를 "서버 생성 opaque 토큰(base64 — 정렬 키 조합)" 으로 정하고 클라이언트는
 * 해석·조작하지 않는다고 못박는다. 정렬 키가 <b>PK 하나</b>인 목록(§5.5 아티스트 공지,
 * §7.1 챌린지 피드)은 {@link #encode}·{@link #decode} 를, 스냅샷 순서를 페이지하는
 * 목록(§6.1 HOT)은 {@link #encodeSnapshot}·{@link #decodeSnapshot} 을, 조회 시점에 점수를
 * 매겨 정렬하는 목록(§8.1 참여 영상 피드)은 {@link #encodeScore}·{@link #decodeScore} 를,
 * 상호작용 시각으로 정렬하는 목록(§4.9 내 활동)은
 * {@link #encodeInteracted}·{@link #decodeInteracted} 를 쓴다.
 *
 * <p><b>네 형식을 하나로 합치지 않는다.</b> 단일 PK 토큰에 버전 칸을 더하면 §5.5·§7.1
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

    /**
     * 정렬 키가 {@code BIGINT} 인 목록의 다음 페이지 토큰 (§11.5 보관함).
     *
     * <p>{@link #encode(Integer)} 와 <b>같은 형식</b>이다 — 숫자 하나를 그대로 담는다.
     * 오버로드로 붙이는 이유가 그것이다. 이 API 만 따로 인코딩하면 토큰 형식이 두 벌이
     * 되고, 클라이언트가 커서를 불투명하게 다루지 못하게 된다.
     */
    public static String encode(Long lastSeq) {
        return ENCODER.encodeToString(String.valueOf(lastSeq).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 토큰 → {@code BIGINT} 정렬 키. 규칙은 {@link #decode(String)} 와 같다 —
     * 빈 값은 최초 호출({@code null}), 망가진 토큰은 400 이다.
     */
    public static Long decodeLong(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(new String(DECODER.decode(cursor), StandardCharsets.UTF_8));
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

    /** 점수 정렬 목록의 커서 위치 — {@code (이번 페이지 마지막 항목의 점수, 그 videoSeq)} (§8.1). */
    public record Score(long score, long lastSeq) {
    }

    /**
     * 점수 정렬 목록의 다음 페이지 토큰 (§8.1).
     *
     * <p><b>seq 가 함께 들어가는 것이 요점이다.</b> 점수만 담으면 같은 점수인 영상들의
     * 경계에서 페이지가 잘리지 않는다 — 점수 0(아직 아무도 안 본 영상)이 흔해 실제로
     * 일어나고, 같은 점수 묶음이 통째로 반복되거나 통째로 사라진다.
     *
     * <p>{@link #encodeSnapshot} 과 칸 수가 같아 <b>토큰 문자열만으로는 구분되지 않는다</b>
     * — 어느 목록의 커서인지는 형식이 아니라 부르는 쪽이 안다. 그래도 record 를 나눠 두는
     * 것은 담는 값의 뜻과 타입이 다르기 때문이다(버전·순위 ≠ 점수·seq, {@code int} ≠
     * {@code long}). 합치면 §6.1 의 순위 자리에 §8.1 의 {@code videoSeq} 를 넣어도
     * 컴파일이 통과한다.
     */
    public static String encodeScore(long score, long lastSeq) {
        return ENCODER.encodeToString(
                (score + DELIMITER + lastSeq).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 토큰 → {@code (점수, seq)}. {@code null}·빈 문자열은 "최초 호출" 이라 {@code null} 을 준다.
     *
     * <p>{@link #decode} 와 같은 이유로 망가진 토큰은 400 이다.
     */
    public static Score decodeScore(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(DECODER.decode(cursor), StandardCharsets.UTF_8);
            int at = decoded.indexOf(DELIMITER);
            if (at < 0) {
                throw new IllegalArgumentException("구분자가 없다");
            }
            return new Score(
                    Long.parseLong(decoded.substring(0, at)),
                    Long.parseLong(decoded.substring(at + 1)));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER, "cursor: " + cursor);
        }
    }

    /**
     * 상호작용 시각 목록의 커서 위치 (§4.9) —
     * {@code (마지막 항목의 상호작용 시각, 그 대상의 종류, 그 seq)}.
     */
    public record Interacted(Instant at, ActivityTargetType type, long lastSeq) {
    }

    /**
     * 상호작용 시각 목록의 다음 페이지 토큰 (§4.9).
     *
     * <p>{@link #encodeScore} 와 칸 수·타입이 같지만 <b>담는 값의 뜻이 다르다</b> —
     * 점수는 조회할 때마다 다시 계산되는 값이고 이쪽은 이미 일어난 사건의 시각이다.
     * 그래서 §8.1 이 감수하는 중복·누락이 여기에는 없다: 좋아요·댓글이 새로 달리면
     * 그 카드는 목록 <b>맨 앞</b>으로 올라가고, 커서는 이미 지나온 구간을 가리키므로
     * 뒤쪽 페이지가 흔들리지 않는다.
     *
     * <p>시각은 <b>epoch milli</b> 로 담는다. ISO 문자열로 담으면 구분자({@code :})가
     * 값 안에 들어가 파싱이 깨진다.
     *
     * <p><b>seq 가 함께 들어가는 것이 요점이다.</b> 같은 밀리초에 상호작용한 카드가
     * 둘이면 시각만으로는 경계가 잘리지 않는다 — 한 번의 요청으로 좋아요와 댓글이
     * 동시에 들어오는 일이 있어 실제로 겹친다.
     *
     * <p><b>대상의 종류도 함께 담는다.</b> 이 목록은 참여 영상과 챌린지를 한 줄로
     * 세우는데 둘은 seq 공간이 겹친다 — 타입이 없으면 같은 시각의 12번 영상과 12번
     * 챌린지 사이에서 경계가 잘리지 않는다. 담는 값은 상수의 <b>이름</b>이고, 정렬
     * 타이브레이크도 같은 문자열을 비교하므로 순서와 절단이 어긋나지 않는다.
     *
     * <p>세 칸이 되면서 {@link #encodeScore}·{@link #encodeSnapshot} 과 토큰 모양이
     * 갈렸다 — 그래도 형식으로 목록을 알아내려 하지 않는다. 어느 목록의 커서인지는
     * 여전히 부르는 쪽이 안다.
     */
    public static String encodeInteracted(Instant at, ActivityTargetType type, long lastSeq) {
        return ENCODER.encodeToString(
                (at.toEpochMilli() + DELIMITER + type.name() + DELIMITER + lastSeq)
                        .getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 토큰 → {@code (상호작용 시각, 대상 종류, seq)}. {@code null}·빈 문자열은
     * "최초 호출" 이라 {@code null} 을 준다.
     *
     * <p>{@link #decode} 와 같은 이유로 망가진 토큰은 400 이다. <b>모르는 타입 이름도
     * 400 이다</b> — {@code valueOf} 가 던지는 {@code IllegalArgumentException} 이
     * 같은 catch 로 떨어진다. 조용히 넘기면 그 이름과 일치하는 행이 없어 목록이 빈
     * 채로 끝나는데, 원인이 응답에 드러나지 않는다.
     */
    public static Interacted decodeInteracted(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(DECODER.decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split(DELIMITER);
            if (parts.length != 3) {
                throw new IllegalArgumentException("칸 수가 3이 아니다: " + parts.length);
            }
            return new Interacted(
                    Instant.ofEpochMilli(Long.parseLong(parts[0])),
                    ActivityTargetType.valueOf(parts[1]),
                    Long.parseLong(parts[2]));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER, "cursor: " + cursor);
        }
    }
}
