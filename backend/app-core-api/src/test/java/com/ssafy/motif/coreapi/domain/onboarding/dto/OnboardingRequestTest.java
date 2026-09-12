package com.ssafy.motif.coreapi.domain.onboarding.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 온보딩 요청의 형식 규칙 (명세 §3.3). DB 없이 도는 계약이라 기본 {@code test} 태스크에 둔다.
 *
 * <p>닉네임 규칙은 명세가 "영문·한글·`.`·`_`, 2~20자"로만 적어 경계가 애매한 지점
 * (숫자·공백·자모·길이 경계)이 많다. 여기서 해석을 고정한다.
 */
class OnboardingRequestTest {

    @ParameterizedTest
    @ValueSource(strings = {"모티프러버", "ab", "kkong.dance", "dancing_mandu", "가나다라마바사아자차카타파하가나다라마바"})
    @DisplayName("허용 — 영문·한글·마침표·언더바 2~20자")
    void validNicknames(String nickname) {
        assertThat(nickname).matches(OnboardingRequest.NICKNAME_PATTERN);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "a",                                  // 2자 미만
            "가나다라마바사아자차카타파하가나다라마바사",   // 21자
            "user_1",                             // 숫자 — 명세 열거에 없다
            "모티프 러버",                          // 공백
            "motif!",                             // 기호
            "ㄱㄴㄷ",                              // 완성형이 아닌 자모
            "🔥모티프"                             // 이모지
    })
    @DisplayName("거부 — 길이 경계 밖·숫자·공백·기호·자모·이모지")
    void invalidNicknames(String nickname) {
        assertThat(nickname).doesNotMatch(OnboardingRequest.NICKNAME_PATTERN);
    }

    @Test
    @DisplayName("아티스트 중복은 제거하고 요청 순서를 유지한다 (명세 §3.3 기본안)")
    void duplicatesAreRemovedKeepingOrder() {
        OnboardingRequest request = new OnboardingRequest(List.of(7, 3, 7, 12, 3), "모티프러버");

        assertThat(request.distinctArtistSeqs()).containsExactly(7, 3, 12);
    }

    @Test
    @DisplayName("중복이 없으면 그대로 유지된다")
    void withoutDuplicatesNothingChanges() {
        OnboardingRequest request = new OnboardingRequest(List.of(3, 7, 12), "모티프러버");

        assertThat(request.distinctArtistSeqs()).containsExactly(3, 7, 12);
    }
}
