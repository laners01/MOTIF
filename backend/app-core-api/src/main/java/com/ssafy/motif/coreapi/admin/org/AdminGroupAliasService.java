package com.ssafy.motif.coreapi.admin.org;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.common.response.ListResponse;
import com.ssafy.motif.coreapi.admin.alias.dto.AliasCreateResponse;
import com.ssafy.motif.coreapi.admin.alias.dto.AliasRequest;
import com.ssafy.motif.coreapi.admin.alias.dto.AliasResponse;
import com.ssafy.motif.coreapi.admin.audit.AdminAuditLogger;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.domain.core.artist.ArtistGroupRepository;
import com.ssafy.motif.domain.core.artist.GroupAlias;
import com.ssafy.motif.domain.core.artist.GroupAliasRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그룹 별칭 CRUD (어드민 명세 §3.4.5~8) — S15P21D104-358.
 *
 * <p>계약·규칙은 {@code AdminArtistAliasService} 와 같다 — 시연 편의용이라 수정·삭제까지
 * 열고 삭제는 하드, 중복은 막지 않고, 대상은 비삭제만이다. 근거는 거기 적었다.
 *
 * <p><b>그룹 별칭이 검색에서 하는 일은 다르다.</b> API §5.2 는 여기 걸린 그룹의 <b>비삭제
 * 멤버 전원</b>을 결과에 펼치고, 그렇게 들어온 멤버를 본인 별칭 매치보다 뒤로 정렬한다
 * (ADR-0014 D2). 그래서 그룹 별칭 하나가 멤버 수만큼의 결과를 만든다 — 운영자가 그룹에
 * 별칭을 달 때 그 파급을 알고 달아야 한다.
 */
@Service
@RequiredArgsConstructor
public class AdminGroupAliasService {

    private final GroupAliasRepository groupAliasRepository;
    private final ArtistGroupRepository artistGroupRepository;
    private final AdminAuditLogger auditLogger;

    /** §3.4.5 목록 — 비페이지 전량. */
    @Transactional(readOnly = true)
    public ListResponse<AliasResponse> list(int groupSeq) {
        requireGroup(groupSeq);
        return ListResponse.of(groupAliasRepository.findByGroupSeqOrderByAliasSeq(groupSeq)
                .stream()
                .map(AliasResponse::of)
                .toList());
    }

    /** §3.4.6 등록. */
    @Transactional
    public AliasCreateResponse create(AuthenticatedAdmin admin, int groupSeq,
                                      AliasRequest request) {
        requireGroup(groupSeq);

        GroupAlias saved = groupAliasRepository.save(GroupAlias.register(
                groupSeq, request.languageCode(), request.alias().trim(), admin.userSeq()));

        auditLogger.write(admin, "CREATE_GROUP_ALIAS", "GROUP_ALIAS",
                saved.getAliasSeq(), AdminAuditLogger.RESULT_OK);
        return new AliasCreateResponse(saved.getAliasSeq());
    }

    /** §3.4.7 수정 — 전체 치환. */
    @Transactional
    public AliasResponse update(AuthenticatedAdmin admin, int groupSeq, int aliasSeq,
                                AliasRequest request) {
        GroupAlias alias = requireAlias(groupSeq, aliasSeq);
        alias.change(request.languageCode(), request.alias().trim());

        auditLogger.write(admin, "UPDATE_GROUP_ALIAS", "GROUP_ALIAS",
                aliasSeq, AdminAuditLogger.RESULT_OK);
        return AliasResponse.of(alias);
    }

    /** §3.4.8 삭제 — <b>하드 삭제</b>. */
    @Transactional
    public void delete(AuthenticatedAdmin admin, int groupSeq, int aliasSeq) {
        groupAliasRepository.delete(requireAlias(groupSeq, aliasSeq));

        auditLogger.write(admin, "DELETE_GROUP_ALIAS", "GROUP_ALIAS",
                aliasSeq, AdminAuditLogger.RESULT_OK);
    }

    /** 대상 그룹 확인 — 비삭제만. 근거는 {@code AdminArtistAliasService} 와 같다. */
    private void requireGroup(int groupSeq) {
        if (artistGroupRepository.findByGroupSeqAndDeletedFalse(groupSeq).isEmpty()) {
            throw new BusinessException(AdminErrorCode.GROUP_NOT_FOUND,
                    "그룹을 찾을 수 없습니다. groupSeq=" + groupSeq);
        }
    }

    /** 소유 확인 — {@code aliasSeq} 만으로 찾지 않는다. 근거는 아티스트 쪽과 같다. */
    private GroupAlias requireAlias(int groupSeq, int aliasSeq) {
        requireGroup(groupSeq);
        return groupAliasRepository.findByAliasSeqAndGroupSeq(aliasSeq, groupSeq)
                .orElseThrow(() -> new BusinessException(AdminErrorCode.ALIAS_NOT_FOUND,
                        "별칭을 찾을 수 없습니다. aliasSeq=" + aliasSeq));
    }
}
