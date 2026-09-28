package com.ssafy.motif.notification.fanout;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 구독자 팬아웃 — 아티스트 → 활성 기기 토큰 (DB SPEC §39, 어드민 명세 §4).
 *
 * <p><b>교차 스키마 조회다.</b> {@code subscriptions} 는 {@code motif_core} 소유이고
 * 이 서비스는 {@code notif_app} 으로 붙는다. 권한은 딱 두 줄로 열려 있다 —
 * {@code GRANT USAGE ON SCHEMA motif_core}(configmap-init) +
 * {@code GRANT SELECT ON subscriptions}(changeSet 0003-03). 그 밖의 테이블은 못 읽는다.
 *
 * <p><b>{@code motif_core.} 를 반드시 붙인다.</b> {@code notif_app} 의 search_path 는
 * {@code motif_notif, public} 이라 그 스키마가 없다. 빼먹으면 권한 오류가 아니라
 * <b>{@code relation does not exist}</b> 가 나서 GRANT 를 의심하게 된다.
 *
 * <p><b>JPA 엔티티로 만들지 않는다.</b> 읽기 전용 GRANT 인데 영속성 컨텍스트에 올리면
 * 플러시가 UPDATE 를 시도하는 순간 권한 오류가 나고, {@code ddl-auto: validate} 가 남의
 * 스키마까지 검증 대상으로 끌고 온다. {@code domain-core} 의존도 금지다(ADR-0004).
 */
@Repository
@RequiredArgsConstructor
public class SubscriberTokenQuery {

    /**
     * 두 스키마를 한 문장에서 조인한다 — 구독자 목록을 애플리케이션으로 가져온 뒤
     * 토큰을 다시 묻지 않는다. 왕복이 두 배가 되고 그 사이 목록이 통째로 메모리에 뜬다.
     */
    private static final String ACTIVE_TOKENS_OF_SUBSCRIBERS = """
            SELECT t.fcm_token
              FROM device_tokens t
             WHERE t.is_active
               AND t.user_seq IN (SELECT s.user_seq
                                    FROM motif_core.subscriptions s
                                   WHERE s.artist_seq = ?)""";

    private final JdbcTemplate jdbc;

    public List<String> activeTokensOfSubscribers(Integer artistSeq) {
        return jdbc.queryForList(ACTIVE_TOKENS_OF_SUBSCRIBERS, String.class, artistSeq);
    }
}
