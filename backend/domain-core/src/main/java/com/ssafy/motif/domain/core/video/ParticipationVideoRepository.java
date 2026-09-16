package com.ssafy.motif.domain.core.video;

import com.ssafy.motif.domain.core.artist.ArtistCount;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ParticipationVideoRepository extends JpaRepository<ParticipationVideo, Long> {

    /**
     * 내 참여 영상 최신순 — 마이페이지 프리뷰(§4.1)와 전체 그리드(§4.8)가 함께 쓴다.
     *
     * <p><b>{@code PRIVATE} 도 포함한다.</b> 본인 화면이라 둘 다 보여 주고 항목에
     * {@code visibility} 를 실어 클라이언트가 자물쇠를 그린다.
     *
     * <p><b>삭제된 챌린지에 속한 영상은 뺀다</b>(§1.8 공통 비노출) — 영상 자체가
     * 살아 있어도 그렇다. 연관 매핑이 없어 {@code exists} 로 건다: 참여 영상은 챌린지를
     * 통해 아무것도 읽지 않으므로 {@code @ManyToOne} 을 두면 쓰지도 않을 조인이
     * 따라다닌다.
     *
     * <p>{@code reg_dttm} 이 아니라 PK 역순으로 정렬한다 — IDENTITY 라 단조 증가해
     * 최신순과 결과가 같은데, 같은 초에 올라온 영상들이 호출마다 순서를 바꿔 가며
     * 나오는 일이 없다({@code ChallengeRepository} 와 같은 근거).
     *
     * <p><b>건수는 {@link Pageable} 로 부르는 쪽이 정한다.</b> §4.1 은 9건(3×3 화면
     * 사정), §4.8 은 {@code Pageable#unpaged()} 로 전량이다 — 둘 다 같은 필터를 써야
     * 하므로 쿼리를 나누지 않는다. 나누면 §1.8 비노출이 한쪽에서만 고쳐질 수 있다.
     */
    @Query("""
            select v from ParticipationVideo v
            where v.userSeq = :userSeq
              and v.deleted = false
              and exists (select 1 from Challenge c
                           where c.challengeSeq = v.challengeSeq and c.deleted = false)
            order by v.videoSeq desc
            """)
    List<ParticipationVideo> findMine(@Param("userSeq") Integer userSeq, Pageable pageable);

    /**
     * 구독 카드의 {@code challengeCount} (API 명세 §4.7) — 아티스트별 <b>내</b> 영상 수.
     *
     * <p>이름이 challenge 지만 세는 것은 <b>참여 영상</b>이다(명세 §4.7 표).
     *
     * <p><b>{@link #findMine} 과 같은 필터를 쓴다</b> — 삭제된 챌린지에 속한 영상까지
     * 뺀다. 명세는 "내 비삭제 참여 영상 수"라고만 적었지만, 여기서 §1.8 을 빼면
     * <b>§4.8 그리드에는 안 보이는 영상이 이 숫자에는 잡힌다</b>. "5개라는데 4개만
     * 보인다"가 되고 사용자는 어느 쪽이 맞는지 알 수 없다.
     *
     * <p>그래서 명세가 언급한 {@code idx_pv_user_artist} 만으로는 끝나지 않고 챌린지
     * 확인이 붙는다. 목록과 숫자가 어긋나는 것보다 낫다고 본다.
     *
     * <p>아티스트 목록 전체를 한 번에 센다 — 카드마다 부르면 그대로 N+1 이다
     * ({@code MotifAssetRepository.countByArtists} 와 같은 방식).
     */
    @Query("""
            select new com.ssafy.motif.domain.core.artist.ArtistCount(v.artistSeq, count(v))
            from ParticipationVideo v
            where v.userSeq = :userSeq
              and v.artistSeq in :artistSeqs
              and v.deleted = false
              and exists (select 1 from Challenge c
                           where c.challengeSeq = v.challengeSeq and c.deleted = false)
            group by v.artistSeq
            """)
    List<ArtistCount> countMineByArtists(@Param("userSeq") Integer userSeq,
                                         @Param("artistSeqs") Collection<Integer> artistSeqs);

    /**
     * 보호 등급 인가 — 이 잡이 게시됐는지, 됐다면 공개 범위가 무엇인지 (API 명세 §1.7).
     *
     * <p><b>삭제분도 가져온다.</b> 여기서 {@code is_deleted=false} 를 걸면 삭제된 영상이
     * "게시된 적 없음"과 같아져 <b>소유자 전용</b> 분기로 떨어지는데, 그쪽이 더 느슨하다
     * — 삭제한 PUBLIC 영상의 재생목록을 소유자에게 계속 내주게 된다. 삭제 판단은
     * 호출자가 따로 한다.
     */
    Optional<ParticipationVideo> findByJobSeq(Long jobSeq);
}
