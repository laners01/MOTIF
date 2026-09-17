package com.ssafy.motif.coreapi.global.media;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

/**
 * 보호 등급 URL 을 검증하는 테스트의 공통 설정 (ADR-0012).
 *
 * <p><b>왜 필요한가.</b> 로컬·CI 에는 {@code motif.media.public-base-url} 과
 * {@code motif.media.signing.private-key} 가 둘 다 없다. 그 상태에서는 모든 보호 URL 이
 * 조용히 {@code null} 로 나오므로, 서명이 통째로 빠져도 테스트가 통과한다.
 *
 * <p><b>고정 PEM 을 소스에 박지 않는다.</b> 진짜 키처럼 보이는 문자열이 레포에 남으면
 * 언젠가 운영 키와 헷갈린다. 값 자체는 의미가 없고 서명 경로를 태우는 것이 목적이다.
 */
public final class MediaSigningTestSupport {

    /** 테스트에서 쓰는 CDN 배포 주소 — 단언의 접두사이기도 하다. */
    public static final String CDN = "https://cdn.test";

    private MediaSigningTestSupport() {
    }

    /** {@code @DynamicPropertySource} 에서 부른다. */
    public static void register(DynamicPropertyRegistry registry) {
        registry.add("motif.media.public-base-url", () -> CDN);
        registry.add("motif.media.signing.private-key", MediaSigningTestSupport::pkcs8Pem);
    }

    public static String pkcs8Pem() {
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
