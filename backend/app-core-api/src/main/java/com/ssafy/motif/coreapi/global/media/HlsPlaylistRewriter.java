package com.ssafy.motif.coreapi.global.media;

import java.util.function.UnaryOperator;

/**
 * HLS 재생목록의 주소를 다시 쓴다 (API 명세 §1.7 보호 등급).
 *
 * <p><b>왜 필요한가.</b> 재생목록 안의 주소는 상대 경로이고, 상대 경로는 쿼리스트링을
 * 상속하지 않는다. {@code master.m3u8} 에 서명을 붙여도 그 안의
 * {@code 720p/index.m3u8} · {@code data.m4s} 요청에는 따라붙지 않아 403 이 난다.
 * 그래서 내려보내기 전에 주소를 바꿔 박는다.
 *
 * <p>두 단계가 향하는 곳이 다르다:
 *
 * <pre>
 * master  의 화질별 주소 → core-api   (자식도 다시 써야 하므로)
 * variant 의 조각 주소   → 서명된 CDN 절대 URL (바이트는 CDN 이 낸다)
 * </pre>
 *
 * <p><b>순수 문자열 변환이다</b> — S3 도 서명도 모른다. 주소를 무엇으로 바꿀지는
 * 호출자가 {@link UnaryOperator} 로 준다. 그래야 서명 키 없이 파싱을 테스트할 수 있다.
 *
 * <p>다루는 것은 두 자리뿐이다. ① {@code #} 로 시작하지 않는 줄 전체가 주소다
 * ② {@code #EXT-X-MAP:URI="..."} 의 따옴표 안. {@code #EXT-X-KEY:URI=}(암호화)는
 * 이 파이프라인이 만들지 않으므로 다루지 않는다 — 생기면 여기도 함께 고쳐야 한다.
 */
public final class HlsPlaylistRewriter {

    private static final String MAP_PREFIX = "#EXT-X-MAP:";
    private static final String URI_ATTRIBUTE = "URI=\"";

    private HlsPlaylistRewriter() {
    }

    /**
     * 마스터 재생목록 — 화질별 재생목록 주소를 바꾼다.
     *
     * @param playlist   원본 본문
     * @param variantUri {@code "720p/index.m3u8"} → 내려보낼 주소
     */
    public static String rewriteMaster(String playlist, UnaryOperator<String> variantUri) {
        return rewrite(playlist, variantUri, false);
    }

    /**
     * 화질별 재생목록 — 조각 주소와 init 세그먼트({@code EXT-X-MAP})를 바꾼다.
     *
     * <p><b>{@code EXT-X-MAP} 을 빼먹으면 재생이 시작조차 안 된다.</b> fMP4 는 init
     * 세그먼트에 디코더 설정이 들어 있어서, 조각만 서명하면 첫 요청이 403 이다.
     *
     * @param playlist    원본 본문
     * @param segmentUri  {@code "data.m4s"} → 서명된 절대 URL
     */
    public static String rewriteVariant(String playlist, UnaryOperator<String> segmentUri) {
        return rewrite(playlist, segmentUri, true);
    }

    private static String rewrite(String playlist, UnaryOperator<String> mapper, boolean rewriteMap) {
        StringBuilder out = new StringBuilder(playlist.length() + 256);
        // split(-1) — 마지막 빈 줄을 버리지 않는다. 재생목록은 줄바꿈으로 끝나는 것이 정상이고
        // 그것까지 보존해야 원본과 바이트가 같은 모양으로 나간다
        String[] lines = playlist.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(rewriteLine(lines[i], mapper, rewriteMap));
        }
        return out.toString();
    }

    private static String rewriteLine(String raw, UnaryOperator<String> mapper, boolean rewriteMap) {
        String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
        String carriage = raw.endsWith("\r") ? "\r" : "";

        if (line.isBlank()) {
            return raw;
        }
        if (!line.startsWith("#")) {
            return mapper.apply(line) + carriage;
        }
        if (rewriteMap && line.startsWith(MAP_PREFIX)) {
            return rewriteMapUri(line, mapper) + carriage;
        }
        return raw;
    }

    /** {@code #EXT-X-MAP:URI="data.m4s",BYTERANGE="1446@0"} — 따옴표 안만 바꾸고 나머지 속성은 둔다. */
    private static String rewriteMapUri(String line, UnaryOperator<String> mapper) {
        int start = line.indexOf(URI_ATTRIBUTE);
        if (start < 0) {
            return line;
        }
        int valueStart = start + URI_ATTRIBUTE.length();
        int valueEnd = line.indexOf('"', valueStart);
        if (valueEnd < 0) {
            // 따옴표가 안 닫혔다. 여기서 조용히 넘기면 서명 없는 init 세그먼트가 나가
            // 재생이 403 으로 죽는데 원인이 재생목록이라는 걸 알기 어렵다
            throw new IllegalArgumentException("EXT-X-MAP 의 URI 따옴표가 닫히지 않았다: " + line);
        }
        return line.substring(0, valueStart)
                + mapper.apply(line.substring(valueStart, valueEnd))
                + line.substring(valueEnd);
    }
}
