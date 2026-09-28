package com.ssafy.motif.coreapi.admin.notice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 공지 작성 요청 (어드민 명세 §3.10.2).
 *
 * <p>바이트가 아니라 <b>키</b>를 받는다 — 이미지는 브라우저에서 S3 로 직행하고(§1.4
 * presign) 서버는 올라왔는지만 HEAD 로 확인한다.
 *
 * @param content   비어 있을 수 없다 ({@code notices.content} NOT NULL). 상한 5000자는
 *                  §3.10.2 기본안이며 {@link NoticeUpdateRequest} 와 같은 값이다
 * @param imageKeys <b>배열 순서가 곧 {@code display_order}</b> 다(§3.10.2). 생략하면
 *                  이미지 없는 공지 — 정상 입력이라 빈 목록으로 정규화한다
 */
@Schema(description = "공지 작성 (본문 + 이미지 키 0~10장)")
public record NoticeCreateRequest(
        @NotNull Integer artistSeq,
        @NotBlank @Size(max = 5000) String content,
        @Size(max = 10) List<@Size(max = 255) String> imageKeys
) {

    public NoticeCreateRequest {
        imageKeys = imageKeys == null ? List.of() : imageKeys;
    }
}
