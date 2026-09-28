package com.ssafy.motif.coreapi.domain.onboarding.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 온보딩 요청의 형식 규칙 (명세 §3.3). DB 없이 도는 계약이라 기본 {@code test} 태스크에 둔다.
 *
 * <p><b>닉네임 경계값은 여기 없다.</b> 규칙이 {@code global.validation.Nickname} 으로
 * 올라가면서(§4.2 와 공유) 그쪽의 {@code NicknameValidationTest} 가 전부 가져갔다 —
 * 같은 경계를 두 파일에서 보면 한쪽만 고쳐졌을 때 어느 쪽이 맞는지 알 수 없다.
 * 여기 남은 것은 <b>온보딩에만 있는 계약</b>인 아티스트 중복 제거다.
 */
class OnboardingRequestTest {

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
