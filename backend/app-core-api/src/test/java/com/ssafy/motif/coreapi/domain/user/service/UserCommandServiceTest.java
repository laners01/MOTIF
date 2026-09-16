package com.ssafy.motif.coreapi.domain.user.service;

import com.ssafy.motif.coreapi.domain.user.dto.NicknameUpdateResponse;
import com.ssafy.motif.domain.core.user.User;
import com.ssafy.motif.domain.core.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 닉네임 변경 (S15P21D104-282 · 명세 §4.2).
 *
 * <p>형식 검증은 {@code @Nickname} 이 하므로 여기까지 오지 않는다 — 그쪽은
 * {@code NicknameValidationTest} 가 본다. 여기서 고정하는 것은 <b>무엇이 실제로
 * 바뀌는가</b>다.
 */
class UserCommandServiceTest {

    private static final int ME = 7;

    private UserRepository userRepository;
    private UserCommandService service;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new UserCommandService(userRepository);

        user = User.signUp();
        ReflectionTestUtils.setField(user, "userSeq", ME);
        ReflectionTestUtils.setField(user, "nickname", "user_7");
        given(userRepository.findById(ME)).willReturn(Optional.of(user));
    }

    @Test
    @DisplayName("닉네임이 바뀌고 확정된 값이 응답에 실린다")
    void changesNickname() {
        NicknameUpdateResponse response = service.changeNickname(ME, "모티프러버");

        assertThat(user.getNickname()).isEqualTo("모티프러버");
        assertThat(response.nickname()).isEqualTo("모티프러버");
    }

    @Test
    @DisplayName("mod_usr_seq 에 본인 seq 를 남긴다 — 온보딩의 최초 확정과 구분되는 표시다")
    void recordsModifier() {
        assertThat(user.getModUsrSeq()).isNull();

        service.changeNickname(ME, "모티프러버");

        // 온보딩도 기존 행을 UPDATE 하므로 mod_dttm 은 어차피 찍힌다.
        // "온보딩에서 정해진 이름"과 "사용자가 바꾼 이름"을 가르는 것은 이 칸뿐이다
        assertThat(user.getModUsrSeq()).isEqualTo(ME);
    }

    @Test
    @DisplayName("같은 값으로 바꿔도 성공한다 — 변경 없음을 오류로 돌려줄 실익이 없다")
    void samValueIsStillSuccess() {
        assertThat(service.changeNickname(ME, "user_7").nickname()).isEqualTo("user_7");
    }

    @Test
    @DisplayName("토큰의 사용자가 users 에 없으면 터뜨린다 — 조용히 넘어갈 상태가 아니다")
    void missingUserFailsLoud() {
        given(userRepository.findById(ME)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeNickname(ME, "모티프러버"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("userSeq=" + ME);
    }
}
