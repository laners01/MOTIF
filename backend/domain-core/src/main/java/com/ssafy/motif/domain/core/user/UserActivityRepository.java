package com.ssafy.motif.domain.core.user;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * 내 활동 피드의 <b>정렬·페이지 전용</b> 저장소 (API 명세 §4.9).
 *
 * <p><b>왜 별도 인터페이스인가.</b> 이 쿼리는 {@code video_likes}·{@code video_comments}·
 * {@code participation_videos} 셋에 걸쳐 있어 어느 엔티티의 저장소에도 속하지 않는다
 * (§6.3 {@code HomeActivityRepository} 와 같은 판단). Spring Data 가 도메인 타입을
 * 요구해 {@link User} 를 적어 두지만 <b>그 엔티티를 읽지는 않는다</b> —
 * {@link Repository} 만 상속해 CRUD 를 물려받지 않는 이유다.
 *
 * <p><b>키만 가져온다.</b> 카드 본문(썸네일·카운트)·내 댓글·답글은 여기서 읽지 않고
 * 호출부가 배치로 조회한다. 하나로 합치면 좋아요/댓글 조인이 카드마다 행을 불려
 * {@code group by} 대상이 통째로 커지고, <b>버려질 행의 TEXT 본문까지 정렬 대상</b>이 된다.
 */
public interface UserActivityRepository extends Repository<User, Integer> {

    /**
     * 좋아요와 댓글을 <b>영상 단위로 합치는</b> 부분 (§4.9 "둘 다 있으면 카드 1장").
     *
     * <p>{@code union all} 로 쌓고 {@code max} 로 접는다 — 좋아요·댓글 어느 쪽이든
     * 마지막 것이 그 영상의 상호작용 시각이다. {@code union}(중복 제거)이 아닌 이유는
     * 어차피 {@code group by} 로 접기 때문이고, 중복 제거는 정렬을 한 번 더 시킨다.
     *
     * <p><b>삭제된 내 댓글은 세지 않는다.</b> 좋아요만 남았다면 카드는 남고 응답의
     * {@code myComment} 가 {@code null} 이 된다 — 카드까지 사라지면 "좋아요는 그대로인데
     * 활동에서 없어졌다" 가 된다.
     */
    String ACTIVITY_FROM = " from (select l.video_seq as video_seq, l.reg_dttm as at"
            + "                       from motif_core.video_likes l"
            + "                      where l.user_seq = :userSeq"
            + "                      union all"
            + "                     select c.video_seq, c.reg_dttm"
            + "                       from motif_core.video_comments c"
            + "                      where c.user_seq = :userSeq"
            + "                        and c.is_deleted = false) i"
            + "  join motif_core.participation_videos v on v.video_seq = i.video_seq"
            + "  join motif_core.users u on u.user_seq = v.user_seq and u.is_deleted = false"
            + "  join motif_core.challenges ch on ch.challenge_seq = v.challenge_seq"
            + "                              and ch.is_deleted = false"
            + " where v.is_deleted = false"
            // 본인 게시물 제외(§4.9). 이걸 먼저 걸었으므로 아래 PUBLIC 조건이 곧
            // "타인의 PRIVATE 제외" 다 — 내 PRIVATE 을 살릴 예외가 필요 없다
            + "   and v.user_seq <> :userSeq"
            + "   and v.visibility = 'PUBLIC'"
            // ACTIVITY-02 — 댓글이 잠긴 영상은 좋아요만 눌렀어도 카드째 뺀다.
            // 카드의 본체가 댓글 스레드라 열어도 보여 줄 것이 없다
            + "   and v.comment_enabled = true"
            + " group by v.video_seq";

    /**
     * 정렬·절단. {@code video_seq} 보조 키가 없으면 <b>같은 밀리초에 상호작용한 카드가
     * 호출마다 자리를 바꿔</b> 커서 페이지에 중복·누락이 생긴다(§8.1 피드와 같은 근거).
     *
     * <p>별칭에 따옴표를 두른 이유는 Postgres 가 따옴표 없는 식별자를 소문자로 내리기
     * 때문이다 — 그대로 두면 {@code videoSeq} 가 {@code videoseq} 로 와서 투영 게터와
     * 맞지 않는다({@code ActivityKeyRow} 와 같은 함정).
     */
    String ACTIVITY_ORDER = " order by \"lastInteractedDttm\" desc, \"videoSeq\" desc"
            + " limit :limit";

    String ACTIVITY_SELECT =
            "select v.video_seq as \"videoSeq\", max(i.at) as \"lastInteractedDttm\"";

    /**
     * 내 활동 첫 페이지의 순서 (API 명세 §4.9).
     *
     * <p>카드 본문을 함께 읽지 않는다 — 순서를 정하는 쿼리와 본문을 읽는 쿼리를
     * 나누면 정렬식이 한 줄로 남는다(§8.1 {@code findFeedKeys} 와 같은 2단 구조).
     *
     * <p>커서 유무로 메서드를 가른다 — 타입 없는 {@code null} 을 Postgres 가 추론하다
     * 운영에서 500 이 나는 함정을 이 프로젝트가 두 번 밟았다({@code ChallengeRepository}).
     */
    @Query(value = ACTIVITY_SELECT + ACTIVITY_FROM + ACTIVITY_ORDER, nativeQuery = true)
    List<UserActivityKeyRow> findActivityKeys(@Param("userSeq") Integer userSeq,
                                              @Param("limit") int limit);

    /**
     * 다음 페이지 — 커서가 가리키는 {@code (상호작용 시각, videoSeq)} 보다 뒤부터.
     *
     * <p>조건이 {@code where} 가 아니라 <b>{@code having}</b> 인 것은 비교 대상이 집계값
     * ({@code max(i.at)})이기 때문이다. {@code where} 에 넣으면 좋아요 한 건이 커서보다
     * 오래됐다는 이유로 <b>같은 영상의 최신 댓글까지</b> 걸러져 카드가 통째로 사라진다.
     */
    @Query(value = ACTIVITY_SELECT + ACTIVITY_FROM
            + " having max(i.at) < :cursorAt"
            + "     or (max(i.at) = :cursorAt and v.video_seq < :cursorVideoSeq)"
            + ACTIVITY_ORDER, nativeQuery = true)
    List<UserActivityKeyRow> findActivityKeysAfter(@Param("userSeq") Integer userSeq,
                                                   @Param("cursorAt") Instant cursorAt,
                                                   @Param("cursorVideoSeq") long cursorVideoSeq,
                                                   @Param("limit") int limit);
}
