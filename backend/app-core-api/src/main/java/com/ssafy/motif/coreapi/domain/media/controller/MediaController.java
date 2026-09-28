package com.ssafy.motif.coreapi.domain.media.controller;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.domain.media.MediaPlaylistQueryService;
import com.ssafy.motif.coreapi.domain.media.docs.MediaControllerDocs;
import com.ssafy.motif.coreapi.domain.media.dto.PlaybackSignatureResponse;
import com.ssafy.motif.coreapi.global.media.MediaPlaylistService;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 보호 등급 HLS 재생목록 (API 명세 §1.7).
 *
 * <p><b>레포에서 유일하게 공통 봉투를 쓰지 않는 성공 응답이다.</b> 이 주소를 읽는 것은
 * 안드로이드 앱 코드가 아니라 <b>플레이어</b>다 — ExoPlayer 가 {@code videoUrl} 을 받아
 * 직접 GET 하고 본문을 재생목록으로 파싱한다. JSON 으로 감싸면 재생이 시작되지 않는다.
 * 실패는 {@code @ApiController} 로 평소의 봉투를 그대로 쓴다 — 플레이어는 상태 코드만
 * 보므로 본문 형태가 갈려도 문제가 없고, 사람이 curl 로 두드릴 때 이유가 보인다.
 *
 * <p><b>왜 CDN 이 아니라 여기를 거치는가.</b> HLS 는 재생목록 안에서 조각을 추가로
 * 요청하는데 상대 경로가 쿼리스트링을 상속하지 않아 서명이 따라붙지 않는다. 그래서
 * core-api 가 재생목록을 읽어 주소를 다시 쓴다. <b>오가는 것은 텍스트뿐이다</b> —
 * 수백 바이트~수 KB 이고 영상 바이트는 여전히 CDN 이 낸다(설계 원칙 ①).
 *
 * <pre>
 * GET /api/v1/media/{jobUuid}/master.m3u8        화질별 주소 → core-api
 * GET /api/v1/media/{jobUuid}/{720p}/index.m3u8  조각 주소 → 서명된 CDN 절대 URL
 * </pre>
 *
 * <p><b>공개 등급은 여기로 오지 않는다.</b> 서명이 필요 없는 영상은 조회 API 가 CDN
 * 직통 주소를 내준다 — 굳이 거치면 왕복만 늘고 캐시 키에 서명 파라미터가 섞인다.
 *
 * <p>인증은 {@code SecurityConfig} 의 {@code anyRequest().authenticated()} 가 건다.
 * <b>안드로이드는 플레이어의 재생목록 요청에도 {@code Authorization} 헤더가 실리도록
 * 데이터 소스를 설정해야 한다</b> — 앱의 일반 HTTP 클라이언트가 아니라 ExoPlayer 가
 * 보내는 요청이라 자동으로 붙지 않는다.
 */
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping(MediaPlaylistQueryService.API_BASE_PATH)
public class MediaController implements MediaControllerDocs {

    private final MediaPlaylistQueryService mediaPlaylistQueryService;

    /**
     * 재생 서명 재발급 (ADR-0012).
     *
     * <p>조각 요청이 403 이면 클라이언트가 이걸 불러 새 파라미터를 받아 재시도한다.
     * 재생목록을 다시 받는 예전 복구 경로를 대신한다 — 이제 재생목록도 CDN 에서 오므로
     * 거기에 같은 만료가 걸려 있다.
     *
     * <p><b>POST 인 이유</b>는 발급이 캐시돼선 안 되기 때문이다. 만료된 파라미터가
     * 중간 캐시에서 계속 돌아오면 재생이 살아나지 않는다.
     */
    @Override
    @PostMapping("/{jobUuid}/signature")
    public ApiResponse<PlaybackSignatureResponse> signature(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String jobUuid) {
        var signed = mediaPlaylistQueryService.signature(user.userSeq(), jobUuid);
        return ApiResponse.success(
                new PlaybackSignatureResponse(signed.query(), signed.expiresDttm()));
    }

    /**
     * §1.7 마스터 재생목록.
     *
     * @deprecated ADR-0012 — 응답의 {@code videoUrl} 이 더 이상 여기를 가리키지 않는다.
     *             안드로이드 전환 기간의 안전망으로만 남겨 둔다.
     */
    @Deprecated(since = "ADR-0012")
    @Override
    @GetMapping("/{jobUuid}/master.m3u8")
    public ResponseEntity<String> master(@AuthenticationPrincipal AuthenticatedUser user,
                                         @PathVariable String jobUuid) {
        return playlist(mediaPlaylistQueryService.master(user.userSeq(), jobUuid));
    }

    /** §1.7 화질별 재생목록. */
    @Override
    @GetMapping("/{jobUuid}/{rendition}/index.m3u8")
    public ResponseEntity<String> variant(@AuthenticationPrincipal AuthenticatedUser user,
                                          @PathVariable String jobUuid,
                                          @PathVariable String rendition) {
        return playlist(mediaPlaylistQueryService.variant(user.userSeq(), jobUuid, rendition));
    }

    /**
     * 재생목록 본문 응답.
     *
     * <p><b>캐시하지 않는다.</b> 본문에 박힌 서명이 만료되면 앱이 이 주소를 다시 받아
     * 새 서명을 얻는 것이 복구 경로인데(§1.7), 중간에 캐시가 끼면 만료된 재생목록이
     * 계속 돌아와 재생이 살아나지 않는다.
     */
    private static ResponseEntity<String> playlist(String body) {
        return ResponseEntity.ok()
                .header("Content-Type", MediaPlaylistService.contentType())
                .header("Cache-Control", "no-store")
                .body(body);
    }
}
