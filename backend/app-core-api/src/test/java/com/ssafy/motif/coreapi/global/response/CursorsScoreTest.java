package com.ssafy.motif.coreapi.global.response;

import com.ssafy.motif.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 점수 커서 토큰 (명세 §1.9 · §8.1 · S15P21D104-140).
 *
 * <p>단일 PK 토큰·스냅샷 토큰은 각각 §5.5·§7.1 과 {@code CursorsSnapshotTest} 소관이라
 * 여기서 다루지 않는다 — 이 테스트가 고정하는 것은 <b>세 번째 형식</b>이다.
 */
class CursorsScoreTest {

    @Test
    @DisplayName("점수와 seq 가 왕복한다")
    void roundTripsScoreAndSeq() {
        String token = Cursors.encodeScore(2_000_000_003_400L, 88L);

        assertThat(Cursors.decodeScore(token))
                .isEqualTo(new Cursors.Score(2_000_000_003_400L, 88L));
    }

    @Test
    @DisplayName("seq 가 long 이다 — videoSeq 는 BIGINT 라 int 로 담으면 넘친다")
    void seqIsLong() {
        String token = Cursors.encodeScore(0L, Long.MAX_VALUE);

        assertThat(Cursors.decodeScore(token).lastSeq()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("점수 0이 왕복한다 — 아무도 안 본 영상이 흔하다")
    void roundTripsZeroScore() {
        assertThat(Cursors.decodeScore(Cursors.encodeScore(0L, 7L)))
                .isEqualTo(new Cursors.Score(0L, 7L));
    }

    @Test
    @DisplayName("URL-safe 다 — 쿼리로 오가도 깨지지 않는다")
    void tokenIsUrlSafe() {
        String token = Cursors.encodeScore(Long.MAX_VALUE, Long.MAX_VALUE);

        assertThat(token).doesNotContain("+", "/", "=");
    }

    @Test
    @DisplayName("커서가 없으면 null 이다 — 최초 호출")
    void blankCursorMeansFirstCall() {
        assertThat(Cursors.decodeScore(null)).isNull();
        assertThat(Cursors.decodeScore("  ")).isNull();
    }

    @Test
    @DisplayName("base64 가 아니면 400 이다 — 조용히 첫 페이지로 되돌리지 않는다")
    void rejectsNonBase64() {
        assertThatThrownBy(() -> Cursors.decodeScore("!!!not-base64!!!"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("구분자가 없는 토큰은 400 이다 — 단일 PK 토큰을 여기 넣은 경우")
    void rejectsSinglePartToken() {
        assertThatThrownBy(() -> Cursors.decodeScore(Cursors.encode(88)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("숫자가 아닌 칸이 섞이면 400 이다")
    void rejectsNonNumericParts() {
        String token = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("abc:10".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThatThrownBy(() -> Cursors.decodeScore(token))
                .isInstanceOf(BusinessException.class);
    }
}
