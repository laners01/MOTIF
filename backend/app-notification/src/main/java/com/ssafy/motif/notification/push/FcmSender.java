package com.ssafy.motif.notification.push;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import com.ssafy.motif.domain.notif.token.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * FCM 멀티캐스트 발송 (§14.3 · 어드민 명세 §4 팬아웃 노트).
 *
 * <p><b>한 번에 500개가 상한이다.</b> 넘기면 일부만 가는 것이 아니라 요청 자체가
 * 거부되므로 호출부가 신경 쓰지 않도록 여기서 자른다.
 *
 * <p><b>죽은 토큰은 그 자리에서 내린다.</b> {@code UNREGISTERED}(앱 삭제·데이터 삭제)와
 * {@code INVALID_ARGUMENT}(형식이 깨진 토큰)는 재시도해도 영원히 실패한다 — 안 내리면
 * 구독자가 늘수록 실패율만 쌓이고 진짜 장애와 구분이 안 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FcmSender {

    /** FCM 멀티캐스트 상한. 초과하면 요청 전체가 거부된다. */
    private static final int BATCH_SIZE = 500;

    private final FirebaseMessaging firebaseMessaging;
    private final DeviceTokenRepository deviceTokenRepository;

    /**
     * @return 실제로 전달에 성공한 토큰 수
     */
    @Transactional
    public int send(PushMessage message, List<String> tokens) {
        if (tokens.isEmpty()) {
            return 0;
        }
        List<String> dead = new ArrayList<>();
        int sent = 0;
        for (int from = 0; from < tokens.size(); from += BATCH_SIZE) {
            List<String> batch = tokens.subList(from, Math.min(from + BATCH_SIZE, tokens.size()));
            sent += sendBatch(message, batch, dead);
        }
        if (!dead.isEmpty()) {
            deviceTokenRepository.deactivateByTokens(dead);
            log.info("죽은 토큰 비활성: count={} type={}", dead.size(), message.type());
        }
        return sent;
    }

    private int sendBatch(PushMessage message, List<String> batch, List<String> dead) {
        MulticastMessage multicast = MulticastMessage.builder()
                .putAllData(message.data())
                .addAllTokens(batch)
                .build();

        BatchResponse response;
        try {
            // sendEachForMulticast — 건별 결과를 돌려준다. 구형 sendMulticast 는 하나만
            // 실패해도 전체를 던져 어느 토큰이 죽었는지 알 수 없다
            response = firebaseMessaging.sendEachForMulticast(multicast);
        } catch (FirebaseMessagingException e) {
            // 배치 전체 실패 = 자격증명·네트워크 문제다. 토큰 탓이 아니므로 하나도
            // 내리지 않고 던진다 — 리스너가 재시도하고 소진되면 DLQ 로 간다
            throw new IllegalStateException("FCM 발송 실패: type=" + message.type(), e);
        }

        for (int i = 0; i < response.getResponses().size(); i++) {
            SendResponse each = response.getResponses().get(i);
            if (each.isSuccessful()) {
                continue;
            }
            MessagingErrorCode code = each.getException() == null
                    ? null : each.getException().getMessagingErrorCode();
            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                dead.add(batch.get(i));
            } else {
                // 그 외(쿼터·일시 장애)는 남겨 둔다 — 다음 발송에서 다시 시도된다
                log.warn("FCM 건별 실패: code={} type={}", code, message.type());
            }
        }
        return response.getSuccessCount();
    }
}
