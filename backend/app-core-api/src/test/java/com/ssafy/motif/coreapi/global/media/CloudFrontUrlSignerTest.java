package com.ssafy.motif.coreapi.global.media;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 보호 등급 URL 서명 (API 명세 §1.7).
 *
 * <p>키는 테스트가 그때그때 만든다 — 저장소에 개인키를 두지 않기 위해서다. CloudFront 가
 * 실제로 받아주는지는 여기서 검증할 수 없고(그건 배포 설정과의 짝), 여기서 지키는 것은
 * <b>키가 없을 때 무서명 URL 이 새어 나가지 않는다</b> 는 것이다.
 */
class CloudFrontUrlSignerTest {

    private static final String KEY_PAIR_ID = "K27QE85H6DLYDD";
    private static final String CDN = "https://d21so2cpi8si9r.cloudfront.net";
    private static final String PREFIX = "private/motif/3fa85f64";
    private static final String URL = CDN + "/" + PREFIX + "/master.m3u8";

    /** `openssl genrsa`(3.x) 가 내는 PKCS#8 PEM 과 같은 모양. */
    private static String pkcs8Pem() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        byte[] der = generator.generateKeyPair().getPrivate().getEncoded();
        return "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der)
                + "\n-----END PRIVATE KEY-----\n";
    }

    private static CloudFrontUrlSigner signer(String pem) {
        return new CloudFrontUrlSigner(new MediaSigningProperties(
                KEY_PAIR_ID, pem, Duration.ofHours(2), Duration.ofMinutes(10),
                Duration.ofHours(6)));
    }

    @Test
    @DisplayName("서명 URL 에 만료·서명·키ID 가 붙는다 — 이 셋이 CloudFront 예약 파라미터다")
    void 서명_파라미터() throws Exception {
        String signed = signer(pkcs8Pem()).sign(URL);

        assertThat(signed).startsWith(URL + "?");
        assertThat(signed).contains("Expires=", "Signature=", "Key-Pair-Id=" + KEY_PAIR_ID);
    }

    @Test
    @DisplayName("이미지는 단일 영상 파일보다 늦게 만료된다 — <img> 에는 다시 받는 복구 경로가 없다")
    void 이미지_만료가_더_길다() throws Exception {
        CloudFrontUrlSigner signer = signer(pkcs8Pem());

        long videoFileExpires = expiresOf(signer.sign(URL));
        long imageExpires = expiresOf(signer.signImage(URL));

        // 10분 대 6시간. 정확한 초를 고정하면 실행 시각에 흔들리므로 "한참 더 길다"만 본다
        assertThat(imageExpires - videoFileExpires).isGreaterThan(Duration.ofHours(5).toSeconds());
    }

    @Test
    @DisplayName("재생목록 서명은 단일 영상 파일보다 오래 산다 — 자동재생은 사후 재발급으로 복구되지 않는다")
    void 재생목록_만료가_더_길다() throws Exception {
        CloudFrontUrlSigner signer = signer(pkcs8Pem());
        setCdnBaseUrl(signer);

        long videoFileExpires = expiresOf(signer.sign(URL));
        long playlistExpires = signer.signPrefixWithExpiry(PREFIX).expiresAt().getEpochSecond();

        // 10분 대 2시간. 값을 고정하지 않는 것은 위와 같은 이유다 — 지키는 것은 순서다
        assertThat(playlistExpires - videoFileExpires).isGreaterThan(Duration.ofHours(1).toSeconds());
    }

    @Test
    @DisplayName("돌려주는 만료는 서명에 박힌 값이다 — 따로 계산하면 클라가 한 발 늦게 갱신한다")
    void 프리픽스_만료는_정책과_같다() throws Exception {
        CloudFrontUrlSigner signer = signer(pkcs8Pem());
        setCdnBaseUrl(signer);

        var signed = signer.signPrefixWithExpiry(PREFIX);

        assertThat(decodedPolicyEpoch(signed.query())).isEqualTo(signed.expiresAt().getEpochSecond());
    }

    /** {@code @Value} 주입이 없는 단위 테스트라 프리픽스 서명의 Resource 뿌리를 직접 넣는다. */
    private static void setCdnBaseUrl(CloudFrontUrlSigner signer) {
        ReflectionTestUtils.setField(signer, "cdnBaseUrl", CDN);
    }

    /**
     * custom policy 의 {@code AWS:EpochTime} 을 꺼낸다.
     *
     * <p>{@code Policy} 는 base64 를 CloudFront 규칙으로 바꾼 값이다 — {@code + = /} 가
     * {@code - _ ~} 로 치환돼 있어 되돌려야 디코딩된다.
     */
    private static long decodedPolicyEpoch(String query) {
        String policy = java.util.Arrays.stream(query.split("&"))
                .filter(param -> param.startsWith("Policy="))
                .map(param -> param.substring("Policy=".length()))
                .findFirst()
                .orElseThrow();
        String json = new String(Base64.getDecoder().decode(
                policy.replace('-', '+').replace('_', '=').replace('~', '/')),
                java.nio.charset.StandardCharsets.UTF_8);
        var matcher = java.util.regex.Pattern.compile("\"AWS:EpochTime\"\\s*:\\s*(\\d+)").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("정책에 만료가 없다: " + json);
        }
        return Long.parseLong(matcher.group(1));
    }

    /** {@code …?Expires=1789000000&Signature=…} 에서 만료 epoch 초만 꺼낸다. */
    private static long expiresOf(String signedUrl) {
        String query = signedUrl.substring(signedUrl.indexOf('?') + 1);
        for (String param : query.split("&")) {
            if (param.startsWith("Expires=")) {
                return Long.parseLong(param.substring("Expires=".length()));
            }
        }
        throw new IllegalStateException("Expires 가 없다: " + signedUrl);
    }

    @Test
    @DisplayName("키가 없으면 던진다 — 무서명 URL 로 대체하면 보호 콘텐츠가 열린다")
    void 키가_없으면_실패한다() {
        CloudFrontUrlSigner signer = signer("  ");

        assertThat(signer.isEnabled()).isFalse();
        assertThatThrownBy(() -> signer.sign(URL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLOUDFRONT_PRIVATE_KEY");
    }

    @Test
    @DisplayName("PKCS#1 은 변환 방법을 알려주고 막는다 — 그냥 두면 원인 모를 InvalidKeySpec 이 난다")
    void PKCS1_은_거부한다() {
        assertThatThrownBy(() -> signer("-----BEGIN RSA PRIVATE KEY-----\nMIIE\n-----END RSA PRIVATE KEY-----"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pkcs8 -topk8");
    }

    @Test
    @DisplayName("줄바꿈이 눌린 PEM 도 읽는다 — Secret 주입 과정에서 한 줄이 될 수 있다")
    void 한_줄_PEM() throws Exception {
        String oneLine = pkcs8Pem().lines().collect(java.util.stream.Collectors.joining());

        assertThat(signer(oneLine).sign(URL)).contains("Signature=");
    }

    @Test
    @DisplayName("깨진 PEM 은 기동에서 터뜨린다 — 잘못된 키로 뜨면 모든 보호 URL 이 403 이다")
    void 깨진_PEM_은_기동을_막는다() {
        assertThatThrownBy(() -> signer("-----BEGIN PRIVATE KEY-----쓰레기-----END PRIVATE KEY-----"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PKCS#8");
    }
}
