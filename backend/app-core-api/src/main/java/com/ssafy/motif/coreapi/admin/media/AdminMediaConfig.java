package com.ssafy.motif.coreapi.admin.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * presign 전용 S3 구성 — <b>{@code S3Client} 는 만들지 않는다</b>.
 *
 * <p>core-api 는 바이트를 다루지 않는다(원칙 ① — 브라우저 → S3 직행). 여기에 필요한
 * 것은 서명 하나뿐이라 {@link S3Presigner} 만 둔다. 실제 오브젝트 입출력은 app-media 다.
 */
@Configuration
@EnableConfigurationProperties(AdminMediaProperties.class)
public class AdminMediaConfig {

    /** 자격증명은 기본 체인(환경변수 → 프로파일 → IRSA)을 쓴다 — 코드에 키를 두지 않는다. */
    @Bean
    public S3Presigner s3Presigner(@Value("${motif.media.s3.region}") String region) {
        return S3Presigner.builder().region(Region.of(region)).build();
    }
}
