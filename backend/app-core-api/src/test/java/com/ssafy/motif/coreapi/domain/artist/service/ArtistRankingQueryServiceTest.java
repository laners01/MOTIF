package com.ssafy.motif.coreapi.domain.artist.service;

import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistRankingResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.RankingFilter;
import com.ssafy.motif.coreapi.domain.artist.ranking.ArtistRankingSnapshot;
import com.ssafy.motif.coreapi.global.media.PublicMediaUrlResolver;
import com.ssafy.motif.domain.core.artist.ArtistCardRow;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.artist.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 아티스트 랭킹 조회 (명세 §5.1 · S15P21D104-51).
 *
 * <p>스냅샷을 목으로 세운다 — 순위를 <b>만드는</b> 쪽은 배치와
 * {@code RankingScore} 의 몫이고, 여기서 고정하는 것은 그 순서에 {@code rankDelta}·
 * 필터·페이지·"내" 상태를 어떻게 얹는가다.
 */
class ArtistRankingQueryServiceTest {

    private static final int ME = 7;

    private static final int KARINA = 3;
    private static final int WINTER = 11;
    private static final int NINGNING = 21;
    private static final int GISELLE = 42;

    private ArtistRankingSnapshot snapshot;
    private ArtistRepository artistRepository;
    private SubscriptionRepository subscriptionRepository;
    private ArtistRankingQueryService service;

    @BeforeEach
    void setUp() {
        snapshot = mock(ArtistRankingSnapshot.class);
        artistRepository = mock(ArtistRepository.class);
        subscriptionRepository = mock(SubscriptionRepository.class);

        given(snapshot.previousRanks()).willReturn(Map.of());
        given(subscriptionRepository.findSubscribedArtistSeqs(anyInt(), any()))
                .willReturn(List.of());
        // 요청한 seq 를 그대로 카드로 돌려준다 — 메타 자체는 이 테스트의 관심사가 아니다.
        // null 을 방어하는 것은 Mockito 함정 때문이다: 아래 테스트들이 given(findCards(any()))
        // 로 다시 스텁할 때 any() 가 null 인 채로 이 Answer 가 한 번 호출된다
        given(artistRepository.findCards(any())).willAnswer(invocation -> {
            Collection<Integer> artistSeqs = invocation.getArgument(0);
            return artistSeqs == null
                    ? List.of()
                    : artistSeqs.stream().map(ArtistRankingQueryServiceTest::card).toList();
        });

        service = new ArtistRankingQueryService(snapshot, artistRepository,
                subscriptionRepository, new PublicMediaUrlResolver("https://cdn.motif.io"));
    }

    private static ArtistCardRow card(Integer artistSeq) {
        return new ArtistCardRow(artistSeq, "아티스트" + artistSeq,
                "profiles/" + artistSeq + ".jpg", "aespa");
    }

    private PageResponse<ArtistRankingResponse> ranking(RankingFilter filter, int page, int size) {
        return service.ranking(ME, filter, page, size);
    }

    @Nested
    @DisplayName("순위")
    class Rank {

        @Test
        @DisplayName("스냅샷 순서대로 1-base 순위를 매긴다")
        void numbersFromOne() {
            given(snapshot.current()).willReturn(List.of(KARINA, WINTER, NINGNING));

            PageResponse<ArtistRankingResponse> response = ranking(RankingFilter.ALL, 0, 20);

            assertThat(response.list()).extracting(ArtistRankingResponse::rank)
                    .containsExactly(1, 2, 3);
            assertThat(response.list()).extracting(ArtistRankingResponse::artistSeq)
                    .containsExactly(KARINA, WINTER, NINGNING);
        }

        @Test
        @DisplayName("점수를 다시 계산하지 않는다 — 순서는 스냅샷이 정한다")
        void doesNotRecompute() {
            given(snapshot.current()).willReturn(List.of(NINGNING, KARINA, WINTER));

            assertThat(ranking(RankingFilter.ALL, 0, 20).list())
                    .extracting(ArtistRankingResponse::artistSeq)
                    .containsExactly(NINGNING, KARINA, WINTER);
        }
    }

    @Nested
    @DisplayName("rankDelta")
    class Delta {

        @Test
        @DisplayName("상승이 양수, 하락이 음수다")
        void upIsPositiveDownIsNegative() {
            given(snapshot.current()).willReturn(List.of(KARINA, WINTER, NINGNING));
            // 직전: WINTER 1위 · KARINA 3위 → KARINA 는 3→1(+2), WINTER 는 1→2(-1)
            given(snapshot.previousRanks()).willReturn(Map.of(WINTER, 1, NINGNING, 2, KARINA, 3));

            assertThat(ranking(RankingFilter.ALL, 0, 20).list())
                    .extracting(ArtistRankingResponse::rankDelta)
                    .containsExactly(2, -1, -1);
        }

        @Test
        @DisplayName("직전 스냅샷에 없으면 null 이다 — 변동 없음(0)과 구분한다")
        void newEntryIsNull() {
            given(snapshot.current()).willReturn(List.of(KARINA, WINTER));
            given(snapshot.previousRanks()).willReturn(Map.of(KARINA, 1));

            assertThat(ranking(RankingFilter.ALL, 0, 20).list())
                    .extracting(ArtistRankingResponse::rankDelta)
                    .containsExactly(0, null);
        }
    }

    @Nested
    @DisplayName("filter")
    class Filter {

        @BeforeEach
        void subscribedToKarinaAndNingning() {
            given(snapshot.current()).willReturn(List.of(KARINA, WINTER, NINGNING, GISELLE));
            given(subscriptionRepository.findSubscribedArtistSeqs(anyInt(), any()))
                    .willReturn(List.of(KARINA, NINGNING));
        }

        @Test
        @DisplayName("SUBSCRIBED 는 구독 중인 것만 남긴다")
        void subscribedOnly() {
            PageResponse<ArtistRankingResponse> response = ranking(RankingFilter.SUBSCRIBED, 0, 20);

            assertThat(response.list()).extracting(ArtistRankingResponse::artistSeq)
                    .containsExactly(KARINA, NINGNING);
            assertThat(response.list()).allMatch(ArtistRankingResponse::isSubscribed);
        }

        @Test
        @DisplayName("UNSUBSCRIBED 는 구독하지 않은 것만 남긴다")
        void unsubscribedOnly() {
            assertThat(ranking(RankingFilter.UNSUBSCRIBED, 0, 20).list())
                    .extracting(ArtistRankingResponse::artistSeq)
                    .containsExactly(WINTER, GISELLE);
        }

        @Test
        @DisplayName("걸러도 rank 는 전체 기준을 유지한다 — '내 구독 중 1위' 가 아니다")
        void rankStaysGlobal() {
            assertThat(ranking(RankingFilter.UNSUBSCRIBED, 0, 20).list())
                    .extracting(ArtistRankingResponse::rank)
                    .containsExactly(2, 4);
        }

        @Test
        @DisplayName("totalElements 는 거른 뒤 개수다")
        void totalIsAfterFiltering() {
            assertThat(ranking(RankingFilter.SUBSCRIBED, 0, 20).totalElements()).isEqualTo(2);
            assertThat(ranking(RankingFilter.ALL, 0, 20).totalElements()).isEqualTo(4);
        }

        @Test
        @DisplayName("구독 조회는 항목 수와 무관하게 1회다")
        void subscriptionIsQueriedOnce() {
            ranking(RankingFilter.ALL, 0, 20);

            verify(subscriptionRepository, times(1)).findSubscribedArtistSeqs(anyInt(), any());
        }
    }

    @Nested
    @DisplayName("페이지")
    class Paging {

        @BeforeEach
        void fiveArtists() {
            given(snapshot.current()).willReturn(List.of(KARINA, WINTER, NINGNING, GISELLE, 99));
        }

        @Test
        @DisplayName("자른 뒤에도 rank 는 이어진다")
        void secondPageContinuesRanks() {
            PageResponse<ArtistRankingResponse> response = ranking(RankingFilter.ALL, 1, 2);

            assertThat(response.list()).extracting(ArtistRankingResponse::rank)
                    .containsExactly(3, 4);
            assertThat(response.page()).isEqualTo(1);
            assertThat(response.hasNext()).isTrue();
            assertThat(response.hasPrevious()).isTrue();
        }

        @Test
        @DisplayName("홈 Who's On 은 size=4 로 같은 API 를 쓴다")
        void topFourForHome() {
            assertThat(ranking(RankingFilter.ALL, 0, 4).list()).hasSize(4);
        }

        @Test
        @DisplayName("size 는 100 을 넘지 못한다")
        void sizeIsCappedAtHundred() {
            assertThat(ranking(RankingFilter.ALL, 0, 500).size()).isEqualTo(100);
        }

        @Test
        @DisplayName("범위를 넘는 페이지는 빈 목록이다 — 예외가 아니다")
        void pageBeyondEndIsEmpty() {
            PageResponse<ArtistRankingResponse> response = ranking(RankingFilter.ALL, 9, 20);

            assertThat(response.list()).isEmpty();
            assertThat(response.totalElements()).isEqualTo(5);
        }

        @Test
        @DisplayName("메타는 그 페이지 분량만 읽는다 — 스냅샷 전량을 읽지 않는다")
        @SuppressWarnings("unchecked")
        void readsMetaForPageOnly() {
            ranking(RankingFilter.ALL, 0, 2);

            ArgumentCaptor<Collection<Integer>> captor = ArgumentCaptor.forClass(Collection.class);
            verify(artistRepository).findCards(captor.capture());
            assertThat(captor.getValue()).containsExactly(KARINA, WINTER);
        }
    }

    @Nested
    @DisplayName("빈 스냅샷·낡은 스냅샷")
    class Degraded {

        @Test
        @DisplayName("스냅샷이 비어 있으면 빈 목록이다 — 배치 전 상태라 에러가 아니다")
        void emptySnapshotIsEmptyPage() {
            given(snapshot.current()).willReturn(List.of());

            PageResponse<ArtistRankingResponse> response = ranking(RankingFilter.ALL, 0, 20);

            assertThat(response.list()).isEmpty();
            assertThat(response.totalElements()).isZero();
            assertThat(response.hasNext()).isFalse();
            verify(artistRepository, times(0)).findCards(any());
        }

        @Test
        @DisplayName("스냅샷에는 있는데 지워진 아티스트는 항목에서 뺀다 — 눌러도 404 인 빈 카드를 만들지 않는다")
        void dropsArtistDeletedAfterSnapshot() {
            given(snapshot.current()).willReturn(List.of(KARINA, WINTER, NINGNING));
            given(artistRepository.findCards(any()))
                    .willReturn(List.of(card(KARINA), card(NINGNING)));

            assertThat(ranking(RankingFilter.ALL, 0, 20).list())
                    .extracting(ArtistRankingResponse::artistSeq)
                    .containsExactly(KARINA, NINGNING);
        }
    }

    @Test
    @DisplayName("프로필 이미지는 공개 URL 이다 — 서명하지 않는다(§1.7)")
    void profileImageIsPublicUrl() {
        given(snapshot.current()).willReturn(List.of(KARINA));

        assertThat(ranking(RankingFilter.ALL, 0, 20).list().getFirst().profileImageUrl())
                .isEqualTo("https://cdn.motif.io/profiles/3.jpg")
                .doesNotContain("Signature", "Policy");
    }

    @Test
    @DisplayName("프로필 키가 없으면 URL 도 null 이다")
    void nullKeyGivesNullUrl() {
        given(snapshot.current()).willReturn(List.of(KARINA));
        given(artistRepository.findCards(any()))
                .willReturn(List.of(new ArtistCardRow(KARINA, "카리나", null, null)));

        ArtistRankingResponse item = ranking(RankingFilter.ALL, 0, 20).list().getFirst();
        assertThat(item.profileImageUrl()).isNull();
        assertThat(item.groupName()).isNull();
    }
}
