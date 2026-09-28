package com.ssafy.motif.domain.core.artist;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Integer> {

    /** 삭제된 소속사에는 아티스트를 붙이지 않는다 (§3.5.2 검증) — 수정·삭제 대상 조회도 같다. */
    Optional<Company> findByCompanySeqAndDeletedFalse(Integer companySeq);

    /** 활성 회사명 중복 (§3.3.2 등록) — {@code uq_companies_name_active} 를 앱에서 먼저 본다. */
    boolean existsByCompanyNameAndDeletedFalse(String companyName);

    /** 활성 회사명 중복 (§3.3.3 수정) — 자기 자신은 제외해야 이름을 그대로 둔 수정이 409 가 안 된다. */
    boolean existsByCompanyNameAndDeletedFalseAndCompanySeqNot(String companyName, Integer companySeq);

    /**
     * 소속사 목록·검색 (§3.3.1) — 이름 부분 일치 + offset 페이지.
     *
     * <p>참조 수(그룹·아티스트)를 <b>상관 서브쿼리로 같은 질의에서</b> 센다. 행마다 따로
     * 세면 한 페이지(최대 100)에 200 왕복이 되고, 페이지 단위 집계를 따로 만들면 Map 조립
     * 코드가 붙는다 — 소속사는 수십 건 규모라 서브쿼리 두 개가 가장 싸다. 두 서브쿼리 모두
     * FK 칼럼({@code artist_groups.company_seq}·{@code artists.company_seq}) 조건이다.
     *
     * <p><b>{@code q} 에 {@code null} 을 넘기지 않는다.</b> 검색어가 없으면 호출부가 빈
     * 문자열로 정규화해 넘기고 {@code like '%%'} 가 전체 조회가 된다. {@code :q is null}
     * 분기를 두면 타입 없는 {@code null} 을 Postgres 가 {@code bytea} 로 추론해
     * {@code function lower(bytea) does not exist} 로 죽는다 — 2026-09-13 운영 500 이
     * 정확히 이것이었다({@link ArtistRepository#search} javadoc).
     *
     * <p>{@code company_name} 에는 trigram 인덱스가 없다(0012 는 그룹명·닉네임만 건다).
     * 소속사 건수가 작아 순차 스캔으로 충분하다 — 수천 건이 되면 GIN 을 더한다.
     *
     * <p>정렬은 {@code companySeq DESC} 고정이다 — 최신 등록순 하나만 화면이 요구한다(§1.5).
     */
    @Query(value = """
            select new com.ssafy.motif.domain.core.artist.CompanyListRow(
                       c.companySeq, c.companyName,
                       (select count(g) from ArtistGroup g
                         where g.companySeq = c.companySeq and g.deleted = false),
                       (select count(a) from Artist a
                         where a.companySeq = c.companySeq and a.deleted = false),
                       c.deleted, c.regDttm)
            from Company c
            where (:includeDeleted = true or c.deleted = false)
              and lower(c.companyName) like lower(concat('%', :q, '%'))
            order by c.companySeq desc
            """,
            countQuery = """
            select count(c)
            from Company c
            where (:includeDeleted = true or c.deleted = false)
              and lower(c.companyName) like lower(concat('%', :q, '%'))
            """)
    Page<CompanyListRow> search(@Param("q") String q,
                                @Param("includeDeleted") boolean includeDeleted,
                                Pageable pageable);

    /** 수정·삭제 응답으로 돌려줄 한 줄 (§3.3.3) — 목록과 같은 투영이다. 삭제분도 준다. */
    @Query("""
            select new com.ssafy.motif.domain.core.artist.CompanyListRow(
                       c.companySeq, c.companyName,
                       (select count(g) from ArtistGroup g
                         where g.companySeq = c.companySeq and g.deleted = false),
                       (select count(a) from Artist a
                         where a.companySeq = c.companySeq and a.deleted = false),
                       c.deleted, c.regDttm)
            from Company c
            where c.companySeq = :companySeq
            """)
    Optional<CompanyListRow> findRow(@Param("companySeq") Integer companySeq);
}
