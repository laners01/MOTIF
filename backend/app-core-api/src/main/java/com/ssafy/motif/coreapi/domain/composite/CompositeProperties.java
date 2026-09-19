package com.ssafy.motif.coreapi.domain.composite;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * AI 경계 설정 (작업명세 §4.2·§4.3 — S15P21D104-536).
 *
 * <p><b>둘 다 비어 있어도 앱은 뜬다.</b> {@code TossProperties}·{@code CLOUDFRONT_PRIVATE_KEY}
 * 와 같은 규칙이다 — AI 키가 없다고 기동을 막으면 합성과 무관한 기능 개발·시연이 함께
 * 막힌다. 대신 각자 <b>쓰는 시점</b>에 안전한 쪽으로 실패한다.
 *
 * <ul>
 *   <li>{@code submitUrl} 이 비면 접수를 시도하지 않고 WARN — 잡은 {@code QUEUED} 로
 *       남고 스윕이 되살린다(설정을 고치면 그때 나간다).</li>
 *   <li>{@code workerToken} 이 비면 콜백을 <b>전부 401</b> 로 막는다(fail-closed —
 *       {@code ADMIN_ALLOWED_CIDRS} 빈 값과 같은 태도). 조용히 열리는 쪽으로 기울면
 *       아무나 잡을 {@code DONE} 으로 바꿀 수 있다.</li>
 * </ul>
 *
 * <p>운영 값은 configmap {@code AI_SUBMIT_URL} · secret {@code MOTIF_WORKER_TOKEN} 이다
 * (배선은 537). {@code envFrom} 은 파드 생성 시 한 번 해석되므로 고쳤으면
 * {@code rollout restart} 까지 해야 반영된다({@code backend/CLAUDE.md}).
 *
 * @param submitUrl      AI 접수 서비스 URL. 잡 하나를 POST 하면 Cloud Run Job 을 띄우고 202 다
 * @param workerToken    콜백 인증 토큰({@code X-Motif-Worker-Token}). 접수 호출에도 같은 값을
 *                       싣는다 — 양방향이 한 비밀을 공유한다
 * @param connectTimeout 접수 연결 타임아웃 (작업명세 §4.2 — 3s)
 * @param readTimeout    접수 응답 타임아웃 (15s). 접수 서비스는 Job 을 띄우기만 하고
 *                       기다리지 않아 1~3초 안에 답한다(AI 명세 §3.2)
 * @param sweepInterval  디스패처 틱 (작업명세 §4.7 — 1분)
 * @param requeueAfter   {@code QUEUED} 를 재접수 대상으로 보기까지 (2분). 방금 13.2 가
 *                       만든 잡을 디스패처가 즉시 집어 중복 접수하지 않게 하는 간격이다
 * @param taskTimeout    AI Job 의 최대 실행 시간 — <b>AI 팀이 준 값</b>(기본 30m).
 *                       {@code PROCESSING} 정체 판정은 여기에 5분을 더한다. 짧게 잡으면
 *                       정상 잡을 {@code STUCK} 으로 닫고 뒤늦은 콜백이 다시 살려
 *                       푸시가 두 번 간다(§7 함정 9)
 */
@ConfigurationProperties(prefix = "motif.composite")
public record CompositeProperties(String submitUrl, String workerToken,
                                  Duration connectTimeout, Duration readTimeout,
                                  Duration sweepInterval, Duration requeueAfter,
                                  Duration taskTimeout) {

    /** {@code PROCESSING} 정체 기준 — 콜드스타트를 포함해야 한다(§7 함정 9). */
    public Duration processingStuckAfter() {
        return taskTimeout.plusMinutes(5);
    }

    public boolean hasSubmitUrl() {
        return submitUrl != null && !submitUrl.isBlank();
    }

    public boolean hasWorkerToken() {
        return workerToken != null && !workerToken.isBlank();
    }
}
