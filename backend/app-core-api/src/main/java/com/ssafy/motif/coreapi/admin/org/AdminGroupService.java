package com.ssafy.motif.coreapi.admin.org;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.admin.audit.AdminAuditLogger;
import com.ssafy.motif.coreapi.admin.org.dto.GroupCreateRequest;
import com.ssafy.motif.coreapi.admin.org.dto.GroupCreateResponse;
import com.ssafy.motif.coreapi.admin.org.dto.GroupSummaryResponse;
import com.ssafy.motif.coreapi.admin.org.dto.GroupUpdateRequest;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.global.response.PageResponses;
import com.ssafy.motif.domain.core.artist.ArtistGroup;
import com.ssafy.motif.domain.core.artist.ArtistGroupRepository;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.artist.Company;
import com.ssafy.motif.domain.core.artist.CompanyRepository;
import com.ssafy.motif.domain.core.artist.GroupListRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 아티스트 그룹 CRUD (어드민 명세 §3.4) — S15P21D104-384.
 *
 * <p><b>그룹이 소속사를 소유한다.</b> 그룹 소속 아티스트의 소속사는 그룹이 정하므로
 * (DB §8·§9), 그룹의 소속사를 옮기면 비삭제 멤버 전원의 {@code artists.company_seq} 를
 * <b>같은 트랜잭션</b>에서 따라 옮긴다. 이 불변식의 반대편 — 그룹 소속 아티스트의 소속사를
 * 혼자 바꾸려는 요청 — 은 {@code AdminArtistService} 가 409
 * {@code ARTIST_COMPANY_LOCKED} 로 막는다(§3.5.4).
 *
 * <p><b>그룹명은 중복을 허용한다</b>(DB §8) — 소속사와 달리 UNIQUE 가 없다. 대신 0012 의
 * trigram 인덱스가 부분 일치 검색을 받친다.
 *
 * <p>삭제는 소프트 삭제이고, 비삭제 멤버가 남아 있으면 409 {@code GROUP_IN_USE} 다
 * (ADMIN-03). 소속사 검증은 {@link CompanyRepository} 로 직접 한다 — 소속사 서비스(§3.3)를
 * 주입하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminGroupService {

    private final ArtistGroupRepository artistGroupRepository;
    private final CompanyRepository companyRepository;
    private final ArtistRepository artistRepository;
    private final AdminAuditLogger auditLogger;

    /** §3.4.1 — 소속사 필터 + 그룹명 부분 일치, 최신 등록순. 멤버 수를 함께 준다. */
    @Transactional(readOnly = true)
    public PageResponse<GroupSummaryResponse> list(String q, Integer companySeq,
                                                   boolean includeDeleted, int page, int size) {
        return PageResponses.of(
                artistGroupRepository.search(blankToEmpty(q), companySeq, includeDeleted,
                        PageResponses.pageable(page, size)),
                AdminGroupService::item);
    }

    /** §3.4.2 — 등록. 그룹명 중복은 검사하지 않는다(DB §8 — 허용이다). */
    @Transactional
    public GroupCreateResponse create(AuthenticatedAdmin admin, GroupCreateRequest request) {
        Company company = company(request.companySeq());

        ArtistGroup group = artistGroupRepository.save(ArtistGroup.register(
                company.getCompanySeq(), request.groupName().strip(), admin.userSeq()));
        auditLogger.write(admin, "CREATE_GROUP", "GROUP", group.getGroupSeq(), AdminAuditLogger.RESULT_OK);
        return new GroupCreateResponse(group.getGroupSeq());
    }

    /**
     * §3.4.3 — 부분 수정. <b>소속사를 바꾸면 비삭제 멤버가 따라 옮겨진다</b>(DB §8).
     *
     * <p>그룹 행만 옮기면 멤버의 {@code company_seq} 가 그룹과 어긋나고, 그 어긋남은
     * 아티스트 수정이 409 로 막는 바로 그 상태다(§3.5.4). 두 UPDATE 는 같은 트랜잭션이라
     * 한쪽만 남지 않는다.
     */
    @Transactional
    public GroupSummaryResponse update(AuthenticatedAdmin admin, int groupSeq,
                                       GroupUpdateRequest request) {
        ArtistGroup group = group(groupSeq);

        group.rename(request.groupName() == null ? null : request.groupName().strip(), admin.userSeq());

        Integer companySeq = request.companySeq();
        if (companySeq != null && !companySeq.equals(group.getCompanySeq())) {
            Company company = company(companySeq);
            group.changeCompany(company.getCompanySeq(), admin.userSeq());
            // 벌크 UPDATE — flushAutomatically 가 위 그룹 변경을 먼저 내보낸다(리포지토리 javadoc)
            int moved = artistRepository.moveMembersCompany(groupSeq, company.getCompanySeq());
            log.info("그룹 소속사 이동 — groupSeq={} companySeq={} 멤버 {}명 동반 이동",
                    groupSeq, company.getCompanySeq(), moved);
        }

        auditLogger.write(admin, "UPDATE_GROUP", "GROUP", groupSeq, AdminAuditLogger.RESULT_OK);
        return item(row(groupSeq));
    }

    /** §3.4.4 — 소프트 삭제. 비삭제 멤버가 있으면 409 다. */
    @Transactional
    public void delete(AuthenticatedAdmin admin, int groupSeq) {
        ArtistGroup group = group(groupSeq);

        long memberCount = artistRepository.countByGroupSeqAndDeletedFalse(groupSeq);
        if (memberCount > 0) {
            auditLogger.write(admin, "DELETE_GROUP", "GROUP", groupSeq,
                    AdminErrorCode.GROUP_IN_USE.getCode());
            throw new BusinessException(AdminErrorCode.GROUP_IN_USE,
                    "멤버가 남은 그룹: groupSeq=%d memberCount=%d".formatted(groupSeq, memberCount),
                    Map.of("memberCount", memberCount));
        }

        group.softDelete(admin.userSeq());
        auditLogger.write(admin, "DELETE_GROUP", "GROUP", groupSeq, AdminAuditLogger.RESULT_OK);
    }

    // --- 조회·변환 ----------------------------------------------------------

    private static GroupSummaryResponse item(GroupListRow row) {
        return new GroupSummaryResponse(row.groupSeq(), row.groupName(),
                new GroupSummaryResponse.CompanyRef(row.companySeq(), row.companyName()),
                row.memberCount(), row.deleted(), row.regDttm());
    }

    private GroupListRow row(int groupSeq) {
        return artistGroupRepository.findRow(groupSeq)
                .orElseThrow(() -> new IllegalStateException(
                        "방금 조회한 그룹이 사라졌다: groupSeq=" + groupSeq));
    }

    private ArtistGroup group(int groupSeq) {
        return artistGroupRepository.findByGroupSeqAndDeletedFalse(groupSeq)
                .orElseThrow(() -> new BusinessException(AdminErrorCode.GROUP_NOT_FOUND,
                        "미존재·삭제 그룹: groupSeq=" + groupSeq));
    }

    /** 등록·이동 대상 소속사 — 삭제된 소속사에는 그룹을 붙이지 않는다(§3.4 실패표 404). */
    private Company company(Integer companySeq) {
        return companyRepository.findByCompanySeqAndDeletedFalse(companySeq)
                .orElseThrow(() -> new BusinessException(AdminErrorCode.COMPANY_NOT_FOUND,
                        "미존재·삭제 소속사: companySeq=" + companySeq));
    }

    /**
     * 검색어 없음 → 빈 문자열. {@code null} 을 쿼리로 넘기지 않는다.
     *
     * <p>타입 없는 {@code null} 이 {@code lower()} 에 들어가면 Postgres 가 {@code bytea} 로
     * 추론해 {@code function lower(bytea) does not exist} 로 죽는다 — 385·389 가 각각
     * 배포 후에야 이 500 을 만났다. 빈 문자열이면 {@code like '%%'} 가 전체 조회다.
     */
    private static String blankToEmpty(String value) {
        return value == null ? "" : value.strip();
    }
}
