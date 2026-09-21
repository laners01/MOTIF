package com.ssafy.motif.coreapi.domain.home.service;

import com.ssafy.motif.common.response.CursorResponse;
import com.ssafy.motif.coreapi.domain.home.dto.HotVideoItem;
import com.ssafy.motif.coreapi.domain.video.ranking.VideoRankingSnapshot;
import com.ssafy.motif.coreapi.global.media.MediaUrlResolver;
import com.ssafy.motif.coreapi.global.response.Cursors;
import com.ssafy.motif.domain.core.video.HotVideoRow;
import com.ssafy.motif.domain.core.video.ParticipationVideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 홈 HOT 참여 영상 조회 (API 명세 §6.1).
 *
 * <p><b>점수를 모른다.</b> 순위는 {@code VideoRankingSnapshotJob} 이 5분마다 확정해
 * Redis 에 적어 두고, 여기는 그 순서를 자를 뿐이다. 정렬을 조회 시점에 하면 배치
 * 교체와 무관하게 페이지마다 순서가 흔들린다.
 *
 * <p>읽기 전용 트랜잭션이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HotVideoQueryService {

    /** §1.9 size 상한 — 커서 목록 공통. */
    private static final int MAX_SIZE = 100;

    private final VideoRankingSnapshot snapshot;
    private final ParticipationVideoRepository participationVideoRepository;

    /**
     * 주소는 전부 여기서 만든다 — 재생목록은 {@code resolvePlaylist}, 썸네일은
     * {@code resolve}.
     *
     * <p><b>{@code PublicMediaUrlResolver} 를 직접 쓰지 않는다.</b> 예전에는 "HOT 에는
     * 공개 사본이 준비된 영상만 오른다" 는 전제로 무서명 URL 을 만들었는데, 공개 사본
     * 트리가 폐기되면서(ADR-0013) 참여 영상이 전부 보호 등급이 됐다 — 그대로 두면 HOT
     * 전량이 무서명 URL 로 나가 CDN 이 403 을 준다.
     *
     * <p><b>둘을 나눠 부르는 이유는 HLS 가 단일 파일이 아니기 때문이다.</b> 재생목록에
     * 단일 파일 서명({@code resolve})을 붙이면 재생목록만 열리고 조각 요청이 403 이
     * 된다({@code VideoItemAssembler} 와 같은 판단).
     */
    private final MediaUrlResolver mediaUrlResolver;

    /**
     * §6.1 HOT 목록 — 커서, 스냅샷 순서 고정.
     *
     * <p><b>커서가 버전을 들고 다닌다.</b> 최초 호출은 현재 버전의 1위부터 시작하고,
     * 이어지는 호출은 커서에 박힌 버전을 계속 읽는다 — 그 사이 배치가 순위를 갈아도
     * 진행 중인 스크롤은 같은 순서를 본다(§1.9 구현 노트). 새 순위는 커서 없이 다시
     * 부를 때(당겨서 새로고침) 시작된다.
     *
     * <p>스냅샷이 없거나 커서의 버전이 보존 기간을 넘겼으면 <b>빈 목록</b>이다. 현재
     * 순위의 1위로 되돌리지 않는다 — 무한 스크롤이 원인 없이 맨 위로 점프한다.
     *
     * <p>{@code limit + 1} 건을 읽어 넘치면 잘라내고 그 사실을 {@code hasNext} 로
     * 쓴다 — 총 개수를 세지 않는 것이 커서 방식의 요점이다.
     *
     * <p><b>{@code userSeq} 를 받는다</b> *(2026-09-21 — ADR-0015)*. 요청자의 "관심
     * 없음"(§8.6)을 목록에서 빼기 위해서다. 순서 자체는 여전히 사용자와 무관한 스냅샷이고,
     * 빠진 자리는 {@code rank} 를 그대로 둔 채 건너뛴다 — 그래서 <b>같은 커서라도 사람마다
     * 페이지 항목 수가 다를 수 있다</b>(§1.8 비노출과 같은 취급).
     */
    public CursorResponse<HotVideoItem> hotVideos(Integer userSeq, String cursor, int size) {
        int limit = Math.clamp(size, 1, MAX_SIZE);

        Cursors.Snapshot at = Cursors.decodeSnapshot(cursor);

        // 삼항으로 합치지 않는다 — Long 과 long 을 섞으면 양쪽이 long 으로 언박싱돼
        // 스냅샷이 없을 때(null) NPE 가 난다. 조건식이 null 을 삼키는 자리다
        long version;
        int afterRank;
        if (at == null) {
            Long current = snapshot.currentVersion();
            if (current == null) {
                return CursorResponse.forward(List.of(), null);
            }
            version = current;
            afterRank = 0;
        } else {
            version = at.version();
            afterRank = at.lastRank();
        }

        List<VideoRankingSnapshot.Ranked> ranked = snapshot.page(version, afterRank, limit + 1);
        if (ranked.isEmpty()) {
            return CursorResponse.forward(List.of(), null);
        }

        boolean hasNext = ranked.size() > limit;
        List<VideoRankingSnapshot.Ranked> page = hasNext ? ranked.subList(0, limit) : ranked;

        List<HotVideoItem> list = cards(userSeq, page);

        // 마지막 순위는 §1.8 로 빠진 항목까지 포함한 자리다 — list 의 마지막을 쓰면
        // 그 항목들을 다음 페이지에서 다시 읽는다
        String nextCursor = hasNext
                ? Cursors.encodeSnapshot(version, page.getLast().rank())
                : null;
        return CursorResponse.forward(list, nextCursor);
    }

    /**
     * 순위 묶음 → 카드 목록. <b>조회 한 번</b>이다 — 항목마다 읽으면 한 페이지에
     * 그만큼 왕복이 늘어난다.
     *
     * <p>{@code in} 조회는 입력 순서를 보장하지 않으므로 스냅샷 순서로 다시 세운다.
     * 그 사이 비노출로 바뀌어 빠진 영상은 조용히 건너뛴다 — 순위 번호는 그대로 둔다
     * ({@code HotVideoItem.rank} javadoc).
     */
    private List<HotVideoItem> cards(Integer userSeq, List<VideoRankingSnapshot.Ranked> page) {
        List<Long> videoSeqs = page.stream().map(VideoRankingSnapshot.Ranked::videoSeq).toList();
        Map<Long, HotVideoRow> rows = participationVideoRepository.findHotCards(videoSeqs).stream()
                .collect(Collectors.toMap(HotVideoRow::videoSeq, Function.identity()));
        // "관심 없음"(§8.6)은 §8.1 피드에만 걸려 있어 HOT 에는 계속 떴다 — 여기서 뺀다.
        // 카드 조회가 JPQL 이고 video_hides 는 엔티티가 없어 seq 만 받아 대조한다
        Set<Long> hidden = Set.copyOf(
                participationVideoRepository.findHiddenVideoSeqs(userSeq, videoSeqs));

        List<HotVideoItem> list = new ArrayList<>(page.size());
        for (VideoRankingSnapshot.Ranked entry : page) {
            HotVideoRow row = rows.get(entry.videoSeq());
            if (row != null && !hidden.contains(entry.videoSeq())) {
                list.add(toItem(entry, row));
            }
        }
        return list;
    }

    private HotVideoItem toItem(VideoRankingSnapshot.Ranked entry, HotVideoRow row) {
        return new HotVideoItem(
                entry.rank(),
                entry.rankDelta(),
                row.videoSeq(),
                mediaUrlResolver.resolvePlaylist(row.videoKey()),
                mediaUrlResolver.resolve(row.thumbnailKey()),
                row.durationMs(),
                new HotVideoItem.Song(row.songTitle(), row.singerName()),
                new HotVideoItem.Author(row.authorUserSeq(), row.authorNickname()),
                row.viewCount());
    }
}
