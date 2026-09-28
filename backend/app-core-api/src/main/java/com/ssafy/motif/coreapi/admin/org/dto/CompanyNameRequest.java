package com.ssafy.motif.coreapi.admin.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 소속사 등록·수정 요청 (어드민 명세 §3.3.2 · §3.3.3).
 *
 * <p>두 API 가 받는 것이 회사명 하나뿐이라 record 도 하나다 — 수정이 부분 수정이 아니기
 * 때문이다(§3.3.3 "회사명 수정"). 필드가 갈리면 그때 나눈다.
 *
 * @param companyName 활성 회사명은 UNIQUE 다({@code uq_companies_name_active}) —
 *                    중복이면 409 {@code COMPANY_NAME_DUPLICATE}
 */
public record CompanyNameRequest(
        @NotBlank @Size(max = 50) String companyName
) {
}
