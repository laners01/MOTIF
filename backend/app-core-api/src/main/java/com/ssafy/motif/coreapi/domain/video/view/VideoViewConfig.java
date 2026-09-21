package com.ssafy.motif.coreapi.domain.video.view;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 조회수 집계 설정 바인딩 (ADR-0015).
 *
 * <p>설정을 쓰는 패키지에서 직접 켠다({@code MediaConfig}·{@code RankingConfig} 와 같은
 * 규칙) — 애플리케이션 클래스에 모으면 무엇 때문에 켜졌는지가 파일 위치로 드러나지 않는다.
 */
@Configuration
@EnableConfigurationProperties(VideoViewProperties.class)
public class VideoViewConfig {
}
