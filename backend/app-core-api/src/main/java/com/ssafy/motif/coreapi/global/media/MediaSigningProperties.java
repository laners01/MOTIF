package com.ssafy.motif.coreapi.global.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * CloudFront signed URL 설정 (API 명세 §1.7 보호 등급).
 *
 * @param keyPairId  CloudFront 공개키 ID. <b>비밀이 아니다</b> — 서명된 URL 의 쿼리에
 *                   그대로 실려 나간다. 그래서 기본값을 둔다
 * @param privateKey 공개키와 짝인 RSA 개인키(PEM). <b>비밀이고 fallback 이 없다</b> —
 *                   이 값이 새면 전 보호 콘텐츠의 서명을 위조할 수 있다.
 *                   로컬·CI 를 위해 빈 값으로 기동은 되지만, 그 상태에서 서명을
 *                   요청하면 {@link CloudFrontUrlSigner} 가 던진다(fail-closed)
 * @param ttl        서명 만료. 짧게 잡아도 되는 이유는 만료된 조각 URL 을 만나면
 *                   앱이 재생목록을 다시 받으면 되기 때문이다 — core-api 가 새 서명으로
 *                   내려준다. §1.7 기본안은 10분이다
 */
@ConfigurationProperties(prefix = "motif.media.signing")
public record MediaSigningProperties(String keyPairId, String privateKey, Duration ttl) {
}
