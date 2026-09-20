package com.ssafy.motif.coreapi.admin.artist;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.common.response.ListResponse;
import com.ssafy.motif.coreapi.admin.alias.dto.AliasCreateResponse;
import com.ssafy.motif.coreapi.admin.alias.dto.AliasRequest;
import com.ssafy.motif.coreapi.admin.alias.dto.AliasResponse;
import com.ssafy.motif.coreapi.admin.audit.AdminAuditLogger;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.domain.artist.ArtistErrorCode;
import com.ssafy.motif.domain.core.artist.ArtistAlias;
import com.ssafy.motif.domain.core.artist.ArtistAliasRepository;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아티스트 별칭 CRUD (어드민 명세 §3.5.6~9) — S15P21D104-358.
 *
 * <p>팬 앱 다국어 검색(API §5.2)이 훑는 별칭을 운영자가 채워 넣는 경로다. 별칭을 넣을 수단이
 * 없으면 검색 결과가 항상 0건이라 검색 API 와 짝이다(ADR-0014).
 *
 * <p><b>이 API 는 프로젝트 기능 범위가 아니라 시연 편의용이다.</b> 그래서 팬 앱에 없는
 * 수정·삭제까지 열어 두고, <b>삭제도 하드 삭제</b>다. 프로젝트 도메인의 소프트 삭제 규약과
 * 헷갈리지 않는다 — {@code AdminArtistService.delete}(§3.5.5)는 별칭을 건드리지 않고,
 * 검색이 조회 시 {@code artists.is_deleted = false} 조인으로 거른다(DB §41).
 *
 * <p><b>{@link AdminArtistService} 를 주입하지 않는다.</b> 대상 아티스트의 존재·비삭제만
 * 확인하면 되므로 {@link ArtistRepository} 로 직접 본다 — {@code AdminGroupService} 가
 * 소속사를 {@code CompanyRepository} 로 확인하는 것과 같은 방식이다. 서비스를 주입하면
 * §3.5.1~5 의 트랜잭션·검증이 별칭 경로까지 따라 들어온다.
 *
 * <p><b>중복을 막지 않는다.</b> 한 언어에 표기 변형이 여럿인 것이 정상이고(`aespa`·`æspa`,
 * 활동명·본명), 완전히 같은 값이 두 번 들어가도 409 를 내지 않는다 — DB 에 UNIQUE 가 없다.
 * 검색은 {@code artistSeq} 로 중복을 제거하므로 결과가 겹치지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AdminArtistAliasService {

    private final ArtistAliasRepository artistAliasRepository;
    private final ArtistRepository artistRepository;
    private final AdminAuditLogger auditLogger;

    /** §3.5.6 목록 — 비페이지 전량. 한 아티스트당 몇 개 수준이라 페이지를 두지 않는다. */
    @Transactional(readOnly = true)
    public ListResponse<AliasResponse> list(int artistSeq) {
        requireArtist(artistSeq);
        return ListResponse.of(artistAliasRepository.findByArtistSeqOrderByAliasSeq(artistSeq)
                .stream()
                .map(AliasResponse::of)
                .toList());
    }

    /** §3.5.7 등록. */
    @Transactional
    public AliasCreateResponse create(AuthenticatedAdmin admin, int artistSeq,
                                      AliasRequest request) {
        requireArtist(artistSeq);

        ArtistAlias saved = artistAliasRepository.save(ArtistAlias.register(
                artistSeq, request.languageCode(), request.alias().trim(), admin.userSeq()));

        auditLogger.write(admin, "CREATE_ARTIST_ALIAS", "ARTIST_ALIAS",
                saved.getAliasSeq(), AdminAuditLogger.RESULT_OK);
        return new AliasCreateResponse(saved.getAliasSeq());
    }

    /** §3.5.8 수정 — 부분 수정이 아니라 전체 치환이다. */
    @Transactional
    public AliasResponse update(AuthenticatedAdmin admin, int artistSeq, int aliasSeq,
                                AliasRequest request) {
        ArtistAlias alias = requireAlias(artistSeq, aliasSeq);
        alias.change(request.languageCode(), request.alias().trim());

        auditLogger.write(admin, "UPDATE_ARTIST_ALIAS", "ARTIST_ALIAS",
                aliasSeq, AdminAuditLogger.RESULT_OK);
        return AliasResponse.of(alias);
    }

    /** §3.5.9 삭제 — <b>하드 삭제</b>. 지워진 기록은 감사 로그에만 남는다. */
    @Transactional
    public void delete(AuthenticatedAdmin admin, int artistSeq, int aliasSeq) {
        artistAliasRepository.delete(requireAlias(artistSeq, aliasSeq));

        auditLogger.write(admin, "DELETE_ARTIST_ALIAS", "ARTIST_ALIAS",
                aliasSeq, AdminAuditLogger.RESULT_OK);
    }

    /**
     * 대상 아티스트 확인 — <b>비삭제만</b>이다(2026-09-21 확정).
     *
     * <p>삭제된 아티스트에는 별칭을 달지도, 그 별칭을 조회하지도 못한다. 행은 DB 에 남아
     * 있어 복구하면 살아 돌아오지만(DB §41), 삭제된 대상을 어드민에서 계속 만질 수 있게
     * 하면 §3.5.5 의 "앱·상점에서 즉시 비노출" 과 어긋난다.
     */
    private void requireArtist(int artistSeq) {
        if (artistRepository.findByArtistSeqAndDeletedFalse(artistSeq).isEmpty()) {
            throw new BusinessException(ArtistErrorCode.ARTIST_NOT_FOUND,
                    "아티스트를 찾을 수 없습니다. artistSeq=" + artistSeq);
        }
    }

    /**
     * 수정·삭제 대상 확인 — <b>{@code aliasSeq} 만으로 찾지 않는다</b>.
     *
     * <p>경로의 아티스트 소유인지 함께 보지 않으면 남의 별칭을 {@code aliasSeq} 하나로
     * 고치는 경로가 열린다. 소유가 아니면 "없음" 과 같은 404 로 답한다 — 남의 별칭이
     * 존재한다는 사실 자체를 흘리지 않는다.
     */
    private ArtistAlias requireAlias(int artistSeq, int aliasSeq) {
        requireArtist(artistSeq);
        return artistAliasRepository.findByAliasSeqAndArtistSeq(aliasSeq, artistSeq)
                .orElseThrow(() -> new BusinessException(AdminErrorCode.ALIAS_NOT_FOUND,
                        "별칭을 찾을 수 없습니다. aliasSeq=" + aliasSeq));
    }
}
