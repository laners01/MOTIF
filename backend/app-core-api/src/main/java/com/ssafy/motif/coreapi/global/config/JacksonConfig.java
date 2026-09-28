package com.ssafy.motif.coreapi.global.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * REST 경계 Jackson 구성 — <b>시각 포맷을 명세 §1.3 으로 고정</b>한다.
 *
 * <p>명세는 모든 시각을 밀리초 3자리 UTC(`2026-09-02T06:00:00.000Z`)로 규정하지만
 * Jackson 기본 {@link Instant} 직렬화는 <b>남는 소수부만큼만</b> 출력한다 —
 * {@code Instant.now()} 는 플랫폼에 따라 마이크로/나노까지 담기므로
 * {@code ...:00.123456Z} 가 되고, 소수부가 0 이면 {@code ...:00Z} 로 아예 빠진다.
 * 자릿수가 요청마다 달라지면 안드로이드의 고정 포맷 파서가 깨지기 때문에
 * 전역 직렬화로 3자리를 강제한다.
 *
 * <p>봉투({@code ApiResponse.timestamp})만이 아니라 DTO 의 모든 시각 필드
 * ({@code regDttm}·{@code expiresDttm} 등)에 같은 규약이 걸려야 하므로,
 * 필드별 {@code @JsonFormat} 이 아니라 {@link Instant} 타입 직렬화를 바꾼다.
 *
 * <p><b>MQ 경계에는 영향이 없다.</b> 이 커스터마이저는 Boot 4 REST 자동구성의
 * Jackson 3({@code tools.jackson}) 빌더만 손대고, MQ 는 별도 Jackson 2
 * ObjectMapper 를 쓴다({@link MqJacksonConfig} — ADR-0004/0005). 두 세대는 타입이
 * 달라 설정이 섞이지 않는다.
 */
@Configuration
public class JacksonConfig {

    /** 명세 §1.3 — ISO-8601 UTC, 밀리초 3자리 고정. */
    private static final DateTimeFormatter MILLIS_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    @Bean
    public JsonMapperBuilderCustomizer motifTimeFormatCustomizer() {
        SimpleModule module = new SimpleModule("motif-time-format");
        module.addSerializer(Instant.class, new MillisUtcInstantSerializer());
        return builder -> builder.addModule(module);
    }

    /**
     * 역직렬화는 건드리지 않는다 — 요청 본문의 시각은 Jackson 기본 ISO-8601 파서가
     * 밀리초·나노·무소수부를 모두 받아주므로 클라이언트를 좁힐 이유가 없다.
     * 우리가 고정하는 것은 <b>내려보내는 형태</b>다.
     */
    private static final class MillisUtcInstantSerializer extends ValueSerializer<Instant> {

        @Override
        public void serialize(Instant value, JsonGenerator gen, SerializationContext ctxt) {
            gen.writeString(MILLIS_UTC.format(value));
        }
    }
}
