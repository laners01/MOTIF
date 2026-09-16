package com.ssafy.motif.coreapi.admin.media;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.media.VideoRules.Meta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 영상 검증 규칙 — 경계값 (S15P21D104-589 DoD).
 *
 * <p>ffmpeg 도 S3 도 타지 않는다 — 폼이 보낸 숫자 3개를 설정값과 대조할 뿐이라
 * 순수 단위 테스트로 규칙 전체를 고정할 수 있다(v0.5 §1.4).
 */
class VideoRulesTest {

    private static final long CHALLENGE_MAX_MS = 60_000;
    private static final long ASSET_MAX_MS = 20_000;

    private final VideoRules rules = new VideoRules(new AdminMediaProperties(
            null,
            new AdminMediaProperties.Rules(
                    Duration.ofSeconds(60), Duration.ofSeconds(20), Duration.ofMillis(500))));

    @Nested
    @DisplayName("챌린지 원본 — 세로 · 60초 이내")
    class ChallengeOrigin {

        @Test
        @DisplayName("썸네일 시각을 안 보내면 검사하지 않는다 — 워커 기본값 1.5초")
        void 썸네일_시각_생략() {
            assertThatCode(() -> rules.checkChallengeOrigin(new Meta(10_000, 1080, 1920), null))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("영상 안이면 통과한다")
        void 썸네일_시각_통과() {
            assertThatCode(() -> rules.checkChallengeOrigin(new Meta(10_000, 1080, 1920), 9_999L))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("길이와 같으면 이미 영상 밖이다 — 경계는 배타적")
        void 썸네일_시각_경계() {
            assertThatThrownBy(() -> rules.checkChallengeOrigin(new Meta(10_000, 1080, 1920), 10_000L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getData())
                    .isEqualTo(List.of(VideoRules.THUMBNAIL_AT_OUT_OF_RANGE));
        }

        @Test
        @DisplayName("음수면 막는다 — 폼 값이라 임의의 수가 올 수 있다")
        void 썸네일_시각_음수() {
            assertThatThrownBy(() -> rules.checkChallengeOrigin(new Meta(10_000, 1080, 1920), -1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getData())
                    .isEqualTo(List.of(VideoRules.THUMBNAIL_AT_OUT_OF_RANGE));
        }

        @Test
        @DisplayName("막으면 ffmpeg 가 빈 프레임을 뱉는 일이 없다 — 다른 위반과 함께 모아서 던진다")
        void 썸네일_시각_다른_위반과_함께() {
            assertThatThrownBy(() -> rules.checkChallengeOrigin(new Meta(10_000, 1920, 1080), 20_000L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getData())
                    .isEqualTo(List.of(VideoRules.PORTRAIT_REQUIRED,
                            VideoRules.THUMBNAIL_AT_OUT_OF_RANGE));
        }

        @Test
        @DisplayName("세로이고 정확히 상한 길이면 통과한다")
        void 경계값_통과() {
            assertThatCode(() -> rules.checkChallengeOrigin(new Meta(CHALLENGE_MAX_MS, 1080, 1920)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("1ms 라도 넘으면 DURATION_EXCEEDED")
        void 길이_초과() {
            assertThat(reasonsOf(() -> rules.checkChallengeOrigin(new Meta(CHALLENGE_MAX_MS + 1, 1080, 1920))))
                    .containsExactly(VideoRules.DURATION_EXCEEDED);
        }

        @Test
        @DisplayName("정사각형은 세로가 아니다 — h > w 여야 한다")
        void 정사각형_거부() {
            assertThat(reasonsOf(() -> rules.checkChallengeOrigin(new Meta(10_000, 1080, 1080))))
                    .containsExactly(VideoRules.PORTRAIT_REQUIRED);
        }

        @Test
        @DisplayName("위반이 여럿이면 한 번에 모아서 준다 — 어드민이 한 번에 고친다")
        void 위반_누적() {
            assertThat(reasonsOf(() -> rules.checkChallengeOrigin(new Meta(CHALLENGE_MAX_MS + 1, 1920, 1080))))
                    .containsExactlyInAnyOrder(VideoRules.PORTRAIT_REQUIRED, VideoRules.DURATION_EXCEEDED);
        }

        @Test
        @DisplayName("화면이 메타데이터를 못 읽어 0 이 오면 방향 판정 전에 걸러진다")
        void 값_없음() {
            assertThat(reasonsOf(() -> rules.checkChallengeOrigin(new Meta(0, 0, 0))))
                    .containsExactlyInAnyOrder(VideoRules.DURATION_INVALID, VideoRules.DIMENSION_INVALID);
        }
    }

    @Nested
    @DisplayName("에셋 — 가로 · 20초 이내")
    class Asset {

        @Test
        void 경계값_통과() {
            assertThatCode(() -> rules.checkAsset(new Meta(ASSET_MAX_MS, 1920, 1080)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("세로 영상은 LANDSCAPE_REQUIRED — 챌린지와 방향이 반대다")
        void 세로_거부() {
            assertThat(reasonsOf(() -> rules.checkAsset(new Meta(10_000, 1080, 1920))))
                    .containsExactly(VideoRules.LANDSCAPE_REQUIRED);
        }

        @Test
        @DisplayName("챌린지 상한(60초)은 에셋에 적용되지 않는다")
        void 에셋_상한은_따로다() {
            assertThat(reasonsOf(() -> rules.checkAsset(new Meta(ASSET_MAX_MS + 1, 1920, 1080))))
                    .containsExactly(VideoRules.DURATION_EXCEEDED);
        }
    }

    @Nested
    @DisplayName("편집본 — 원본 ±500ms")
    class EditedDuration {

        @Test
        @DisplayName("허용 오차 경계(±500ms)는 통과한다")
        void 경계값_통과() {
            assertThatCode(() -> rules.checkEditedDuration(30_500, 30_000)).doesNotThrowAnyException();
            assertThatCode(() -> rules.checkEditedDuration(29_500, 30_000)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("오차를 넘으면 MEDIA_INVALID 가 아니라 EDITED_DURATION_MISMATCH(409) — 명세 §5")
        void 오차_초과() {
            assertThatThrownBy(() -> rules.checkEditedDuration(30_501, 30_000))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(AdminErrorCode.EDITED_DURATION_MISMATCH);
        }
    }

    /** {@code MEDIA_INVALID} 의 {@code data} 에 실린 위반 사유 목록 (§1.4 "data 에 사유"). */
    @SuppressWarnings("unchecked")
    private static List<String> reasonsOf(Runnable check) {
        try {
            check.run();
        } catch (BusinessException e) {
            assertThat(e.getErrorCode()).isEqualTo(AdminErrorCode.MEDIA_INVALID);
            return (List<String>) e.getData();
        }
        throw new AssertionError("MEDIA_INVALID 가 나와야 한다");
    }
}
