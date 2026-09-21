package com.ssafy.motif.coreapi.admin.media;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.media.dto.PresignRequest;
import com.ssafy.motif.coreapi.admin.media.dto.PresignResponse;
import com.ssafy.motif.coreapi.global.media.MediaObjectStore;
import com.ssafy.motif.coreapi.global.media.MediaUploadProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * presign 발급 (S15P21D104-588).
 *
 * <p>서명은 전부 로컬 계산이라 네트워크가 필요 없다 — 더미 자격증명으로 진짜
 * {@link S3Presigner} 를 쓴다. 목을 세우는 것보다 짧고, URL 이 실제로 서명되는지까지 본다.
 * (DoD 의 "발급 키로 실제 PUT" 은 버킷이 필요해 여기서는 못 한다 — 수동 확인 항목.)
 */
class AdminMediaServiceTest {

    private static final String BUCKET = "motif-media";

    private final AdminMediaService service = new AdminMediaService(
            new MediaObjectStore(
                    S3Presigner.builder()
                            .region(Region.AP_NORTHEAST_2)
                            .credentialsProvider(StaticCredentialsProvider.create(
                                    AwsBasicCredentials.create("test-access-key", "test-secret-key")))
                            .build(),
                    null,   // S3Client — presign 은 HEAD 를 쓰지 않는다
                    BUCKET),
            new AdminMediaProperties(
                    new MediaUploadProperties.Upload(
                            List.of("video/mp4", "video/quicktime"),
                            DataSize.ofMegabytes(300), Duration.ofSeconds(600)),
                    null),
            new MediaUploadProperties(
                    new MediaUploadProperties.Upload(
                            List.of("image/jpeg", "image/png", "image/webp"),
                            DataSize.ofMegabytes(10), Duration.ofSeconds(300))));

    @Test
    @DisplayName("챌린지 원본은 challenges/{uuid}.mp4 — raw/ 와 분리된 프리픽스다")
    void 챌린지_원본_키() {
        PresignResponse response = service.presign(
                new PresignRequest(AdminMediaKind.CHALLENGE_VIDEO, "video/mp4", 48_213_377L, null));

        assertThat(response.objectKey()).matches("challenges/[0-9a-f-]{36}\\.mp4");
        assertThat(response.expiresIn()).isEqualTo(600);
        assertThat(response.uploadUrl()).contains(BUCKET).contains("X-Amz-Signature");
    }

    /**
     * 0020 — 앱이 §7.6 으로 편집본을 직접 받게 되면서 <b>보호 등급</b>으로 올라갔다.
     * 프리픽스가 곧 등급이라({@code MediaKeys.isProtected}, CloudFront 캐시 동작
     * {@code private/*}) 여기가 {@code edited/} 로 되돌아가면 서명이 장식이 된다 —
     * 서버는 서명을 붙이는데 CloudFront 가 검증하지 않아 무서명 요청이 통과한다.
     */
    @Test
    @DisplayName("편집본은 private/edited/{challengeSeq} 아래에, 등록 전이면 .../pending/ 아래에 놓인다")
    void 편집본_키() {
        assertThat(service.presign(new PresignRequest(AdminMediaKind.EDITED_VIDEO, "video/mp4", 1L, 11))
                .objectKey()).startsWith("private/edited/11/").endsWith(".mp4");
        assertThat(service.presign(new PresignRequest(AdminMediaKind.EDITED_SCENE, "image/jpeg", 1L, null))
                .objectKey()).startsWith("private/edited/pending/").endsWith(".jpg");
        assertThat(service.presign(new PresignRequest(AdminMediaKind.EDITED_PREVIEW, "image/jpeg", 1L, 11))
                .objectKey()).startsWith("private/edited/11/").endsWith(".jpg");
    }

    @Test
    @DisplayName("이미지 kind 는 이미지 조건을 쓴다 — 만료 300초")
    void 이미지_조건() {
        assertThat(service.presign(new PresignRequest(AdminMediaKind.BANNER_IMAGE, "image/png", 1L, null))
                .expiresIn()).isEqualTo(300);
    }

    @Test
    @DisplayName("kind 에 맞지 않는 Content-Type 은 MEDIA_TYPE_NOT_ALLOWED")
    void 형식_거부() {
        assertThatThrownBy(() -> service.presign(
                new PresignRequest(AdminMediaKind.CHALLENGE_VIDEO, "image/jpeg", 1L, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.MEDIA_TYPE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("크기 상한 초과도 같은 코드 — 명세 §3.2 실패 표가 둘을 한 코드로 묶는다")
    void 크기_거부() {
        assertThatThrownBy(() -> service.presign(new PresignRequest(
                AdminMediaKind.CHALLENGE_VIDEO, "video/mp4", DataSize.ofMegabytes(301).toBytes(), null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.MEDIA_TYPE_NOT_ALLOWED);
    }

    // HEAD 의 403/404 판정은 MediaObjectStoreTest 로 옮겼다 — 코드가 그리로 갔고,
    // 같은 판정을 두 파일에서 보면 한쪽만 고쳐졌을 때 어느 쪽이 맞는지 알 수 없다.
}
