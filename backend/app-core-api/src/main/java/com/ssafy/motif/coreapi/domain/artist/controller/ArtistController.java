package com.ssafy.motif.coreapi.domain.artist.controller;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.CursorResponse;
import com.ssafy.motif.common.response.ListResponse;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.domain.artist.docs.ArtistControllerDocs;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistCardResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistChallengeResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistDetailResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistMotifPassResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistNoticeResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistRankingResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistSearchResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.RankingFilter;
import com.ssafy.motif.coreapi.domain.artist.dto.SubscriptionResponse;
import com.ssafy.motif.coreapi.domain.artist.service.ArtistMotifPassQueryService;
import com.ssafy.motif.coreapi.domain.artist.service.ArtistQueryService;
import com.ssafy.motif.coreapi.domain.artist.service.ArtistRankingQueryService;
import com.ssafy.motif.coreapi.domain.artist.service.SubscriptionCommandService;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팬 앱 아티스트 API (명세 §5.1 · §5.3 · §5.4 · §5.5 · §5.6 · §5.7 · §5.10 · §11.4).
 * Base URL 은 {@code /api/v1}.
 *
 * <p><b>전부 인증이 필요하다</b>(🔓 아님) — §5.3 은 {@code isSubscribed}·
 * {@code subscribedDays}·{@code pass} 를, §5.5 는 {@code isLiked} 를 호출자 기준으로
 * 채운다. §5.4 만 항목에 내 상태가 없지만 팬 앱 로그인 뒤 화면이라 공개로 열지 않는다.
 *
 * <p><b>매핑만 여기 모이고 서비스는 넷으로 갈린다.</b> 조회는
 * {@link ArtistQueryService}(DB), 랭킹은 {@link ArtistRankingQueryService}(1차 출처가
 * 배치가 만든 Redis 스냅샷), 구독은 {@link SubscriptionCommandService}(유일한 쓰기 —
 * 읽기 전용 서비스에 섞지 않는다), 패스는
 * {@link ArtistMotifPassQueryService}(§11 도메인이라 조회 서비스에 섞지 않는다).
 *
 * <p><b>§5.2 검색이 자동완성을 겸한다</b>(v0.13). v0.7 이 나눴던 §5.9
 * {@code /artists/autocomplete} 는 삭제됐다 — 두 응답의 차이가 {@code isSubscribed} 한
 * 칸뿐이었다. 온보딩 §3.2 도 이 경로를 그대로 부른다.
 *
 * <p>경로가 {@code /api/v1/**} 이고 {@code /api/v1/admin/**} 이 아니므로 springdoc
 * {@code user} 그룹 문서에 자동으로 잡힌다({@code OpenApiConfig}).
 *
 * <p>Swagger 문서는 {@link ArtistControllerDocs} 가 갖는다.
 */
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/artists")
public class ArtistController implements ArtistControllerDocs {

    private final ArtistQueryService artistQueryService;
    private final ArtistRankingQueryService artistRankingQueryService;
    private final SubscriptionCommandService subscriptionCommandService;
    private final ArtistMotifPassQueryService artistMotifPassQueryService;

    /**
     * §5.1 아티스트 랭킹 — <b>offset 페이지</b>(§1.2).
     *
     * <p>§5.5 공지와 달리 커서가 아니다. 랭킹은 배치가 만든 <b>고정 스냅샷</b>을 읽으므로
     * 페이지를 넘기는 동안 항목이 밀려들지 않는다 — 커서가 막으려는 문제가 없다.
     *
     * <p>{@code sort} 를 받지 않는다. 서버 고정 정렬이고, 열면 화이트리스트가 필요해진다.
     */
    @Override
    @GetMapping("/ranking")
    public ApiResponse<PageResponse<ArtistRankingResponse>> ranking(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "ALL") RankingFilter filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                artistRankingQueryService.ranking(user.userSeq(), filter, page, size));
    }

    /**
     * §5.2 아티스트 검색 — <b>자동완성 겸용</b>. 타이핑 중이든 엔터 실행이든 같은 API 다.
     *
     * <p><b>{@code /{artistSeq}} 보다 먼저 선언한다.</b> Spring 은 리터럴 경로를 경로 변수
     * 패턴보다 우선하므로 순서가 동작을 바꾸지는 않지만, 두 매핑이 떨어져 있으면 읽는
     * 사람이 {@code /search} 가 {@code artistSeq} 로 먹히는지 매번 확인하게 된다.
     *
     * <p>{@code page} 가 없다 — 상위 {@code size} 건만 주는 API 다(§5.2). 더 필요하면
     * 검색어를 좁히는 것이 자연스럽다.
     *
     * <p><b>{@code q} 에 {@code @NotBlank} 를 달지 않는다.</b> 파라미터 제약은 클래스에
     * {@code @Validated} 가 있어야 동작하는데 이 레포에는 그 선례가 없고, 전역 advice 도
     * {@code ConstraintViolationException} 을 봉투로 바꾸지 않는다 — 달아 두면 검증이
     * 조용히 통과하거나 봉투 밖 오류가 나간다. 빈 검색어는
     * {@link ArtistQueryService#search} 가 {@code INVALID_PARAMETER} 로 막는다.
     */
    @Override
    @GetMapping("/search")
    public ApiResponse<ListResponse<ArtistSearchResponse>> search(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(artistQueryService.search(user.userSeq(), q, size));
    }

    /**
     * §5.10 전체 아티스트 목록 — <b>구독자 수 내림차순 비페이지 전량</b>.
     *
     * <p>파라미터가 없다. 전량이라 {@code page}·{@code size}·{@code cursor} 가 없고, 정렬도
     * 서버 고정이라 {@code sort} 를 받지 않는다(§5.1 과 같은 근거 — 열면 정렬 칼럼
     * 화이트리스트가 필요해진다).
     *
     * <p>§5.1 랭킹과 경로가 나란하지만 별개 API 다 — 근거는
     * {@link ArtistQueryService#list} javadoc.
     */
    @Override
    @GetMapping
    public ApiResponse<ListResponse<ArtistCardResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(artistQueryService.list(user.userSeq()));
    }

    /** §5.3 아티스트 상세. */
    @Override
    @GetMapping("/{artistSeq}")
    public ApiResponse<ArtistDetailResponse> detail(@AuthenticationPrincipal AuthenticatedUser user,
                                                    @PathVariable Integer artistSeq) {
        return ApiResponse.success(artistQueryService.detail(user.userSeq(), artistSeq));
    }

    /**
     * §5.4 아티스트 챌린지 그리드 — 비페이지 전량(회의 확정 R2).
     *
     * <p>principal 을 받지 않는다 — §5.4 항목에는 "내" 상태 칸이 없다
     * ({@code challengeSeq}·{@code thumbnailUrl}·{@code viewCount} 뿐). 인증 자체는
     * {@code SecurityConfig} 의 {@code anyRequest().authenticated()} 가 걸며, 쓰지도
     * 않을 값을 받아 두면 "여기서 뭔가 사용자별로 갈린다"는 오해만 남는다.
     */
    @Override
    @GetMapping("/{artistSeq}/challenges")
    public ApiResponse<ListResponse<ArtistChallengeResponse>> challenges(
            @PathVariable Integer artistSeq) {
        return ApiResponse.success(artistQueryService.challenges(artistSeq));
    }

    /**
     * §5.6 구독 — 무료 팔로우. <b>패스 구매(§11.4)와 별개</b>다.
     *
     * <p>본문이 없다. 대상은 경로의 {@code artistSeq} 뿐이고 토글이 아니라 <b>단언</b>이라
     * ("구독 상태로 만들어라") 보낼 값이 없다.
     *
     * <p>좋아요와 달리 <b>204 가 아니라 200</b> 이다 — §1.2 의 204 예외는 좋아요 토글
     * 전용이고, 연타 규약(§5.6)이 응답 값으로 UI 를 보정하라고 정했다.
     */
    @Override
    @PostMapping("/{artistSeq}/subscription")
    public ApiResponse<SubscriptionResponse> subscribe(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer artistSeq) {
        return ApiResponse.success(
                subscriptionCommandService.subscribe(user.userSeq(), artistSeq));
    }

    /** §5.7 구독 취소 — 하드 삭제. 멱등이라 구독하지 않은 상태에서 불러도 200 이다. */
    @Override
    @DeleteMapping("/{artistSeq}/subscription")
    public ApiResponse<SubscriptionResponse> unsubscribe(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer artistSeq) {
        return ApiResponse.success(
                subscriptionCommandService.unsubscribe(user.userSeq(), artistSeq));
    }

    /**
     * §5.5 아티스트 공지 목록 — <b>커서</b>(§1.9). 최초 호출은 {@code cursor} 를 생략한다.
     *
     * <p>어드민 목록(§1.5 offset)과 달리 {@code page} 파라미터가 없다 — 공지는 실시간
     * 유입으로 offset 이 밀리는 대상이라 §1.9 가 커서로 지정한 목록이다.
     */
    @Override
    @GetMapping("/{artistSeq}/notices")
    public ApiResponse<CursorResponse<ArtistNoticeResponse>> notices(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer artistSeq,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                artistQueryService.notices(user.userSeq(), artistSeq, cursor, size));
    }

    /**
     * §11.4 아티스트별 모티프 패스 — 구매 화면의 배너·보유 상태·판매 중인 가격.
     *
     * <p><b>§11 도메인이지만 여기 있다.</b> 경로가 {@code /artists/{artistSeq}} 아래이고,
     * 이 레포는 경로가 컨트롤러를 정한다 — 한 경로를 두 클래스가 나눠 가지면 매핑을 찾을
     * 때 두 곳을 봐야 한다. 대신 서비스는 {@link ArtistQueryService} 에 섞지 않고
     * {@link ArtistMotifPassQueryService} 로 따로 뒀다(랭킹·구독과 같은 방식).
     *
     * <p>§5.3 상세의 {@code pass} 칸과 같은 판정을 쓴다 — 보유 여부를 두 곳이 각자
     * 계산하지 않는다.
     */
    @Override
    @GetMapping("/{artistSeq}/motif-pass")
    public ApiResponse<ArtistMotifPassResponse> motifPass(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer artistSeq) {
        return ApiResponse.success(
                artistMotifPassQueryService.motifPass(user.userSeq(), artistSeq));
    }
}
