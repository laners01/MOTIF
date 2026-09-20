package com.ssafy.motif.coreapi.admin.alias.dto;

import com.ssafy.motif.domain.core.artist.ArtistAlias;
import com.ssafy.motif.domain.core.artist.GroupAlias;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 별칭 항목 (어드민 명세 §3.4.5 · §3.5.6) — 목록과 수정 응답이 같은 모양이다.
 *
 * <p>대상 식별자({@code artistSeq}·{@code groupSeq})를 싣지 않는다 — 경로에 이미 있고,
 * 이 항목을 쓰는 화면은 언제나 한 대상의 별칭만 다룬다.
 *
 * <p>아티스트·그룹이 같은 record 를 쓰는 근거는 {@link AliasRequest} 와 같다.
 */
@Schema(description = "별칭 항목")
public record AliasResponse(

        @Schema(description = "별칭 seq", example = "12")
        Integer aliasSeq,

        @Schema(description = "ISO 639-1 소문자 2자", example = "en")
        String languageCode,

        @Schema(description = "별칭", example = "Karina")
        String alias,

        @Schema(description = "등록 일시")
        Instant regDttm
) {

    public static AliasResponse of(ArtistAlias entity) {
        return new AliasResponse(entity.getAliasSeq(), entity.getLanguageCode(),
                entity.getAlias(), entity.getRegDttm());
    }

    public static AliasResponse of(GroupAlias entity) {
        return new AliasResponse(entity.getAliasSeq(), entity.getLanguageCode(),
                entity.getAlias(), entity.getRegDttm());
    }
}
