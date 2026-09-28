package com.ssafy.motif.coreapi.global.media;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 재생목록 리라이팅 (API 명세 §1.7).
 *
 * <p>본문은 <b>배포된 실물에서 가져왔다</b> —
 * {@code challenges/5ece3915-.../master.m3u8} 과 그 720p 재생목록(2026-09-15).
 * 손으로 지어낸 샘플은 우리가 실제로 만드는 형태와 어긋날 수 있다.
 */
class HlsPlaylistRewriterTest {

    private static final String MASTER = """
            #EXTM3U
            #EXT-X-VERSION:7
            #EXT-X-STREAM-INF:BANDWIDTH=1616590,RESOLUTION=480x854
            480p/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2686178,RESOLUTION=720x1280
            720p/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=4492974,RESOLUTION=1080x1920
            1080p/index.m3u8
            """;

    private static final String VARIANT = """
            #EXTM3U
            #EXT-X-VERSION:7
            #EXT-X-TARGETDURATION:2
            #EXT-X-MEDIA-SEQUENCE:0
            #EXT-X-PLAYLIST-TYPE:VOD
            #EXT-X-MAP:URI="data.m4s",BYTERANGE="1446@0"
            #EXTINF:2.000000,
            #EXT-X-BYTERANGE:648186@1446
            data.m4s
            #EXTINF:2.000000,
            #EXT-X-BYTERANGE:251353@648:
            data.m4s
            #EXT-X-ENDLIST
            """;

    @Test
    @DisplayName("마스터의 화질별 주소만 바뀐다 — BANDWIDTH·RESOLUTION 은 그대로여야 ABR 이 고른다")
    void 마스터_리라이팅() {
        String out = HlsPlaylistRewriter.rewriteMaster(MASTER, u -> "/api/v1/media/JOB/" + u);

        assertThat(out).contains(
                "/api/v1/media/JOB/480p/index.m3u8",
                "/api/v1/media/JOB/720p/index.m3u8",
                "/api/v1/media/JOB/1080p/index.m3u8");
        assertThat(out).contains("#EXT-X-STREAM-INF:BANDWIDTH=1616590,RESOLUTION=480x854");
        assertThat(out).doesNotContain("\n480p/index.m3u8");
    }

    @Test
    @DisplayName("조각 주소와 EXT-X-MAP 이 함께 바뀐다 — MAP 을 빼면 재생이 시작조차 안 된다")
    void 화질별_리라이팅() {
        String out = HlsPlaylistRewriter.rewriteVariant(VARIANT, u -> "https://cdn/x/" + u + "?Signature=s");

        assertThat(out).contains("#EXT-X-MAP:URI=\"https://cdn/x/data.m4s?Signature=s\",BYTERANGE=\"1446@0\"");
        assertThat(out).contains("\nhttps://cdn/x/data.m4s?Signature=s\n");
        assertThat(out).doesNotContain("\ndata.m4s\n");
    }

    @Test
    @DisplayName("BYTERANGE 는 손대지 않는다 — 이 값이 어긋나면 엉뚱한 바이트를 디코더에 넣는다")
    void 바이트레인지_보존() {
        String out = HlsPlaylistRewriter.rewriteVariant(VARIANT, u -> "SIGNED");

        assertThat(out).contains("#EXT-X-BYTERANGE:648186@1446");
        assertThat(out).contains("#EXT-X-BYTERANGE:251353@648:");
        assertThat(out).contains("#EXT-X-TARGETDURATION:2", "#EXT-X-ENDLIST");
    }

    @Test
    @DisplayName("마스터 모드는 EXT-X-MAP 을 건드리지 않는다 — 단계마다 향하는 곳이 다르다")
    void 마스터는_MAP_을_두고_간다() {
        String out = HlsPlaylistRewriter.rewriteMaster(VARIANT, u -> "BAD");

        assertThat(out).contains("#EXT-X-MAP:URI=\"data.m4s\",BYTERANGE=\"1446@0\"");
    }

    @Test
    @DisplayName("끝 줄바꿈과 CRLF 를 보존한다 — 플레이어가 줄 단위로 읽는다")
    void 줄바꿈_보존() {
        assertThat(HlsPlaylistRewriter.rewriteMaster(MASTER, u -> "X")).endsWith("\n");

        String crlf = "#EXTM3U\r\n#EXT-X-STREAM-INF:BANDWIDTH=1\r\n480p/index.m3u8\r\n";
        assertThat(HlsPlaylistRewriter.rewriteMaster(crlf, u -> "X")).isEqualTo(
                "#EXTM3U\r\n#EXT-X-STREAM-INF:BANDWIDTH=1\r\nX\r\n");
    }

    @Test
    @DisplayName("깨진 EXT-X-MAP 은 던진다 — 조용히 넘기면 서명 없는 init 이 나가 403 으로 죽는다")
    void 깨진_MAP_은_던진다() {
        assertThatThrownBy(() -> HlsPlaylistRewriter.rewriteVariant(
                "#EXT-X-MAP:URI=\"data.m4s\n", u -> "X"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EXT-X-MAP");
    }
}
