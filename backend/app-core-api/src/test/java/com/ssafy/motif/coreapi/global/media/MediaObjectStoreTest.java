package com.ssafy.motif.coreapi.global.media;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * S3 원시 연산 (S15P21D104-280 · 명세 §1.4 · §4.3).
 *
 * <p>서명은 전부 로컬 계산이라 네트워크가 필요 없다 — 더미 자격증명으로 진짜
 * {@link S3Presigner} 를 쓴다. HEAD 만 목이다.
 *
 * <p>HEAD 의 403/404 판정은 {@code AdminMediaServiceTest} 에 있던 것을 코드와 함께
 * 옮겨온 것이다(2026-09-16). 어드민과 팬 프로필이 같은 연산을 쓰게 되면서 판정이
 * 한 곳으로 내려왔다.
 */
class MediaObjectStoreTest {

    private static final String BUCKET = "motif-media";

    private static MediaObjectStore storeWith(S3Client s3) {
        return new MediaObjectStore(
                S3Presigner.builder()
                        .region(Region.AP_NORTHEAST_2)
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create("test-access-key", "test-secret-key")))
                        .build(),
                s3,
                BUCKET);
    }

    @Test
    @DisplayName("presign URL 에 버킷·키·서명이 들어간다")
    void presignsPutUrl() {
        String url = storeWith(null).presignPut(
                "profiles/3f9c1a2b.jpg", "image/jpeg", 204_800L, Duration.ofSeconds(300));

        assertThat(url).contains(BUCKET, "profiles/3f9c1a2b.jpg", "X-Amz-Signature");
    }

    @Test
    @DisplayName("Content-Type·Length 가 서명 대상에 들어간다 — 다른 값으로 PUT 하면 S3 가 거부한다")
    void bindsUploadConditionsIntoSignature() {
        MediaObjectStore store = storeWith(null);

        String jpeg = store.presignPut("profiles/a.jpg", "image/jpeg", 1L, Duration.ofSeconds(300));
        String png = store.presignPut("profiles/a.jpg", "image/png", 1L, Duration.ofSeconds(300));
        String bigger = store.presignPut("profiles/a.jpg", "image/jpeg", 2L, Duration.ofSeconds(300));

        // 서명 대상 헤더 목록에 실려 나온다
        assertThat(jpeg).contains("content-type", "content-length");
        // 조건이 다르면 서명이 달라진다 — 이게 "조건이 서명에 박혔다"는 증거다
        assertThat(signatureOf(jpeg)).isNotEqualTo(signatureOf(png));
        assertThat(signatureOf(jpeg)).isNotEqualTo(signatureOf(bigger));
    }

    /**
     * 권한 문제가 "파일 없음"으로 위장하지 않는지 (2026-09-14 사고).
     *
     * <p>403 을 {@code false} 로 삼키면 "파일이 안 올라왔다"는 응답이 나간다 — 파일은
     * 버킷에 멀쩡히 있는데 그렇다. 그러면 아무도 IAM 을 의심하지 않고, 올린 사람은
     * 업로드만 반복한다.
     */
    @Test
    @DisplayName("HEAD 가 403 이면 삼키지 않고 던진다 — 권한 문제는 파일 없음이 아니다")
    void headForbiddenIsNotTreatedAsMissing() {
        assertThatThrownBy(() -> storeWith(s3Throwing(403, "AccessDenied")).exists("profiles/x.jpg"))
                .isInstanceOf(S3Exception.class);
    }

    @Test
    @DisplayName("HEAD 가 404 면 없는 것이 맞다")
    void headNotFoundIsMissing() {
        assertThat(storeWith(s3Throwing(404, "NoSuchKey")).exists("profiles/x.jpg")).isFalse();
    }

    @Test
    @DisplayName("NoSuchKey 예외도 없는 것으로 읽는다")
    void noSuchKeyIsMissing() {
        S3Client s3 = mock(S3Client.class);
        given(s3.headObject(any(Consumer.class)))
                .willThrow(NoSuchKeyException.builder().statusCode(404).build());

        assertThat(storeWith(s3).exists("profiles/x.jpg")).isFalse();
    }

    @Test
    @DisplayName("HEAD 가 성공하면 있는 것이다")
    void headSuccessIsPresent() {
        S3Client s3 = mock(S3Client.class);

        assertThat(storeWith(s3).exists("profiles/x.jpg")).isTrue();
    }

    private static S3Client s3Throwing(int status, String errorCode) {
        S3Client s3 = mock(S3Client.class);
        S3Exception failure = (S3Exception) S3Exception.builder()
                .statusCode(status)
                .awsErrorDetails(AwsErrorDetails.builder().errorCode(errorCode).build())
                .build();
        given(s3.headObject(any(Consumer.class))).willThrow(failure);
        return s3;
    }

    private static String signatureOf(String url) {
        int at = url.indexOf("X-Amz-Signature=");
        String tail = url.substring(at + "X-Amz-Signature=".length());
        int amp = tail.indexOf('&');
        return amp < 0 ? tail : tail.substring(0, amp);
    }
}
