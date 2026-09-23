package com.ssafy.motif.coreapi.global.media;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import software.amazon.awssdk.services.cloudfront.CloudFrontUtilities;
import software.amazon.awssdk.services.cloudfront.model.CannedSignerRequest;
import software.amazon.awssdk.services.cloudfront.model.CustomSignerRequest;

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
    private final Duration playlistTtl;
    private final Duration videoFileTtl;
    private final Duration imageTtl;

    /** 프리픽스 서명의 Resource 를 만들 때 쓴다 — 서명 대상이 CDN 절대 주소여야 한다. */
    @Value("${motif.media.public-base-url:}")
    private String cdnBaseUrl;

    public CloudFrontUrlSigner(MediaSigningProperties properties) {
        this.keyPairId = properties.keyPairId();
        this.playlistTtl = properties.playlistTtl();
        this.videoFileTtl = properties.videoFileTtl();
        this.imageTtl = properties.imageTtl();
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
     * 영상 파일 하나를 서명한다 — §7.6 편집본처럼 HLS 가 아닌 단일 MP4.
     *
     * <p><b>만료가 셋 중 가장 짧다.</b> 만료되면 앱이 §7.6 을 다시 불러 새 URL 을 받는
     * 복구 경로가 실제로 있다 — 재생목록({@link #signPrefix})·이미지({@link #signImage})
     * 와 값을 나눠 둔 이유가 그것이다.
     *
     * @param resourceUrl {@code https://<배포 도메인>/<키>} — 쿼리 없는 절대 주소
     * @throws IllegalStateException 개인키가 주입되지 않았을 때. <b>무서명 URL 을 대신
     *                               돌려주지 않는다</b> — 그러면 보호 콘텐츠가 열린다
     */
    public String sign(String resourceUrl) {
        return sign(resourceUrl, videoFileTtl);
    }

    /**
     * 이미지 한 장을 서명한다 — 썸네일·프로필.
     *
     * <p><b>복구 경로가 아예 없다.</b> {@code <img>} 가 403 을 받으면 깨진 채로 남고
     * 다시 받을 계기가 없다. 길게 줘도 새는 범위는 서명된 그 한 장뿐이다.
     */
    public String signImage(String resourceUrl) {
        return sign(resourceUrl, imageTtl);
    }

    /**
     * 프리픽스 하나를 통째로 서명한다 — <b>custom policy</b> (ADR-0012).
     *
     * <p>{@link #sign}·{@link #signImage} 와 <b>다루는 단위가 다르다</b>. 저쪽은 URL
     * 하나(canned policy)라 조각마다 서명이 달라지지만, 여기는 {@code {prefix}/*} 를
     * Resource 로 삼아 <b>그 아래 전부에 통하는 파라미터 한 벌</b>을 낸다. HLS 는 재생목록
     * 안의 상대 경로로 조각을 더 요청하는데, 클라이언트(OkHttp 인터셉터)가 그 요청마다
     * 같은 파라미터를 붙일 수 있어야 재생이 성립한다.
     *
     * <p>반환값은 <b>쿼리스트링만</b>이다({@code Policy=…&Signature=…&Key-Pair-Id=…}).
     * 어느 주소에 붙일지는 호출부가 정한다 — 마스터 재생목록에 붙여 내보내고, 조각에는
     * 클라이언트가 같은 값을 붙인다.
     *
     * <p><b>범위를 영상 하나로 좁힌다.</b> 상위 경로(예: {@code private/motif/*})로 넓히면
     * 파라미터가 새는 순간 그 범위가 통째로 열린다. 프리픽스 하나는 영상 하나다.
     *
     * <p>서명 파라미터는 CloudFront 예약값이라 <b>엣지에서 소비되고 캐시 키에 들어가지
     * 않는다</b> — 사용자마다 값이 달라도 캐시 적중률은 유지된다.
     *
     * <p><b>만료가 {@code playlistTtl} 이다</b>(ADR-0017). 자동재생에서는 403 을 만난
     * 뒤 재발급받는 사후 복구가 성립하지 않아, 목록이 살아 있는 동안 서명도 살아 있어야
     * 한다 — 근거는 {@link MediaSigningProperties} javadoc 에 있다.
     *
     * @param prefix S3 키의 산출물 프리픽스 — {@code private/motif/{uuid}} 꼴
     * @throws IllegalStateException 개인키가 없을 때. {@link #sign} 과 같은 이유로
     *                               무서명을 대신 돌려주지 않는다
     */
    public String signPrefix(String prefix) {
        return signPrefixWithExpiry(prefix).query();
    }

    /**
     * {@link #signPrefix} 와 같되 <b>서명에 박힌 만료 시각을 함께</b> 돌려준다 —
     * 클라이언트가 갱신 시점을 잡는 데 쓴다.
     *
     * <p><b>만료를 따로 계산하지 않는다.</b> 예전에는 서명과 만료가 각자
     * {@code Instant.now()} 를 불러, 돌려준 시각이 실제 만료보다 뒤였다 — 그 값에 맞춰
     * 미리 갱신하는 클라이언트가 한 발 늦게 갱신한다.
     */
    public PrefixSignature signPrefixWithExpiry(String prefix) {
        if (privateKey == null) {
            throw new IllegalStateException(
                    "CloudFront 개인키가 없어 보호 등급 URL 을 낼 수 없다 — "
                            + "core-api-secret 의 CLOUDFRONT_PRIVATE_KEY 를 확인하라");
        }
        Instant expiresAt = Instant.now().plus(playlistTtl);
        // Resource 에 와일드카드를 넣고 서명한 뒤 쿼리만 떼어 쓴다. 정책 JSON 조립과
        // 서명은 SDK 에 맡긴다 — 직접 만들면 base64 변형 알파벳(+=/ → -_~)에서 틀리기 쉽다
        var signed = utilities.getSignedUrlWithCustomPolicy(CustomSignerRequest.builder()
                .resourceUrl(prefixResourceUrl(prefix))
                .privateKey(privateKey)
                .keyPairId(keyPairId)
                .expirationDate(expiresAt)
                .build());
        return new PrefixSignature(queryOf(signed.url()), expiresAt);
    }

    /**
     * 프리픽스 서명 한 벌.
     *
     * @param query     조각 요청에 그대로 붙일 쿼리스트링
     * @param expiresAt 이 파라미터에 실제로 박힌 만료 시각
     */
    public record PrefixSignature(String query, Instant expiresAt) {
    }

    private String prefixResourceUrl(String prefix) {
        String base = cdnBaseUrl.endsWith("/")
                ? cdnBaseUrl.substring(0, cdnBaseUrl.length() - 1)
                : cdnBaseUrl;
        return base + "/" + prefix + "/*";
    }

    /**
     * 서명 URL 에서 쿼리만 떼어낸다.
     *
     * <p>{@code *} 가 들어간 Resource 는 그대로 주소가 되지 못하므로 URL 자체는 버린다 —
     * 필요한 것은 SDK 가 만든 파라미터 세 개뿐이다.
     */
    private static String queryOf(String signedUrl) {
        int mark = signedUrl.indexOf('?');
        if (mark < 0) {
            throw new IllegalStateException("서명 파라미터가 없다: " + signedUrl);
        }
        return signedUrl.substring(mark + 1);
    }

    private String sign(String resourceUrl, Duration expiresIn) {
        if (privateKey == null) {
            throw new IllegalStateException(
                    "CloudFront 개인키가 없어 보호 등급 URL 을 낼 수 없다 — "
                            + "core-api-secret 의 CLOUDFRONT_PRIVATE_KEY 를 확인하라");
        }
        var signed = utilities.getSignedUrlWithCannedPolicy(CannedSignerRequest.builder()
                .resourceUrl(resourceUrl)
                .privateKey(privateKey)
                .keyPairId(keyPairId)
                .expirationDate(Instant.now().plus(expiresIn))
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
