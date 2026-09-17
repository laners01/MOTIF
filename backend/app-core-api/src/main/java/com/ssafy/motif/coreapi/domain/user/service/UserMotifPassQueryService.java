package com.ssafy.motif.coreapi.domain.user.service;

import com.ssafy.motif.common.response.ListResponse;
import com.ssafy.motif.coreapi.domain.user.dto.MotifPassItem;
import com.ssafy.motif.coreapi.global.media.MediaUrlResolver;
import com.ssafy.motif.domain.core.artist.ArtistCardRow;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.pass.MotifPass;
import com.ssafy.motif.domain.core.pass.MotifPassRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 내 패스 일람 조회 (API 명세 §11.9).
 *
 * <p><b>{@link UserQueryService} 에 섞지 않는다.</b> 패스는 §11 도메인이라 마이페이지
 * 조회와 관심사가 다르고, §11.4 를 {@code ArtistMotifPassQueryService} 로 분리한 것과
 * 같은 방식이다.
 *
 * <p><b>조회 두 번으로 끝난다</b> — 패스 전량 · 아티스트 요약(배치). 항목마다 아티스트를
 * 읽으면 그대로 N+1 이다.
 *
 * <p><b>이용 가능 여부를 계산하지 않는다.</b> {@code status}·{@code expiresDttm} 원시값을
 * 그대로 내려준다 — 판정이 필요한 화면은 §11.4·§4.7 의 {@code passActive} 를 쓴다. 여기서도
 * 계산하면 같은 질문에 세 곳이 각자 답하게 된다.
 *
 * <p>읽기 전용 트랜잭션이다.
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

        Map<Integer, ArtistCardRow> artists = artistRepository
                .findCards(passes.stream().map(MotifPass::getArtistSeq).distinct().toList())
                .stream()
                .collect(Collectors.toMap(ArtistCardRow::artistSeq, Function.identity()));

        return ListResponse.of(passes.stream()
                .map(pass -> item(pass, artists.get(pass.getArtistSeq())))
                .toList());
    }

    private MotifPassItem item(MotifPass pass, ArtistCardRow card) {
        return new MotifPassItem(
                pass.getArtistSeq(),
                artist(pass.getArtistSeq(), card),
                pass.getStatus(),
                pass.getStartedDttm(),
                pass.getExpiresDttm(),
                pass.getDurationMonths(),
                pass.getCanceledDttm());
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
                mediaUrlResolver.resolve(card.profileImageKey()));
    }
}
