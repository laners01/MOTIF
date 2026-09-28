package com.ssafy.motif.coreapi.domain.media;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 산출물 프리픽스 규약 고정 (ADR-0010 · API 명세 §1.7).
 *
 * <p><b>값을 그대로 박아 둔다.</b> 이 문자열은 CloudFront 캐시 동작의 path pattern
 * (`private/*`)과 짝이다 — 코드만 바꾸면 배포 설정과 조용히 어긋나서, 보호 대상이
 * 서명 없는 기본 동작으로 넘어가도 <b>테스트도 화면도 아무 말을 하지 않는다</b>.
 * 여기서 깨뜨려 사람이 CloudFront 도 같이 고치게 만든다.
 */
class MediaKeysTest {

    private static final String UUID = "3fa85f64-5717-4562-b3fc-2c963f66afa6";

    @Test
    @DisplayName("합성 결과는 언제나 private/ 로 간다 — 게시 전 프리뷰도 재생 대상이다")
    void 합성_결과는_보호_프리픽스() {
        assertThat(MediaKeys.forCompositeResult("CHALLENGE", UUID))
                .isEqualTo("private/challenge/" + UUID);
        assertThat(MediaKeys.forCompositeResult("MOTIF", UUID))
                .isEqualTo("private/motif/" + UUID);
    }

    @Test
    @DisplayName("게시해도 자리를 옮기지 않는다 — 공개 사본 트리가 없다 (ADR-0013)")
    void 게시본도_보호_프리픽스() {
        // forPublishedChallenge 가 있던 자리다. 되살리면 visibility 와 키가 다시 갈린다
        assertThat(MediaKeys.isProtected(MediaKeys.forCompositeResult("CHALLENGE", UUID)))
                .isTrue();
    }

    @Test
    @DisplayName("어드민 챌린지 원본과 에셋은 자리를 옮기지 않는다")
    void 공개_등급은_그대로() {
        assertThat(MediaKeys.forChallengeOrigin(UUID)).isEqualTo("challenges/" + UUID);
        assertThat(MediaKeys.forAssetThumbnail("assets/" + UUID + ".mp4"))
                .isEqualTo("assets/" + UUID + "/thumb.jpg");
    }

    @Test
    @DisplayName("경로에 user_seq 가 섞이지 않는다 — 순차 정수는 열거를 부른다")
    void 소유자는_경로에_없다() {
        assertThat(MediaKeys.forCompositeResult("MOTIF", UUID).split("/"))
                .containsExactly("private", "motif", UUID);
    }

    @Test
    @DisplayName("빈 uuid·모르는 jobType 은 기동 중에 터뜨린다 — 조용히 이상한 키를 만들지 않는다")
    void 잘못된_입력은_예외() {
        assertThatThrownBy(() -> MediaKeys.forCompositeResult("CHALLENGE", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MediaKeys.forCompositeResult("UNKNOWN", UUID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MediaKeys.forCompositeResult("CHALLENGE", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
