package com.ssafy.motif.coreapi.global.media;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 화질 인자 검증 — 경로 탈출 차단.
 *
 * <p>{@code rendition} 은 URL 경로에서 와서 S3 키에 그대로 이어붙는다. 이 가드가 없으면
 * <b>인가를 통과한 사용자가 프리픽스를 벗어나 임의의 오브젝트에 서명을 받아낼 수 있다</b>.
 * S3 나 서명 키가 없어도 검증은 인자만 보므로 여기서 돈다.
 */
class MediaPlaylistServiceTest {

    /** S3·서명 없이 검증 단계까지만 태운다 — 통과하면 그 다음(S3 접근)에서 NPE 로 떨어진다. */
    private static void callVariant(String rendition) {
        new MediaPlaylistService(null, null).variant("private/motif/uuid", rendition);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../../../private/motif/남의uuid/720p",
            "..",
            "720p/../../challenges",
            "720P",
            "",
            "720p/index.m3u8",
    })
    @DisplayName("사다리에 없는 화질은 거부한다 — 경로 탈출과 오타를 같은 자리에서 막는다")
    void 알_수_없는_화질(String rendition) {
        assertThatThrownBy(() -> callVariant(rendition))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("알 수 없는 화질");
    }

    @ParameterizedTest
    @ValueSource(strings = {"480p", "720p", "1080p"})
    @DisplayName("사다리에 있는 화질은 통과한다 — app-media 의 Rendition.LADDER 와 같은 값이어야 한다")
    void 알려진_화질(String rendition) {
        // 검증을 지나면 S3 접근에서 떨어진다. IllegalArgumentException 이 아니면 통과한 것이다
        assertThatThrownBy(() -> callVariant(rendition))
                .isNotInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("재생목록 Content-Type 은 고정 — 틀리면 플레이어가 받아들이지 않는다")
    void 콘텐츠_타입() {
        assertThat(MediaPlaylistService.contentType()).isEqualTo("application/vnd.apple.mpegurl");
    }
}
