package com.ssafy.motif.coreapi.domain.artist.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 검색 결과 한 줄 (API 명세 §5.2) — 아티스트 요약(상세 명세 README §3.5) +
 * {@code isSubscribed}.
 *
 * <p><b>결과는 항상 멤버 단위다.</b> 검색어가 그룹 별칭에 걸리면 그 그룹의 비삭제 멤버가
 * 이 항목으로 펼쳐져 들어온다 — 그룹으로 묶은 중첩 구조를 만들지 않는다.
 *
 * <p><b>{@code name} 은 {@code users.nickname} 이다.</b> 별칭으로 찾았더라도 별칭을 싣지
 * 않는다 — "Karina" 로 검색해도 화면에는 "카리나" 가 뜬다. 별칭을 표시에 쓰면 같은
 * 아티스트가 질의에 따라 다른 이름으로 보인다(ADR-0014 D1).
 *
 * <p><b>{@link ArtistCardResponse}(§5.10)와 합치지 않는다.</b> 지금은 다섯 칸이 모두 같지만,
 * 그쪽 javadoc 이 "§5.2 가 붙을 때 판단한다" 로 남겨 둔 건이다. 합쳐 두면 한쪽 화면이 칸을
 * 더하는 순간 다른 쪽 응답이 함께 흔들린다 — 전량 목록과 검색은 화면도 호출 빈도도 다르다.
 */
@Schema(description = "아티스트 검색 결과 항목 (§5.2)")
public record ArtistSearchResponse(

        @Schema(description = "아티스트 seq", example = "3")
        Integer artistSeq,

        @Schema(description = "아티스트 이름 (users.nickname) — 별칭이 아니다", example = "카리나")
        String name,

        @Schema(description = "그룹명. 솔로 아티스트는 null", example = "aespa", nullable = true)
        String groupName,

        @Schema(description = "프로필 이미지 URL — 공개 등급이라 서명하지 않는다(§1.7). 미등록이면 null",
                example = "https://cdn.motif.io/profiles/3f9c1a2b.jpg", nullable = true)
        String profileImageUrl,

        @Schema(description = "요청자의 구독 여부", example = "true")
        boolean isSubscribed
) {
}
