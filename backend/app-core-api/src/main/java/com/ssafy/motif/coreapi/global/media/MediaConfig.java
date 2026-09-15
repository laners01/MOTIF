package com.ssafy.motif.coreapi.global.media;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 미디어 URL 설정 바인딩.
 *
 * <p>{@code AdminMediaConfig} 와 같은 모양이다 — 설정 레코드를 쓰는 패키지에서
 * 직접 켠다. 전역 {@code @ConfigurationPropertiesScan} 을 쓰지 않는 것은 어느 설정이
 * 살아 있는지 파일을 열어 보면 알 수 있게 하기 위해서다.
 */
@Configuration
@EnableConfigurationProperties(MediaSigningProperties.class)
public class MediaConfig {
}
