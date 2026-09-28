package com.ssafy.motif.domain.core.user;

import com.ssafy.motif.domain.core.auth.AccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {

    /**
     * 회원 검색 (어드민 명세 §3.14.1) — 닉네임 부분 일치 + seq·역할·계정상태 필터.
     *
     * <p><b>삭제된 사용자를 거르지 않는다.</b> 어드민에게는 보여야 한다(§3.14 —
     * {@code isDeleted} 로 표시). 사용자 API 의 §1.8 비노출 규칙은 여기 적용되지 않는다.
     *
     * <p>{@code user_accounts} 는 left join 이다 — 아티스트는 로그인하지 않아 행이 없다.
     * 그래서 {@code accountStatus} 필터를 걸면 아티스트는 자연히 빠진다(정지 대상이
     * 아니므로 맞는 동작이다).
     *
     * <p><b>{@code q} 에 {@code null} 을 넘기지 않는다.</b> 검색어가 없으면 호출부가 빈
     * 문자열로 정규화해 {@code like '%%'} 가 전체 조회가 되게 한다. {@code :q is null}
     * 분기를 두면 타입 없는 {@code null} 을 Postgres 가 {@code bytea} 로 추론해
     * {@code function lower(bytea) does not exist} 로 죽는다 — 2026-09-13 운영 500 의
     * 원인이고 {@code ArtistRepository.search}·{@code SongRepository.search} 가 같은
     * 함정을 밟았다.
     *
     * <p>0012 의 {@code gin_users_nickname}(trigram)이 앞뒤 와일드카드 ILIKE 를 받친다.
     * 정렬은 {@code userSeq DESC} 고정 — 어드민 목록은 서버 고정 정렬이다(§1.5).
     */
    @Query(value = """
            select new com.ssafy.motif.domain.core.user.UserListRow(
                       u.userSeq, u.nickname, u.profileImageKey, u.role,
                       ua.accountStatus, u.deleted, u.regDttm)
            from User u
              left join UserAccount ua on ua.user = u
            where (:userSeq is null or u.userSeq = :userSeq)
              and (:role is null or u.role = :role)
              and (:accountStatus is null or ua.accountStatus = :accountStatus)
              and lower(u.nickname) like lower(concat('%', :q, '%'))
            order by u.userSeq desc
            """,
            countQuery = """
            select count(u)
            from User u
              left join UserAccount ua on ua.user = u
            where (:userSeq is null or u.userSeq = :userSeq)
              and (:role is null or u.role = :role)
              and (:accountStatus is null or ua.accountStatus = :accountStatus)
              and lower(u.nickname) like lower(concat('%', :q, '%'))
            """)
    Page<UserListRow> search(@Param("q") String q,
                             @Param("userSeq") Integer userSeq,
                             @Param("role") UserRole role,
                             @Param("accountStatus") AccountStatus accountStatus,
                             Pageable pageable);

    /** 회원 상세의 머리 부분 (§3.14.2) — 목록 한 줄과 같은 투영을 재사용한다. */
    @Query("""
            select new com.ssafy.motif.domain.core.user.UserListRow(
                       u.userSeq, u.nickname, u.profileImageKey, u.role,
                       ua.accountStatus, u.deleted, u.regDttm)
            from User u
              left join UserAccount ua on ua.user = u
            where u.userSeq = :userSeq
            """)
    Optional<UserListRow> findRow(@Param("userSeq") Integer userSeq);

    /**
     * 회원 상세의 통계 6종 (§3.14.2) — 서브쿼리 6개를 한 행으로 뽑는다.
     *
     * <p>네이티브인 이유와 별칭 따옴표는 {@link UserStatsRow} 참조.
     *
     * <p><b>유효 패스 수는 여기 없다</b> — {@code MotifPassRepository.findActive} 가 준
     * 목록의 크기다. 같은 판정을 두 곳에 두면 한쪽만 고쳤을 때 카운트와 목록이 어긋난다.
     */
    @Query(value = """
            select
              (select count(*) from motif_core.participation_videos pv
                where pv.user_seq = :userSeq and pv.is_deleted = false)        as "videoCount",
              (select count(*) from motif_core.subscriptions s
                where s.user_seq = :userSeq)                                   as "subscriptionCount",
              (select count(*) from motif_core.asset_purchases ap
                where ap.user_seq = :userSeq and ap.status = 'ACTIVE')         as "activePurchaseCount",
              (select count(*) from motif_core.reports r
                where r.video_seq in (select pv.video_seq from motif_core.participation_videos pv
                                       where pv.user_seq = :userSeq)
                   or r.challenge_comment_seq in (select cc.challenge_comment_seq from motif_core.challenge_comments cc
                                                   where cc.user_seq = :userSeq)
                   or r.video_comment_seq in (select vc.video_comment_seq from motif_core.video_comments vc
                                               where vc.user_seq = :userSeq)
                   or r.notice_comment_seq in (select nc.notice_comment_seq from motif_core.notice_comments nc
                                                where nc.user_seq = :userSeq)) as "reportedCount",
              (select count(*) from motif_core.reports r2
                where r2.reporter_seq = :userSeq)                              as "reportCount"
            """, nativeQuery = true)
    UserStatsRow stats(@Param("userSeq") Integer userSeq);

}
