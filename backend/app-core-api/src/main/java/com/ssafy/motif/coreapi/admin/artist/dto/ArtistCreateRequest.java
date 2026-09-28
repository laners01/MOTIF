package com.ssafy.motif.coreapi.admin.artist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 아티스트 등록 요청 (어드민 명세 §3.5.2).
 *
 * @param name       표시 이름 — {@code users.nickname} 이 된다. 공백만 있는 값은 막는다
 * @param groupSeq   {@code null} = 솔로. 지정하면 그룹이 비삭제여야 하고
 *                   {@code companySeq} 가 그 그룹의 소속사와 <b>같아야</b> 한다
 *                   (불일치 400 — 화면은 그룹 선택 시 소속사를 자동 채움·잠금)
 * @param companySeq 솔로면 필수. 그룹 지정 시에는 생략하면 그룹 소속사를 따른다
 * @param profileImageKey  {@code profiles/{uuid}.jpg} — §3.2 presign 으로 받은 키. 선택,
 *                         지정하면 HEAD 로 업로드 완료를 확인한다(§1.4)
 * @param motifBgImageKey  {@code artists/{uuid}.jpg} — 모티프 배경(API §11.4 배너)
 */
public record ArtistCreateRequest(
        @NotBlank @Size(max = 50) String name,
        Integer groupSeq,
        Integer companySeq,
        String bio,
        String profileImageKey,
        String motifBgImageKey
) {
}
