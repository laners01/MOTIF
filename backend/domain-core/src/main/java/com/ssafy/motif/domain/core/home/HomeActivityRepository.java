package com.ssafy.motif.domain.core.home;

import com.ssafy.motif.domain.core.asset.MotifAsset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

/**
 * 홈 구독 활동 피드의 <b>정렬·페이지 전용</b> 저장소 (API 명세 §6.3).
 *
 * <p><b>왜 별도 인터페이스인가.</b> 이 쿼리는 {@code motif_assets} 와 {@code notices}
 * 둘에 걸쳐 있어 어느 엔티티의 저장소에도 속하지 않는다. 한쪽에 끼워 넣으면 그
 * 저장소가 자기 테이블 밖을 읽게 되고, 나중에 타입이 하나 더 늘 때 어디를 고쳐야
 * 하는지가 이름에서 사라진다. Spring Data 가 도메인 타입을 요구해
 * {@link MotifAsset} 을 적어 두지만 <b>그 엔티티를 읽지는 않는다</b> —
 * {@link Repository} 만 상속해 CRUD 를 물려받지 않는 이유다.
 *
 * <p><b>키만 가져온다.</b> 본문·이미지·카운트는 여기서 읽지 않고 호출부가 타입별로
 * 배치 조회한다. 두 타입은 칸 구성이 달라 UNION 에 본문까지 넣으려면 빈 칸을 맞춰야
 * 하고, 그러면 <b>버려질 행의 TEXT 본문까지 정렬 대상</b>이 된다.
 */
public interface HomeActivityRepository extends Repository<MotifAsset, Integer> {

    /**
     * 구독 아티스트의 활동을 등록 시각 최신순으로 한 페이지 (§6.3).
     *
     * <p>정렬은 {@code reg_dttm DESC, type ASC, seq DESC} 다. 보조 키 둘이 없으면
     * <b>같은 초에 등록된 항목이 페이지마다 자리를 바꿔</b> offset 페이지에서 중복·누락이
     * 난다. {@code Pageable} 에 정렬을 실어 보내지 않는다 — 서버 고정 정렬이고
     * ({@code sort} 를 받지 않는다) 여기 {@code order by} 가 유일한 근거다.
     *
     * <p><b>{@code artistSeqs} 가 비면 부르지 않는다.</b> 빈 {@code in ()} 은 Postgres
     * 문법 오류다. 구독 0건은 호출부가 목록 조회 전에 빈 페이지로 끊는다.
     *
     * <p>별칭에 따옴표를 두른 이유는 Postgres 가 따옴표 없는 식별자를 소문자로 내리기
     * 때문이다 — 그대로 두면 {@code regDttm} 이 {@code regdttm} 으로 와서 프로젝션
     * 게터와 맞지 않는다({@code MotifAssetRepository.search} 와 같은 함정).
     *
     * <p>count 는 UNION 을 다시 세지 않고 <b>두 스칼라 서브쿼리의 합</b>이다 — 세는 데
     * 정렬이 필요 없다.
     */
    @Query(value = """
            select 'MOTIF_ASSET' as "type", a.asset_seq as "seq", a.reg_dttm as "regDttm"
              from motif_core.motif_assets a
             where a.artist_seq in (:artistSeqs)
               and a.is_active = true
            union all
            select 'NOTICE' as "type", n.notice_seq as "seq", n.reg_dttm as "regDttm"
              from motif_core.notices n
             where n.artist_seq in (:artistSeqs)
               and n.is_deleted = false
             order by "regDttm" desc, "type" asc, "seq" desc
            """,
            countQuery = """
            select (select count(*) from motif_core.motif_assets a
                     where a.artist_seq in (:artistSeqs) and a.is_active = true)
                 + (select count(*) from motif_core.notices n
                     where n.artist_seq in (:artistSeqs) and n.is_deleted = false)
            """,
            nativeQuery = true)
    Page<ActivityKeyRow> findActivities(@Param("artistSeqs") Collection<Integer> artistSeqs,
                                        Pageable pageable);
}
