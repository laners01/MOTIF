package com.ssafy.motif.coreapi.admin.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 어드민 S3 구성 — <b>서명과 HEAD 뿐이다</b>.
 *
 * <p>core-api 는 바이트를 다루지 않는다(원칙 ① — 브라우저 → S3 직행). 필요한 것은
 * 업로드 URL 서명({@link S3Presigner})과 업로드 완료 확인용 HEAD({@link S3Client},
 * §1.4 검증 1항) 둘뿐이다 — {@code getObject}·{@code putObject} 는 쓰지 않으며 실제
 * 오브젝트 입출력은 app-media 소유다.
 */
@Configuration
@EnableConfigurationProperties(AdminMediaProperties.class)
public class AdminMediaConfig {

    /** 자격증명은 기본 체인(환경변수 → 프로파일 → IRSA)을 쓴다 — 코드에 키를 두지 않는다. */
    @Bean
    public S3Presigner s3Presigner(@Value("${motif.media.s3.region}") String region) {
        return S3Presigner.builder().region(Region.of(region)).build();
    }

    /** 업로드 완료 확인({@code headObject}) 전용 — {@link MediaObjectChecker} 가 쓴다. */
    @Bean
    public S3Client s3Client(@Value("${motif.media.s3.region}") String region) {
        return S3Client.builder().region(Region.of(region)).build();
    }
}
