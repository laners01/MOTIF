package com.ssafy.motif.coreapi.domain.composite.service;

import com.ssafy.motif.coreapi.domain.composite.CompositeProperties;
import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobPayload;
import com.ssafy.motif.coreapi.domain.composite.worker.WorkerHeaders;
import com.ssafy.motif.coreapi.global.filter.RequestIdFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * AI 접수 호출 (작업명세 §4.2 · AI 서버 명세 §3.2 — S15P21D104-536).
 *
 * <p><b>큐가 아니라 HTTP 다.</b> AI 는 GCP Cloud Run Jobs 로 도는데 Job 은 상주하지 않아
 * 큐를 구독할 수 없다(§0). 접수 서비스에 잡 하나를 POST 하면 Job 을 <b>띄우기만</b> 하고
 * 1~3초 안에 202 로 답한다 — 합성이 끝날 때까지 기다리지 않는다.
 *
 * <p><b>부르는 쪽은 디스패처(537)다.</b> §13.2 요청 스레드에서 부르지 않는다 — 커밋 전에
 * 부르면 AI 가 커밋 전에 콜백을 쳐서 404 를 받는다(§7 함정 2). 빠른 실패 코드일 때 실제로 난다.
 *
 * <p><b>실패해도 던지지 않는다.</b> WARN 만 남기고 잡을 {@code QUEUED} 에 둔다 — 스윕이
 * 2분 뒤 재접수한다(§4.7). 여기서 예외를 올리면 부르는 쪽이 무엇을 해야 할지 알 수 없고,
 * 재시도 수단은 이미 스윕 하나로 정해져 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompositeJobSubmitter {

    private final RestClient compositeSubmitRestClient;
    private final CompositeProperties properties;
    /** 전이는 여기 몰려 있다 — 상태를 쓰는 주체를 늘리지 않는다(작업명세 §2 원칙 1). */
    private final CompositeJobCompletionService completionService;

    /**
     * 잡 하나를 AI 에 접수한다.
     *
     * <p>202 면 {@code QUEUED → PROCESSING}. 그 밖의 응답·예외는 상태를 그대로 둔다 —
     * 401(토큰 불일치)은 설정 오류라 재시도해도 같고, 503(쿼터·일시 오류)은 스윕이
     * 되살린다(AI 명세 §3.2 응답표).
     *
     * @return 접수에 성공해 {@code PROCESSING} 으로 넘어갔으면 {@code true}
     */
    public boolean submit(CompositeJobPayload payload) {
        if (!properties.hasSubmitUrl()) {
            log.warn("motif.composite.submit-url 이 비어 있다 — 접수를 건너뛴다: jobUuid={}",
                    payload.jobUuid());
            return false;
        }
        HttpStatus status;
        try {
            status = HttpStatus.valueOf(compositeSubmitRestClient.post()
                    .uri(properties.submitUrl())
                    .header(WorkerHeaders.TOKEN, properties.workerToken())
                    .header(RequestIdFilter.HEADER_NAME, MDC.get(RequestIdFilter.MDC_KEY))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    // 4xx·5xx 를 예외로 올리지 않는다 — 상태 코드로 갈라야 하고,
                    // 어느 쪽이든 우리 동작은 "상태 유지 + WARN" 하나다
                    .retrieve()
                    .onStatus(httpStatus -> true, (request, response) -> { })
                    .toBodilessEntity()
                    .getStatusCode()
                    .value());
        } catch (RestClientException e) {
            // 타임아웃·연결 실패·해석 불가 — 전부 "답을 못 받았다". AI 가 이미 Job 을
            // 띄웠을 수도 있지만, 두 번 도는 것은 GPU 시간 낭비일 뿐 결과는 같다(§5)
            log.warn("AI 접수 결과 불명 — QUEUED 로 둔다: jobUuid={} {}",
                    payload.jobUuid(), e.getMessage());
            return false;
        }
        if (status != HttpStatus.ACCEPTED) {
            log.warn("AI 접수 거절 — QUEUED 로 둔다: jobUuid={} status={}",
                    payload.jobUuid(), status.value());
            return false;
        }
        completionService.submitted(payload.jobUuid());
        log.info("AI 접수 202 → PROCESSING: jobUuid={}", payload.jobUuid());
        return true;
    }

}
