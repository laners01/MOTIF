package com.ssafy.motif.coreapi.global.response;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.domain.core.user.ActivityTargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 상호작용 커서 토큰 (명세 §1.9 · §4.9).
 *
 * <p>이 형식만 <b>칸이 셋</b>이다 — 시각·대상 종류·seq. 종류가 들어간 것은 §4.9 가
 * 참여 영상과 챌린지를 한 목록에 세우면서 두 seq 공간이 겹쳤기 때문이고, 그래서
 * 이 테스트가 고정하는 것은 <b>그 칸의 왕복과 거절</b>이다.
 */
class CursorsInteractedTest {

    private static final Instant AT = Instant.ofEpochMilli(1_787_918_400_000L);

    @Test
    @DisplayName("시각·대상 종류·seq 가 왕복한다")
    void roundTripsAllThreeParts() {
        String token = Cursors.encodeInteracted(AT, ActivityTargetType.CHALLENGE, 12L);

        assertThat(Cursors.decodeInteracted(token))
                .isEqualTo(new Cursors.Interacted(AT, ActivityTargetType.CHALLENGE, 12L));
    }

    @Test
    @DisplayName("시각은 밀리초까지 남는다 — 정렬 기준이 이 값이다")
    void keepsMillisecondPrecision() {
        Instant at = Instant.ofEpochMilli(1_787_918_400_123L);

        assertThat(Cursors.decodeInteracted(
                Cursors.encodeInteracted(at, ActivityTargetType.VIDEO, 1L)).at())
                .isEqualTo(at);
    }

    @Test
    @DisplayName("seq 가 long 이다 — videoSeq 는 BIGINT 라 int 로 담으면 넘친다")
    void seqIsLong() {
        String token = Cursors.encodeInteracted(AT, ActivityTargetType.VIDEO, Long.MAX_VALUE);

        assertThat(Cursors.decodeInteracted(token).lastSeq()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("URL-safe 다 — 쿼리로 오가도 깨지지 않는다")
    void tokenIsUrlSafe() {
        String token = Cursors.encodeInteracted(AT, ActivityTargetType.CHALLENGE, Long.MAX_VALUE);

        assertThat(token).doesNotContain("+", "/", "=");
    }

    @Test
    @DisplayName("커서가 없으면 null 이다 — 최초 호출")
    void blankCursorMeansFirstCall() {
        assertThat(Cursors.decodeInteracted(null)).isNull();
        assertThat(Cursors.decodeInteracted("  ")).isNull();
    }

    @Test
    @DisplayName("base64 가 아니면 400 이다 — 조용히 첫 페이지로 되돌리지 않는다")
    void rejectsNonBase64() {
        assertThatThrownBy(() -> Cursors.decodeInteracted("!!!not-base64!!!"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("칸이 둘뿐인 토큰은 400 이다 — 대상 종류가 없던 옛 형식")
    void rejectsTwoPartToken() {
        assertThatThrownBy(() -> Cursors.decodeInteracted(encoded("1787918400000:88")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("모르는 대상 종류는 400 이다 — 빈 목록으로 조용히 끝내지 않는다")
    void rejectsUnknownTargetType() {
        assertThatThrownBy(() -> Cursors.decodeInteracted(encoded("1787918400000:NOTICE:88")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("숫자가 아닌 칸이 섞이면 400 이다")
    void rejectsNonNumericParts() {
        assertThatThrownBy(() -> Cursors.decodeInteracted(encoded("abc:VIDEO:88")))
                .isInstanceOf(BusinessException.class);
    }

    private static String encoded(String raw) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
