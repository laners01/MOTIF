package com.ssafy.motif.event.topology;

/**
 * RabbitMQ exchange 이름 — {@code infra/k8s/20-rabbitmq/configmap-definitions.yaml}의 거울.
 *
 * <p>토폴로지의 원본은 definitions.json이며 앱은 선언하지 않는다(운영 계정의
 * configure 권한은 {@code "^$"}). 이 상수들은 발행·구독 코드가 문자열 오타 없이
 * 참조하기 위한 사본이다 — definitions.json이 바뀌면 이 파일도 함께 바꾼다.
 */
public final class Exchanges {

    /** 잡·도메인 이벤트 공용 topic exchange. */
    public static final String MOTIF_TOPIC = "motif.topic";

    /** 모든 큐의 dead-letter exchange. 소진된 메시지는 dlq.*로 격리된다. */
    public static final String MOTIF_DLX = "motif.dlx";

    private Exchanges() {
    }
}
