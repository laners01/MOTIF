package com.ssafy.motif.coreapi.global.validation;

import com.ssafy.motif.coreapi.domain.onboarding.dto.OnboardingRequest;
import com.ssafy.motif.coreapi.domain.user.dto.NicknameUpdateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 닉네임 형식 제약 (S15P21D104-282 · 명세 §3.3 · §4.2).
 *
 * <p>검증은 애너테이션이 하므로 <b>서비스 테스트가 닿지 않는다.</b> 그래서 {@link Validator}
 * 를 직접 돌려 경계를 고정한다 — MockMvc 로 HTTP 를 묶는 것과는 다르다. 여기서 보는 것은
 * 제약 자체이지 웹 계층이 아니다.
 *
 * <p>이 티켓의 핵심은 <b>온보딩(§3.3)과 수정(§4.2)이 같은 규칙을 쓴다</b>는 것이라,
 * 두 DTO 를 나란히 놓고 같은 입력에 같은 결과가 나오는지까지 본다. 갈라지면 사용자가
 * 온보딩에서 정한 닉네임을 수정 화면에서 못 바꾸는 상태가 되는데, 그때 나오는 오류는
 * "형식이 틀렸다" 라서 원인을 짚기 어렵다.
 */
class NicknameValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void openValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @ParameterizedTest(name = "\"{0}\" 는 통과한다")
    @ValueSource(strings = {
            "모티프러버",
            "ab",                                      // 최소 길이 2
            "가나다라마바사아자차카타파하가나다라마바",         // 최대 길이 20
            "kkong.dance",
            "dancing_mandu",
            "a.b_C9",
            "__",
    })
    @DisplayName("허용 — 영문·한글·마침표·언더바 2~20자")
    void 통과(String nickname) {
        assertThat(violationsOf(nickname)).isEmpty();
    }

    @ParameterizedTest(name = "\"{0}\" 는 통과한다")
    @ValueSource(strings = {"user_1", "모티프2025", "99", "b2b.dance"})
    @DisplayName("허용 — 숫자 (§2.1 임시 닉네임 user_{seq} 가 통과해야 한다)")
    void 숫자는_허용된다(String nickname) {
        assertThat(violationsOf(nickname)).isEmpty();
    }

    @ParameterizedTest(name = "\"{0}\" 는 막힌다")
    @ValueSource(strings = {
            "a",                                          // 1자
            "가나다라마바사아자차카타파하가나다라마바사",          // 21자
            "",                                           // 빈 문자열
            " ",                                          // 공백
            " 닉네임",                                     // 앞 공백 — 다듬지 않고 막는다
            "닉네임 ",                                     // 뒤 공백
            "모티프 러버",                                  // 가운데 공백
            "motif!",                                     // 기호
            "a-b",                                        // 하이픈은 허용 목록에 없다
            "ㄱㄴㄷ",                                      // 완성형이 아닌 자모
            "🔥모티프",                                     // 이모지
    })
    @DisplayName("거부 — 길이 경계 밖·공백·기호·자모·이모지")
    void 막힘(String nickname) {
        assertThat(violationsOf(nickname)).isNotEmpty();
    }

    @ParameterizedTest
    @NullSource
    @DisplayName("null 은 필수 오류로 막힌다")
    void null_은_필수_오류다(String nickname) {
        assertThat(messagesOf(nickname)).containsExactly(Nickname.MESSAGE_REQUIRED);
    }

    @ParameterizedTest(name = "\"{0}\" 의 오류는 정확히 1건이다")
    @ValueSource(strings = {"", " ", "a", "hello!"})
    @DisplayName("오류는 언제나 1건이다 — 명세 §4.2 실패 예시가 1건이다")
    void 오류는_한_건이다(String nickname) {
        // @NotBlank 였다면 빈 문자열이 필수·형식 양쪽에 걸려 2건이 나간다.
        // @NotNull 이라 null 은 필수로, 나머지는 형식으로 정확히 갈린다
        assertThat(messagesOf(nickname)).containsExactly(Nickname.MESSAGE_FORMAT);
    }

    @ParameterizedTest(name = "\"{0}\" 에 온보딩과 §4.2 가 같게 반응한다")
    @ValueSource(strings = {"모티프러버", "user_1", "a", "hello!", "", " ", "가"})
    @DisplayName("온보딩(§3.3)과 수정(§4.2)이 같은 규칙을 쓴다")
    void 두_경로가_같은_규칙을_쓴다(String nickname) {
        Set<ConstraintViolation<NicknameUpdateRequest>> update =
                validator.validateProperty(new NicknameUpdateRequest(nickname), "nickname");
        Set<ConstraintViolation<OnboardingRequest>> onboarding =
                validator.validateProperty(
                        new OnboardingRequest(List.of(1), nickname), "nickname");

        assertThat(messages(update)).isEqualTo(messages(onboarding));
    }

    private static Set<ConstraintViolation<NicknameUpdateRequest>> violationsOf(String nickname) {
        return validator.validateProperty(new NicknameUpdateRequest(nickname), "nickname");
    }

    private static List<String> messagesOf(String nickname) {
        return messages(violationsOf(nickname));
    }

    private static List<String> messages(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream().map(ConstraintViolation::getMessage).sorted().toList();
    }
}
