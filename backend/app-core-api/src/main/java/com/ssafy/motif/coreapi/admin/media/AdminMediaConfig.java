package com.ssafy.motif.coreapi.admin.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 어드민 S3 구성 — 서명 발급({@link S3Presigner})과 오브젝트 접근({@link S3Client}).
 *
 * <p>core-api 는 <b>바이트를 다루지 않는다</b>(원칙 ① — 브라우저 → S3 직행). 오브젝트
 * 입출력·인코딩은 전부 app-media 다. {@code S3Client} 가 하는 일은 둘로 한정된다 —
 * 명세 §1.4 의 {@code HEAD}(키만 받고 존재 확인, §3.7.2 검증 4항)와, 보호 등급 HLS
 * 재생목록 읽기(§1.7 — 수 KB 텍스트). 둘 다 영상 바이트를 옮기지 않아 원칙 ① 과 어긋나지 않는다.
 */
@Configuration
@EnableConfigurationProperties(AdminMediaProperties.class)
public class AdminMediaConfig {

    /** 자격증명은 기본 체인(환경변수 → 프로파일 → IRSA)을 쓴다 — 코드에 키를 두지 않는다. */
    @Bean
    public S3Presigner s3Presigner(@Value("${motif.media.s3.region}") String region) {
        return S3Presigner.builder().region(Region.of(region)).build();
    }

    /**
     * HEAD 와 재생목록 읽기 — {@code putObject} 를 부르는 코드는 core-api 에 없다.
     *
     * <p>2026-09-15 부터 읽기가 하나 붙었다 — {@code MediaPlaylistService} 가 보호 등급 재생목록을
     * {@code getObject} 로 읽는다. 재생목록은 수 KB 의 텍스트이고 조각({@code data.m4s})은
     * 여전히 CDN 이 내므로 원칙 ① 의 경계(영상 바이트가 API 서버를 통과하지 않는다)는 유지된다.
     *
     * <p><b>HEAD 와 서명 발급은 {@code global.media.MediaObjectStore} 한 곳에서만 한다.</b>
     * 같은 일을 하는 클래스를 따로 만들지 않는다 — 403 처리(버킷에 {@code s3:ListBucket}
     * 이 없으면 S3 가 없는 키에 404 대신 403 을 준다)가 거기 들어 있고, 복제하면 그 판단이
     * 갈린다. 2026-09-16 에 팬 프로필 업로드(API §4.3·§4.4)가 같은 연산을 필요로 하면서
     * {@link AdminMediaService} 에서 그리로 <b>옮겼다</b>. 어드민에 남은 것은 정책이다 —
     * 종류별 키 규약·실패 코드·부재 키 일괄 수집.
     */
    @Bean
    public S3Client s3Client(@Value("${motif.media.s3.region}") String region) {
        return S3Client.builder().region(Region.of(region)).build();
    }
}
