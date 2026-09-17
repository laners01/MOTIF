package com.ssafy.motif.coreapi.admin.actas;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.artist.ArtistErrorCode;
import com.ssafy.motif.domain.core.artist.Artist;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대행 컨텍스트 (어드민 명세 §3.12) — {@code artistSeq} 를 <b>행위자 user_seq</b> 로 바꾼다.
 * S15P21D104-407.
 *
 * <p>대행은 "사용자 API 와 같은 서비스 메서드를 아티스트의 {@code user_seq} 를 액터로
 * 호출" 하는 것이다. 그래서 대행 컨트롤러가 받는 {@code artistSeq} 와 서비스에 넘기는
 * 액터는 <b>다른 값</b>이고, 그 변환이 여기 한 곳에만 있어야 한다. 호출부마다
 * {@code artistRepository} 를 주입받아 {@code getUserSeq()} 를 꺼내면 비삭제 검증을
 * 빠뜨린 경로가 섞여 들어온다 — 삭제된 아티스트로도 댓글이 달린다.
 *
 * <p>감사 <b>로그</b>의 수행자는 어드민이다({@code AdminAuditLogger.actAs}) — 여기서
 * 얻는 값은 DB 행의 주인일 뿐 수행자가 아니다(§1.3).
 */
@Component
@RequiredArgsConstructor
public class ActAsArtistResolver {

    private final ArtistRepository artistRepository;

    /**
     * 대행 대상 아티스트의 {@code users.user_seq}.
     *
     * <p>삭제된 아티스트는 없는 것으로 본다 — 명세 §3.12 실패표가 404
     * {@code ARTIST_NOT_FOUND} 로 못박았다. 400 이 아닌 이유는 사용자 API 의 존재 은닉
     * 규칙과 같다.
     *
     * @throws BusinessException 404 {@code ARTIST_NOT_FOUND} — 미존재·삭제
     */
    @Transactional(readOnly = true)
    public Integer actorUserSeq(Integer artistSeq) {
        return artistRepository.findByArtistSeqAndDeletedFalse(artistSeq)
                .map(Artist::getUserSeq)
                .orElseThrow(() -> new BusinessException(ArtistErrorCode.ARTIST_NOT_FOUND,
                        "존재하지 않거나 삭제된 아티스트: artistSeq=" + artistSeq));
    }
}
