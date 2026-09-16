package com.ssafy.motif.coreapi.domain.user.service;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.user.dto.ProfileImagePresignResponse;
import com.ssafy.motif.coreapi.global.media.MediaObjectStore;
import com.ssafy.motif.coreapi.global.media.MediaUploadProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 프로필 이미지 업로드 URL 발급 (S15P21D104-280 · 명세 §4.3).
 */
class UserProfileImageServiceTest {

    private static final int ME = 7;
    private static final Duration TTL = Duration.ofSeconds(300);

    private MediaObjectStore objectStore;
    private ProfileImagePresignRegistry registry;
    private UserProfileImageService service;

    @BeforeEach
    void setUp() {
        objectStore = mock(MediaObjectStore.class);
        registry = mock(ProfileImagePresignRegistry.class);
        service = new UserProfileImageService(
                objectStore,
                new MediaUploadProperties(new MediaUploadProperties.Upload(
                        List.of("image/jpeg", "image/png", "image/webp"),
                        DataSize.ofMegabytes(10), TTL)),
                registry);

        given(objectStore.presignPut(anyString(), anyString(), anyLong(), any()))
                .willReturn("https://s3.example/put?X-Amz-Signature=sig");
    }

    @Test
    @DisplayName("profiles/{uuid}.jpg 키를 발급하고 만료를 함께 내린다")
    void issuesKeyAndExpiry() {
        ProfileImagePresignResponse response = service.presign(ME, "image/jpeg", 204_800L);

        assertThat(response.imageKey()).matches("profiles/[0-9a-f-]{36}\\.jpg");
        assertThat(response.expiresIn()).isEqualTo(300);
        assertThat(response.uploadUrl()).contains("X-Amz-Signature");
    }

    @Test
    @DisplayName("발급한 키를 기록한다 — 기록이 없으면 §4.4 가 통과시킬 근거가 없다")
    void remembersIssuedKey() {
        ProfileImagePresignResponse response = service.presign(ME, "image/jpeg", 204_800L);

        verify(registry).remember(ME, response.imageKey(), TTL);
    }

    @Test
    @DisplayName("보낸 조건이 그대로 서명에 넘어간다 — S3 가 그것으로 PUT 을 검사한다")
    void passesUploadConditionsToSignature() {
        ProfileImagePresignResponse response = service.presign(ME, "image/png", 1_024L);

        verify(objectStore).presignPut(response.imageKey(), "image/png", 1_024L, TTL);
    }

    @Test
    @DisplayName("허용 외 Content-Type 은 400 — 어드민 코드가 아니라 공통 코드다")
    void rejectsUnsupportedContentType() {
        assertRejected(() -> service.presign(ME, "image/gif", 1L));
    }

    @Test
    @DisplayName("크기 상한 초과는 400")
    void rejectsOversizedUpload() {
        assertRejected(() -> service.presign(ME, "image/jpeg", DataSize.ofMegabytes(11).toBytes()));
    }

    @Test
    @DisplayName("상한 경계값은 통과한다")
    void allowsExactlyMaxSize() {
        assertThat(service.presign(ME, "image/jpeg", DataSize.ofMegabytes(10).toBytes())).isNotNull();
    }

    private void assertRejected(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_PARAMETER);
        // 거부한 요청은 서명도 기록도 남기지 않는다
        verify(objectStore, never()).presignPut(anyString(), anyString(), anyLong(), any());
        verify(registry, never()).remember(any(), anyString(), any());
    }
}
