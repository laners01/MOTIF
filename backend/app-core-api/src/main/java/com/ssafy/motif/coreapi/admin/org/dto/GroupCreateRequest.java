package com.ssafy.motif.coreapi.admin.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 그룹 등록 요청 (어드민 명세 §3.4.2).
 *
 * @param companySeq 필수 — 그룹이 소속사를 소유한다(DB §8). 비삭제여야 하고, 아니면 404
 * @param groupName  <b>중복을 허용한다</b> — 소속사와 달리 UNIQUE 가 없다(DB §8)
 */
public record GroupCreateRequest(
        @NotNull Integer companySeq,
        @NotBlank @Size(max = 50) String groupName
) {
}
