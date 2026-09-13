package com.ssafy.motif.coreapi.admin.media;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.media.dto.PresignRequest;
import com.ssafy.motif.coreapi.admin.media.dto.PresignResponse;
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
            S3Presigner.builder()
                    .region(Region.AP_NORTHEAST_2)
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("test-access-key", "test-secret-key")))
                    .build(),
            null,   // S3Client — presign 은 HEAD 를 쓰지 않는다
            new AdminMediaProperties(
                    new AdminMediaProperties.Upload(
                            List.of("video/mp4", "video/quicktime"),
                            DataSize.ofMegabytes(300), Duration.ofSeconds(600)),
                    new AdminMediaProperties.Upload(
                            List.of("image/jpeg", "image/png", "image/webp"),
                            DataSize.ofMegabytes(10), Duration.ofSeconds(300)),
                    null),
            BUCKET);

    @Test
    @DisplayName("챌린지 원본은 challenges/{uuid}.mp4 — raw/ 와 분리된 프리픽스다")
    void 챌린지_원본_키() {
        PresignResponse response = service.presign(
                new PresignRequest(AdminMediaKind.CHALLENGE_VIDEO, "video/mp4", 48_213_377L, null));

        assertThat(response.objectKey()).matches("challenges/[0-9a-f-]{36}\\.mp4");
        assertThat(response.expiresIn()).isEqualTo(600);
        assertThat(response.uploadUrl()).contains(BUCKET).contains("X-Amz-Signature");
    }

    @Test
    @DisplayName("편집본은 challengeSeq 아래에, 등록 전이면 edited/pending/ 아래에 놓인다")
    void 편집본_키() {
        assertThat(service.presign(new PresignRequest(AdminMediaKind.EDITED_VIDEO, "video/mp4", 1L, 11))
                .objectKey()).startsWith("edited/11/").endsWith(".mp4");
        assertThat(service.presign(new PresignRequest(AdminMediaKind.EDITED_SCENE, "image/jpeg", 1L, null))
                .objectKey()).startsWith("edited/pending/").endsWith(".jpg");
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

    /**
     * 권한 문제가 "파일 없음"으로 위장하지 않는지 (2026-09-14 사고).
     *
     * <p>403 을 {@code false} 로 삼키면 어드민 화면에 {@code MEDIA_NOT_UPLOADED} 가 뜬다 —
     * 파일은 버킷에 멀쩡히 있는데 "안 올라왔다"고 하는 상태다. 그러면 아무도 IAM 을
     * 의심하지 않는다.
     */
    @Test
    @DisplayName("HEAD 가 403 이면 삼키지 않고 던진다 — 권한 문제는 파일 없음이 아니다")
    void headForbiddenIsNotTreatedAsMissing() {
        AdminMediaService withS3 = serviceWith(s3Throwing(403, "AccessDenied"));

        assertThatThrownBy(() -> withS3.exists("profiles/x.jpg"))
                .isInstanceOf(S3Exception.class);
    }

    @Test
    @DisplayName("HEAD 가 404 면 없는 것이 맞다")
    void headNotFoundIsMissing() {
        AdminMediaService withS3 = serviceWith(s3Throwing(404, "NoSuchKey"));

        assertThat(withS3.exists("profiles/x.jpg")).isFalse();
    }

    private static S3Client s3Throwing(int status, String errorCode) {
        S3Client s3 = mock(S3Client.class);
        S3Exception failure = (S3Exception) S3Exception.builder()
                .statusCode(status)
                .awsErrorDetails(AwsErrorDetails.builder().errorCode(errorCode).build())
                .build();
        given(s3.headObject(any(java.util.function.Consumer.class))).willThrow(failure);
        return s3;
    }

    private AdminMediaService serviceWith(S3Client s3) {
        return new AdminMediaService(
                S3Presigner.builder()
                        .region(Region.AP_NORTHEAST_2)
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create("test-access-key", "test-secret-key")))
                        .build(),
                s3,
                new AdminMediaProperties(
                        new AdminMediaProperties.Upload(
                                List.of("video/mp4"), DataSize.ofMegabytes(300), Duration.ofSeconds(600)),
                        new AdminMediaProperties.Upload(
                                List.of("image/jpeg"), DataSize.ofMegabytes(10), Duration.ofSeconds(300)),
                        null),
                BUCKET);
    }
}
