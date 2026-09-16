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

    /**
     * HOT 랭킹 배치의 재료 — 스냅샷 후보 전량 (API 명세 §6.1).
     *
     * <p>계산은 {@code VideoRankingScore}(순수)가 하고 여기는 재료만 꺼낸다.
     *
     * <p><b>모집단이 곧 노출 범위다.</b> §6.1 은 스냅샷 순서를 그대로 내보내므로
     * 여기서 빠진 영상은 HOT 에 아예 오르지 않는다. 그래서 §1.8 공통 비노출을
     * 여기에 건다 — 삭제된 챌린지에 속한 영상과 삭제 사용자의 영상을 뺀다.
     * {@link #findMine} 과 같은 이유로 {@code exists} 를 쓴다(연관 매핑을 두면 쓰지도
     * 않을 조인이 따라다닌다).
     *
     * <p><b>{@code PRIVATE} 은 후보가 아니다.</b> 인기 목록은 남의 영상을 보여 주는
     * 자리고 {@code PRIVATE} 은 본인 외에 존재 자체를 숨긴다(§1.8).
     *
     * <p><b>공개 사본 여부는 여기서 보지 않는다.</b> 키 프리픽스로 등급을 판정하는
     * 지점은 {@code MediaKeys} 하나여야 하므로({@code 'private/%'} 리터럴을 SQL 에
     * 넣으면 규약이 두 곳으로 갈라진다) {@code videoKey} 를 실어 보내고 배치가 건다.
     *
     * <p>집계 없이 전량을 준다 — 감쇠가 영상 하나하나에 걸려야 의미가 있다. 5분에
     * 한 번이라 전량 스캔이 부담되지 않는다({@code ChallengeRepository.findRankingInputs}
     * 와 같은 판단).
     */
    @Query("""
            select new com.ssafy.motif.domain.core.video.VideoScoreRow(
                       v.videoSeq, v.viewCount, v.regDttm, v.videoKey)
            from ParticipationVideo v
            where v.deleted = false
              and v.visibility = com.ssafy.motif.domain.core.video.Visibility.PUBLIC
              and exists (select 1 from Challenge c
                           where c.challengeSeq = v.challengeSeq and c.deleted = false)
              and exists (select 1 from User u
                           where u.userSeq = v.userSeq and u.deleted = false)
            """)
    List<VideoScoreRow> findRankingInputs();

    /**
     * HOT 카드 배치 조회 — 스냅샷이 준 {@code videoSeq} 묶음을 한 번에 읽는다
     * (API 명세 §6.1).
     *
     * <p><b>비노출 조건을 한 번 더 건다.</b> {@link #findRankingInputs} 가 이미 걸렀지만
     * 스냅샷은 5분마다 갱신되므로 그 사이에 삭제·비공개로 바뀐 영상이 남아 있다.
     * 명세 §1.8 은 조회 API <b>전부</b>에 적용되는 규칙이라 읽는 쪽에서 막는다.
     *
     * <p>그래서 결과가 요청한 {@code videoSeqs} 보다 적을 수 있고, 응답의 {@code rank}
     * 에 건너뛴 번호가 생긴다. {@code rank} 는 스냅샷 순위라서 그것이 맞는 값이다 —
     * 번호를 다시 매기면 같은 커서로 이어 받은 페이지와 어긋난다.
     *
     * <p>{@code in} 은 순서를 보장하지 않는다 — 호출부가 스냅샷 순서로 다시 세운다.
     */
    @Query("""
            select new com.ssafy.motif.domain.core.video.HotVideoRow(
                       v.videoSeq, v.videoKey, v.thumbnailKey, v.durationMs, v.viewCount,
                       s.title, s.singerName, u.userSeq, u.nickname)
            from ParticipationVideo v
              join Challenge c on c.challengeSeq = v.challengeSeq
              join Song s on s.songSeq = c.songSeq
              join User u on u.userSeq = v.userSeq
            where v.videoSeq in :videoSeqs
              and v.deleted = false
              and v.visibility = com.ssafy.motif.domain.core.video.Visibility.PUBLIC
              and c.deleted = false
              and u.deleted = false
            """)
    List<HotVideoRow> findHotCards(@Param("videoSeqs") Collection<Long> videoSeqs);
}
