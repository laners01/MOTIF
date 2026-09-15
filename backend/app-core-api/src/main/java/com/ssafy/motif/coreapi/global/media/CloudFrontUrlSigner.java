package com.ssafy.motif.coreapi.global.media;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cloudfront.CloudFrontUtilities;
import software.amazon.awssdk.services.cloudfront.model.CannedSignerRequest;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * CloudFront signed URL 발급 (API 명세 §1.7).
 *
 * <p><b>URL 하나마다 서명한다.</b> 쿠키가 아닌 이유는 CDN 도메인이 API 도메인과 달라
 * {@code Set-Cookie} 가 CDN 요청에 실리지 않고, 쿠키는 도메인당 한 벌이라 "영상마다
 * 다른 권한"을 표현할 수 없기 때문이다. URL 서명은 영상 단위로 정밀하게 끊는다.
 *
 * <p>서명 파라미터({@code Expires}·{@code Signature}·{@code Key-Pair-Id})는 CloudFront
 * 예약값이라 <b>엣지에서 검증되고 오리진으로 넘어가지 않는다</b> — 캐시 키에 들어가지
 * 않으므로 적중률이 유지된다. S3 presigned 를 쓰지 않는 이유가 이 차이다(ADR-0010 D5).
 *
 * <p><b>HLS 재생목록에는 이것만으로 부족하다.</b> {@code master.m3u8} 에 서명을 붙여도
 * 그 안의 상대 경로({@code 720p/index.m3u8} → {@code data.m4s})에는 따라붙지 않는다.
 * 재생목록을 읽어 조각 주소를 서명된 절대 URL 로 바꿔 내리는 쪽이 따로 필요하다.
 * 이 클래스는 그 아래 단계 — 주소 하나를 서명하는 일만 한다.
 *
 * <p>키가 주입되지 않으면 <b>기동은 되지만 서명 요청에서 던진다</b>. 로컬·CI 가 보호
 * 미디어를 건드리지 않고도 뜨게 하면서, 운영에서 키가 빠졌을 때 무서명 URL 이 조용히
 * 나가는 일은 막는다(fail-closed).
 */
@Slf4j
@Component
public class CloudFrontUrlSigner {

    private static final String PEM_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PEM_FOOTER = "-----END PRIVATE KEY-----";
    private static final String PEM_PKCS1_HEADER = "-----BEGIN RSA PRIVATE KEY-----";

    private final CloudFrontUtilities utilities = CloudFrontUtilities.create();
    private final PrivateKey privateKey;
    private final String keyPairId;
    private final Duration ttl;

    public CloudFrontUrlSigner(MediaSigningProperties properties) {
        this.keyPairId = properties.keyPairId();
        this.ttl = properties.ttl();
        this.privateKey = parse(properties.privateKey());
        if (this.privateKey == null) {
            log.warn("motif.media.signing.private-key 가 비어 있다 — 보호 등급 URL 발급이 전부 실패한다");
        }
    }

    /** 이 배포가 서명을 낼 수 있는가. 설정 점검용이고, 분기해서 무서명 URL 을 내면 안 된다. */
    public boolean isEnabled() {
        return privateKey != null;
    }

    /**
     * 절대 URL 하나를 서명한다.
     *
     * @param resourceUrl {@code https://<배포 도메인>/<키>} — 쿼리 없는 절대 주소
     * @throws IllegalStateException 개인키가 주입되지 않았을 때. <b>무서명 URL 을 대신
     *                               돌려주지 않는다</b> — 그러면 보호 콘텐츠가 열린다
     */
    public String sign(String resourceUrl) {
        if (privateKey == null) {
            throw new IllegalStateException(
                    "CloudFront 개인키가 없어 보호 등급 URL 을 낼 수 없다 — "
                            + "core-api-secret 의 CLOUDFRONT_PRIVATE_KEY 를 확인하라");
        }
        var signed = utilities.getSignedUrlWithCannedPolicy(CannedSignerRequest.builder()
                .resourceUrl(resourceUrl)
                .privateKey(privateKey)
                .keyPairId(keyPairId)
                .expirationDate(Instant.now().plus(ttl))
                .build());
        return signed.url();
    }

    /**
     * PEM → {@link PrivateKey}. 빈 값이면 {@code null}(기동은 계속).
     *
     * <p>공백·줄바꿈에 관대하게 처리한다 — 이 값은 k8s Secret 을 거쳐 환경변수로 오고,
     * 그 과정에서 줄바꿈이 어떻게 보존될지가 작성 방식에 달려 있다. MIME 디코더가
     * 줄바꿈을 무시하므로 한 줄로 눌린 값도 통과한다.
     */
    private static PrivateKey parse(String pem) {
        if (pem == null || pem.isBlank()) {
            return null;
        }
        String trimmed = pem.strip();
        if (trimmed.startsWith(PEM_PKCS1_HEADER)) {
            // openssl 1.x 의 `genrsa` 가 내는 형식이다. Java 의 KeyFactory 는 PKCS#8 만 읽는다.
            // 여기서 막지 않으면 InvalidKeySpecException 이 원인을 안 알려주는 채로 난다.
            throw new IllegalArgumentException(
                    "PKCS#1 개인키다. PKCS#8 로 변환하라: "
                            + "openssl pkcs8 -topk8 -nocrypt -in cf_private.pem -out cf_private_pkcs8.pem");
        }
        String base64 = trimmed
                .replace(PEM_HEADER, "")
                .replace(PEM_FOOTER, "");
        try {
            byte[] der = Base64.getMimeDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            // 기동을 깨뜨린다 — 잘못된 키로 뜨면 모든 보호 URL 이 403 이 되는데,
            // 그 증상이 "영상이 안 나온다" 라서 원인까지 가는 데 오래 걸린다
            throw new IllegalArgumentException("CloudFront 개인키를 읽지 못했다 (PEM·PKCS#8 이어야 한다)", e);
        }
    }
}
