package com.ssafy.motif.coreapi.admin.org.dto;

import jakarta.validation.constraints.Size;

/**
 * 그룹 수정 요청 (어드민 명세 §3.4.3) — <b>부분 수정</b>이다.
 *
 * <p>두 필드 모두 {@code null} 이면 "그대로" 다. 아티스트 수정(§3.5.4)과 달리
 * "키는 있는데 값이 {@code null}" 을 구분할 필요가 없어 평범한 record 다 — 그룹은
 * 소속사를 반드시 갖고({@code NOT NULL}) 이름도 비울 수 없으므로, {@code null} 에
 * "지운다" 는 뜻이 없다.
 *
 * @param companySeq 바꾸면 <b>비삭제 멤버 전원</b>의 {@code artists.company_seq} 가 같은
 *                   트랜잭션에서 따라 옮겨진다(DB §8)
 */
public record GroupUpdateRequest(
        @Size(max = 50) String groupName,
        Integer companySeq
) {
}
