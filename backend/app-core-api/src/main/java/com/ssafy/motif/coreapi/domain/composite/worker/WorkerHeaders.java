package com.ssafy.motif.coreapi.domain.composite.worker;

/**
 * AI 경계의 HTTP 헤더 이름 (AI 서버 명세 §3, 작업명세 §3.1).
 *
 * <p>접수(우리 → AI)와 콜백(AI → 우리)이 <b>같은 헤더 이름</b>을 쓴다. 문자열을 양쪽에
 * 따로 적으면 한쪽만 고쳤을 때 401 이 나는데, 그 401 이 "토큰이 틀렸다" 로 보여
 * 비밀을 의심하게 된다.
 *
 * <p>추적 헤더는 여기 두지 않는다 — {@code RequestIdFilter.HEADER_NAME} 이 이미 소유한다.
 */
public final class WorkerHeaders {

    /** 공유 비밀. 접수 요청에 싣고 콜백에서 대조한다. */
    public static final String TOKEN = "X-Motif-Worker-Token";

    private WorkerHeaders() {
    }
}
