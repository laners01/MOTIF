package com.ssafy.motif.coreapi.domain.video;

import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.UUID;

/**
 * §8 참여 영상 테스트의 픽스처 — {@code users} → {@code companies} → {@code artists} →
 * {@code songs} → {@code challenges} → {@code composite_jobs} → {@code participation_videos}
 * FK 체인을 세운다.
 *
 * <p><b>엔티티로 세우지 못한다.</b> {@code ParticipationVideo}·{@code CompositeJob} 은
 * 읽기 전용이라 생성자가 없다(게시는 §8.8 소관). 그래서 네이티브 INSERT 다.
 *
 * <p><b>§8.1 피드와 §8.2 단건이 같은 픽스처를 쓴다.</b> 두 테스트가 각자 세우면
 * "피드에는 보이는데 단건은 404" 같은 차이를 만들어 놓고도 픽스처가 달라서 그런 줄
 * 알게 된다 — 그 둘이 같은 데이터에서 같은 항목을 내는지가 §8.2 의 핵심 단언이다.
 */
public final class VideoTestData {

    private final EntityManager entityManager;

    public VideoTestData(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Integer newUser() {
        String nickname = "t-" + UUID.randomUUID().toString().substring(0, 20);
        execute("insert into motif_core.users (nickname, role) values (?1, 'USER')", nickname);
        return scalar("select user_seq from motif_core.users where nickname = ?1", nickname);
    }

    /** {@code users(ARTIST)} → {@code companies} → {@code artists} FK 체인. */
    public Integer newArtist() {
        Integer userSeq = newUser();
        String companyName = "t-" + UUID.randomUUID().toString().substring(0, 20);
        execute("insert into motif_core.companies (company_name, reg_usr_seq) values (?1, ?2)",
                companyName, userSeq);
        Integer companySeq = scalar(
                "select company_seq from motif_core.companies where company_name = ?1", companyName);

        execute("insert into motif_core.artists (user_seq, company_seq, reg_usr_seq) values (?1, ?2, ?1)",
                userSeq, companySeq);
        return scalar("select artist_seq from motif_core.artists where user_seq = ?1", userSeq);
    }

    public Integer newSong(Integer regUsrSeq) {
        String title = "t-" + UUID.randomUUID().toString().substring(0, 20);
        execute("insert into motif_core.songs (title, singer_name, reg_usr_seq) values (?1, '가수', ?2)",
                title, regUsrSeq);
        return scalar("select song_seq from motif_core.songs where title = ?1", title);
    }

    public Integer newChallenge(Integer artistSeq, Integer songSeq, boolean deleted) {
        String uuid = UUID.randomUUID().toString();
        execute("""
                insert into motif_core.challenges
                    (artist_seq, song_seq, description, video_key, thumbnail_key, duration_ms,
                     is_deleted)
                values (?1, ?2, '소개글', ?3, ?4, 15000, ?5)
                """, artistSeq, songSeq,
                "challenges/" + uuid + ".mp4", "challenges/" + uuid + "/thumb.jpg", deleted);
        return scalar("select challenge_seq from motif_core.challenges where video_key = ?1",
                "challenges/" + uuid + ".mp4");
    }

    public VideoFixture video(Integer challengeSeq, Integer artistSeq) {
        return new VideoFixture(challengeSeq, artistSeq);
    }

    void execute(String sql, Object... params) {
        var query = entityManager.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) {
            query.setParameter(i + 1, params[i]);
        }
        query.executeUpdate();
    }

    private Integer scalar(String sql, Object param) {
        return ((Number) entityManager.createNativeQuery(sql)
                .setParameter(1, param)
                .getSingleResult()).intValue();
    }

    /**
     * 참여 영상 한 건. 칸이 많아 빌더로 둔다 — 위치 인자 8개짜리 헬퍼는 테스트를 읽어도
     * 무엇을 세우는지 알 수 없다.
     */
    public final class VideoFixture {

        private final Integer challengeSeq;
        private final Integer artistSeq;
        private Integer owner;
        private long views;
        private int likes;
        private int comments;
        private boolean artistLike;
        private boolean artistComment;
        private boolean deleted;
        private String visibility = "PUBLIC";
        /** 공개 사본 트리가 없다(ADR-0013) — 게시 여부와 무관하게 보호 프리픽스다. */
        private String prefix = "private/challenge/";

        private VideoFixture(Integer challengeSeq, Integer artistSeq) {
            this.challengeSeq = challengeSeq;
            this.artistSeq = artistSeq;
        }

        public VideoFixture owner(Integer userSeq) {
            this.owner = userSeq;
            return this;
        }

        public VideoFixture views(long viewCount) {
            this.views = viewCount;
            return this;
        }

        public VideoFixture likes(int likeCount) {
            this.likes = likeCount;
            return this;
        }

        public VideoFixture comments(int commentCount) {
            this.comments = commentCount;
            return this;
        }

        public VideoFixture artistLike() {
            this.artistLike = true;
            return this;
        }

        public VideoFixture artistComment() {
            this.artistComment = true;
            return this;
        }

        public VideoFixture deleted() {
            this.deleted = true;
            return this;
        }

        public VideoFixture privateVisibility() {
            this.visibility = "PRIVATE";
            return this;
        }

        public VideoFixture prefix(String keyPrefix) {
            this.prefix = keyPrefix;
            return this;
        }

        /**
         * {@code composite_jobs} 를 먼저 만드는 것은 {@code job_seq} 가 NOT NULL FK 이기
         * 때문이다. 게시된 영상이라 잡은 {@code DONE} 이어야 한다
         * ({@code ck_composite_jobs_status_shape} 가 결과 키를 함께 요구한다).
         */
        public Long insert() {
            String uuid = UUID.randomUUID().toString();

            // composite_jobs 는 워커가 실제로 쓴 정본을 가리킨다. 산출물이 한 자리뿐이라
            // (ADR-0013) 참여 영상의 키와 같은 값이지만, prefix() 로 어긋난 상태를 일부러
            // 만드는 테스트가 있어 따로 둔다
            String originKey = "private/challenge/" + uuid + "/master.m3u8";
            String originThumbKey = "private/challenge/" + uuid + "/thumb.jpg";

            String resultKey = prefix + uuid + "/master.m3u8";
            String thumbKey = prefix + uuid + "/thumb.jpg";

            execute("""
                    insert into motif_core.composite_jobs
                        (job_uuid, job_type, user_seq, challenge_seq, raw_video_key, status,
                         result_video_key, result_thumbnail_key)
                    values (cast(?1 as uuid), 'CHALLENGE', ?2, ?3, ?4, 'DONE', ?5, ?6)
                    """, uuid, owner, challengeSeq, "raw/" + uuid + ".mp4",
                    originKey, originThumbKey);
            long jobSeq = ((Number) entityManager
                    .createNativeQuery("""
                            select job_seq from motif_core.composite_jobs
                             where job_uuid = cast(?1 as uuid)
                            """)
                    .setParameter(1, uuid)
                    .getSingleResult()).longValue();

            execute("""
                    insert into motif_core.participation_videos
                        (challenge_seq, artist_seq, user_seq, job_seq, description, video_key,
                         thumbnail_key, duration_ms, visibility, view_count, like_count,
                         comment_count, has_artist_like, has_artist_comment, reg_dttm, is_deleted)
                    values (?1, ?2, ?3, ?4, '설명', ?5, ?6, 15000, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14)
                    """, challengeSeq, artistSeq, owner, jobSeq, resultKey, thumbKey,
                    visibility, views, likes, comments, artistLike, artistComment,
                    Instant.now(), deleted);
            entityManager.flush();
            entityManager.clear();

            return ((Number) entityManager
                    .createNativeQuery(
                            "select video_seq from motif_core.participation_videos where job_seq = ?1")
                    .setParameter(1, jobSeq)
                    .getSingleResult()).longValue();
        }
    }
}
