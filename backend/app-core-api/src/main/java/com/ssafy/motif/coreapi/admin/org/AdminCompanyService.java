package com.ssafy.motif.coreapi.admin.org;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.admin.audit.AdminAuditLogger;
import com.ssafy.motif.coreapi.admin.org.dto.CompanyCreateResponse;
import com.ssafy.motif.coreapi.admin.org.dto.CompanyNameRequest;
import com.ssafy.motif.coreapi.admin.org.dto.CompanySummaryResponse;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.global.response.PageResponses;
import com.ssafy.motif.domain.core.artist.ArtistGroupRepository;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.artist.Company;
import com.ssafy.motif.domain.core.artist.CompanyListRow;
import com.ssafy.motif.domain.core.artist.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 소속사 CRUD (어드민 명세 §3.3) — S15P21D104-383.
 *
 * <p><b>삭제는 소프트 삭제다.</b> 실제 DELETE 는 하지 않는다 — 챌린지·곡이 아티스트를 거쳐
 * 소속사를 참조하고 있어 물리 삭제는 FK 로 막히거나 이력을 지운다. 대신 비삭제 참조가
 * 남아 있으면 409 {@code COMPANY_IN_USE} 로 먼저 막고, 운영자가 옮긴 뒤 지우게 한다
 * (ADMIN-02).
 *
 * <p><b>활성 회사명만 UNIQUE 다</b>({@code uq_companies_name_active} — 부분 인덱스).
 * 삭제된 회사명은 같은 이름으로 다시 등록할 수 있다.
 *
 * <p>그룹 쪽 참조는 {@link ArtistGroupRepository} 로 직접 센다 — 그룹 서비스(§3.4)를
 * 주입하지 않는다. 둘은 서로를 필요로 하지 않고 리포지토리만 공유한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCompanyService {

    private final CompanyRepository companyRepository;
    private final ArtistGroupRepository artistGroupRepository;
    private final ArtistRepository artistRepository;
    private final AdminAuditLogger auditLogger;

    /** §3.3.1 — 회사명 부분 일치, 최신 등록순. 그룹·아티스트 참조 수를 함께 준다. */
    @Transactional(readOnly = true)
    public PageResponse<CompanySummaryResponse> list(String q, boolean includeDeleted,
                                                     int page, int size) {
        return PageResponses.of(
                companyRepository.search(blankToEmpty(q), includeDeleted, PageResponses.pageable(page, size)),
                AdminCompanyService::item);
    }

    /**
     * §3.3.2 — 등록. 활성 회사명 중복은 409 다.
     *
     * <p>앱에서 먼저 보고 DB 제약으로 한 번 더 받는다. 앞의 검사는 친절한 메시지를 위한
     * 것이고, 같은 이름이 동시에 들어오면 통과해 버리므로 실제로 막는 것은
     * {@code uq_companies_name_active} 다 — 그때 터지는 것을 500 이 아니라 같은 409 로
     * 바꿔 준다.
     */
    @Transactional
    public CompanyCreateResponse create(AuthenticatedAdmin admin, CompanyNameRequest request) {
        String name = request.companyName().strip();
        if (companyRepository.existsByCompanyNameAndDeletedFalse(name)) {
            throw duplicateName(name);
        }

        Company company = save(Company.register(name, admin.userSeq()), name);
        auditLogger.write(admin, "CREATE_COMPANY", "COMPANY", company.getCompanySeq(),
                AdminAuditLogger.RESULT_OK);
        return new CompanyCreateResponse(company.getCompanySeq());
    }

    /** §3.3.3 — 회사명 수정. 자기 이름을 그대로 보내는 것은 중복이 아니다. */
    @Transactional
    public CompanySummaryResponse update(AuthenticatedAdmin admin, int companySeq,
                                         CompanyNameRequest request) {
        Company company = company(companySeq);
        String name = request.companyName().strip();
        if (companyRepository.existsByCompanyNameAndDeletedFalseAndCompanySeqNot(name, companySeq)) {
            throw duplicateName(name);
        }

        company.rename(name, admin.userSeq());
        save(company, name);
        auditLogger.write(admin, "UPDATE_COMPANY", "COMPANY", companySeq, AdminAuditLogger.RESULT_OK);
        return item(row(companySeq));
    }

    /**
     * §3.3.4 — 소프트 삭제. 비삭제 그룹·아티스트가 참조 중이면 409 다.
     *
     * <p>두 수를 <b>둘 다</b> 세어 {@code data} 에 싣는다. 그룹에서 먼저 걸려 멈추면
     * 운영자가 그룹만 옮기고 다시 시도해 또 막히는 왕복이 생긴다 — 한 번에 보여 준다.
     */
    @Transactional
    public void delete(AuthenticatedAdmin admin, int companySeq) {
        Company company = company(companySeq);
        long groupCount = artistGroupRepository.countByCompanySeqAndDeletedFalse(companySeq);
        long artistCount = artistRepository.countByCompanySeqAndDeletedFalse(companySeq);
        if (groupCount > 0 || artistCount > 0) {
            auditLogger.write(admin, "DELETE_COMPANY", "COMPANY", companySeq,
                    AdminErrorCode.COMPANY_IN_USE.getCode());
            throw new BusinessException(AdminErrorCode.COMPANY_IN_USE,
                    "참조 중인 소속사: companySeq=%d groupCount=%d artistCount=%d"
                            .formatted(companySeq, groupCount, artistCount),
                    Map.of("groupCount", groupCount, "artistCount", artistCount));
        }

        company.softDelete(admin.userSeq());
        auditLogger.write(admin, "DELETE_COMPANY", "COMPANY", companySeq, AdminAuditLogger.RESULT_OK);
    }

    // --- 조회·변환 ----------------------------------------------------------

    private static CompanySummaryResponse item(CompanyListRow row) {
        return new CompanySummaryResponse(row.companySeq(), row.companyName(),
                row.groupCount(), row.artistCount(), row.deleted(), row.regDttm());
    }

    private CompanyListRow row(int companySeq) {
        return companyRepository.findRow(companySeq)
                .orElseThrow(() -> new IllegalStateException(
                        "방금 조회한 소속사가 사라졌다: companySeq=" + companySeq));
    }

    /** 수정·삭제 대상 — 삭제된 소속사는 없는 것으로 본다(§3.3 실패표 404). */
    private Company company(Integer companySeq) {
        return companyRepository.findByCompanySeqAndDeletedFalse(companySeq)
                .orElseThrow(() -> new BusinessException(AdminErrorCode.COMPANY_NOT_FOUND,
                        "미존재·삭제 소속사: companySeq=" + companySeq));
    }

    /** 부분 UNIQUE 위반을 409 로 바꾼다 — 동시 등록에서만 여기까지 온다. */
    private Company save(Company company, String name) {
        try {
            return companyRepository.saveAndFlush(company);
        } catch (DataIntegrityViolationException e) {
            log.info("활성 회사명 동시 등록 충돌 — uq_companies_name_active: {}", name);
            throw duplicateName(name);
        }
    }

    private static BusinessException duplicateName(String name) {
        return new BusinessException(AdminErrorCode.COMPANY_NAME_DUPLICATE,
                "활성 회사명 중복: " + name, Map.of("companyName", name));
    }

    /**
     * 검색어 없음 → 빈 문자열. {@code null} 을 쿼리로 넘기지 않는다.
     *
     * <p>{@code lower(:q)} 에 타입 없는 {@code null} 이 들어가면 Postgres 가 {@code bytea} 로
     * 추론해 {@code function lower(bytea) does not exist} 로 죽는다 — 385·389 가 각각
     * 배포 후에야 이 500 을 만났다. 빈 문자열이면 {@code like '%%'} 라 전체 조회가 되고
     * 쿼리에서 {@code is null} 분기가 아예 사라진다.
     */
    private static String blankToEmpty(String value) {
        return value == null ? "" : value.strip();
    }
}
