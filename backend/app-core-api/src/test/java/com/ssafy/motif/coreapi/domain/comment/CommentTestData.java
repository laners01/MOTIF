package com.ssafy.motif.coreapi.domain.comment;

import com.ssafy.motif.coreapi.domain.video.VideoTestData;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.UUID;

/**
 * §9 댓글 테스트의 픽스처 — 대상 3종({@code challenges}·{@code participation_videos}·
 * {@code notices})과 그 위의 댓글·좋아요 행을 세운다.
 *
 * <p><b>FK 체인은 {@link VideoTestData} 를 그대로 쓴다</b> — users → companies → artists
 * → songs → challenges → composite_jobs → participation_videos 가 이미 거기 있다.
 * 같은 체인을 다시 만들면 두 픽스처가 서서히 달라지고, 그 차이가 테스트 실패의 원인처럼
 * 보이기 시작한다.
 *
 * <p><b>엔티티로 세우지 못한다.</b> 댓글 엔티티는 읽기 전용이라 생성자가 없다(작성은
 * §9.3 소관). 그래서 네이티브 INSERT 다.
 *
 * <p>테이블 이름을 문자열로 받는 것은 세 대상의 DDL 이 칼럼 이름만 다르고 모양이 같기
 * 때문이다 — 같은 INSERT 를 세 번 쓰면 한 곳만 고쳤을 때 대상별로 다른 데이터가 선다.
 */
public final class CommentTestData {

    private final EntityManager entityManager;
    private final VideoTestData videos;

    public CommentTestData(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.videos = new VideoTestData(entityManager);
    }

    public VideoTestData videos() {
        return videos;
    }

    /** 공지 한 건 — {@code notices} 에는 {@code reg_usr_seq} 가 없다(DB §27). */
    public Integer newNotice(Integer artistSeq) {
        String content = "t-" + UUID.randomUUID();
        execute("insert into motif_core.notices (artist_seq, content) values (?1, ?2)",
                artistSeq, content);
        return ((Number) entityManager
                .createNativeQuery("select notice_seq from motif_core.notices where content = ?1")
                .setParameter(1, content)
                .getSingleResult()).intValue();
    }

    /**
     * {@code artists.artist_seq} → {@code users.user_seq}.
     *
     * <p>아티스트로 <b>댓글을 쓰려면</b> 필요하다 — 댓글의 작성자는 {@code artist_seq} 가
     * 아니라 {@code user_seq} 이고, 정렬 1순위 판정도 그 값으로 한다.
     */
    public Integer artistUserSeq(Integer artistSeq) {
        return ((Number) entityManager
                .createNativeQuery("select user_seq from motif_core.artists where artist_seq = ?1")
                .setParameter(1, artistSeq)
                .getSingleResult()).intValue();
    }

    /** 참여 영상의 댓글 잠금 — {@code VideoTestData} 는 이 칼럼을 기본값(TRUE)으로 둔다. */
    public void disableComments(Long videoSeq) {
        execute("update motif_core.participation_videos set comment_enabled = false"
                + " where video_seq = ?1", videoSeq);
        entityManager.flush();
        entityManager.clear();
    }

    /** 사용자를 <b>탈퇴</b> 상태로 — §1.8 비노출 검증용. */
    public void deleteUser(Integer userSeq) {
        execute("update motif_core.users set is_deleted = true where user_seq = ?1", userSeq);
        entityManager.flush();
        entityManager.clear();
    }

    public CommentFixture challengeComment(Integer challengeSeq) {
        return new CommentFixture("challenge_comments", "challenge_comment_seq",
                "challenge_seq", challengeSeq);
    }

    public CommentFixture videoComment(Long videoSeq) {
        return new CommentFixture("video_comments", "video_comment_seq", "video_seq", videoSeq);
    }

    public CommentFixture noticeComment(Integer noticeSeq) {
        return new CommentFixture("notice_comments", "notice_comment_seq",
                "notice_seq", noticeSeq);
    }

    public void likeChallengeComment(Long commentSeq, Integer userSeq) {
        like("challenge_comment_likes", "challenge_comment_seq", commentSeq, userSeq);
    }

    public void likeVideoComment(Long commentSeq, Integer userSeq) {
        like("video_comment_likes", "video_comment_seq", commentSeq, userSeq);
    }

    public void likeNoticeComment(Long commentSeq, Integer userSeq) {
        like("notice_comment_likes", "notice_comment_seq", commentSeq, userSeq);
    }

    private void like(String table, String column, Long commentSeq, Integer userSeq) {
        execute("insert into motif_core." + table + " (" + column + ", user_seq) values (?1, ?2)",
                commentSeq, userSeq);
        entityManager.flush();
        entityManager.clear();
    }

    private void execute(String sql, Object... params) {
        var query = entityManager.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) {
            query.setParameter(i + 1, params[i]);
        }
        query.executeUpdate();
    }

    /**
     * 댓글 한 건. {@code regDttm} 을 직접 지정할 수 있어야 한다 — 정렬이 <b>그룹 우선순위
     * 안에서 최신순</b>이라 시각을 흔들지 않고는 순서를 단언할 수 없다.
     *
     * <p><b>{@code content} 는 대상 안에서 유일해야 한다</b> — 만든 행의 PK 를 그것으로
     * 되찾는다. 본문이 곧 단언 대상이라 무작위 마커로 덮어쓰지 않는다.
     */
    public final class CommentFixture {

        private final String table;
        private final String pkColumn;
        private final String targetColumn;
        private final Object targetSeq;
        private Integer author;
        private Long parent;
        private String content = "댓글";
        private int likeCount;
        private boolean deleted;
        private Instant regDttm = Instant.now();

        private CommentFixture(String table, String pkColumn, String targetColumn,
                               Object targetSeq) {
            this.table = table;
            this.pkColumn = pkColumn;
            this.targetColumn = targetColumn;
            this.targetSeq = targetSeq;
        }

        public CommentFixture author(Integer userSeq) {
            this.author = userSeq;
            return this;
        }

        /** 답글로 만든다 — 인자는 <b>루트</b> 댓글의 PK. */
        public CommentFixture replyTo(Long parentCommentSeq) {
            this.parent = parentCommentSeq;
            return this;
        }

        public CommentFixture content(String value) {
            this.content = value;
            return this;
        }

        public CommentFixture likes(int value) {
            this.likeCount = value;
            return this;
        }

        public CommentFixture deleted() {
            this.deleted = true;
            return this;
        }

        public CommentFixture at(Instant value) {
            this.regDttm = value;
            return this;
        }

        public Long insert() {
            execute("""
                    insert into motif_core.%s
                        (%s, user_seq, parent_comment_seq, content, like_count, is_deleted, reg_dttm)
                    values (?1, ?2, ?3, ?4, ?5, ?6, ?7)
                    """.formatted(table, targetColumn),
                    targetSeq, author, parent, content, likeCount, deleted, regDttm);
            entityManager.flush();
            entityManager.clear();

            return ((Number) entityManager
                    .createNativeQuery("select %s from motif_core.%s where %s = ?1 and content = ?2"
                            .formatted(pkColumn, table, targetColumn))
                    .setParameter(1, targetSeq)
                    .setParameter(2, content)
                    .getSingleResult()).longValue();
        }
    }
}
