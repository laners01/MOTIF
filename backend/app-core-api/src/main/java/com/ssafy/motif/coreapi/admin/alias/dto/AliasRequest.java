package com.ssafy.motif.coreapi.admin.alias.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 별칭 등록·수정 요청 (어드민 명세 §3.4.6·§3.4.7 · §3.5.7·§3.5.8).
 *
 * <p><b>등록과 수정이 같은 본문을 쓴다.</b> 수정은 부분 수정이 아니라 전체 치환이라
 * ({@code PUT}) 두 칸을 항상 함께 보낸다 — 칸이 둘뿐이고 화면이 둘 다 채워 보내므로,
 * {@code null} 을 "그대로" 로 해석하는 규약을 두면 빈 값을 보낸 것과 구분이 안 된다.
 *
 * <p><b>아티스트와 그룹이 같은 record 를 쓴다.</b> 칸이 완전히 같고 두 API 가 같은 티켓에서
 * 같이 나가므로, 병렬 브랜치가 각자 칸을 더해 서로를 흔들 여지가 없다. 그래서 어느 한쪽
 * 패키지에 두지 않고 중립 패키지에 뒀다 — {@code admin/artist} 에 두면 {@code admin/org} 가
 * 남의 도메인 dto 를 import 하게 된다.
 */
@Schema(description = "별칭 등록·수정 요청")
public record AliasRequest(

        @Schema(description = "ISO 639-1 소문자 2자. 검색에는 쓰지 않는 저장 구분이다",
                example = "en", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Pattern(regexp = "^[a-z]{2}$", message = "언어 코드는 ISO 639-1 소문자 2자입니다.")
        String languageCode,

        @Schema(description = "별칭. 같은 언어에 여러 개를 달 수 있고 중복도 막지 않는다",
                example = "Karina", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 100)
        String alias
) {
}
