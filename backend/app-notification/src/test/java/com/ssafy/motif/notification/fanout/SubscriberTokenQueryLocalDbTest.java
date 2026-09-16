package com.ssafy.motif.notification.fanout;

import com.ssafy.motif.domain.notif.token.DeviceToken;
import com.ssafy.motif.domain.notif.token.DeviceTokenRepository;
import com.ssafy.motif.domain.notif.token.Platform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 팬아웃 교차 스키마 조회 (S15P21D104-312).
 *
 * <p><b>이 테스트가 잡는 것은 권한과 스키마 수식자다.</b> 둘 중 하나라도 틀리면
 * 쿼리가 던지는데, 증상이 헷갈린다 — {@code motif_core.} 를 빼먹으면 권한 오류가
 * 아니라 {@code relation does not exist} 가 나서 GRANT 를 의심하게 된다.
 * 여기서 한 번이라도 돌면 {@code GRANT USAGE ON SCHEMA motif_core} +
 * {@code GRANT SELECT ON subscriptions} 가 실제로 걸려 있다는 뜻이다.
 *
 * <p><b>양성 경로(구독자가 있을 때 토큰이 나온다)는 덮지 못한다.</b> 이 서비스는
 * {@code subscriptions} 에 SELECT 권한만 있어 구독 행을 심을 수 없고, 심으려면
 * {@code users}·{@code artists} 까지 만들어야 하는데 그건 {@code motif_core} 소유
 * 모듈의 몫이다. 대신 <b>구독이 없으면 활성 토큰이 있어도 안 나온다</b>는 쪽으로
 * 조인이 실제로 거르는지를 확인한다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class SubscriberTokenQueryLocalDbTest {

    /** 구독자가 있을 리 없는 값. 팬아웃 대상이 0명인 경우다. */
    private static final int UNKNOWN_ARTIST = 999_999;

    @Autowired
    private SubscriberTokenQuery subscriberTokenQuery;
    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    @Test
    @DisplayName("교차 스키마 조회가 권한·수식자 없이 터지지 않는다")
    void crossSchemaQueryRuns() {
        assertThatCode(() -> subscriberTokenQuery.activeTokensOfSubscribers(UNKNOWN_ARTIST))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("구독자가 없으면 활성 토큰이 있어도 대상이 아니다")
    void noSubscriberMeansNoToken() {
        // 조인을 빼먹고 device_tokens 만 훑으면 이 토큰이 딸려 나온다
        deviceTokenRepository.save(DeviceToken.register(
                999_001, "tok-" + UUID.randomUUID(), Platform.ANDROID));
        deviceTokenRepository.flush();

        assertThat(subscriberTokenQuery.activeTokensOfSubscribers(UNKNOWN_ARTIST)).isEmpty();
    }
}
