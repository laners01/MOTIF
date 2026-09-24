package com.ssafy.motif.coreapi.domain.home.service;

import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.domain.home.dto.ActivityItem;
import com.ssafy.motif.coreapi.global.media.PublicMediaUrlResolver;
import com.ssafy.motif.domain.core.artist.SubscribedArtistRow;
import com.ssafy.motif.domain.core.artist.SubscriptionRepository;
import com.ssafy.motif.domain.core.asset.MotifAsset;
import com.ssafy.motif.domain.core.asset.MotifAssetRepository;
import com.ssafy.motif.domain.core.home.ActivityKeyRow;
import com.ssafy.motif.domain.core.home.HomeActivityRepository;
import com.ssafy.motif.domain.core.notice.Notice;
import com.ssafy.motif.domain.core.notice.NoticeImage;
import com.ssafy.motif.domain.core.notice.NoticeImageRepository;
import com.ssafy.motif.domain.core.notice.NoticeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 구독 활동 피드 조립 (명세 §6.3 · S15P21D104-116).
 *
 * <p>여기서 고정하는 것은 <b>이종 항목 조립과 단락</b>이다 — 타입별로 맞는 칸이 채워지는지,
 * UNION 순서가 유지되는지, 필요 없는 조회를 건너뛰는지. UNION 쿼리 자체는
 * {@code HomeActivitiesLocalDbTest} 가 본다.
 */
class HomeActivityQueryServiceTest {

    private static final Integer ME = 5;
    private static final String CDN = "https://cdn.motif.io";
    private static final Instant T1 = Instant.parse("2026-09-02T05:00:00Z");
    private static final Instant T2 = Instant.parse("2026-09-02T04:00:00Z");

    private SubscriptionRepository subscriptionRepository;
    private HomeActivityRepository homeActivityRepository;
    private MotifAssetRepository motifAssetRepository;
    private NoticeRepository noticeRepository;
    private NoticeImageRepository noticeImageRepository;
    private HomeActivityQueryService service;

    @BeforeEach
    void setUp() {
        subscriptionRepository = mock(SubscriptionRepository.class);
        homeActivityRepository = mock(HomeActivityRepository.class);
        motifAssetRepository = mock(MotifAssetRepository.class);
        noticeRepository = mock(NoticeRepository.class);
        noticeImageRepository = mock(NoticeImageRepository.class);
        service = new HomeActivityQueryService(subscriptionRepository, homeActivityRepository,
                motifAssetRepository, noticeRepository, noticeImageRepository,
                new PublicMediaUrlResolver(CDN, "profiles/default.png"));

        // 기본값 — 각 테스트가 필요한 것만 덮어쓴다
        given(motifAssetRepository.findAllById(any())).willReturn(List.of());
        given(noticeRepository.findAllById(any())).willReturn(List.of());
        given(noticeImageRepository.findByNoticeSeqInOrderByNoticeSeqAscDisplayOrderAsc(any()))
                .willReturn(List.of());
        given(noticeRepository.findLikedNoticeSeqs(any(), any())).willReturn(List.of());
    }

    private static ActivityKeyRow key(String type, Integer seq, Instant regDttm) {
        return new ActivityKeyRow() {
            @Override
            public String getType() {
                return type;
            }

            @Override
            public Integer getSeq() {
                return seq;
            }

            @Override
            public Instant getRegDttm() {
                return regDttm;
            }
        };
    }

    private void givenSubscribed(SubscribedArtistRow... rows) {
        given(subscriptionRepository.findSubscribed(ME)).willReturn(List.of(rows));
    }

    private static SubscribedArtistRow artist(Integer artistSeq, String name, String groupName) {
        return new SubscribedArtistRow(artistSeq, name, "profiles/" + artistSeq + ".jpg",
                groupName, T1);
    }

    private void givenKeys(long total, ActivityKeyRow... rows) {
        given(homeActivityRepository.findActivities(anyCollection(), any()))
                .willReturn(new PageImpl<>(List.of(rows), PageRequest.of(0, 20), total));
    }

    /** PK 가 IDENTITY 라 {@code register} 팩터리로는 seq 를 줄 수 없다. */
    private static MotifAsset asset(Integer assetSeq, Integer artistSeq) {
        MotifAsset asset = mock(MotifAsset.class);
        given(asset.getAssetSeq()).willReturn(assetSeq);
        given(asset.getArtistSeq()).willReturn(artistSeq);
        given(asset.getTitle()).willReturn("9월 신규 에셋");
        given(asset.getThumbnailKey()).willReturn("assets/2b7f1c93.jpg");
        given(asset.getDurationMs()).willReturn(9800);
        return asset;
    }

    private static Notice notice(Integer noticeSeq, Integer artistSeq) {
        Notice notice = mock(Notice.class);
        given(notice.getNoticeSeq()).willReturn(noticeSeq);
        given(notice.getArtistSeq()).willReturn(artistSeq);
        given(notice.getContent()).willReturn("컴백 소식 전체 본문...");
        given(notice.getLikeCount()).willReturn(200);
        given(notice.getCommentCount()).willReturn(14);
        return notice;
    }

    private static NoticeImage image(Integer noticeSeq, String key, short displayOrder) {
        NoticeImage image = mock(NoticeImage.class);
        given(image.getNoticeSeq()).willReturn(noticeSeq);
        given(image.getImageKey()).willReturn(key);
        given(image.getDisplayOrder()).willReturn(displayOrder);
        return image;
    }

    @Test
    @DisplayName("구독이 0건이면 빈 페이지다 — 목록 쿼리를 돌리지 않는다")
    void noSubscriptionShortCircuits() {
        givenSubscribed();

        PageResponse<ActivityItem> response = service.activities(ME, 0, 20);

        assertThat(response.list()).isEmpty();
        assertThat(response.totalElements()).isZero();
        // 빈 in () 은 Postgres 문법 오류다 — 쿼리까지 가면 500 이 된다
        verify(homeActivityRepository, never()).findActivities(anyCollection(), any());
    }

    @Test
    @DisplayName("MOTIF_ASSET 항목은 asset 만 채우고 notice 는 비운다")
    void assetItemHasNoNotice() {
        givenSubscribed(artist(3, "카리나", "aespa"));
        givenKeys(1, key("MOTIF_ASSET", 21, T1));
        // mock 생성을 given(...) 인자 안에서 하면 Mockito 가 스터빙 중첩으로 본다
        List<MotifAsset> assets = List.of(asset(21, 3));
        given(motifAssetRepository.findAllById(List.of(21))).willReturn(assets);

        ActivityItem item = service.activities(ME, 0, 20).list().getFirst();

        assertThat(item.type()).isEqualTo(ActivityItem.ActivityType.MOTIF_ASSET);
        assertThat(item.notice()).isNull();
        assertThat(item.regDttm()).isEqualTo(T1);
        assertThat(item.asset().assetSeq()).isEqualTo(21);
        assertThat(item.asset().title()).isEqualTo("9월 신규 에셋");
        assertThat(item.asset().thumbnailUrl()).isEqualTo(CDN + "/assets/2b7f1c93.jpg");
        assertThat(item.asset().durationMs()).isEqualTo(9800);
    }

    @Test
    @DisplayName("NOTICE 항목은 본문·이미지·좋아요를 채우고 asset 은 비운다")
    void noticeItemHasNoAsset() {
        givenSubscribed(artist(7, "윈터", "aespa"));
        givenKeys(1, key("NOTICE", 5, T2));
        List<Notice> notices = List.of(notice(5, 7));
        List<NoticeImage> images = List.of(
                image(5, "notices/a.jpg", (short) 0),
                image(5, "notices/b.jpg", (short) 1));
        given(noticeRepository.findAllById(List.of(5))).willReturn(notices);
        given(noticeImageRepository.findByNoticeSeqInOrderByNoticeSeqAscDisplayOrderAsc(List.of(5)))
                .willReturn(images);
        given(noticeRepository.findLikedNoticeSeqs(ME, List.of(5))).willReturn(List.of(5));

        ActivityItem item = service.activities(ME, 0, 20).list().getFirst();

        assertThat(item.type()).isEqualTo(ActivityItem.ActivityType.NOTICE);
        assertThat(item.asset()).isNull();
        assertThat(item.notice().content()).isEqualTo("컴백 소식 전체 본문...");
        assertThat(item.notice().likeCount()).isEqualTo(200);
        assertThat(item.notice().commentCount()).isEqualTo(14);
        assertThat(item.notice().isLiked()).isTrue();
        assertThat(item.notice().images())
                .extracting(ActivityItem.Image::imageUrl, ActivityItem.Image::displayOrder)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(CDN + "/notices/a.jpg", (short) 0),
                        org.assertj.core.groups.Tuple.tuple(CDN + "/notices/b.jpg", (short) 1));
    }

    @Test
    @DisplayName("artist 요약은 두 타입이 같은 모양이다 — groupName 을 포함한다")
    void bothTypesShareTheArtistSummary() {
        givenSubscribed(artist(3, "카리나", "aespa"), artist(9, "솔로", null));
        givenKeys(2, key("MOTIF_ASSET", 21, T1), key("NOTICE", 5, T2));
        List<MotifAsset> assets = List.of(asset(21, 3));
        List<Notice> notices = List.of(notice(5, 9));
        given(motifAssetRepository.findAllById(List.of(21))).willReturn(assets);
        given(noticeRepository.findAllById(List.of(5))).willReturn(notices);

        List<ActivityItem> list = service.activities(ME, 0, 20).list();

        assertThat(list.get(0).artist()).isEqualTo(new ActivityItem.Artist(
                3, "카리나", "aespa", CDN + "/profiles/3.jpg"));
        // 솔로 아티스트는 groupName 이 null 이다 — 칸 자체는 있다
        assertThat(list.get(1).artist()).isEqualTo(new ActivityItem.Artist(
                9, "솔로", null, CDN + "/profiles/9.jpg"));
    }

    @Test
    @DisplayName("UNION 이 준 순서를 그대로 유지한다 — 타입별 조회가 순서를 흩뜨리지 않는다")
    void keepsUnionOrder() {
        givenSubscribed(artist(3, "카리나", "aespa"));
        givenKeys(4,
                key("NOTICE", 5, T1),
                key("MOTIF_ASSET", 21, T2),
                key("NOTICE", 4, T2),
                key("MOTIF_ASSET", 20, T2));
        // 배치 조회가 입력과 다른 순서로 주는 상황을 흉내 낸다
        List<MotifAsset> assets = List.of(asset(20, 3), asset(21, 3));
        List<Notice> notices = List.of(notice(4, 3), notice(5, 3));
        given(motifAssetRepository.findAllById(List.of(21, 20))).willReturn(assets);
        given(noticeRepository.findAllById(List.of(5, 4))).willReturn(notices);

        List<ActivityItem> list = service.activities(ME, 0, 20).list();

        assertThat(list).extracting(ActivityItem::type).containsExactly(
                ActivityItem.ActivityType.NOTICE,
                ActivityItem.ActivityType.MOTIF_ASSET,
                ActivityItem.ActivityType.NOTICE,
                ActivityItem.ActivityType.MOTIF_ASSET);
    }

    @Test
    @DisplayName("공지가 없는 페이지에서는 이미지·좋아요를 조회하지 않는다")
    void skipsNoticeOnlyQueriesWhenPageHasNoNotice() {
        givenSubscribed(artist(3, "카리나", "aespa"));
        givenKeys(1, key("MOTIF_ASSET", 21, T1));
        List<MotifAsset> assets = List.of(asset(21, 3));
        given(motifAssetRepository.findAllById(List.of(21))).willReturn(assets);

        service.activities(ME, 0, 20);

        verify(noticeImageRepository, never())
                .findByNoticeSeqInOrderByNoticeSeqAscDisplayOrderAsc(any());
        verify(noticeRepository, never()).findLikedNoticeSeqs(any(), any());
    }

    @Test
    @DisplayName("참조가 끊긴 줄은 빠지되 totalElements 는 count 쿼리 값을 지킨다")
    void skipsDanglingRowsButKeepsTotal() {
        givenSubscribed(artist(3, "카리나", "aespa"));
        // 키 조회 뒤 비활성화된 에셋 — 배치 조회가 못 찾는다
        givenKeys(37, key("MOTIF_ASSET", 21, T1), key("NOTICE", 5, T2));
        List<Notice> notices = List.of(notice(5, 3));
        given(noticeRepository.findAllById(List.of(5))).willReturn(notices);

        PageResponse<ActivityItem> response = service.activities(ME, 0, 20);

        assertThat(response.list()).hasSize(1);
        assertThat(response.list().getFirst().type())
                .isEqualTo(ActivityItem.ActivityType.NOTICE);
        // 총계를 줄이면 페이지 수가 흔들린다
        assertThat(response.totalElements()).isEqualTo(37);
    }

    @Test
    @DisplayName("size 를 100 으로 자른다 (§1.5 목록 공통 상한)")
    void clampsSize() {
        givenSubscribed(artist(3, "카리나", "aespa"));
        givenKeys(0);

        service.activities(ME, 0, 500);

        verify(homeActivityRepository).findActivities(anyCollection(),
                org.mockito.ArgumentMatchers.argThat(pageable -> pageable.getPageSize() == 100));
    }
}
