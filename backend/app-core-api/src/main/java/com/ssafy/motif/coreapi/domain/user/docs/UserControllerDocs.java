package com.ssafy.motif.coreapi.domain.user.docs;

import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.ListResponse;
import com.ssafy.motif.coreapi.domain.user.dto.NicknameUpdateRequest;
import com.ssafy.motif.coreapi.domain.user.dto.NicknameUpdateResponse;
import com.ssafy.motif.coreapi.domain.user.dto.ProfileImagePresignRequest;
import com.ssafy.motif.coreapi.domain.user.dto.ProfileImagePresignResponse;
import com.ssafy.motif.coreapi.domain.user.dto.ProfileImageUpdateRequest;
import com.ssafy.motif.coreapi.domain.user.dto.ProfileImageUpdateResponse;
import com.ssafy.motif.coreapi.domain.user.dto.UserMeResponse;
import com.ssafy.motif.coreapi.domain.user.dto.UserProfileResponse;
import com.ssafy.motif.coreapi.domain.user.dto.VideoPreviewItem;
import com.ssafy.motif.coreapi.global.docs.FailResponseSchema;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 사용자·마이페이지 API 문서 계약 (명세 §4).
 *
 * <p>Swagger 애너테이션만 여기 둔다 — 매핑은 구현 클래스가 갖는다
 * (팀 표준, {@code backend/README.md}).
 *
 * <p>§4.2 예시의 닉네임 오류 문구는 {@link com.ssafy.motif.coreapi.global.validation.Nickname}
 * 가 소유한다 — 텍스트 블록 안이라 상수를 끼워 넣지 못해 옮겨 적은 사본이다.
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

    @Operation(
            summary = "내 챌린지 그리드 (§4.8)",
            description = """
                    내 참여 영상을 **전량** 내려준다(회의 확정 R4 — 커서에서 전환).
                    **§4.1 로 첫 화면을 그린 뒤 "더보기/전체" 진입 시 1회 호출**한다.

                    - 항목 형태는 **§4.1 `videos[]` 와 같다** — 같은 그리드를 그리는 카드다
                    - **본인 화면이라 `PUBLIC`·`PRIVATE` 전부 포함**하고 항목마다
                      `visibility` 로 구분한다
                    - 삭제 영상은 제외한다. **삭제된 챌린지에 속한 영상도 뺀다**(§1.8)
                    - 0건이면 `"list": []`
                    - 정렬은 최신순

                    항목을 고르면 **§8.2 단건 조회**로 뷰어에 진입한다. 연속 스와이프 재생이
                    필요해지면 `?view=FULL` 확장을 예약해 뒀다(원본 §16 #12).

                    ### 페이지가 없는 이유와 그 전제
                    전량 반환의 전제는 **"본인 영상 수가 크지 않다"** 이다. 수백 건이 쌓이면
                    커서 전환 재검토 대상이고, 서버는 그 시점을 알 수 있도록 일정 건수를
                    넘으면 로그로 남긴다 — **응답을 자르지는 않는다.** 조용히 자르면
                    사용자에게는 영상이 사라진 것으로 보인다.

                    ### 썸네일
                    공개/보호는 **S3 키의 프리픽스**가 정한다 — `private/` 로 시작하면 서명된
                    URL, 아니면 무서명 CDN URL 이다. `visibility` 로 판정하지 않는다(§4.1 참고).
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "내 참여 영상 전량",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "SUCCESS",
                                      "code": "SUCCESS",
                                      "message": "요청이 성공했습니다.",
                                      "data": {
                                        "list": [
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
                                    }""")))
    })
    ApiResponse<ListResponse<VideoPreviewItem>> myVideos(
            @Parameter(hidden = true) AuthenticatedUser user);

    @Operation(
            summary = "타 사용자 프로필 (§4.6)",
            description = """
                    참여 영상 뷰어에서 **작성자를 눌렀을 때 뜨는 Bottom Sheet** 데이터다.
                    닉네임·프로필과 그 사람이 구독 중인 아티스트를 함께 내린다.

                    - `subscribedArtists[]` 는 **공통 아티스트 요약**(상세 명세 README §3.5)
                      그대로다. 구독 **최신순**, 0건이면 `[]`
                    - `groupName` 은 솔로 아티스트면 `null`. 원본 §4.6 은 이 필드를 뺐지만
                      **타입 통일을 위해 되살렸다** — 아티스트를 참조하는 응답은 상황마다
                      필드를 빼지 않는다
                    - **삭제된 아티스트는 목록에서 뺀다.** 남겨 두면 눌러도 404 가 나는
                      카드가 생긴다(§1.8 — 삭제 아티스트는 없는 것으로 답한다)
                    - `participationCount` 는 제거됐다(회의 확정 R2)

                    본인 `userSeq` 로 불러도 막지 않는다. 마이페이지는 **§4.1** 로 따로 있다.

                    ### 스키마 주의
                    아티스트 이름은 `artists` 에 없다 — `artists.user_seq → users.nickname`
                    조인으로 얻는다(`artists` 는 `role='ARTIST'` 사용자의 도메인 확장).
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "사용자 프로필",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "SUCCESS",
                                      "code": "SUCCESS",
                                      "message": "요청이 성공했습니다.",
                                      "data": {
                                        "userSeq": 5,
                                        "nickname": "팬123",
                                        "profileImageUrl": "https://cdn.motif.io/profiles/aa11bb22.jpg",
                                        "subscribedArtists": [
                                          {
                                            "artistSeq": 3,
                                            "name": "카리나",
                                            "groupName": "aespa",
                                            "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg"
                                          }
                                        ]
                                      },
                                      "timestamp": "2026-09-16T00:00:00.000Z"
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = """
                            미존재 **또는 삭제된 사용자** — 둘을 구분하지 않는다.
                            **삭제 사용자는 존재를 숨긴다**(§1.8): 403 으로 갈라 내면
                            "그 사람은 있는데 탈퇴했다"가 응답에서 읽힌다.
                            """,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "USER_NOT_FOUND",
                                      "message": "존재하지 않는 사용자입니다.",
                                      "data": null,
                                      "timestamp": "2026-09-16T00:00:00.000Z"
                                    }""")))
    })
    ApiResponse<UserProfileResponse> profile(
            @Parameter(description = "조회할 사용자 seq", example = "5") Integer userSeq);

    @Operation(
            summary = "닉네임 수정 (§4.2)",
            description = """
                    마이페이지에서 닉네임을 바꾼다.

                    - **형식 검증만 한다. 중복 확인은 하지 않는다** — `users.nickname` 에
                      UNIQUE 가 없다(C1 회의 확정). 같은 닉네임을 쓰는 사용자가 여럿일 수 있다
                    - 규칙은 **온보딩(§3.3)과 같다**: 영문·한글·**숫자**·`.`·`_` 2~20자.
                      같은 제약 애너테이션을 공유하므로 한쪽만 달라지지 않는다
                    - 숫자가 허용 목록에 있는 이유는 §2.1 이 가입 시 발급하는 임시 닉네임이
                      `user_{seq}` 라, 막으면 **서버가 만든 값이 서버의 검증을 통과하지 못하기**
                      때문이다
                    - **앞뒤 공백을 다듬지 않는다.** `" 닉"` 은 400 이다 — 서버가 조용히 고치면
                      보낸 값과 저장된 값이 달라진다
                    - 같은 값으로 바꿔도 **200** 이다. 변경 없음을 오류로 돌려줄 실익이 없다

                    응답은 확정된 닉네임 하나다. 마이페이지 전체를 다시 그려야 하면 **§4.1** 을 부른다.

                    설정·해제가 아니라 **수정**이므로 `users.mod_usr_seq` 에 본인 seq 가 기록된다
                    (온보딩의 최초 확정은 기록하지 않는다).
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "수정된 닉네임",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "SUCCESS",
                                      "code": "SUCCESS",
                                      "message": "요청이 성공했습니다.",
                                      "data": { "nickname": "새닉네임" },
                                      "timestamp": "2026-09-16T00:00:00.000Z"
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = """
                            형식 위반 — **이 API 의 유일한 도메인 실패다.**
                            `data` 에 필드 오류가 실리며 닉네임 오류는 항상 **1건**이다
                            (`null` 이면 필수, 그 밖은 형식).
                            """,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "INVALID_PARAMETER",
                                      "message": "요청 값이 올바르지 않습니다.",
                                      "data": [
                                        { "field": "nickname", "message": "2~20자의 영문·한글·숫자·마침표·언더바만 허용합니다." }
                                      ],
                                      "timestamp": "2026-09-16T00:00:00.000Z"
                                    }""")))
    })
    ApiResponse<NicknameUpdateResponse> updateNickname(
            @Parameter(hidden = true) AuthenticatedUser user,
            NicknameUpdateRequest request);

    @Operation(
            summary = "프로필 이미지 업로드 URL 발급 (§4.3)",
            description = """
                    이미지를 **S3 에 직접 올리기 위한** presigned PUT URL 을 받는다.
                    이미지 바이트는 **API 서버를 통과하지 않는다**(설계 원칙 ①).

                    ### 순서
                    1. 이 API 로 `uploadUrl`·`imageKey` 수령
                    2. `PUT {uploadUrl}` — 본문은 이미지 바이너리,
                       `Content-Type`·`Content-Length` 는 **이 요청에 보낸 값과 같아야 한다**
                    3. **§4.4** 로 `imageKey` 를 보내 반영

                    ### 왜 요청 본문이 있나 (원본 명세는 본문 없음)
                    보낸 `contentType`·`contentLength` 가 **서명에 박힌다.** 다른 값으로
                    PUT 하면 S3 가 거부한다. 이게 없으면 발급된 URL 로 **아무 타입이든
                    아무 크기든 올릴 수 있고**, 바이트가 서버를 지나가지 않으므로 사후에
                    확인할 방법도 없다. 어드민 §1.4 가 같은 근거로 이미 그렇게 한다.

                    허용: `image/jpeg` · `image/png` · `image/webp`, 최대 10MB.

                    ### 만료
                    `expiresIn` 초가 지나면 URL 과 함께 **발급 기록도 사라져 §4.4 가 403**
                    이다. 그때는 이 API 부터 다시 부른다.

                    ### 참고 — MY-08 모티프 프레임 프로필
                    "모티프 영상의 한 프레임을 프로필로" 는 **클라이언트가 재생 중 프레임을
                    캡처해 같은 흐름으로 업로드**한다. 서버가 영상에서 프레임을 뽑지 않는다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "업로드 URL",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "SUCCESS",
                                      "code": "SUCCESS",
                                      "message": "요청이 성공했습니다.",
                                      "data": {
                                        "uploadUrl": "https://motif-media.s3.ap-northeast-2.amazonaws.com/profiles/3f9c1a2b.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Signature=...",
                                        "imageKey": "profiles/3f9c1a2b.jpg",
                                        "expiresIn": 300
                                      },
                                      "timestamp": "2026-09-16T00:00:00.000Z"
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "허용되지 않는 `contentType` · 크기 상한 초과",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "INVALID_PARAMETER",
                                      "message": "요청 값이 올바르지 않습니다.",
                                      "data": { "allowedContentTypes": ["image/jpeg", "image/png", "image/webp"] },
                                      "timestamp": "2026-09-16T00:00:00.000Z"
                                    }""")))
    })
    ApiResponse<ProfileImagePresignResponse> presignProfileImage(
            @Parameter(hidden = true) AuthenticatedUser user,
            ProfileImagePresignRequest request);

    @Operation(
            summary = "프로필 이미지 반영 (§4.4)",
            description = """
                    §4.3 → `PUT` 업로드를 **마친 뒤** 호출한다. 서버가 S3 에 오브젝트가
                    실제로 있는지 확인(HEAD)하고 `users.profile_image_key` 를 갱신한다.

                    응답의 `profileImageUrl` 은 **무서명 공개 CDN URL** 이다. 프로필은 공개
                    등급이라 서명이 붙지 않는다 — S3 presigned 로 내보내지 않는 이유는 서명
                    쿼리스트링이 오리진까지 전달돼 **CDN 캐시 적중률을 0% 로 만들기** 때문이다
                    (§1.7 · ADR-0010 D5).

                    설정이 아니라 **수정**이므로 `users.mod_usr_seq` 에 본인 seq 가 기록된다.

                    **이전 이미지는 지우지 않는다.** 1년 `immutable` 로 서빙 중이라 S3 에서
                    지워도 엣지가 계속 내보내고, 무효화까지 걸면 이 API 가 무거워진다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "반영된 프로필 이미지",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "SUCCESS",
                                      "code": "SUCCESS",
                                      "message": "요청이 성공했습니다.",
                                      "data": { "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg" },
                                      "timestamp": "2026-09-16T00:00:00.000Z"
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = """
                            본인에게 발급된 키가 아니다 — **발급 기록 만료도 여기로 떨어진다**
                            (§4.3 의 `expiresIn` 경과). 남의 `imageKey` 를 보낸 경우도 같다.
                            """,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "FORBIDDEN",
                                      "message": "권한이 없습니다.",
                                      "data": null,
                                      "timestamp": "2026-09-16T00:00:00.000Z"
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = """
                            발급은 받았지만 **S3 에 오브젝트가 없다** — 업로드를 하지 않았거나
                            PUT 이 실패했다. `PUT` 부터 다시 한다(URL 이 아직 살아 있으면
                            §4.3 을 다시 부를 필요는 없다).
                            """,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "IMAGE_NOT_UPLOADED",
                                      "message": "업로드되지 않은 이미지입니다.",
                                      "data": null,
                                      "timestamp": "2026-09-16T00:00:00.000Z"
                                    }""")))
    })
    ApiResponse<ProfileImageUpdateResponse> updateProfileImage(
            @Parameter(hidden = true) AuthenticatedUser user,
            ProfileImageUpdateRequest request);
}
