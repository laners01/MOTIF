package com.ssafy.motif.coreapi.domain.user.service;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.media.MediaErrorCode;
import com.ssafy.motif.coreapi.domain.user.dto.NicknameUpdateResponse;
import com.ssafy.motif.coreapi.global.media.MediaObjectStore;
import com.ssafy.motif.coreapi.global.media.MediaUrlResolver;
import com.ssafy.motif.domain.core.user.User;
import com.ssafy.motif.domain.core.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 마이페이지 수정 (명세 §4.2 · §4.4).
 *
 * <p>닉네임 형식 검증은 {@code @Nickname} 이 하므로 여기까지 오지 않는다 — 그쪽은
 * {@code NicknameValidationTest} 가 본다. 여기서 고정하는 것은 <b>무엇이 실제로
 * 바뀌는가</b>와 <b>어떤 순서로 거부하는가</b>다.
 */
class UserCommandServiceTest {

    private static final int ME = 7;
    private static final String IMAGE_KEY = "profiles/3f9c1a2b.jpg";

    private UserRepository userRepository;
    private ProfileImagePresignRegistry presignRegistry;
    private MediaObjectStore objectStore;
    private UserCommandService service;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        presignRegistry = mock(ProfileImagePresignRegistry.class);
        objectStore = mock(MediaObjectStore.class);
        MediaUrlResolver mediaUrlResolver = mock(MediaUrlResolver.class);
        given(mediaUrlResolver.resolveProfile(anyString()))
                .willAnswer(call -> "https://cdn.motif.test/" + call.getArgument(0));

        service = new UserCommandService(
                userRepository, presignRegistry, objectStore, mediaUrlResolver);

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

    @Nested
    @DisplayName("§4.4 프로필 이미지 반영")
    class ProfileImage {

        @Test
        @DisplayName("키를 반영하고 무서명 공개 CDN URL 을 돌려준다")
        void appliesKeyAndReturnsPublicUrl() {
            givenIssuedAndUploaded();

            assertThat(service.changeProfileImage(ME, IMAGE_KEY).profileImageUrl())
                    .isEqualTo("https://cdn.motif.test/" + IMAGE_KEY)
                    .doesNotContain("Signature=");
            assertThat(user.getProfileImageKey()).isEqualTo(IMAGE_KEY);
        }

        @Test
        @DisplayName("mod_usr_seq 에 본인 seq 를 남긴다 — §4.2 와 같은 규칙이다")
        void recordsModifier() {
            givenIssuedAndUploaded();

            service.changeProfileImage(ME, IMAGE_KEY);

            assertThat(user.getModUsrSeq()).isEqualTo(ME);
        }

        @Test
        @DisplayName("반영이 끝나면 발급 기록을 지운다")
        void forgetsRecordAfterApply() {
            givenIssuedAndUploaded();

            service.changeProfileImage(ME, IMAGE_KEY);

            verify(presignRegistry).forget(ME, IMAGE_KEY);
        }

        @Test
        @DisplayName("발급받지 않은 키는 403 — 남의 키를 보내도 여기서 막힌다")
        void rejectsKeyNotIssuedToMe() {
            given(presignRegistry.isIssuedTo(ME, IMAGE_KEY)).willReturn(false);

            assertThatThrownBy(() -> service.changeProfileImage(ME, IMAGE_KEY))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(CommonErrorCode.FORBIDDEN);
        }

        @Test
        @DisplayName("403 일 때는 S3 를 두드리지 않는다 — 존재 여부가 응답 시간에 드러난다")
        void doesNotTouchStorageWhenRejected() {
            given(presignRegistry.isIssuedTo(ME, IMAGE_KEY)).willReturn(false);

            assertThatThrownBy(() -> service.changeProfileImage(ME, IMAGE_KEY))
                    .isInstanceOf(BusinessException.class);

            verify(objectStore, never()).exists(anyString());
        }

        @Test
        @DisplayName("발급은 받았지만 S3 에 없으면 409 — 프로필도 바뀌지 않는다")
        void rejectsWhenObjectMissing() {
            given(presignRegistry.isIssuedTo(ME, IMAGE_KEY)).willReturn(true);
            given(objectStore.exists(IMAGE_KEY)).willReturn(false);

            assertThatThrownBy(() -> service.changeProfileImage(ME, IMAGE_KEY))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(MediaErrorCode.IMAGE_NOT_UPLOADED);
            assertThat(user.getProfileImageKey()).isNull();
        }

        private void givenIssuedAndUploaded() {
            given(presignRegistry.isIssuedTo(ME, IMAGE_KEY)).willReturn(true);
            given(objectStore.exists(IMAGE_KEY)).willReturn(true);
        }
    }
}
