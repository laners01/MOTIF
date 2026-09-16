package com.ssafy.motif.coreapi.domain.media.docs;

import com.ssafy.motif.coreapi.global.docs.FailResponseSchema;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

/**
 * 보호 등급 재생목록 API 문서 계약 (명세 §1.7).
 *
 * <p>Swagger 애너테이션만 여기 둔다 — 매핑은 구현 클래스가 갖는다
 * (팀 표준, {@code backend/README.md}).
 */
@Tag(name = "미디어 (MEDIA)", description = "명세 §1.7 — 보호 등급 HLS 재생목록")
public interface MediaControllerDocs {

    @Operation(
            summary = "마스터 재생목록 (§1.7)",
            description = """
                    보호 등급 영상의 **HLS 마스터 재생목록**을 내려준다. 조회 API 가 주는
                    `videoUrl` 이 이 주소이고, **앱 코드가 아니라 플레이어가 직접 GET 한다.**

                    - 성공 본문은 JSON 봉투가 아니라 **m3u8 텍스트**다
                      (`application/vnd.apple.mpegurl`). 실패만 공통 봉투를 쓴다.
                    - 안에 적힌 화질별 주소는 다시 core-api 를 가리킨다 — 자식 재생목록도
                      조각 주소를 서명해야 하기 때문이다.
                    - **공개 등급 영상은 이 주소를 쓰지 않는다.** 조회 API 가 CDN 직통
                      주소를 준다.

                    ### 인증
                    **`Authorization` 헤더가 필요하다.** 플레이어(ExoPlayer)가 보내는
                    요청이라 앱의 기본 HTTP 클라이언트 설정이 적용되지 않는다 — 데이터
                    소스에 헤더를 직접 실어야 한다.

                    ### 만료
                    본문에 박히는 조각 서명은 **10분**이다. 만료된 조각으로 403 을 만나면
                    **이 주소를 다시 받으면 된다** — 새 서명으로 내려준다. 재시도는
                    클라이언트 규약이다. 응답은 `Cache-Control: no-store` 다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "마스터 재생목록",
                    content = @Content(mediaType = "application/vnd.apple.mpegurl",
                            examples = @ExampleObject(value = """
                                    #EXTM3U
                                    #EXT-X-VERSION:7
                                    #EXT-X-STREAM-INF:BANDWIDTH=1400000,RESOLUTION=480x854
                                    /api/v1/media/9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f/480p/index.m3u8
                                    #EXT-X-STREAM-INF:BANDWIDTH=2400000,RESOLUTION=720x1280
                                    /api/v1/media/9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f/720p/index.m3u8
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = """
                            재생할 수 없다 — 잡 없음 · 가공 미완료 · 삭제된 영상 ·
                            **볼 권한 없음**. 권한 거부를 403 으로 가르지 않는다(§1.8):
                            403 은 "그 영상은 존재하고 당신 것이 아니다"를 알려 준다.
                            """,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "result": "FAIL",
                                      "code": "PLAYLIST_NOT_FOUND",
                                      "message": "재생할 수 없는 영상입니다.",
                                      "data": null,
                                      "timestamp": "2026-09-16T00:00:00.000Z"
                                    }""")))
    })
    ResponseEntity<String> master(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "합성 잡 UUID (`composite_jobs.job_uuid`)",
                    example = "9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f") String jobUuid);

    @Operation(
            summary = "화질별 재생목록 (§1.7)",
            description = """
                    마스터가 가리키는 **자식 재생목록**이다. 플레이어가 화질을 고르면
                    이어서 요청한다 — 조각(`data.m4s`) 주소가 **서명된 CDN 절대 URL** 로
                    바뀌어 있어, 영상 바이트는 core-api 를 거치지 않고 CDN 이 낸다.

                    - `rendition` 은 사다리에 있는 값만 받는다: `480p` · `720p` · `1080p`.
                      **그 밖의 값은 404 다** — 이 값이 S3 키에 이어붙으므로 화이트리스트
                      밖은 아무것도 하지 않는다.
                    - 사다리에 있어도 **원본보다 큰 단은 만들어지지 않는다**(원본
                      해상도에서 잘린다). 그런 화질도 404 다.
                    - `EXT-X-MAP`(init 세그먼트)도 함께 서명된다. 빠지면 재생이 시작조차
                      하지 않는다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "화질별 재생목록",
                    content = @Content(mediaType = "application/vnd.apple.mpegurl",
                            examples = @ExampleObject(value = """
                                    #EXTM3U
                                    #EXT-X-VERSION:7
                                    #EXT-X-TARGETDURATION:10
                                    #EXT-X-MAP:URI="https://cdn.motif.io/private/motif/9c1d.../720p/data.m4s?Expires=...&Signature=...&Key-Pair-Id=...",BYTERANGE="1446@0"
                                    #EXTINF:9.000,
                                    #EXT-X-BYTERANGE:284736@1446
                                    https://cdn.motif.io/private/motif/9c1d.../720p/data.m4s?Expires=...&Signature=...&Key-Pair-Id=...
                                    #EXT-X-ENDLIST
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "마스터와 같은 분기 + 사다리에 없는 화질",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FailResponseSchema.class)))
    })
    ResponseEntity<String> variant(
            @Parameter(hidden = true) AuthenticatedUser user,
            @Parameter(description = "합성 잡 UUID (`composite_jobs.job_uuid`)",
                    example = "9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f") String jobUuid,
            @Parameter(description = "화질 — `480p` · `720p` · `1080p`",
                    example = "720p") String rendition);
}
