package com.ssafy.motif.domain.core.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /**
     * 참여 영상 신고 접수 (API 명세 §8.7).
     *
     * <p><b>중복 판정을 DB 가 한다.</b> "이미 신고했는가"를 먼저 묻고 갈라지면 묻는
     * 시점과 쓰는 시점이 벌어져, 동시에 들어온 두 요청이 둘 다 통과한다 — 그러면
     * {@code uq_reports_reporter_video} 위반이 500 으로 터진다. {@code on conflict do
     * nothing} 은 확인과 삽입을 한 문장에서 끝내므로 <b>반환값이 곧 "내가 새로
     * 접수했는가"</b> 다({@code SubscriptionRepository.insertIfAbsent} 와 같은 방식).
     *
     * <p><b>엔티티로 저장하지 않는 이유가 하나 더 있다.</b> 대상 네 칸 중 하나만 채우는
     * XOR CHECK 가 걸려 있어 JPA 로 쓰면 나머지 세 칸을 명시적으로 비워야 하고, 그 규칙이
     * 엔티티 생성자에 복제된다. 접수 문장이 곧 규칙인 편이 낫다.
     *
     * <p>{@code status}·{@code reg_dttm} 을 넣지 않는 것은 DDL 에 기본값이 있기
     * 때문이다(0008-01: {@code 'RECEIVED'} · {@code CURRENT_TIMESTAMP}). 엔티티의
     * {@code @PrePersist} 는 네이티브라 타지 않는다.
     *
     * @return 1 이면 접수됐다. <b>0 이면 이미 신고한 대상</b>이라 호출부가 409 로 답한다
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into motif_core.reports (reporter_seq, video_seq, reason_code)
            values (:reporterSeq, :videoSeq, :reasonCode)
            on conflict do nothing
            """, nativeQuery = true)
    int insertVideoReport(@Param("reporterSeq") Integer reporterSeq,
                          @Param("videoSeq") Long videoSeq,
                          @Param("reasonCode") String reasonCode);

    /** 접수된 신고를 되읽는다 — 지금은 테스트가 쓴다. */
    Optional<Report> findByReporterSeqAndVideoSeq(Integer reporterSeq, Long videoSeq);

    /**
     * 챌린지 댓글 신고 접수 (API 명세 §9.6).
     *
     * <p>중복 판정을 DB 가 하는 이유와 네이티브인 이유는 {@link #insertVideoReport} 에
     * 적었다 — 대상 칸만 다르다.
     *
     * <p><b>대상 네 칸 중 하나만 채운다</b>({@code ck_reports_exactly_one_target}).
     * 나머지 세 칸은 문장에 넣지 않아 NULL 로 남고, 그 NULL 덕에 UNIQUE 4종이 서로
     * 간섭하지 않는다(PostgreSQL 기본 NULLS DISTINCT — changeSet 0008 주석).
     * <b>그래서 타입이 다르면 같은 번호를 각각 신고할 수 있다</b>: 챌린지 댓글 501 과
     * 공지 댓글 501 은 다른 대상이다.
     *
     * @return 1 이면 접수됐다. <b>0 이면 이미 신고한 대상</b>이라 호출부가 409 로 답한다
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into motif_core.reports (reporter_seq, challenge_comment_seq, reason_code)
            values (:reporterSeq, :commentSeq, :reasonCode)
            on conflict do nothing
            """, nativeQuery = true)
    int insertChallengeCommentReport(@Param("reporterSeq") Integer reporterSeq,
                                     @Param("commentSeq") Long commentSeq,
                                     @Param("reasonCode") String reasonCode);

    /** 참여 영상 댓글 신고 접수 (§9.6) — {@link #insertChallengeCommentReport} 참고. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into motif_core.reports (reporter_seq, video_comment_seq, reason_code)
            values (:reporterSeq, :commentSeq, :reasonCode)
            on conflict do nothing
            """, nativeQuery = true)
    int insertVideoCommentReport(@Param("reporterSeq") Integer reporterSeq,
                                 @Param("commentSeq") Long commentSeq,
                                 @Param("reasonCode") String reasonCode);

    /** 공지 댓글 신고 접수 (§9.6) — {@link #insertChallengeCommentReport} 참고. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into motif_core.reports (reporter_seq, notice_comment_seq, reason_code)
            values (:reporterSeq, :commentSeq, :reasonCode)
            on conflict do nothing
            """, nativeQuery = true)
    int insertNoticeCommentReport(@Param("reporterSeq") Integer reporterSeq,
                                  @Param("commentSeq") Long commentSeq,
                                  @Param("reasonCode") String reasonCode);

    /**
     * 어드민 신고 목록 (어드민 명세 §3.13.1) — S15P21D104-578.
     *
     * <p><b>접수순 고정이다.</b> 오래 방치된 신고가 먼저 보여야 한다 — 최신순이면
     * 밀린 것이 계속 아래로 내려간다.
     *
     * <p>{@code targetType} 필터는 채워진 칸으로 가른다. enum 을 문자열로 받는 이유는
     * {@code reports} 에 종류 칸이 없어서다 — 4개 대상 컬럼의 NULL 여부가 곧 종류다.
     */
    @Query("""
            select r from Report r
             where (:status is null or r.status = :status)
               and (:targetType is null
                    or (:targetType = 'VIDEO'             and r.videoSeq is not null)
                    or (:targetType = 'CHALLENGE_COMMENT' and r.challengeCommentSeq is not null)
                    or (:targetType = 'VIDEO_COMMENT'     and r.videoCommentSeq is not null)
                    or (:targetType = 'NOTICE_COMMENT'    and r.noticeCommentSeq is not null))
             order by r.regDttm asc, r.reportSeq asc
            """)
    Page<Report> search(@Param("status") ReportStatus status,
                        @Param("targetType") String targetType,
                        Pageable pageable);

    /**
     * 같은 대상에 쌓인 신고 수 (§3.13.1 {@code sameTargetReportCount}).
     *
     * <p>한 영상에 여러 사람이 신고하면 행이 그만큼 생긴다({@code UNIQUE(reporter, video)}
     * 는 같은 사람의 중복만 막는다). 처리자가 "여러 명이 신고했다" 를 보고 판단하라는 값이다.
     */
    long countByVideoSeq(Long videoSeq);
}
