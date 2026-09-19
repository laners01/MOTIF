package com.ssafy.motif.coreapi.domain.composite.service;

import com.ssafy.motif.coreapi.domain.composite.CompositeProperties;
import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobPayload;
import com.ssafy.motif.coreapi.domain.composite.worker.WorkerHeaders;
import com.ssafy.motif.coreapi.global.filter.RequestIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * AI 접수 호출 (AI 서버 명세 §3.2 응답표 · 작업명세 §4.2 — S15P21D104-536).
 *
 * <p><b>가르는 것은 202 하나다.</b> 202 면 {@code PROCESSING} 으로 넘기고, 그 밖의 응답과
 * 예외는 전부 "상태 유지 + WARN" 이다 — 401(설정 오류)은 재시도해도 같고 503(쿼터·일시
 * 오류)은 스윕이 되살린다. 여기서 던지면 부르는 쪽(디스패처·스윕)이 할 일이 없는데
 * 예외만 올라간다.
 *
 * <p>스텁(537)이 아직 없어 {@link MockRestServiceServer} 로 대신한다 — 헤더·본문·URL 까지
 * 계약대로 나가는지는 실제 스텁보다 여기서 더 촘촘히 본다.
 */
class CompositeJobSubmitterTest {

    private static final String SUBMIT_URL = "http://ai.test/submit";
    private static final String TOKEN = "test-worker-token";
    private static final UUID JOB_UUID = UUID.fromString("b7c93b4e-0a56-4d2e-9f0d-2f6e8a54d61c");

    private MockRestServiceServer server;
    private CompositeJobCompletionService completionService;
    private CompositeJobSubmitter submitter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        completionService = mock(CompositeJobCompletionService.class);
        submitter = new CompositeJobSubmitter(builder.build(), properties(SUBMIT_URL),
                completionService);
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("202 — 계약대로 POST 하고 PROCESSING 으로 넘긴다")
    void acceptedMarksSubmitted() {
        MDC.put(RequestIdFilter.MDC_KEY, "req-42");
        server.expect(requestTo(SUBMIT_URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header(WorkerHeaders.TOKEN, TOKEN))
                .andExpect(header(RequestIdFilter.HEADER_NAME, "req-42"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // 본문은 snake_case 계약이다 — camelCase 로 나가면 AI 가 전부 null 로 읽는다
                .andExpect(jsonPath("$.job_uuid").value(JOB_UUID.toString()))
                .andExpect(jsonPath("$.output_video_key")
                        .value("results/challenge/" + JOB_UUID + ".mp4"))
                .andExpect(jsonPath("$.participant_mask").value(0))
                .andRespond(withStatus(HttpStatus.ACCEPTED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"execution\":\"projects/x/executions/motif-compose-abc12\"}"));

        assertThat(submitter.submit(payload())).isTrue();

        server.verify();
        verify(completionService).submitted(JOB_UUID);
    }

    @Test
    @DisplayName("503 — 쿼터·일시 오류다. 상태를 그대로 두고 스윕에 맡긴다")
    void serviceUnavailableKeepsQueued() {
        server.expect(requestTo(SUBMIT_URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"quota exceeded\"}"));

        assertThat(submitter.submit(payload())).isFalse();

        verify(completionService, never()).submitted(JOB_UUID);
    }

    @Test
    @DisplayName("401 — 토큰 불일치는 설정 오류다. 던지지 않고 상태를 둔다")
    void unauthorizedKeepsQueued() {
        server.expect(requestTo(SUBMIT_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThat(submitter.submit(payload())).isFalse();

        verify(completionService, never()).submitted(JOB_UUID);
    }

    @Test
    @DisplayName("타임아웃·연결 실패 — 결과 불명이다. 예외를 올리지 않는다")
    void connectionFailureIsSwallowed() {
        server.expect(requestTo(SUBMIT_URL))
                .andRespond(withException(new java.net.SocketTimeoutException("read timed out")));

        assertThat(submitter.submit(payload())).isFalse();

        verify(completionService, never()).submitted(JOB_UUID);
    }

    @Test
    @DisplayName("접수 URL 이 없으면 호출 자체를 하지 않는다 — 설정 전에는 스윕이 들고 있는다")
    void missingUrlSkipsTheCall() {
        submitter = new CompositeJobSubmitter(RestClient.builder().build(), properties(""),
                completionService);

        assertThat(submitter.submit(payload())).isFalse();

        verify(completionService, never()).submitted(JOB_UUID);
    }

    private static CompositeProperties properties(String submitUrl) {
        // 뒤 셋은 디스패처(537) 몫이라 접수 호출과 무관하다
        return new CompositeProperties(submitUrl, TOKEN,
                Duration.ofSeconds(3), Duration.ofSeconds(15),
                Duration.ofMinutes(1), Duration.ofMinutes(2), Duration.ofMinutes(30));
    }

    /** 조립기(187)가 만드는 것과 같은 모양 — 무교체 CHALLENGE. */
    private static CompositeJobPayload payload() {
        return new CompositeJobPayload(JOB_UUID, "CHALLENGE", (short) 1,
                "raw/" + JOB_UUID + ".mp4",
                "challenges/8a1b.mp4",
                "results/challenge/" + JOB_UUID + ".mp4",
                true, 0, List.of(), 15_000);
    }
}
