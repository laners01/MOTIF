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

    /** 루트 댓글 작성 — 게시물 소유자에게 (API §9.3). */
    public static final String COMMENT_CREATED = "comment.created";

    /**
     * 답글 작성 — 부모 댓글 작성자에게 (API §9.3).
     *
     * <p><b>명세 문구는 {@code reply.created} 지만 그 키는 쓸 수 없다.</b>
     * {@code evt.notification} 바인딩이 {@code comment.*} 하나고(infra
     * {@code 20-rabbitmq/configmap-definitions.yaml}), 토픽 {@code *} 는 한 단어만
     * 받으므로 {@code reply.created} 는 어디에도 걸리지 않는다 — 발행은 성공하고
     * 메시지만 조용히 사라진다({@code user.suspended} 가 그 상태다).
     * 바인딩을 늘리려면 브로커 재기동이 필요해서 키를 기존 패턴 안으로 넣는다.
     */
    public static final String REPLY_CREATED = "comment.replied";
    /** evt.notification. */
    public static final String NOTICE_CREATED = "notice.created";
    /** evt.notification. */
    public static final String CHALLENGE_CREATED = "challenge.created";
    /**
     * evt.notification — 회원 정지 시 기기 토큰 비활성 (어드민 명세 §3.14.3·§4).
     *
     * <p><b>거울인 definitions 쪽 바인딩이 아직 없다</b>(develop/infra 의
     * {@code configmap-definitions.yaml}). 붙기 전까지 이 키로 나간 메시지는 unroutable
     * 로 버려진다 — 정지 자체는 refresh 토큰 폐기로 이미 끝나 있어 기능은 성립한다.
     */
    public static final String USER_SUSPENDED = "user.suspended";
    /** evt.ranking. */
    public static final String VIDEO_PATTERN = "video.*";

    // ── 발행용 구체 키 ─────────────────────────────────────────────
    /** GPU 워커의 합성 완료 발행 — COMPOSITE_PATTERN에 매칭된다. */
    public static final String COMPOSITE_DONE = "composite.done";

    /**
     * 가공 완료 — app-media → core (S15P21D104-189).
     *
     * <p>{@link #COMPOSITE_PATTERN} 에 걸리므로 <b>브로커 변경이 필요 없다</b>.
     * 바인딩을 늘리려면 재기동이라 키를 기존 패턴 안으로 넣는다({@link #REPLY_CREATED}
     * 와 같은 판단).
     *
     * <p>{@code evt.notification} 에도 같이 떨어진다 — 알림 리스너가 debug 로그만 남기고
     * 흘린다(319). 거기서 {@code default → throw} 에 걸리면 DLQ 로 간다.
     */
    public static final String COMPOSITE_PACKAGED = "composite.packaged";

    /**
     * 합성 종료(DONE·FAILED) — core → 알림 (S15P21D104-318).
     *
     * <p>{@link #COMPOSITE_PATTERN} 에 걸려 {@code evt.core} 에도 떨어진다 — core 가 낸
     * 것을 core 가 다시 받는 모양이라 자기 리스너는 ack 만 한다.
     */
    public static final String COMPOSITE_FINISHED = "composite.finished";

    // ── DLX 라우팅 (definitions.json의 x-dead-letter-routing-key) ──
    public static final String DLQ_JOB_COMPOSITE = "dlq.job.composite";
    public static final String DLQ_JOB_MEDIA = "dlq.job.media";
    public static final String DLQ_EVT_CORE = "dlq.evt.core";
    public static final String DLQ_EVT_NOTIFICATION = "dlq.evt.notification";
    public static final String DLQ_EVT_RANKING = "dlq.evt.ranking";

    private RoutingKeys() {
    }
}
