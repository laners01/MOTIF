package com.ssafy.motif.coreapi.admin.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 어드민 S3 구성 — 서명 발급({@link S3Presigner})과 존재 확인({@link S3Client}) 둘뿐이다.
 *
 * <p>core-api 는 <b>바이트를 다루지 않는다</b>(원칙 ① — 브라우저 → S3 직행). 오브젝트
 * 입출력·인코딩은 전부 app-media 다. {@code S3Client} 를 둔 이유는 하나 —
 * {@code HEAD} 다. 명세 §1.4 가 "서버는 키만 받고(Claim-Check) 존재를 HEAD 로 확인한다"
 * 고 정했고 §3.7.2 검증 4항이 등록 트랜잭션 진입 전 모든 키를 HEAD 하게 한다.
 * HEAD 는 메타데이터만 읽으므로 원칙 ① 과 어긋나지 않는다.
 */
@Configuration
@EnableConfigurationProperties(AdminMediaProperties.class)
public class AdminMediaConfig {

    /** 자격증명은 기본 체인(환경변수 → 프로파일 → IRSA)을 쓴다 — 코드에 키를 두지 않는다. */
    @Bean
    public S3Presigner s3Presigner(@Value("${motif.media.s3.region}") String region) {
        return S3Presigner.builder().region(Region.of(region)).build();
    }

    /** HEAD 전용 — {@code getObject}·{@code putObject} 를 부르는 코드는 core-api 에 없다. */
    @Bean
    public S3Client s3Client(@Value("${motif.media.s3.region}") String region) {
        return S3Client.builder().region(Region.of(region)).build();
    }
}
