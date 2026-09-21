package com.ssafy.motif.coreapi.domain.artist.ranking;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 아티스트 랭킹 설정 바인딩 (API 명세 §5.1).
 *
 * <p>설정을 쓰는 패키지에서 직접 켠다({@code MediaConfig}·{@code ViewConfig} 와 같은
 * 규칙) — 애플리케이션 클래스에 모으면 무엇 때문에 켜졌는지가 파일 위치로 드러나지 않는다.
 *
 * <p><b>스케줄링과 무관하다.</b> 예전 {@code RankingConfig} 는 바인딩·주기 등록·스케줄러
 * 소유를 겸했는데, 랭킹이 조회수 집계 배치의 단계가 되면서(ADR-0016) 주기가 사라지고
 * 스케줄러는 {@code global.config.SchedulingConfig} 로 옮겼다. 여기 남은 것은 바인딩뿐이다.
 */
@Configuration
@EnableConfigurationProperties(RankingProperties.class)
public class RankingPropertiesConfig {
}
