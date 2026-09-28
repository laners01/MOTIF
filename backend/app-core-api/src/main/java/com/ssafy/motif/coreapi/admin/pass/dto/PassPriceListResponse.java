package com.ssafy.motif.coreapi.admin.pass.dto;

import java.util.List;

/**
 * 아티스트 패스 가격표 (어드민 명세 §3.9.1 — {@code data.list[]}).
 *
 * <p>배열을 그대로 {@code data} 에 싣지 않고 한 겹 감싸는 것은 팀 공통 목록 규약이다
 * (API 명세 §1.2) — 나중에 항목 외의 필드를 더해도 클라이언트 파싱이 깨지지 않는다.
 */
public record PassPriceListResponse(List<PassPriceResponse> list) {
}
