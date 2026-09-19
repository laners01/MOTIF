package com.ssafy.motif.coreapi.domain.user.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.user.UserErrorCode;
import com.ssafy.motif.coreapi.domain.user.dto.UserMeResponse;
import com.ssafy.motif.coreapi.domain.user.dto.UserProfileResponse;
import com.ssafy.motif.coreapi.domain.user.dto.VideoPreviewItem;
import com.ssafy.motif.coreapi.global.media.CloudFrontUrlSigner;
import com.ssafy.motif.coreapi.global.media.MediaSigningProperties;
import com.ssafy.motif.coreapi.global.media.MediaUrlResolver;
import com.ssafy.motif.coreapi.global.media.ProtectedMediaUrlResolver;
import com.ssafy.motif.coreapi.global.media.PublicMediaUrlResolver;
import com.ssafy.motif.coreapi.domain.user.dto.SubscriptionCardItem;
import com.ssafy.motif.domain.core.artist.ArtistCount;
import com.ssafy.motif.domain.core.artist.SubscribedArtistRow;
import com.ssafy.motif.domain.core.artist.SubscriptionRepository;
import com.ssafy.motif.domain.core.asset.MotifAssetRepository;
import com.ssafy.motif.domain.core.pass.MotifPassRepository;
import com.ssafy.motif.domain.core.job.CompositeJob;
import com.ssafy.motif.domain.core.job.CompositeJobRepository;
import com.ssafy.motif.domain.core.motif.MotifResult;
import com.ssafy.motif.domain.core.motif.MotifResultRepository;
import com.ssafy.motif.domain.core.user.User;
import com.ssafy.motif.domain.core.user.UserRepository;
import com.ssafy.motif.domain.core.video.ParticipationVideo;
import com.ssafy.motif.domain.core.video.ParticipationVideoRepository;
import com.ssafy.motif.domain.core.video.Visibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 사용자 조회 (명세 §4.1 원콜 · §4.6 타 사용자 프로필 · §4.8 전체 그리드).
 *
 * <p><b>URL 변환은 목으로 세우지 않는다.</b> 진짜 {@link MediaUrlResolver} 에 진짜
 * 서명기를 물리고(키는 테스트에서 만든다) 나오는 URL 을 본다 — 이 티켓에서 가장
 * 틀리기 쉬운 자리가 "어느 키를 서명하고 어느 키를 안 하는가"이고, 목으로 두면
 * 그 판정이 테스트에서 통째로 빠진다.
 *
 * <p>서명은 로컬 RSA 연산이라 네트워크가 필요 없다.
 */
class UserQueryServiceTest {

    private static final int ME = 7;
    private static final String CDN = "https://cdn.motif.test";
    private static final String JOB_UUID = "9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f";

    private UserRepository userRepository;
    private MotifResultRepository motifResultRepository;
    private CompositeJobRepository compositeJobRepository;
    private ParticipationVideoRepository participationVideoRepository;
    private SubscriptionRepository subscriptionRepository;
    private MotifAssetRepository motifAssetRepository;
    private MotifPassRepository motifPassRepository;
    private UserQueryService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        motifResultRepository = mock(MotifResultRepository.class);
        compositeJobRepository = mock(CompositeJobRepository.class);
        participationVideoRepository = mock(ParticipationVideoRepository.class);
        subscriptionRepository = mock(SubscriptionRepository.class);
        motifAssetRepository = mock(MotifAssetRepository.class);
        motifPassRepository = mock(MotifPassRepository.class);

        CloudFrontUrlSigner signer = new CloudFrontUrlSigner(new MediaSigningProperties(
                "KTESTKEYPAIRID", generatePkcs8Pem(), Duration.ofMinutes(10), Duration.ofHours(6)));
        // 프리픽스 서명(ADR-0012)은 CDN base URL 을 Resource 로 쓴다 — @Value 주입이
        // 없는 단위 테스트라 직접 넣는다
        ReflectionTestUtils.setField(signer, "cdnBaseUrl", CDN);
        MediaUrlResolver resolver = new MediaUrlResolver(
                new PublicMediaUrlResolver(CDN), new ProtectedMediaUrlResolver(CDN, signer), signer);

        service = new UserQueryService(userRepository, motifResultRepository,
                participationVideoRepository, subscriptionRepository,
                motifAssetRepository, motifPassRepository, resolver);

        givenUser(user(null, null));
        given(participationVideoRepository.findMine(anyInt(), any())).willReturn(List.of());
        given(subscriptionRepository.findSubscribed(anyInt())).willReturn(List.of());
        given(participationVideoRepository.countMineByArtists(anyInt(), any())).willReturn(List.of());
        given(motifAssetRepository.countActiveByArtists(any())).willReturn(List.of());
        given(motifAssetRepository.countPurchasedByArtists(anyInt(), any())).willReturn(List.of());
        given(motifPassRepository.findActivePasses(anyInt(), any())).willReturn(List.of());
    }

    @Test
    @DisplayName("프로필 이미지는 무서명 공개 CDN URL 이다 — presign 을 쓰지 않는다")
    void profileImageIsPlainCdnUrl() {
        givenUser(user("profiles/3f9c1a2b.jpg", null));

        assertThat(service.me(ME).profileImageUrl())
                .isEqualTo(CDN + "/profiles/3f9c1a2b.jpg")
                .doesNotContain("Signature=");
    }

    @Test
    @DisplayName("프로필 이미지가 없으면 null 이다")
    void profileImageMayBeNull() {
        assertThat(service.me(ME).profileImageUrl()).isNull();
    }

    @Test
    @DisplayName("대표 모티프 미설정이면 repMotif 는 null 이다")
    void repMotifNullWhenUnset() {
        assertThat(service.me(ME).repMotif()).isNull();
    }

    @Test
    @DisplayName("가리키는 결과물이 삭제·부재면 repMotif 는 null 이다 — FK 가 없는 논리 참조다")
    void repMotifNullWhenDangling() {
        givenUser(user(null, 10L));
        given(motifResultRepository.findByResultSeqAndUserSeqAndDeletedFalse(10L, ME))
                .willReturn(Optional.empty());

        assertThat(service.me(ME).repMotif()).isNull();
    }

    @Test
    @DisplayName("대표 모티프의 videoUrl 은 프리픽스 서명이 붙은 CDN 주소다 (ADR-0012)")
    void repMotifUsesSignedCdnPlaylistAndSignedThumbnail() {
        givenUser(user(null, 10L));
        givenRepMotif("private/motif/" + JOB_UUID + "/thumb.jpg");

        UserMeResponse.RepMotif repMotif = service.me(ME).repMotif();

        assertThat(repMotif.resultSeq()).isEqualTo(10L);
        assertThat(repMotif.videoUrl())
                .startsWith(CDN + "/private/motif/" + JOB_UUID + "/master.m3u8?")
                // 조각까지 통하는 프리픽스 서명이라 Policy 다 — 단일 URL 서명(Expires)이 아니다
                .contains("Policy=", "Signature=", "Key-Pair-Id=KTESTKEYPAIRID");
        assertThat(repMotif.thumbnailUrl())
                .startsWith(CDN + "/private/motif/" + JOB_UUID + "/thumb.jpg?")
                .contains("Signature=", "Key-Pair-Id=KTESTKEYPAIRID");
    }

    @Test
    @DisplayName("영상이 없으면 빈 배열이다 — null 로 내리지 않는다")
    void videosAreEmptyArrayWhenNone() {
        assertThat(service.me(ME).videos()).isEmpty();
    }

    @Test
    @DisplayName("썸네일 등급은 키 프리픽스가 정한다 — visibility 가 아니다")
    void thumbnailGradeFollowsKeyPrefixNotVisibility() {
        given(participationVideoRepository.findMine(anyInt(), any())).willReturn(List.of(
                // 참여 영상은 visibility 와 무관하게 전부 private/ 다(ADR-0013).
                // visibility 로 판정했다면 PUBLIC 쪽에 무서명 URL 이 나가 403 이 된다
                video(88L, "private/challenge/aaa/thumb.jpg", Visibility.PUBLIC, 3400L),
                video(87L, "private/challenge/bbb/thumb.jpg", Visibility.PUBLIC, 120L),
                video(86L, "private/challenge/ccc/thumb.jpg", Visibility.PRIVATE, 7L)));

        List<VideoPreviewItem> videos = service.me(ME).videos();

        assertThat(videos).hasSize(3);
        assertThat(videos).allSatisfy(video ->
                assertThat(video.thumbnailUrl())
                        .startsWith(CDN + "/private/challenge/")
                        .contains("Signature="));
    }

    @Test
    @DisplayName("무서명 프리픽스에는 서명이 붙지 않는다 — 어드민 카탈로그가 그 자리다")
    void unsignedPrefixStaysUnsigned() {
        given(participationVideoRepository.findMine(anyInt(), any())).willReturn(List.of(
                video(88L, "challenges/aaa/thumb.jpg", Visibility.PUBLIC, 3400L)));

        assertThat(service.me(ME).videos().getFirst().thumbnailUrl())
                .isEqualTo(CDN + "/challenges/aaa/thumb.jpg")
                .doesNotContain("Signature=");
    }

    @Test
    @DisplayName("본인 화면이라 PRIVATE 도 내려보내고 visibility 를 그대로 싣는다")
    void privateVideosAreIncluded() {
        given(participationVideoRepository.findMine(anyInt(), any())).willReturn(List.of(
                video(86L, "private/challenge/ccc/thumb.jpg", Visibility.PRIVATE, 7L)));

        assertThat(service.me(ME).videos())
                .singleElement()
                .satisfies(preview -> {
                    assertThat(preview.videoSeq()).isEqualTo(86L);
                    assertThat(preview.viewCount()).isEqualTo(7L);
                    assertThat(preview.visibility()).isEqualTo(Visibility.PRIVATE);
                });
    }

    @Test
    @DisplayName("프리뷰는 9건까지만 요청한다 (3×3)")
    void previewIsCappedAtNine() {
        service.me(ME);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(participationVideoRepository).findMine(any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(UserQueryService.PREVIEW_SIZE);
        assertThat(UserQueryService.PREVIEW_SIZE).isEqualTo(9);
    }

    @Nested
    @DisplayName("§4.6 타 사용자 프로필")
    class Profile {

        private static final int OTHER = 5;

        @Test
        @DisplayName("닉네임·프로필과 구독 아티스트를 함께 내린다")
        void returnsProfileWithSubscriptions() {
            givenTarget(OTHER, "팬123", "profiles/aa11bb22.jpg");
            given(subscriptionRepository.findSubscribed(OTHER)).willReturn(List.of(
                    new SubscribedArtistRow(3, "카리나", "profiles/3f9c1a2b.jpg", "aespa", Instant.now())));

            UserProfileResponse profile = service.profile(OTHER);

            assertThat(profile.userSeq()).isEqualTo(OTHER);
            assertThat(profile.nickname()).isEqualTo("팬123");
            assertThat(profile.profileImageUrl()).isEqualTo(CDN + "/profiles/aa11bb22.jpg");
            assertThat(profile.subscribedArtists()).singleElement().satisfies(artist -> {
                assertThat(artist.artistSeq()).isEqualTo(3);
                assertThat(artist.name()).isEqualTo("카리나");
                assertThat(artist.groupName()).isEqualTo("aespa");
                assertThat(artist.profileImageUrl()).isEqualTo(CDN + "/profiles/3f9c1a2b.jpg");
            });
        }

        @Test
        @DisplayName("솔로 아티스트는 groupName 이 null 이다 — 필드를 빼지는 않는다")
        void soloArtistHasNullGroupName() {
            givenTarget(OTHER, "팬123", null);
            given(subscriptionRepository.findSubscribed(OTHER)).willReturn(List.of(
                    new SubscribedArtistRow(9, "아이유", "profiles/iu.jpg", null, Instant.now())));

            assertThat(service.profile(OTHER).subscribedArtists())
                    .singleElement()
                    .satisfies(artist -> assertThat(artist.groupName()).isNull());
        }

        @Test
        @DisplayName("구독이 0건이면 빈 배열이다")
        void noSubscriptionsIsEmptyArray() {
            givenTarget(OTHER, "팬123", null);

            assertThat(service.profile(OTHER).subscribedArtists()).isEmpty();
        }

        @Test
        @DisplayName("프로필 이미지가 없으면 null 이다 — 아티스트 쪽도 같다")
        void missingImagesAreNull() {
            givenTarget(OTHER, "팬123", null);
            given(subscriptionRepository.findSubscribed(OTHER)).willReturn(List.of(
                    new SubscribedArtistRow(9, "아이유", null, null, Instant.now())));

            UserProfileResponse profile = service.profile(OTHER);

            assertThat(profile.profileImageUrl()).isNull();
            assertThat(profile.subscribedArtists().get(0).profileImageUrl()).isNull();
        }

        @Test
        @DisplayName("없는 사용자는 404")
        void unknownUserIsNotFound() {
            given(userRepository.findById(OTHER)).willReturn(Optional.empty());

            assertNotFound(OTHER);
        }

        @Test
        @DisplayName("삭제된 사용자도 404 — 존재를 숨긴다 (§1.8)")
        void deletedUserIsHidden() {
            User deleted = user(null, null);
            ReflectionTestUtils.setField(deleted, "userSeq", OTHER);
            ReflectionTestUtils.setField(deleted, "deleted", true);
            given(userRepository.findById(OTHER)).willReturn(Optional.of(deleted));

            assertNotFound(OTHER);
        }

        private void givenTarget(int seq, String nickname, String profileImageKey) {
            User target = user(profileImageKey, null);
            ReflectionTestUtils.setField(target, "userSeq", seq);
            ReflectionTestUtils.setField(target, "nickname", nickname);
            given(userRepository.findById(seq)).willReturn(Optional.of(target));
        }

        private void assertNotFound(int seq) {
            assertThatThrownBy(() -> service.profile(seq))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("§4.7 구독 아티스트 카드")
    class Subscriptions {

        @Test
        @DisplayName("구독이 0건이면 빈 배열이고 집계를 아예 돌리지 않는다")
        void noSubscriptionsSkipsAggregates() {
            assertThat(service.subscriptions(ME).list()).isEmpty();

            verify(motifAssetRepository, never()).countActiveByArtists(any());
            verify(motifPassRepository, never()).findActivePasses(anyInt(), any());
        }

        @Test
        @DisplayName("요약과 집계를 한 카드에 담는다")
        void buildsCard() {
            givenSubscribed(subscription(3, "카리나", "aespa", Instant.now().minus(41, ChronoUnit.DAYS)));
            given(participationVideoRepository.countMineByArtists(eq(ME), any()))
                    .willReturn(List.of(new ArtistCount(3, 5L)));
            given(motifAssetRepository.countActiveByArtists(any()))
                    .willReturn(List.of(new ArtistCount(3, 12L)));
            givenPurchased(3, 2L);
            givenActivePass(3, Instant.parse("2026-10-01T00:00:00Z"));

            SubscriptionCardItem card = service.subscriptions(ME).list().get(0);

            assertThat(card.artistSeq()).isEqualTo(3);
            assertThat(card.name()).isEqualTo("카리나");
            assertThat(card.groupName()).isEqualTo("aespa");
            assertThat(card.passActive()).isTrue();
            assertThat(card.passExpiresDttm()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
            assertThat(card.subscribedDays()).isEqualTo(41);
            assertThat(card.challengeCount()).isEqualTo(5);
            assertThat(card.purchasedAssetCount()).isEqualTo(2);
            assertThat(card.totalAssetCount()).isEqualTo(12);
        }

        @Test
        @DisplayName("패스가 없으면 passActive=false 이고 만료는 null 이다")
        void noPassIsInactive() {
            givenSubscribed(subscription(3, "카리나", "aespa", Instant.now()));

            SubscriptionCardItem card = service.subscriptions(ME).list().get(0);

            assertThat(card.passActive()).isFalse();
            assertThat(card.passExpiresDttm()).isNull();
        }

        @Test
        @DisplayName("패스만 있고 산 게 없으면 0/m 이다 — 패스로 열린 것은 세지 않는다")
        void passDoesNotCountAsPurchase() {
            givenSubscribed(subscription(3, "카리나", "aespa", Instant.now()));
            given(motifAssetRepository.countActiveByArtists(any()))
                    .willReturn(List.of(new ArtistCount(3, 12L)));
            givenActivePass(3, Instant.parse("2026-10-01T00:00:00Z"));
            // 구매 조회는 빈 결과 — 패스는 asset_purchases 에 행을 만들지 않는다

            SubscriptionCardItem card = service.subscriptions(ME).list().get(0);

            assertThat(card.passActive()).isTrue();
            assertThat(card.purchasedAssetCount()).isZero();
            assertThat(card.totalAssetCount()).isEqualTo(12);
        }

        @Test
        @DisplayName("판매 중단 에셋을 샀으면 분자가 분모보다 클 수 있다 — 서버가 보정하지 않는다")
        void purchasedMayExceedTotal() {
            givenSubscribed(subscription(3, "카리나", "aespa", Instant.now()));
            given(motifAssetRepository.countActiveByArtists(any()))
                    .willReturn(List.of(new ArtistCount(3, 1L)));
            givenPurchased(3, 3L);

            SubscriptionCardItem card = service.subscriptions(ME).list().get(0);

            assertThat(card.purchasedAssetCount()).isEqualTo(3);
            assertThat(card.totalAssetCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("집계에 없는 아티스트는 0 이다 — group by 는 0건을 주지 않는다")
        void missingAggregatesAreZero() {
            givenSubscribed(subscription(9, "아이유", null, Instant.now()));

            SubscriptionCardItem card = service.subscriptions(ME).list().get(0);

            assertThat(card.groupName()).isNull();
            assertThat(card.challengeCount()).isZero();
            assertThat(card.purchasedAssetCount()).isZero();
            assertThat(card.totalAssetCount()).isZero();
        }

        @Test
        @DisplayName("구독이 몇 건이든 집계 쿼리는 4번뿐이다 — 카드마다 부르면 4N 이다")
        void aggregatesAreBatchedRegardlessOfSize() {
            givenSubscribed(
                    subscription(1, "a", null, Instant.now()),
                    subscription(2, "b", null, Instant.now()),
                    subscription(3, "c", null, Instant.now()),
                    subscription(4, "d", null, Instant.now()),
                    subscription(5, "e", null, Instant.now()));

            assertThat(service.subscriptions(ME).list()).hasSize(5);

            verify(participationVideoRepository, times(1)).countMineByArtists(eq(ME), any());
            verify(motifAssetRepository, times(1)).countActiveByArtists(any());
            verify(motifAssetRepository, times(1)).countPurchasedByArtists(eq(ME), any());
            verify(motifPassRepository, times(1)).findActivePasses(eq(ME), any());
        }

        private void givenSubscribed(SubscribedArtistRow... rows) {
            given(subscriptionRepository.findSubscribed(ME)).willReturn(List.of(rows));
        }

        private SubscribedArtistRow subscription(int artistSeq, String name, String groupName,
                                                 Instant subscribedDttm) {
            return new SubscribedArtistRow(artistSeq, name, "profiles/a.jpg", groupName, subscribedDttm);
        }

        private void givenPurchased(int artistSeq, long count) {
            MotifAssetRepository.ArtistCountProjection row =
                    mock(MotifAssetRepository.ArtistCountProjection.class);
            given(row.getArtistSeq()).willReturn(artistSeq);
            given(row.getCount()).willReturn(count);
            given(motifAssetRepository.countPurchasedByArtists(eq(ME), any()))
                    .willReturn(List.of(row));
        }

        private void givenActivePass(int artistSeq, Instant expiresDttm) {
            MotifPassRepository.ActivePassProjection row =
                    mock(MotifPassRepository.ActivePassProjection.class);
            given(row.getArtistSeq()).willReturn(artistSeq);
            given(row.getExpiresDttm()).willReturn(expiresDttm);
            given(motifPassRepository.findActivePasses(eq(ME), any())).willReturn(List.of(row));
        }
    }

    @Nested
    @DisplayName("§4.8 내 챌린지 그리드")
    class MyVideos {

        @Test
        @DisplayName("건수를 제한하지 않는다 — 전량 반환이다")
        void requestsEverything() {
            service.myVideos(ME);

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(participationVideoRepository).findMine(any(), pageable.capture());
            assertThat(pageable.getValue().isUnpaged()).isTrue();
        }

        @Test
        @DisplayName("0건이면 빈 배열이다 — 봉투는 유지한다")
        void emptyListKeepsEnvelope() {
            assertThat(service.myVideos(ME).list()).isEmpty();
        }

        @Test
        @DisplayName("§4.1 과 같은 항목을 만든다 — 같은 그리드라 한쪽만 달라지면 안 된다")
        void producesSameItemsAsMyPage() {
            given(participationVideoRepository.findMine(anyInt(), any())).willReturn(List.of(
                    video(88L, "private/challenge/aaa/thumb.jpg", Visibility.PUBLIC, 3400L),
                    video(87L, "challenges/bbb/thumb.jpg", Visibility.PUBLIC, 120L),
                    video(86L, "private/challenge/ccc/thumb.jpg", Visibility.PRIVATE, 7L)));

            List<VideoPreviewItem> fromGrid = service.myVideos(ME).list();
            List<VideoPreviewItem> fromMyPage = service.me(ME).videos();

            // 서명에 만료 시각이 들어가 URL 문자열은 호출마다 다를 수 있다 — 등급 판정과
            // 나머지 칸이 같은지를 본다
            assertThat(fromGrid).hasSize(3);
            assertThat(fromGrid).zipSatisfy(fromMyPage, (grid, myPage) -> {
                assertThat(grid.videoSeq()).isEqualTo(myPage.videoSeq());
                assertThat(grid.viewCount()).isEqualTo(myPage.viewCount());
                assertThat(grid.visibility()).isEqualTo(myPage.visibility());
                assertThat(grid.thumbnailUrl().contains("Signature="))
                        .isEqualTo(myPage.thumbnailUrl().contains("Signature="));
            });
        }

        @Test
        @DisplayName("PRIVATE 도 내려보내고 보호 썸네일은 서명된다")
        void includesPrivateWithSignedThumbnail() {
            given(participationVideoRepository.findMine(anyInt(), any())).willReturn(List.of(
                    video(86L, "private/challenge/ccc/thumb.jpg", Visibility.PRIVATE, 7L)));

            assertThat(service.myVideos(ME).list())
                    .singleElement()
                    .satisfies(item -> {
                        assertThat(item.visibility()).isEqualTo(Visibility.PRIVATE);
                        assertThat(item.thumbnailUrl()).contains("Signature=");
                    });
        }
    }

    // ── 도우미 ──────────────────────────────────────────────────────────

    private void givenUser(User user) {
        given(userRepository.findById(ME)).willReturn(Optional.of(user));
    }

    private void givenRepMotif(String thumbnailKey) {
        MotifResult result = newInstance(MotifResult.class);
        ReflectionTestUtils.setField(result, "resultSeq", 10L);
        ReflectionTestUtils.setField(result, "userSeq", ME);
        ReflectionTestUtils.setField(result, "jobSeq", 5L);
        // 재생 주소는 결과물의 video_key 로 만든다 — 잡을 읽지 않는다(ADR-0012)
        ReflectionTestUtils.setField(result, "videoKey",
                "private/motif/" + JOB_UUID + "/master.m3u8");
        ReflectionTestUtils.setField(result, "thumbnailKey", thumbnailKey);
        given(motifResultRepository.findByResultSeqAndUserSeqAndDeletedFalse(10L, ME))
                .willReturn(Optional.of(result));
    }

    private static User user(String profileImageKey, Long repMotifResultSeq) {
        User user = User.signUp();
        ReflectionTestUtils.setField(user, "userSeq", ME);
        ReflectionTestUtils.setField(user, "nickname", "모티프러버");
        ReflectionTestUtils.setField(user, "profileImageKey", profileImageKey);
        ReflectionTestUtils.setField(user, "repMotifResultSeq", repMotifResultSeq);
        return user;
    }

    private static ParticipationVideo video(Long seq, String thumbnailKey,
                                            Visibility visibility, Long viewCount) {
        ParticipationVideo video = newInstance(ParticipationVideo.class);
        ReflectionTestUtils.setField(video, "videoSeq", seq);
        ReflectionTestUtils.setField(video, "thumbnailKey", thumbnailKey);
        ReflectionTestUtils.setField(video, "visibility", visibility);
        ReflectionTestUtils.setField(video, "viewCount", viewCount);
        return video;
    }

    /** 엔티티는 생성 경로가 없다(§8.1·§13 이 만든다) — 읽기용 인스턴스는 리플렉션으로 세운다. */
    private static <T> T newInstance(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(type.getSimpleName() + " 인스턴스를 만들지 못했다", e);
        }
    }

    /** 진짜 서명을 내려면 진짜 키가 필요하다. 테스트마다 새로 만든다 — 저장할 이유가 없다. */
    private static String generatePkcs8Pem() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            byte[] pkcs8 = generator.generateKeyPair().getPrivate().getEncoded();
            return "-----BEGIN PRIVATE KEY-----\n"
                    + Base64.getMimeEncoder().encodeToString(pkcs8)
                    + "\n-----END PRIVATE KEY-----";
        } catch (Exception e) {
            throw new IllegalStateException("테스트용 RSA 키를 만들지 못했다", e);
        }
    }
}
