package com.ssafy.motif.media.global.config;

import com.ssafy.motif.media.consumer.MediaProperties;
import com.ssafy.motif.media.encode.HlsPackager;
import com.ssafy.motif.media.encode.Rendition;
import com.ssafy.motif.media.storage.MediaStorageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties({MediaProperties.class, MediaStorageProperties.class})
public class MediaConfig {

    /** 사다리는 {@link Rendition#LADDER} 고정 — 실행 중 바뀌면 화질 간 정렬이 깨진다. */
    @Bean
    public HlsPackager hlsPackager(MediaProperties props) {
        return new HlsPackager(props.ffmpeg(), props.ffprobe(), Rendition.LADDER);
    }

    /** 자격증명은 기본 체인(환경변수 → 프로파일 → IRSA)을 쓴다 — 코드에 키를 두지 않는다. */
    @Bean
    public S3Client s3Client(MediaStorageProperties props) {
        return S3Client.builder().region(Region.of(props.region())).build();
    }
}
