package com.ssafy.motif.coreapi.global.response;

import com.ssafy.motif.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 스냅샷 커서 토큰 (명세 §1.9 · §6.1 · S15P21D104-113).
 *
 * <p>단일 PK 토큰({@code encode}/{@code decode})은 §5.5·§7.1 소관이라 여기서 다루지
 * 않는다 — 이 테스트가 고정하는 것은 <b>새로 더한 조합 형식</b>이다.
 */
class CursorsSnapshotTest {

    @Test
    @DisplayName("버전과 순위가 왕복한다")
    void roundTripsVersionAndRank() {
        String token = Cursors.encodeSnapshot(1788998400000L, 20);

        assertThat(Cursors.decodeSnapshot(token))
                .isEqualTo(new Cursors.Snapshot(1788998400000L, 20));
    }

    @Test
    @DisplayName("URL-safe 다 — 쿼리로 오가도 깨지지 않는다")
    void tokenIsUrlSafe() {
        String token = Cursors.encodeSnapshot(Long.MAX_VALUE, Integer.MAX_VALUE);

        assertThat(token).doesNotContain("+", "/", "=");
    }

    @Test
    @DisplayName("커서가 없으면 null 이다 — 최초 호출")
    void blankCursorMeansFirstCall() {
        assertThat(Cursors.decodeSnapshot(null)).isNull();
        assertThat(Cursors.decodeSnapshot("  ")).isNull();
    }

    @Test
    @DisplayName("base64 가 아니면 400 이다 — 조용히 첫 페이지로 되돌리지 않는다")
    void rejectsNonBase64() {
        assertThatThrownBy(() -> Cursors.decodeSnapshot("!!!not-base64!!!"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("구분자가 없는 토큰은 400 이다 — 단일 PK 토큰을 여기 넣은 경우")
    void rejectsSinglePartToken() {
        // §5.5·§7.1 의 토큰을 §6.1 에 잘못 보내면 여기에 걸린다
        assertThatThrownBy(() -> Cursors.decodeSnapshot(Cursors.encode(88)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("숫자가 아닌 칸이 섞이면 400 이다")
    void rejectsNonNumericParts() {
        String token = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("abc:10".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThatThrownBy(() -> Cursors.decodeSnapshot(token))
                .isInstanceOf(BusinessException.class);
    }
}
