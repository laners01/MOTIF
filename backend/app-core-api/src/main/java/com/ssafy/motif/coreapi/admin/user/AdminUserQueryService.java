package com.ssafy.motif.coreapi.admin.user;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.admin.user.dto.UserDetailResponse;
import com.ssafy.motif.coreapi.admin.user.dto.UserSummaryResponse;
import com.ssafy.motif.coreapi.domain.user.UserErrorCode;
import com.ssafy.motif.coreapi.global.media.PublicMediaUrlResolver;
import com.ssafy.motif.coreapi.global.response.PageResponses;
import com.ssafy.motif.domain.core.auth.AccountStatus;
import com.ssafy.motif.domain.core.pass.ActivePassRow;
import com.ssafy.motif.domain.core.pass.MotifPassRepository;
import com.ssafy.motif.domain.core.user.UserListRow;
import com.ssafy.motif.domain.core.user.UserRepository;
import com.ssafy.motif.domain.core.user.UserRole;
import com.ssafy.motif.domain.core.user.UserStatsRow;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 회원 검색·상세 (어드민 명세 §3.14.1~2) — S15P21D104-581.
 *
 * <p><b>삭제 회원을 감추지 않는다.</b> 사용자 API 는 삭제 사용자의 존재까지 숨기지만
 * (§1.8, {@code UserErrorCode.USER_NOT_FOUND} javadoc) 운영은 "탈퇴한 회원의 신고 이력"
 * 을 봐야 한다. 404 는 정말 없는 {@code userSeq} 일 때만이다.
 *
 * <p>정지·해제(§3.14.3~4)는 {@link AdminUserCommandService} 다 — 사용자 도메인의
 * {@code UserQueryService}·{@code UserCommandService} 와 같은 가름이다. 두 쪽이 쓰는
 * 협력자가 {@code UserRepository} 하나만 겹친다.
 */
@Service
@RequiredArgsConstructor
public class AdminUserQueryService {

    private final UserRepository userRepository;
    private final MotifPassRepository motifPassRepository;
    private final PublicMediaUrlResolver mediaUrlResolver;

    /** §3.14.1 — 닉네임 부분 일치 + seq·역할·계정상태 필터, 최신 가입순. */
    @Transactional(readOnly = true)
    public PageResponse<UserSummaryResponse> list(String q, Integer userSeq, UserRole role,
                                                  AccountStatus accountStatus, int page, int size) {
        Page<UserListRow> rows = userRepository.search(
                blankToEmpty(q), userSeq, role, accountStatus, PageResponses.pageable(page, size));
        return PageResponses.of(rows, this::summary);
    }

    /** §3.14.2 — 통계 6종과 보유 패스를 함께 준다. */
    @Transactional(readOnly = true)
    public UserDetailResponse detail(Integer userSeq) {
        UserListRow row = userRepository.findRow(userSeq)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND,
                        "존재하지 않는 사용자: userSeq=" + userSeq));

        UserStatsRow stats = userRepository.stats(userSeq);
        List<ActivePassRow> activePasses = motifPassRepository.findActive(userSeq);

        return new UserDetailResponse(
                row.userSeq(), row.nickname(), row.role(), mediaUrlResolver.resolve(row.profileImageKey()),
                row.accountStatus(), row.deleted(), row.regDttm(),
                new UserDetailResponse.Stats(
                        stats.getVideoCount(), stats.getSubscriptionCount(),
                        stats.getActivePurchaseCount(), activePasses.size(),
                        stats.getReportedCount(), stats.getReportCount()),
                activePasses.stream().map(AdminUserQueryService::activePass).toList());
    }

    private UserSummaryResponse summary(UserListRow row) {
        return new UserSummaryResponse(
                row.userSeq(), row.nickname(), row.role(), mediaUrlResolver.resolve(row.profileImageKey()),
                row.accountStatus(), row.deleted(), row.regDttm());
    }

    private static UserDetailResponse.ActivePass activePass(ActivePassRow row) {
        return new UserDetailResponse.ActivePass(
                row.artistSeq(), row.artistName(), row.expiresDttm());
    }

    /** {@code UserRepository.search} 가 {@code null} 검색어를 받지 못한다 — 그 javadoc 참조. */
    private static String blankToEmpty(String q) {
        return q == null ? "" : q.strip();
    }
}
