package com.ssafy.motif.coreapi.global.docs;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.utils.SpringDocAnnotationsUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.HashMap;
import java.util.Map;

/**
 * 성공 응답의 <b>스키마</b>를 핸들러 반환 타입에서 채워 넣는다 — Swagger 하단
 * <i>Schemas</i> 에 응답 DTO 가 뜨게 하는 장치다.
 *
 * <p>docs 인터페이스는 성공 응답에 {@code @Content(examples = ...)} 만 준다. 그런데
 * springdoc 은 애너테이션 {@code @Content} 에 {@code schema} 가 없으면 <b>반환 타입에서
 * 추론해 둔 스키마를 버린다</b> — 예시만 있고 타입이 없는 문서가 되고, 응답 DTO 가
 * {@code components.schemas} 에 한 건도 등록되지 않는다(요청 DTO 만 남는다). 안드로이드가
 * 필드 목록·nullable·enum 을 볼 곳이 사라진다는 뜻이다.
 *
 * <p>고치는 방법은 둘이다. 엔드포인트마다 봉투 미러 record 를 만들어
 * {@code @Schema(implementation = ...)} 로 지목하거나({@link TokenApiResponseSchema} 가 그
 * 방식이다), 여기처럼 <b>한 곳에서</b> 비어 있는 스키마만 반환 타입으로 메우거나다.
 * 엔드포인트가 40개를 넘어 후자를 쓴다 — 새 API 는 아무것도 하지 않아도 스키마가 붙는다.
 *
 * <p>이미 채워져 있는 스키마는 <b>건드리지 않는다</b>. 명시적으로 지목한
 * {@link FailResponseSchema}·{@link SuccessNoDataApiResponseSchema}·
 * {@link TokenApiResponseSchema} 가 반환 타입보다 정확한 경우가 있어서다
 * (예: {@code ApiResponse<Void>} 인데 문서는 "data: null" 봉투를 보여주고 싶은 경우).
 *
 * <p>2xx 만 채운다. 실패 응답은 반환 타입과 무관하게 {@code FailResponse} 봉투다.
 *
 * <p><b>{@code GlobalOpenApiCustomizer} 다 — {@code OpenApiCustomizer} 로 바꾸면 조용히
 * 아무 일도 안 한다.</b> 문서가 {@code GroupedOpenApi} 로 갈려 있으면(user·admin —
 * {@link OpenApiConfig}) 평범한 {@code OpenApiCustomizer} 빈은 <i>그룹 없는 합본 문서</i>
 * 쪽에만 붙는데, 그 합본은 {@code springdoc.enable-default-api-docs=false} 로 꺼 두었다.
 * 모든 그룹에 붙는 것은 {@code Global…} 쪽이다.
 */
@Component
public class ResponseSchemaCustomizer implements GlobalOpenApiCustomizer {

    private static final String JSON = "application/json";

    private final RequestMappingHandlerMapping handlerMapping;

    public ResponseSchemaCustomizer(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getPaths() == null) {
            return;
        }
        Map<String, HandlerMethod> routes = routes();
        openApi.getPaths().forEach((path, pathItem) ->
                pathItem.readOperationsMap().forEach((httpMethod, operation) -> {
                    HandlerMethod handler = routes.get(httpMethod.name() + ' ' + path);
                    if (handler == null || operation.getResponses() == null) {
                        return;
                    }
                    operation.getResponses().forEach((code, response) -> fill(openApi, handler, code, response));
                }));
    }

    private void fill(OpenAPI openApi, HandlerMethod handler, String code, ApiResponse response) {
        if (!code.startsWith("2") || response.getContent() == null) {
            return;
        }
        MediaType json = response.getContent().get(JSON);
        if (json == null || json.getSchema() != null) {
            return;
        }
        // 반환 타입 전체(봉투 포함)를 푼다. 참조된 DTO 는 components.schemas 에 등록되고
        // 여기에는 $ref 만 들어간다 — 같은 DTO 를 쓰는 엔드포인트가 한 정의를 공유한다.
        json.setSchema(SpringDocAnnotationsUtils.extractSchema(
                openApi.getComponents(),
                handler.getMethod().getGenericReturnType(),
                null,
                null,
                openApi.getSpecVersion()));
    }

    /** {@code "GET /api/v1/users/me"} → 핸들러. springdoc 이 쓰는 패턴 문자열과 같은 출처다. */
    private Map<String, HandlerMethod> routes() {
        Map<String, HandlerMethod> routes = new HashMap<>();
        handlerMapping.getHandlerMethods().forEach((info, handler) ->
                info.getMethodsCondition().getMethods().forEach(method ->
                        info.getPatternValues().forEach(pattern ->
                                routes.put(method.name() + ' ' + pattern, handler))));
        return routes;
    }
}
