package com.ssafy.motif.coreapi.domain.composite.service;

import com.ssafy.motif.coreapi.global.media.MediaObjectStore;
import com.ssafy.motif.domain.core.artist.Artist;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.challenge.Challenge;
import com.ssafy.motif.domain.core.challenge.ChallengeRepository;
import com.ssafy.motif.domain.core.job.CompositeJob;
import com.ssafy.motif.domain.core.job.CompositeJobRepository;
import com.ssafy.motif.domain.core.job.JobStatus;
import com.ssafy.motif.domain.core.job.JobType;
import com.ssafy.motif.domain.core.song.Song;
import com.ssafy.motif.domain.core.song.SongRepository;
import com.ssafy.motif.domain.core.user.User;
import com.ssafy.motif.domain.core.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * AI 접수 URL 이 아직 없을 때 디스패처가 <b>잡을 망가뜨리지 않는지</b> (S15P21D104-537).
 *
 * <p><b>왜 별도 클래스인가.</b> {@code motif.composite.submit-url} 이 빈 값인 컨텍스트가
 * 필요한데, 그건 {@code @TestPropertySource} 로만 만들 수 있고 클래스 단위다.
 *
 * <p><b>무엇이 걸렸었나.</b> URL 이 없어도 디스패처가 {@code reattempt()} 를 먼저 불러
 * {@code attempt} 를 올렸다. 접수는 {@code submit()} 안에서 건너뛰므로 상태는 그대로인데
 * 카운터만 오르고, <b>3틱(기본 6분) 만에 멀쩡한 잡이 전부 {@code STUCK} 으로 닫히며
 * 사용자에게 실패 푸시가 나갔다.</b> AI 접수 서비스는 아직 없는 값이라(작업명세 §8 #4)
 * 앱 연동이 먼저 오면 운영에서 그대로 터진다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@TestPropertySource(properties = "motif.composite.submit-url=")
class CompositeJobDispatcherNoSubmitUrlLocalDbTest {

    @Autowired
    private CompositeJobDispatcher dispatcher;
    @Autowired
    private CompositeJobRepository compositeJobRepository;
    @Autowired
    private ChallengeRepository challengeRepository;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private SongRepository songRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @PersistenceContext
    private EntityManager entityManager;

    @MockitoBean
    private CompositeJobSubmitter submitter;
    @MockitoBean
    private MediaObjectStore objectStore;
    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    private final List<Long> createdJobs = new ArrayList<>();
    private final List<Integer> createdChallenges = new ArrayList<>();
    private final List<Integer> createdSongs = new ArrayList<>();
    private final List<Integer> createdArtists = new ArrayList<>();
    private final List<Integer> createdCompanies = new ArrayList<>();
    private final List<Integer> createdUsers = new ArrayList<>();

    private Integer adminUserSeq;
    private Integer fanSeq;
    private Integer artistSeq;

    @BeforeEach
    void setUp() {
        adminUserSeq = inTransaction(() -> newUser().getUserSeq());
        fanSeq = inTransaction(() -> newUser().getUserSeq());
        artistSeq = inTransaction(this::newArtist);
    }

    @Test
    @DisplayName("접수 URL 이 없으면 잡을 그대로 둔다 — attempt 도 안 오르고 STUCK 도 안 된다")
    void keepsJobsWaitingWhenSubmitUrlIsMissing() {
        CompositeJob job = inTransaction(this::queuedJob);
        backdate(job.getJobSeq());

        // 기본값이면 3틱에 STUCK 이 된다. URL 이 없으면 몇 번을 돌려도 그대로여야 한다
        dispatcher.sweep();
        dispatcher.sweep();
        dispatcher.sweep();
        dispatcher.sweep();

        CompositeJob saved = reload(job.getJobSeq());
        assertThat(saved.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(saved.getAttempt()).isEqualTo((short) 1);
        assertThat(saved.getFailCode()).isNull();
        verify(submitter, never()).submit(any());
        // 실패 푸시도 나가지 않는다
        verify(rabbitTemplate, never()).convertAndSend(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.<Object>any());
    }

    // --- 픽스처 -------------------------------------------------------------------------

    private void backdate(Long jobSeq) {
        inTransaction(() -> {
            entityManager.createNativeQuery(
                            "update motif_core.composite_jobs set mod_dttm = now() - interval '3 minutes'"
                                    + " where job_seq = :seq")
                    .setParameter("seq", jobSeq)
                    .executeUpdate();
            return null;
        });
    }

    private CompositeJob reload(Long jobSeq) {
        return inTransaction(() -> compositeJobRepository.findById(jobSeq).orElseThrow());
    }

    private CompositeJob queuedJob() {
        CompositeJob job = CompositeJob.create(UUID.randomUUID(), JobType.CHALLENGE, fanSeq,
                newChallenge(), null, null, "raw/" + UUID.randomUUID() + ".mp4");
        job.queue(true);
        compositeJobRepository.saveAndFlush(job);
        createdJobs.add(job.getJobSeq());
        return job;
    }

    private User newUser() {
        User user = userRepository.saveAndFlush(User.signUp());
        user.applyInitialNickname();
        createdUsers.add(user.getUserSeq());
        return user;
    }

    private Integer newArtist() {
        User artistUser = userRepository.saveAndFlush(User.createArtist("카리나"));
        createdUsers.add(artistUser.getUserSeq());
        Integer seq = artistRepository.saveAndFlush(Artist.register(
                artistUser.getUserSeq(), null, newCompany(), null, null, adminUserSeq)).getArtistSeq();
        createdArtists.add(seq);
        return seq;
    }

    private Integer newCompany() {
        String name = "t-" + UUID.randomUUID().toString().substring(0, 20);
        entityManager.createNativeQuery(
                        "insert into motif_core.companies (company_name, reg_usr_seq) values (:name, :regUsrSeq)")
                .setParameter("name", name)
                .setParameter("regUsrSeq", adminUserSeq)
                .executeUpdate();
        Integer companySeq = ((Number) entityManager.createNativeQuery(
                        "select company_seq from motif_core.companies where company_name = :name")
                .setParameter("name", name)
                .getSingleResult()).intValue();
        createdCompanies.add(companySeq);
        return companySeq;
    }

    private Integer newChallenge() {
        String uuid = UUID.randomUUID().toString();
        Integer songSeq = songRepository.saveAndFlush(
                Song.register("곡 " + uuid.substring(0, 8), "카리나", adminUserSeq)).getSongSeq();
        createdSongs.add(songSeq);
        Integer challengeSeq = challengeRepository.saveAndFlush(Challenge.register(
                        artistSeq, songSeq, "소개", "challenges/" + uuid + "/master.m3u8",
                        "challenges/" + uuid + "/thumb.jpg", 15_000))
                .getChallengeSeq();
        createdChallenges.add(challengeSeq);
        return challengeSeq;
    }

    @AfterEach
    void cleanUp() {
        inTransaction(() -> {
            createdJobs.forEach(seq ->
                    execute("delete from motif_core.composite_jobs where job_seq = ?1", seq));
            createdChallenges.forEach(seq ->
                    execute("delete from motif_core.challenges where challenge_seq = ?1", seq));
            createdSongs.forEach(seq ->
                    execute("delete from motif_core.songs where song_seq = ?1", seq));
            createdArtists.forEach(seq ->
                    execute("delete from motif_core.artists where artist_seq = ?1", seq));
            createdCompanies.forEach(seq ->
                    execute("delete from motif_core.companies where company_seq = ?1", seq));
            createdUsers.forEach(seq ->
                    execute("delete from motif_core.users where user_seq = ?1", seq));
            return null;
        });
        createdJobs.clear();
        createdChallenges.clear();
        createdSongs.clear();
        createdArtists.clear();
        createdCompanies.clear();
        createdUsers.clear();
    }

    private void execute(String sql, Object param) {
        entityManager.createNativeQuery(sql).setParameter(1, param).executeUpdate();
    }

    private <T> T inTransaction(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }
}
