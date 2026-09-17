package com.ssafy.motif.coreapi.global.media;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 프리픽스 서명 (ADR-0012) — DB 없이 서명 자체만 본다.
 *
 * <p><b>정책 안의 Resource 를 반드시 확인한다.</b> 와일드카드가 인코딩되거나 빠지면
 * 서명은 정상적으로 만들어지는데 조각 요청만 403 이 된다 — 예외도 로그도 없이 "재생만
 * 안 되는" 종류라, 발급 시점에 잡지 않으면 실기기에서야 드러난다.
 */
class SignedPrefixPlaybackTest {

    private static final String CDN = "https://cdn.test";
    private static final String KEY_PAIR_ID = "K27QE85H6DLYDD";
    private static final String PREFIX = "private/motif/9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f";

    @Test
    @DisplayName("프리픽스 서명은 Policy·Signature·Key-Pair-Id 를 낸다")
    void signPrefixYieldsThreeParameters() {
        String query = signer().signPrefix(PREFIX);

        assertThat(query)
                .contains("Policy=")
                .contains("Signature=")
                .contains("Key-Pair-Id=" + KEY_PAIR_ID);
        assertThat(query).doesNotStartWith("?");
    }

    @Test
    @DisplayName("정책의 Resource 가 프리픽스 와일드카드다 — 조각까지 이 한 벌로 통한다")
    void policyCoversTheWholePrefix() {
        String query = signer().signPrefix(PREFIX);

        assertThat(decodePolicy(query))
                .contains(CDN + "/" + PREFIX + "/*");
    }

    @Test
    @DisplayName("정책이 영상 하나를 넘지 않는다 — 상위 경로가 열리면 안 된다")
    void policyDoesNotWidenBeyondOneVideo() {
        assertThat(decodePolicy(signer().signPrefix(PREFIX)))
                .doesNotContain(CDN + "/private/motif/*")
                .doesNotContain(CDN + "/private/*");
    }

    @Test
    @DisplayName("개인키가 없으면 던진다 — 무서명을 대신 돌려주지 않는다")
    void withoutKeyItThrows() {
        CloudFrontUrlSigner signer = new CloudFrontUrlSigner(
                new MediaSigningProperties(KEY_PAIR_ID, "", Duration.ofMinutes(10),
                        Duration.ofHours(6)));
        ReflectionTestUtils.setField(signer, "cdnBaseUrl", CDN);

        assertThatThrownBy(() -> signer.signPrefix(PREFIX))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLOUDFRONT_PRIVATE_KEY");
    }

    /**
     * {@code Policy} 는 base64 를 CloudFront 규칙으로 바꾼 값이다 — {@code + = /} 가
     * {@code - _ ~} 로 치환돼 있어 되돌려야 디코딩된다.
     */
    private static String decodePolicy(String query) {
        String policy = java.util.Arrays.stream(query.split("&"))
                .filter(param -> param.startsWith("Policy="))
                .map(param -> param.substring("Policy=".length()))
                .findFirst()
                .orElseThrow();
        String base64 = policy.replace('-', '+').replace('_', '=').replace('~', '/');
        return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
    }

    private static CloudFrontUrlSigner signer() {
        CloudFrontUrlSigner signer = new CloudFrontUrlSigner(
                new MediaSigningProperties(KEY_PAIR_ID, generatePkcs8Pem(),
                        Duration.ofMinutes(10), Duration.ofHours(6)));
        ReflectionTestUtils.setField(signer, "cdnBaseUrl", CDN);
        return signer;
    }

    /** 서명 경로를 태우는 것이 목적이라 값 자체는 의미가 없다 — 고정 PEM 을 박지 않는다. */
    private static String generatePkcs8Pem() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return "-----BEGIN PRIVATE KEY-----\n"
                    + Base64.getMimeEncoder().encodeToString(pair.getPrivate().getEncoded())
                    + "\n-----END PRIVATE KEY-----";
        } catch (Exception e) {
            throw new IllegalStateException("테스트 키 생성 실패", e);
        }
    }
}
