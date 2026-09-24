package com.ssafy.motif.coreapi.domain.motif.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.common.response.CursorResponse;
import com.ssafy.motif.coreapi.domain.motif.MotifErrorCode;
import com.ssafy.motif.coreapi.domain.motif.dto.MotifResultDetailResponse;
import com.ssafy.motif.coreapi.domain.motif.dto.MotifResultItem;
import com.ssafy.motif.coreapi.global.media.MediaUrlResolver;
import com.ssafy.motif.coreapi.global.response.Cursors;
import com.ssafy.motif.domain.core.artist.ArtistCardRow;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.asset.MotifAssetRepository;
import com.ssafy.motif.domain.core.motif.MotifResult;
import com.ssafy.motif.domain.core.motif.MotifResultRepository;
import com.ssafy.motif.domain.core.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 모티프 보관함 조회 (API 명세 §11.5 · §11.6).
 *
 * <p><b>조회 세 번으로 끝난다</b> — 목록 · 아티스트 요약(배치) · 내 대표 모티프 seq.
 * 항목마다 아티스트를 읽거나 대표 여부를 물으면 그대로 N+1 이다.
 *
 * <p><b>썸네일은 보호 등급이다</b>(§1.7). 모티프 결과물은 전량 {@code private/} 아래라
 * {@link MediaUrlResolver} 가 서명 URL 을 만든다 — 호출부가 등급을 고르지 않는다.
 *
 * <p>읽기 전용 트랜잭션이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MotifResultQueryService {

    /** §1.9 커서 목록의 크기 상한. */
    private static final int MAX_SIZE = 100;

    private final MotifResultRepository motifResultRepository;
    private final ArtistRepository artistRepository;
    private final MotifAssetRepository motifAssetRepository;
    private final UserRepository userRepository;
    private final MediaUrlResolver mediaUrlResolver;

    /**
     * §11.5 보관함 목록 — 커서 페이지, 최신순 고정.
     *
     * <p>§5.8(아티스트 상세의 "내 모티프 전체 보기")은 {@code artistSeq} 를 준 이 호출이다 —
     * 별도 API 를 두지 않는다.
     *
     * <p>실패 분기가 없다. 결과물이 0건이면 빈 목록이고, 망가진 커서만 400 이다
     * ({@link Cursors#decodeLong} 이 던진다 — 조용히 첫 페이지로 되돌리지 않는다).
     */
    public CursorResponse<MotifResultItem> results(Integer userSeq, Integer artistSeq,
                                                   String cursor, int size) {
        int limit = Math.clamp(size, 1, MAX_SIZE);
        Long after = Cursors.decodeLong(cursor);

        // limit+1 을 읽어 "다음이 있나"를 판정한다 — count 쿼리를 따로 쏘지 않는다
        PageRequest page = PageRequest.of(0, limit + 1);
        List<MotifResult> rows = find(userSeq, artistSeq, after, page);

        boolean hasNext = rows.size() > limit;
        List<MotifResult> results = hasNext ? rows.subList(0, limit) : rows;
        if (results.isEmpty()) {
            return CursorResponse.forward(List.of(), null);
        }

        Map<Integer, ArtistCardRow> artists = artistRepository
                .findCards(results.stream().map(MotifResult::getArtistSeq).distinct().toList())
                .stream()
                .collect(Collectors.toMap(ArtistCardRow::artistSeq, Function.identity()));
        Long repResultSeq = userRepository.findById(userSeq)
                .map(user -> user.getRepMotifResultSeq())
                .orElse(null);

        List<MotifResultItem> list = results.stream()
                .map(result -> item(result, artists.get(result.getArtistSeq()), repResultSeq))
                .toList();

        String nextCursor = hasNext ? Cursors.encode(results.getLast().getResultSeq()) : null;
        return CursorResponse.forward(list, nextCursor);
    }

    /**
     * §11.6 결과물 상세 — 재생 화면.
     *
     * <p><b>안 보이는 결과물은 전부 404 다</b> — 미존재 · 삭제 · 타인 소유가 같은
     * {@code MOTIF_RESULT_NOT_FOUND} 다(§1.8 존재 은닉). 조회 메서드가 이미
     * {@code userSeq} 를 걸고 있어 타인 소유가 자동으로 빈 결과가 된다.
     *
     * <p><b>잡을 읽지 않는다.</b> 재생 주소는 결과물의 {@code video_key} 로 만든다 —
     * 그 값이 이미 마스터 재생목록 키다(ADR-0012).
     *
     * @throws BusinessException {@code MOTIF_RESULT_NOT_FOUND}
     */
    public MotifResultDetailResponse detail(Integer userSeq, Long resultSeq) {
        MotifResult result = motifResultRepository
                .findByResultSeqAndUserSeqAndDeletedFalse(resultSeq, userSeq)
                .orElseThrow(() -> new BusinessException(MotifErrorCode.MOTIF_RESULT_NOT_FOUND));

        ArtistCardRow card = artistRepository.findCards(List.of(result.getArtistSeq()))
                .stream().findFirst().orElse(null);
        Long repResultSeq = userRepository.findById(userSeq)
                .map(user -> user.getRepMotifResultSeq())
                .orElse(null);

        return new MotifResultDetailResponse(
                result.getResultSeq(),
                // 프리픽스 서명 — 조각까지 이 한 벌로 통한다
                mediaUrlResolver.resolvePlaylist(result.getVideoKey()),
                // 단일 파일 서명 — 그 한 장만 열린다. 둘을 같은 메서드로 만들면
                // 재생목록에 단일 서명이 붙어 조각이 403 이 된다
                mediaUrlResolver.resolve(result.getThumbnailKey()),
                result.getDescription(),
                result.getDurationMs(),
                asset(result.getAssetSeq()),
                detailArtist(result.getArtistSeq(), card),
                Objects.equals(repResultSeq, result.getResultSeq()),
                result.getRegDttm());
    }

    /**
     * 에셋 요약 — 판매가 중단됐거나 아티스트가 삭제됐어도 <b>상세는 열린다</b>.
     *
     * <p>§11.5 목록에 나오는 것이 상세에서 404 면 목록과 상세가 어긋난다. 내가 만든
     * 결과물이라는 사실은 에셋의 판매 상태와 무관하다.
     */
    private MotifResultDetailResponse.Asset asset(Integer assetSeq) {
        return motifAssetRepository.findById(assetSeq)
                .map(asset -> new MotifResultDetailResponse.Asset(
                        asset.getAssetSeq(), asset.getTitle()))
                .orElseGet(() -> new MotifResultDetailResponse.Asset(assetSeq, null));
    }

    private MotifResultDetailResponse.Artist detailArtist(Integer artistSeq, ArtistCardRow card) {
        if (card == null) {
            return new MotifResultDetailResponse.Artist(artistSeq, null, null, null);
        }
        return new MotifResultDetailResponse.Artist(
                card.artistSeq(),
                card.name(),
                card.groupName(),
                mediaUrlResolver.resolveProfile(card.profileImageKey()));
    }

    /**
     * 커서 유무 · {@code artistSeq} 유무로 메서드를 가른다.
     *
     * <p>한 쿼리에 {@code null} 분기를 넣지 않는 이유는 리포지토리 javadoc 에 있다 —
     * 타입 없는 {@code null} 바인딩이 운영 500 이 되는 자리다.
     */
    private List<MotifResult> find(Integer userSeq, Integer artistSeq, Long after,
                                   PageRequest page) {
        if (artistSeq == null) {
            return after == null
                    ? motifResultRepository
                            .findByUserSeqAndDeletedFalseOrderByResultSeqDesc(userSeq, page)
                    : motifResultRepository
                            .findByUserSeqAndDeletedFalseAndResultSeqLessThanOrderByResultSeqDesc(
                                    userSeq, after, page);
        }
        return after == null
                ? motifResultRepository
                        .findByUserSeqAndArtistSeqAndDeletedFalseOrderByResultSeqDesc(
                                userSeq, artistSeq, page)
                : motifResultRepository
                        .findByUserSeqAndArtistSeqAndDeletedFalseAndResultSeqLessThanOrderByResultSeqDesc(
                                userSeq, artistSeq, after, page);
    }

    /**
     * @param card 삭제된 아티스트도 행이 남아 있어 보통 채워진다. 그래도 {@code null} 을
     *             허용하는 것은 아티스트 행 자체가 사라진 상태에서 보관함이 통째로 500 이
     *             되지 않게 하기 위해서다 — 내가 만든 결과물은 남아야 한다
     */
    private MotifResultItem item(MotifResult result, ArtistCardRow card, Long repResultSeq) {
        return new MotifResultItem(
                result.getResultSeq(),
                mediaUrlResolver.resolve(result.getThumbnailKey()),
                artist(result.getArtistSeq(), card),
                result.getRegDttm(),
                Objects.equals(repResultSeq, result.getResultSeq()));
    }

    private MotifResultItem.Artist artist(Integer artistSeq, ArtistCardRow card) {
        if (card == null) {
            return new MotifResultItem.Artist(artistSeq, null, null, null);
        }
        return new MotifResultItem.Artist(
                card.artistSeq(),
                card.name(),
                card.groupName(),
                mediaUrlResolver.resolveProfile(card.profileImageKey()));
    }
}
