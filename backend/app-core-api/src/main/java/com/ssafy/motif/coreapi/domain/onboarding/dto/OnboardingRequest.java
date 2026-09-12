package com.ssafy.motif.coreapi.domain.onboarding.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 명세 §3.3 요청 — 아티스트 선택과 닉네임을 한 번에 제출한다. */
public record OnboardingRequest(

        @NotEmpty(message = "아티스트를 1명 이상 선택해야 합니다")
        @Schema(description = "구독할 아티스트 목록. 최소 1개, 중복은 서버가 제거한다",
                example = "[3, 7, 12]", requiredMode = Schema.RequiredMode.REQUIRED)
        List<Integer> artistSeqs,

        @NotBlank(message = "nickname은 필수입니다")
        @Pattern(regexp = NICKNAME_PATTERN, message = NICKNAME_MESSAGE)
        @Schema(description = "확정할 닉네임. 영문·한글·숫자·마침표·언더바 2~20자. 중복 허용",
                example = "모티프러버", requiredMode = Schema.RequiredMode.REQUIRED)
        String nickname
) {

    /**
     * 명세 §3.3 "영문·한글·`.`·`_`, 2~20자" + <b>숫자 허용</b> (2026-09-12 팀 확정).
     *
     * <p>숫자를 넣은 이유: 명세의 열거에는 없지만 §2.1 이 가입 시 만드는 임시 닉네임이
     * {@code user_{seq}}(예: {@code user_1}) 라, 숫자를 막으면 서버가 발급한 값이
     * 자기 검증을 통과하지 못하는 모순이 생긴다. 원본 명세서에도 반영이 필요하다.
     */
    public static final String NICKNAME_PATTERN = "^[a-zA-Z0-9가-힣._]{2,20}$";

    public static final String NICKNAME_MESSAGE = "2~20자의 영문·한글·숫자·마침표·언더바만 허용합니다.";

    /**
     * 중복을 제거한 아티스트 목록. 순서는 요청 그대로 유지한다(로그·디버깅 편의).
     *
     * <p>거르지 않으면 복합 PK {@code (user_seq, artist_seq)} 충돌로 500 이 난다.
     * 클라이언트 실수를 400 으로 되돌려줄 실익이 없어 서버가 정리한다(명세 §3.3 기본안).
     */
    public Set<Integer> distinctArtistSeqs() {
        return new LinkedHashSet<>(artistSeqs);
    }
}
