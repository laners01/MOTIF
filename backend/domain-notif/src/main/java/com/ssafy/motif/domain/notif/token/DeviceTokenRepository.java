package com.ssafy.motif.domain.notif.token;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Integer> {

    /** §14.1 재할당·§14.2 해제의 진입점. {@code fcm_token} 이 UNIQUE 라 한 건이다. */
    Optional<DeviceToken> findByFcmToken(String fcmToken);

    /** 회원 정지(583) — 그 사용자의 기기를 전부 내린다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DeviceToken t set t.active = false where t.userSeq = :userSeq and t.active = true")
    int deactivateAllOf(@Param("userSeq") Integer userSeq);

    /**
     * FCM 이 죽었다고 답한 토큰을 내린다.
     *
     * <p>발송 응답을 보고 부르므로 <b>사용자 요청 밖</b>이다. 실패해도 발송 자체는 이미
     * 끝났으니 예외를 위로 던지지 않는다 — 다음 발송에서 다시 걸린다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DeviceToken t set t.active = false where t.fcmToken in :fcmTokens")
    int deactivateByTokens(@Param("fcmTokens") Collection<String> fcmTokens);
}
