package com.ssafy.motif.event.topology;

/**
 * 라우팅 키 — {@code infra/k8s/20-rabbitmq/configmap-definitions.yaml} 바인딩의 거울
 * + 발행용 구체 키.
 *
 * <p>{@code *_PATTERN}은 topic 바인딩 패턴(와일드카드)이고, 나머지는 발행 시
 * 그대로 쓰는 구체 키다.
 */
public final class RoutingKeys {

    // ── 잡 발행 (바인딩 키 == 발행 키) ──────────────────────────────
    public static final String JOB_COMPOSITE = "job.composite";
    public static final String JOB_MEDIA = "job.media";

    // ── evt.* 큐 바인딩 패턴 ───────────────────────────────────────
    /** evt.core, evt.notification. */
    public static final String COMPOSITE_PATTERN = "composite.*";
    /** evt.notification, evt.ranking. */
    public static final String COMMENT_PATTERN = "comment.*";
    /** evt.notification. */
    public static final String NOTICE_CREATED = "notice.created";
    /** evt.notification. */
    public static final String CHALLENGE_CREATED = "challenge.created";
    /** evt.ranking. */
    public static final String VIDEO_PATTERN = "video.*";

    // ── 발행용 구체 키 ─────────────────────────────────────────────
    /** GPU 워커의 합성 완료 발행 — COMPOSITE_PATTERN에 매칭된다. */
    public static final String COMPOSITE_DONE = "composite.done";

    // ── DLX 라우팅 (definitions.json의 x-dead-letter-routing-key) ──
    public static final String DLQ_JOB_COMPOSITE = "dlq.job.composite";
    public static final String DLQ_JOB_MEDIA = "dlq.job.media";
    public static final String DLQ_EVT_CORE = "dlq.evt.core";
    public static final String DLQ_EVT_NOTIFICATION = "dlq.evt.notification";
    public static final String DLQ_EVT_RANKING = "dlq.evt.ranking";

    private RoutingKeys() {
    }
}
