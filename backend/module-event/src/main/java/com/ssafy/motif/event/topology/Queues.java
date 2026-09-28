package com.ssafy.motif.event.topology;

/**
 * RabbitMQ 큐 이름 — {@code infra/k8s/20-rabbitmq/configmap-definitions.yaml}의 거울.
 *
 * <p>job.composite만 우선순위 큐({@code x-max-priority=10})다 — 모티프(유료) 5,
 * 챌린지(무료) 0으로 발행한다. 모든 큐는 motif.dlx로 dead-letter 된다.
 */
public final class Queues {

    /** GPU 합성 잡. x-max-priority=10 (유료 5 / 무료 0). */
    public static final String JOB_COMPOSITE = "job.composite";

    /** 미디어 후처리 잡. */
    public static final String JOB_MEDIA = "job.media";

    /** core-api가 소비하는 도메인 이벤트 (composite.*). */
    public static final String EVT_CORE = "evt.core";

    /** 알림 서비스가 소비하는 도메인 이벤트 (composite.*, comment.*, notice.created, challenge.created). */
    public static final String EVT_NOTIFICATION = "evt.notification";

    /** 랭킹 집계가 소비하는 도메인 이벤트 (video.*, comment.*). */
    public static final String EVT_RANKING = "evt.ranking";

    // dead-letter 큐 — 재발행 판단은 잡 스윕 배치·어드민의 몫 (ADR-0005)
    public static final String DLQ_JOB_COMPOSITE = "dlq.job.composite";
    public static final String DLQ_JOB_MEDIA = "dlq.job.media";
    public static final String DLQ_EVT_CORE = "dlq.evt.core";
    public static final String DLQ_EVT_NOTIFICATION = "dlq.evt.notification";
    public static final String DLQ_EVT_RANKING = "dlq.evt.ranking";

    private Queues() {
    }
}
