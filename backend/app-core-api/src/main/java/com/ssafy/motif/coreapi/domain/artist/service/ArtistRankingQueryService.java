package com.ssafy.motif.coreapi.domain.artist.service;

import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistRankingResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.RankingFilter;
import com.ssafy.motif.coreapi.domain.artist.ranking.ArtistRankingSnapshot;
import com.ssafy.motif.coreapi.global.media.PublicMediaUrlResolver;
import com.ssafy.motif.coreapi.global.response.PageResponses;
import com.ssafy.motif.domain.core.artist.ArtistCardRow;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.artist.SubscriptionRepository;
import com.ssafy.motif.domain.core.song.SongRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 아티스트 랭킹 조회 (API 명세 §5.1) — S15P21D104-51.
 *
 * <p>홈 Who's On TOP4(§6.2)와 온보딩 인기 아티스트 목록(§3.1)이 {@code size} 만 달리해
 * 이 하나를 재사용한다 — 필드도 캐시 정책도 같아 나눌 실익이 없다.
 *
 * <p><b>순서를 만들지 않는다.</b> 정렬은 배치가 만든 스냅샷이 확정했고
 * ({@link ArtistRankingSnapshot}) 여기는 그 순서에 "내" 상태와 표시용 메타를 붙일 뿐이다.
 * 조회 시점에 점수를 다시 계산하면 요청마다 순위가 흔들린다.
 *
 * <p>{@link ArtistQueryService}(§5.3·§5.4·§5.5) 와 나누어 둔 것은 데이터 출처가 다르기
 * 때문이다 — 저쪽은 DB 만 읽고 이쪽은 Redis 스냅샷이 1차 출처다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArtistRankingQueryService {

    private final ArtistRankingSnapshot snapshot;
    private final ArtistRepository artistRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SongRepository songRepository;
    private final PublicMediaUrlResolver mediaUrlResolver;

    /**
     * §5.1 랭킹 목록.
     *
     * <p><b>스냅샷을 전량 꺼낸다.</b> 구간만 꺼내면 아래 필터에서 걸러진 만큼 페이지가
     * 비고, {@code totalElements} 도 낼 수 없다. 아티스트가 수백 규모라 전량이 싸다.
     *
     * <p><b>DB 조회는 3회로 고정이다</b> — 구독 여부 1회(스냅샷 전체 대상), 메타·대표곡 각 1회
     * (자른 페이지 대상). 항목마다 조회하면 그대로 N+1 이다.
     */
    public PageResponse<ArtistRankingResponse> ranking(Integer userSeq, RankingFilter filter,
                                                       int page, int size) {
        // 순위와 변동을 같은 버전에서 함께 읽는다 — 따로 읽으면 배치가 그 사이에
        // 포인터를 옮겼을 때 서로 다른 버전의 값이 섞인다
        ArtistRankingSnapshot.Snapshot current = snapshot.read();
        if (current.isEmpty()) {
            return PageResponses.of(new PageImpl<>(List.of(), PageResponses.pageable(page, size), 0));
        }
        List<Integer> ranking = current.ranking();

        Set<Integer> subscribed = Set.copyOf(
                subscriptionRepository.findSubscribedArtistSeqs(userSeq, ranking));

        // rank 는 거르기 전에 매긴다 — 필터를 걸어도 "전체 3위" 가 그대로 보여야 한다
        List<Ranked> filtered = withRanks(ranking, current.deltas())
                .stream()
                .filter(ranked -> filter.matches(subscribed.contains(ranked.artistSeq())))
                .toList();

        Pageable pageable = PageResponses.pageable(page, size);
        List<Ranked> pageRows = slice(filtered, pageable);
        Map<Integer, ArtistCardRow> cards = cardsOf(pageRows);
        Map<Integer, SongRepository.ArtistSong> songs = songsOf(pageRows);

        List<ArtistRankingResponse> list = pageRows.stream()
                .map(ranked -> toItem(ranked, cards, songs, subscribed))
                .filter(Objects::nonNull)
                .toList();

        return PageResponses.of(new PageImpl<>(list, pageable, filtered.size()));
    }

    /**
     * 스냅샷 순서에 1-base 순위와 {@code rankDelta} 를 붙인다.
     *
     * <p><b>변동을 여기서 계산하지 않는다</b> *(2026-09-21 — ADR-0016)*. 배치가 적재
     * 시점에 3틱 전 버전과 견줘 적어 둔 값을 그대로 쓴다 — 조회 시점에 이전 버전을
     * 읽으면 요청마다 스냅샷 전량을 한 번 더 읽게 된다.
     *
     * <p>{@code deltas} 에 없으면 <b>신규 진입</b>이다 — 0(변동 없음)과 구분해 {@code null}.
     */
    private List<Ranked> withRanks(List<Integer> ranking, Map<Integer, Integer> deltas) {
        return IntStream.range(0, ranking.size())
                .mapToObj(index -> {
                    Integer artistSeq = ranking.get(index);
                    return new Ranked(artistSeq, index + 1, deltas.get(artistSeq));
                })
                .toList();
    }

    private List<Ranked> slice(List<Ranked> rows, Pageable pageable) {
        int from = (int) Math.min(pageable.getOffset(), rows.size());
        int to = Math.min(from + pageable.getPageSize(), rows.size());
        return rows.subList(from, to);
    }

    /** 자른 페이지 분 메타만 읽는다 — 스냅샷 전량에 대해 읽을 이유가 없다. */
    private Map<Integer, ArtistCardRow> cardsOf(List<Ranked> pageRows) {
        if (pageRows.isEmpty()) {
            return Map.of();
        }
        List<Integer> artistSeqs = pageRows.stream().map(Ranked::artistSeq).toList();
        return artistRepository.findCards(artistSeqs).stream()
                .collect(Collectors.toMap(ArtistCardRow::artistSeq, Function.identity()));
    }

    /** 대표곡도 자른 페이지 분만 읽는다. 챌린지가 없는 아티스트는 맵에 없다. */
    private Map<Integer, SongRepository.ArtistSong> songsOf(List<Ranked> pageRows) {
        if (pageRows.isEmpty()) {
            return Map.of();
        }
        List<Integer> artistSeqs = pageRows.stream().map(Ranked::artistSeq).toList();
        return songRepository.findLatestSongs(artistSeqs).stream()
                .collect(Collectors.toMap(SongRepository.ArtistSong::getArtistSeq, Function.identity()));
    }

    /**
     * 메타가 없으면 그 항목을 뺀다 — 스냅샷이 만들어진 뒤 아티스트가 삭제된 경우다.
     *
     * <p>이름 없는 항목을 내보내면 앱에 빈 카드가 뜨고, 눌러도 §5.3 이 404 다. 최대 한
     * 주기({@code motif.view.interval}) 동안만 생기는 상태라 다음 스냅샷이 알아서
     * 치운다. {@code totalElements} 와 실제 항목 수가 잠깐 어긋나는 것은 감수한다 —
     * 지워진 아티스트를 보여주는 쪽이 훨씬 나쁘다.
     */
    private ArtistRankingResponse toItem(Ranked ranked, Map<Integer, ArtistCardRow> cards,
                                         Map<Integer, SongRepository.ArtistSong> songs,
                                         Set<Integer> subscribed) {
        ArtistCardRow card = cards.get(ranked.artistSeq());
        if (card == null) {
            return null;
        }
        SongRepository.ArtistSong song = songs.get(ranked.artistSeq());
        return new ArtistRankingResponse(
                ranked.rank(),
                ranked.rankDelta(),
                card.artistSeq(),
                card.name(),
                card.groupName(),
                mediaUrlResolver.resolveProfile(card.profileImageKey()),
                subscribed.contains(ranked.artistSeq()),
                song == null ? null : song.getSongTitle(),
                song == null ? null : song.getSingerName());
    }

    /** 스냅샷에서 나온 순위 정보 — 메타가 붙기 전 단계다. */
    private record Ranked(Integer artistSeq, int rank, Integer rankDelta) {
    }
}
