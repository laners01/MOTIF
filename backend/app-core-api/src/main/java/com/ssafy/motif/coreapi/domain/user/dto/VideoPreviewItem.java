package com.ssafy.motif.coreapi.domain.user.dto;

import com.ssafy.motif.domain.core.video.Visibility;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 내 참여 영상 그리드 한 칸 (API 명세 §4.1 · §4.8).
 *
 * <p><b>두 API 가 같은 카드를 그린다.</b> §4.1 은 첫 화면 3×3 에 최신 9개를, §4.8 은
 * "더보기" 이후 전량을 내리는데 항목의 모양은 같다. 그래서 각자 record 를 두지 않는다 —
 * 한쪽만 필드가 바뀌면 같은 그리드가 화면에 따라 다른 것을 그리게 된다.
 *
 * <p>재생에 필요한 것은 담지 않는다. 항목을 고르면 <b>§8.2 단건 조회</b>로 뷰어에
 * 들어간다.
 *
 * @param visibility 본인 화면이라 {@code PRIVATE} 도 내려온다 — 클라이언트가 이 값으로
 *                   자물쇠를 그린다
 */
@Schema(description = "참여 영상 그리드 항목 (§4.1 · §4.8)")
public record VideoPreviewItem(
        Long videoSeq,
        String thumbnailUrl,
        Long viewCount,
        @Schema(description = "PUBLIC | PRIVATE — 본인 화면이라 둘 다 내려온다")
        Visibility visibility) {
}
