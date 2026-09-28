package com.ssafy.motif.coreapi.domain.shop.docs;

import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.ListResponse;
import com.ssafy.motif.coreapi.domain.shop.dto.ShopBannerItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 숍 배너 API 문서 계약 (명세 §11.11).
 *
 * <p>Swagger 애너테이션만 여기 둔다 — 매핑은 구현 클래스가 갖는다
 * (팀 표준, {@code backend/README.md}).
 *
 * <p>예시 JSON 은 상세 명세 {@code docs/api/detail/11-motif.md} 와 <b>같은 값</b>이다.
 */
@Tag(name = "모티프 (MOTIF)", description = "명세 §11 — 에셋 카탈로그·패스·보관함")
public interface ShopBannerControllerDocs {

    @Operation(summary = "숍 공용 배너 (§11.11)", description = """
            숍 상단의 공용 배너다. **어드민이 관리한다**(§3.11).

            - **인증 필요.** 다만 내용은 호출자에 따라 달라지지 않는다.
            - **활성 배너만**, `displayOrder` **오름차순**. 받은 순서대로 그리면 된다.
            - 파라미터가 없고 페이지네이션도 없다. 봉투는 `ListResponse` 다.
            - 배너가 0건이면 **빈 배열 200** 이다. 숍은 배너 없이도 그려진다.

            ### 아티스트별 패스 배너와 별개다

            §11.4 의 `bannerImageUrl` 은 `artists.motif_bg_image_key` 로, **그 아티스트의
            패스 구매 화면** 배경이다. 여기는 숍 상단에 공통으로 걸리는 배너다.

            ### imageUrl

            공개 콘텐츠라 **무서명 CloudFront URL** 이다. 서명을 붙이면 사용자마다 URL 이
            갈려 CDN 캐시가 쪼개진다.

            ### 클릭 액션이 없다

            배너를 눌렀을 때의 이동 대상을 주지 않는다. 필요해지면 `linkType`/`linkTarget`
            을 추가한다 — 컬럼도 도입하지 않은 상태다.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "성공 — 배너가 없으면 빈 배열",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "SUCCESS",
                                      "code": "SUCCESS",
                                      "message": "요청이 성공했습니다.",
                                      "data": {
                                        "list": [
                                          {
                                            "bannerSeq": 1,
                                            "imageUrl": "https://cdn.motif.io/banners/uuid.jpg",
                                            "displayOrder": 0
                                          }
                                        ]
                                      },
                                      "timestamp": "2026-09-10T00:00:00.000Z"
                                    }""")))
    })
    ApiResponse<ListResponse<ShopBannerItem>> banners();
}
