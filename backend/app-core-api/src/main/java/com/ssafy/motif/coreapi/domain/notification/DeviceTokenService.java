package com.ssafy.motif.coreapi.domain.notification;

import com.ssafy.motif.coreapi.domain.notification.dto.DeviceTokenRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FCM 토큰 등록·해제 (API 명세 §14.1·§14.2) — S15P21D104-615.
 *
 * <p><b>교차 스키마 쓰기다.</b> {@code device_tokens} 는 {@code motif_notif} 소유이고
 * init 스크립트가 {@code REVOKE ALL ON SCHEMA motif_notif FROM core_app} 을 걸어뒀다.
 * 그 위에 테이블 하나만 여는 예외를 뒀다({@code app-notification} changeSet 0002) —
 * {@code motif_core.subscriptions} 를 {@code notif_app} 에 읽기로 연 것과 대칭이다.
 *
 * <p><b>{@code motif_notif.} 를 반드시 붙인다.</b> {@code core_app} 의 search_path 는
 * {@code motif_core, public} 이라 그 스키마가 없다. 빼먹으면 권한 오류가 아니라
 * {@code relation does not exist} 가 나서 GRANT 를 의심하게 된다.
 *
 * <p><b>JPA 엔티티를 쓰지 않는다.</b> {@code domain-notif} 에 의존하면 모듈 경계가
 * 무너지고, 여기서 엔티티를 또 정의하면 같은 테이블이 두 벌이 된다. 무엇보다
 * 재할당이 {@code ON CONFLICT} 한 문장으로 원자적으로 끝난다 — 조회 후 분기로 짜면
 * 두 기기가 동시에 같은 토큰을 보낼 때 UNIQUE 위반(500)이 난다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    /**
     * §14.1 — 등록/재할당.
     *
     * <p>{@code fcm_token} 이 UNIQUE 라 충돌 대상이 유일하다. 충돌 시 <b>소유자를 옮기고
     * 다시 켠다</b> — 기기를 남에게 넘겼거나 같은 기기에 다른 계정이 로그인한 경우다.
     * 새 행을 만들면 옛 계정의 알림이 그 기기로 계속 간다.
     */
    private static final String UPSERT = """
            INSERT INTO motif_notif.device_tokens
                        (user_seq, fcm_token, platform, is_active, last_used_dttm, reg_dttm)
                 VALUES (?, ?, ?, TRUE, now(), now())
            ON CONFLICT (fcm_token)
            DO UPDATE SET user_seq       = EXCLUDED.user_seq,
                          platform       = EXCLUDED.platform,
                          is_active      = TRUE,
                          last_used_dttm = now(),
                          mod_dttm       = now()""";

    /**
     * §14.2 — 해제.
     *
     * <p>{@code user_seq} 로도 좁힌다. 토큰만으로 지우게 두면 남의 토큰 문자열을 아는
     * 사람이 그 기기의 알림을 끌 수 있다. 0행이어도 정상이다(멱등 — 미등록도 200).
     */
    private static final String DEACTIVATE = """
            UPDATE motif_notif.device_tokens
               SET is_active = FALSE, mod_dttm = now()
             WHERE fcm_token = ? AND user_seq = ?""";

    private final JdbcTemplate jdbc;

    @Transactional
    public void register(Integer userSeq, DeviceTokenRequest request) {
        jdbc.update(UPSERT, userSeq, request.fcmToken(), request.platformOrDefault());
    }

    @Transactional
    public void deactivate(Integer userSeq, DeviceTokenRequest request) {
        int updated = jdbc.update(DEACTIVATE, request.fcmToken(), userSeq);
        if (updated == 0) {
            // 명세가 200 을 요구한다(멱등). 앱이 로그아웃을 재시도하거나 토큰이 이미
            // 갱신된 흔한 경우라 실패로 볼 것이 아니다 — 대신 기록은 남긴다
            log.debug("해제 대상 없음: userSeq={}", userSeq);
        }
    }
}
