package com.ssafy.motif.coreapi.domain.user.docs;

import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.domain.user.dto.UserMeResponse;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 사용자·마이페이지 API 문서 계약 (명세 §4).
 *
 * <p>Swagger 애너테이션만 여기 둔다 — 매핑은 구현 클래스가 갖는다
 * (팀 표준, {@code backend/README.md}).
 */
@Tag(name = "사용자 (USER)", description = "명세 §4 — 마이페이지")
public interface UserControllerDocs {

    @Operation(
            summary = "마이페이지 원콜 (§4.1)",
            description = """
                    마이페이지 첫 화면에 필요한 것을 **한 번의 호출**로 내려준다
                    (회의 확정 2026-09-02 개편) — 프로필 + 대표 모티프 + 내 영상 3×3 프리뷰.

                    - `profileImageUrl` — 공개 CDN URL. **미설정이면 `null`**
                    - `repMotif` — **미설정·삭제·부재면 `null`**. 설정·해제·문구 수정은
                      이 API 가 아니라 **§11.8 `PATCH /motif/results/{resultSeq}`** 다(R4 통합)
                    - `videos` — 내 참여 영상 **최신 9건**. **없으면 빈 배열 `[]`**.
                      전체 목록·더보기는 **§4.8**
                    - `videoCount` 는 불요로 철회됐다(R2)

                    ### 영상 주소가 CDN 이 아닌 이유
                    `repMotif.videoUrl` 은 **core-api** 를 가리킨다
                    (`/api/v1/media/{jobUuid}/master.m3u8`). 모티프 결과물은 §1.7 **보호
                    등급**이고, HLS 는 재생목록 안에서 조각을 추가로 요청하는데 URL 서명이
                    거기까지 따라붙지 않는다 — core-api 가 재생목록을 다시 써서 조각 주소에
                    서명을 박아 내린다. **플레이어가 이 주소를 직접 GET 하므로
                    `Authorization` 헤더가 실리도록 데이터 소스를 설정해야 한다.**

                    ### 썸네일
                    `thumb.jpg` 는 그 자체로 끝나는 단일 파일이라 리라이팅 없이 서명 URL 을
                    바로 낸다. **공개/보호는 S3 키의 프리픽스가 정한다** — `private/` 로
                    시작하면 서명된 URL, 아니면 무서명 CDN URL 이다. `visibility` 로
                    판정하지 않는다: PUBLIC 게시는 산출물을 `public/` 으로 **복사**할 뿐
                    키를 바꾸지 않아, 그 사이 행에서 둘이 어긋난다.

                    이미지 서명 만료는 **6시간**이다(재생목록·조각은 10분). 이미지는 만료돼도
                    다시 받아 복구되는 경로가 없어 길게 잡는다.

                    ### 실패
                    **도메인 분기가 없다.** 공통 분기(401 등)만 적용된다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "마이페이지",
                    content = @Content(mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "대표 모티프·영상이 있는 경우", value = """
                                            {
                                              "result": "SUCCESS",
                                              "code": "SUCCESS",
                                              "message": "요청이 성공했습니다.",
                                              "data": {
                                                "userSeq": 1,
                                                "nickname": "모티프러버",
                                                "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg",
                                                "repMotif": {
                                                  "resultSeq": 10,
                                                  "videoUrl": "/api/v1/media/9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f/master.m3u8",
                                                  "thumbnailUrl": "https://cdn.motif.io/private/motif/9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f/thumb.jpg?Expires=1789000000&Signature=...&Key-Pair-Id=K27QE85H6DLYDD"
                                                },
                                                "videos": [
                                                  {
                                                    "videoSeq": 88,
                                                    "thumbnailUrl": "https://cdn.motif.io/public/challenge/3fa85f64-5717-4562-b3fc-2c963f66afa6/thumb.jpg",
                                                    "viewCount": 3400,
                                                    "visibility": "PUBLIC"
                                                  },
                                                  {
                                                    "videoSeq": 87,
                                                    "thumbnailUrl": "https://cdn.motif.io/private/challenge/7c9e1d20-8b3a-4f51-9c6d-0e2f4a6b8c10/thumb.jpg?Expires=1789000000&Signature=...&Key-Pair-Id=K27QE85H6DLYDD",
                                                    "viewCount": 120,
                                                    "visibility": "PRIVATE"
                                                  }
                                                ]
                                              },
                                              "timestamp": "2026-09-16T00:00:00.000Z"
                                            }"""),
                                    @ExampleObject(name = "대표 모티프 미설정 · 영상 0건", value = """
                                            {
                                              "result": "SUCCESS",
                                              "code": "SUCCESS",
                                              "message": "요청이 성공했습니다.",
                                              "data": {
                                                "userSeq": 1,
                                                "nickname": "모티프러버",
                                                "profileImageUrl": null,
                                                "repMotif": null,
                                                "videos": []
                                              },
                                              "timestamp": "2026-09-16T00:00:00.000Z"
                                            }""")
                            }))
    })
    ApiResponse<UserMeResponse> me(@Parameter(hidden = true) AuthenticatedUser user);
}
