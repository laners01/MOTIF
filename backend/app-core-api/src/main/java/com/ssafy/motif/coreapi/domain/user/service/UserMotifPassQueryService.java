package com.ssafy.motif.coreapi.domain.user.service;

import com.ssafy.motif.common.response.ListResponse;
import com.ssafy.motif.coreapi.domain.user.dto.MotifPassItem;
import com.ssafy.motif.coreapi.global.media.MediaUrlResolver;
import com.ssafy.motif.domain.core.artist.ArtistCardRow;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.pass.MotifPass;
import com.ssafy.motif.domain.core.pass.MotifPassRepository;
import com.ssafy.motif.domain.core.pass.PassStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 내 패스 일람 조회 (API 명세 §11.9).
 *
 * <p><b>{@link UserQueryService} 에 섞지 않는다.</b> 패스는 §11 도메인이라 마이페이지
 * 조회와 관심사가 다르고, §11.4 를 {@code ArtistMotifPassQueryService} 로 분리한 것과
 * 같은 방식이다.
 *
 * <p><b>조회 세 번으로 끝난다</b> — 패스 전량 · 아티스트 요약(배치) · 유효 패스(배치).
 * 항목마다 아티스트나 유효 여부를 읽으면 그대로 N+1 이다.
 *
 * <p><b>{@code status} 는 실효 상태다</b>(S15P21D104-269). {@code ACTIVE → EXPIRED} 전이
 * 배치가 없어 기간이 지난 {@code ACTIVE} 행이 정상적으로 존재하는데, 그 원시값을 그대로
 * 내보내면 클라이언트가 못 쓰는 패스를 활성으로 읽는다 — 실제 접근(§11.2·§11.3)은 이미
 * 거부하므로 화면 둘이 갈린다.
 *
 * <p><b>그 판정을 여기서 계산하지 않는다.</b> {@link MotifPassRepository#findActivePasses}
 * 가 DB 시각으로 답한 집합을 덧대기만 한다 — §4.7 구독 카드와 §11.3 숍이 쓰는 바로 그
 * 메서드다. 자바에서 만료 시각을 비교하면 앱 서버 시계가 DB 와 어긋난 만큼 이 화면만
 * 만료 직전·직후 판정이 달라진다.
 *
 * <p>읽기 전용 트랜잭션이다 — <b>어긋난 행을 고쳐 쓰지 않는다</b>. 조회가 DB 를 갱신하면
 * 이 화면을 연 사람의 행만 정리돼 정합성은 여전히 보장되지 않고, 권한은 이미
 * {@code expires_dttm} 이 책임지므로 얻는 것이 없다(DB 명세 v0.15 §35).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserMotifPassQueryService {

    private final MotifPassRepository motifPassRepository;
    private final ArtistRepository artistRepository;
    private final MediaUrlResolver mediaUrlResolver;

    /**
     * §11.9 내 패스 일람 — 전량, 만료 늦은 순.
     *
     * <p>실패 분기가 없다. 패스가 0건이면 빈 배열이다.
     *
     * <p><b>삭제된 아티스트의 패스도 나온다.</b> §4.6·§4.7 은 삭제 아티스트를 빼지만
     * 그쪽은 "지금 갈 수 있는 곳" 목록이고 여기는 결제 이력이다 — 돈을 낸 기록이 아티스트
     * 삭제로 사라지면 안 된다(§11.1 보유함과 같은 규칙).
     */
    public ListResponse<MotifPassItem> passes(Integer userSeq) {
        List<MotifPass> passes =
                motifPassRepository.findByUserSeqOrderByExpiresDttmDescPassSeqDesc(userSeq);
        if (passes.isEmpty()) {
            // 빈 in 절을 보내지 않는다 — 뒤 조회를 아예 부르지 않는다
            return ListResponse.of(List.of());
        }

        List<Integer> artistSeqs = passes.stream().map(MotifPass::getArtistSeq).distinct().toList();
        Map<Integer, ArtistCardRow> artists = artistRepository.findCards(artistSeqs).stream()
                .collect(Collectors.toMap(ArtistCardRow::artistSeq, Function.identity()));
        Set<Integer> activeArtistSeqs = motifPassRepository.findActivePasses(userSeq, artistSeqs)
                .stream()
                .map(MotifPassRepository.ActivePassProjection::getArtistSeq)
                .collect(Collectors.toSet());

        return ListResponse.of(passes.stream()
                .map(pass -> item(pass, artists.get(pass.getArtistSeq()), activeArtistSeqs))
                .toList());
    }

    private MotifPassItem item(MotifPass pass, ArtistCardRow card, Set<Integer> activeArtistSeqs) {
        return new MotifPassItem(
                pass.getArtistSeq(),
                artist(pass.getArtistSeq(), card),
                status(pass, activeArtistSeqs),
                pass.getStartedDttm(),
                pass.getExpiresDttm(),
                pass.getDurationMonths(),
                pass.getCanceledDttm());
    }

    /**
     * 실효 상태 — 기간이 지난 {@code ACTIVE} 는 {@code EXPIRED} 로 내려간다.
     *
     * <p>(회원, 아티스트)당 한 행이라({@code uq_motif_passes_user_artist}) 아티스트 식별자
     * 하나로 이 행이 유효한지가 정해진다. 집합에 있으면 DB 가 유효하다고 답한 것이므로
     * {@code ACTIVE} 그대로고, 없는데 DB 가 {@code ACTIVE} 면 기간이 지난 행이다.
     *
     * <p>{@code EXPIRED}·{@code REFUNDED} 는 건드리지 않는다 — 이미 종료 상태다.
     * {@code canceledDttm} 도 보지 않는다. 취소는 의사 기록이고 이용 판정에 불참한다(DB §35).
     */
    private static PassStatus status(MotifPass pass, Set<Integer> activeArtistSeqs) {
        if (pass.getStatus() != PassStatus.ACTIVE || activeArtistSeqs.contains(pass.getArtistSeq())) {
            return pass.getStatus();
        }
        return PassStatus.EXPIRED;
    }

    /**
     * @param card 아티스트 행이 통째로 사라진 상태에서도 결제 이력이 500 이 되지 않게
     *             {@code null} 을 허용한다 — 삭제는 플래그라 보통은 채워진다
     */
    private MotifPassItem.Artist artist(Integer artistSeq, ArtistCardRow card) {
        if (card == null) {
            return new MotifPassItem.Artist(artistSeq, null, null, null);
        }
        return new MotifPassItem.Artist(
                card.artistSeq(),
                card.name(),
                card.groupName(),
                mediaUrlResolver.resolveProfile(card.profileImageKey()));
    }
}
