package com.ssafy.motif.coreapi.admin.challenge;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.challenge.dto.BboxRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeCreateRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeListItem;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeSlotsResponse;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeValidateResponse;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeSlotsRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.EditedChallengeRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.EditedChallengeResponse;
import com.ssafy.motif.coreapi.admin.challenge.dto.EditedChallengeUpdateRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.ParticipantRequest;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.domain.challenge.ChallengeErrorCode;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.global.response.PageResponses;
import com.ssafy.motif.event.contract.ChallengeCreatedEvent;
import com.ssafy.motif.event.contract.MediaJobRequestedEvent;
import com.ssafy.motif.event.topology.Exchanges;
import com.ssafy.motif.event.topology.RoutingKeys;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 챌린지 등록 경로의 회귀 안전망 (S15P21D104-563 — 명세 §3.7.2, 부록 C).
 *
 * <p><b>{@code @Transactional} 을 쓰지 않는다.</b> 이 테스트가 확인해야 할 두 가지가
 * 트랜잭션 경계 그 자체이기 때문이다 — ① 실패 시 {@code challenges} 행까지 <b>전부</b>
 * 롤백되는가 ② 메시지가 <b>커밋 후에만</b> 나가는가. 테스트가 바깥에 트랜잭션을 열면
 * 서비스의 커밋이 커밋이 아니게 되고 {@code AFTER_COMMIT} 리스너는 영영 안 돈다.
 * 대신 만든 행을 {@link #cleanUp()} 이 역순으로 지운다.
 *
 * <p>S3 와 MQ 는 목이다 — DoD 가 "LocalStack 또는 mock" 을 허용한다. 실행 전
 * {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
class AdminChallengeServiceLocalDbTest {

    private static final AuthenticatedAdmin ADMIN = new AuthenticatedAdmin(1, 1, "테스트어드민");
    private static final int ORIGIN_DURATION_MS = 15_000;

    @Autowired
    private AdminChallengeService adminChallengeService;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @PersistenceContext
    private EntityManager entityManager;

    @MockitoBean
    private S3Client s3Client;
    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    /** HEAD 가 성공하는 키 — 테스트가 "업로드된 것으로 친다"고 선언한 것만 존재한다. */
    private final Set<String> uploadedKeys = new HashSet<>();
    private final List<Integer> createdChallenges = new ArrayList<>();
    private final List<Integer> createdArtists = new ArrayList<>();
    private final List<Integer> createdJobs = new ArrayList<>();

    private Integer mainArtist;
    private Integer songSeq;
    private String videoKey;

    @BeforeEach
    void setUp() {
        stubHead();
        mainArtist = newArtist();
        songSeq = newSong();
        videoKey = newUploadedKey("challenges/" + UUID.randomUUID() + ".mp4");
    }

    @Test
    @DisplayName("n=0(솔로) — 편집본 0개로 등록된다. 원본 위 합성이 유일한 참여 경로다")
    void 솔로_등록() {
        ChallengeSlotsResponse response = create(List.of(), List.of());

        assertThat(response.participants()).isEmpty();
        assertThat(response.editedChallenges()).isEmpty();
        assertThat(rows("challenges", response.challengeSeq())).isEqualTo(1);
    }

    @Test
    @DisplayName("n=1 — 편집본 1개, 마스크 1")
    void 슬롯_하나_등록() {
        Integer slot = newArtist();

        ChallengeSlotsResponse response = create(List.of(slot(slot)), List.of(edited(0)));

        assertThat(response.participants()).extracting(ChallengeSlotsResponse.Participant::participantMask)
                .containsExactly(1);
        assertThat(response.editedChallenges()).extracting(ChallengeSlotsResponse.Edited::participantMask)
                .containsExactly(1);
    }

    @Test
    @DisplayName("n=2 — 슬롯 2개·편집본 3개(1,2,3)가 한 트랜잭션에 들어간다")
    void 슬롯_둘_등록() {
        ChallengeSlotsResponse response = create(
                List.of(slot(newArtist()), slot(newArtist())),
                List.of(edited(0), edited(1), edited(0, 1)));

        assertThat(response.participants()).extracting(ChallengeSlotsResponse.Participant::participantMask)
                .containsExactly(1, 2);
        assertThat(response.editedChallenges()).extracting(ChallengeSlotsResponse.Edited::participantMask)
                .containsExactly(1, 2, 3);
        assertThat(rows("challenge_participants", response.challengeSeq())).isEqualTo(2);
        assertThat(rows("edited_challenges", response.challengeSeq())).isEqualTo(3);
    }

    @Test
    @DisplayName("등록 성공이면 커밋 후 메시지 2건 — challenge.created + job.media")
    void 메시지_두_건() {
        create(List.of(), List.of());

        verify(rabbitTemplate).convertAndSend(eq(Exchanges.MOTIF_TOPIC),
                eq(RoutingKeys.CHALLENGE_CREATED), any(ChallengeCreatedEvent.class));
        verify(rabbitTemplate).convertAndSend(eq(Exchanges.MOTIF_TOPIC),
                eq(RoutingKeys.JOB_MEDIA), any(MediaJobRequestedEvent.class));
    }

    @Test
    @DisplayName("편집본 1개가 누락되면 challenges 행도 남지 않고 메시지도 0건이다")
    void 누락은_전체_롤백() {
        Integer slotA = newArtist();
        Integer slotB = newArtist();
        long before = challengeCount();

        assertThatThrownBy(() -> create(List.of(slot(slotA), slot(slotB)),
                List.of(edited(0), edited(1))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.EDITED_SET_INCOMPLETE);

        assertThat(challengeCount()).isEqualTo(before);
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("중복 조합도 409 — 개수는 맞아도 집합이 다르다")
    void 중복_조합() {
        assertThatThrownBy(() -> create(
                List.of(slot(newArtist()), slot(newArtist())),
                List.of(edited(0), edited(0), edited(1))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.EDITED_SET_INCOMPLETE);
    }

    @Test
    @DisplayName("범위를 벗어난 슬롯 인덱스도 409")
    void 범위_초과_조합() {
        assertThatThrownBy(() -> create(
                List.of(slot(newArtist())),
                List.of(edited(3))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.EDITED_SET_INCOMPLETE);
    }

    @Test
    @DisplayName("편집본 길이가 ±500ms 를 넘으면 409 EDITED_DURATION_MISMATCH")
    void 길이_불일치() {
        long before = challengeCount();
        EditedChallengeRequest 길이초과 = withDuration(edited(0), ORIGIN_DURATION_MS + 501);

        assertThatThrownBy(() -> create(List.of(slot(newArtist())), List.of(길이초과)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.EDITED_DURATION_MISMATCH);
        assertThat(challengeCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("±500ms 경계 안쪽은 통과한다")
    void 길이_허용_오차() {
        EditedChallengeRequest 경계 = withDuration(edited(0), ORIGIN_DURATION_MS + 500);

        assertThat(create(List.of(slot(newArtist())), List.of(경계)).editedChallenges()).hasSize(1);
    }

    @Test
    @DisplayName("S3 에 없는 키는 409 MEDIA_NOT_UPLOADED — data 에 부재 키가 담긴다")
    void 업로드되지_않은_키() {
        long before = challengeCount();
        EditedChallengeRequest 미업로드 = new EditedChallengeRequest(
                List.of(0), "edited/pending/" + UUID.randomUUID() + ".mp4",
                newUploadedKey("edited/pending/" + UUID.randomUUID() + ".jpg"), null);

        assertThatThrownBy(() -> create(List.of(slot(newArtist())), List.of(미업로드)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AdminErrorCode.MEDIA_NOT_UPLOADED));
        assertThat(challengeCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("원본이 가로면 400 MEDIA_INVALID — 챌린지는 세로다")
    void 가로_원본_거부() {
        assertThatThrownBy(() -> adminChallengeService.create(ADMIN, new ChallengeCreateRequest(
                mainArtist, songSeq, "소개글", videoKey, (long) ORIGIN_DURATION_MS, null, 1920, 1080,
                List.of(), List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.MEDIA_INVALID);
    }

    @Test
    @DisplayName("사전 검증은 DB 를 바꾸지 않는다 — 실패도 200 이고 사유가 issues 에 담긴다")
    void 사전_검증은_무변경() {
        long before = challengeCount();
        Integer slotA = newArtist();
        Integer slotB = newArtist();

        ChallengeValidateResponse ok = adminChallengeService.validate(request(
                List.of(slot(slotA), slot(slotB)), List.of(edited(0), edited(1), edited(0, 1))));
        ChallengeValidateResponse 누락 = adminChallengeService.validate(request(
                List.of(slot(slotA), slot(slotB)), List.of(edited(0), edited(1))));

        assertThat(ok.valid()).isTrue();
        assertThat(ok.computedMasks()).containsExactly(1, 2, 3);
        assertThat(누락.valid()).isFalse();
        assertThat(누락.issues()).singleElement()
                .extracting(ChallengeValidateResponse.Issue::code)
                .isEqualTo(AdminErrorCode.EDITED_SET_INCOMPLETE.getCode());
        assertThat(challengeCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("슬롯 교체 — 같은 마스크를 다시 쓰므로 DELETE 가 INSERT 보다 먼저 나가야 한다")
    void 슬롯_교체() {
        ChallengeSlotsResponse created = create(List.of(slot(newArtist())), List.of(edited(0)));

        ChallengeSlotsResponse replaced = adminChallengeService.replaceSlots(ADMIN,
                created.challengeSeq(),
                new ChallengeSlotsRequest(List.of(slot(newArtist()), slot(newArtist())),
                        List.of(edited(0), edited(1), edited(0, 1))));

        assertThat(replaced.editedChallenges()).extracting(ChallengeSlotsResponse.Edited::participantMask)
                .containsExactly(1, 2, 3);
        assertThat(rows("challenge_participants", created.challengeSeq())).isEqualTo(2);
        assertThat(rows("edited_challenges", created.challengeSeq())).isEqualTo(3);
    }

    @Test
    @DisplayName("합성 이력이 있는 편집본이 하나라도 있으면 슬롯 교체는 409 EDITED_IN_USE")
    void 사용_중인_편집본은_교체_거부() {
        ChallengeSlotsResponse created = create(List.of(slot(newArtist())), List.of(edited(0)));
        newCompositeJob(created.challengeSeq(),
                created.editedChallenges().getFirst().editedChallengeSeq());

        assertThatThrownBy(() -> adminChallengeService.replaceSlots(ADMIN, created.challengeSeq(),
                new ChallengeSlotsRequest(List.of(), List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.EDITED_IN_USE);

        assertThat(rows("edited_challenges", created.challengeSeq())).isEqualTo(1);
    }

    @Test
    @DisplayName("편집본 키 교체는 사용 중이어도 성공한다 — 결과물은 별도 오브젝트다")
    void 편집본_키_교체() {
        ChallengeSlotsResponse created = create(List.of(slot(newArtist())), List.of(edited(0)));
        Integer editedSeq = created.editedChallenges().getFirst().editedChallengeSeq();
        newCompositeJob(created.challengeSeq(), editedSeq);
        String newKey = newUploadedKey("edited/pending/" + UUID.randomUUID() + ".mp4");

        EditedChallengeResponse response = adminChallengeService.replaceEditedKeys(ADMIN, editedSeq,
                new EditedChallengeUpdateRequest(newKey, null, (long) ORIGIN_DURATION_MS));

        assertThat(response.participantMask()).isEqualTo(1);
        assertThat(scalar("select 1 from motif_core.edited_challenges "
                + "where edited_challenge_seq = ?1 and video_key = '" + newKey + "'", editedSeq))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("목록은 아티스트·곡·슬롯 수·편집본 수를 쿼리 한 번에 준다 — 필터는 아티스트·곡 (§3.7.1)")
    void 목록_필터와_집계() {
        ChallengeSlotsResponse created = create(List.of(slot(newArtist())), List.of(edited(0)));

        PageResponse<ChallengeListItem> page =
                adminChallengeService.list(mainArtist, null, false, PageResponses.pageable(0, 20));

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.list()).singleElement().satisfies(item -> {
            assertThat(item.challengeSeq()).isEqualTo(created.challengeSeq());
            assertThat(item.artist().artistSeq()).isEqualTo(mainArtist);
            assertThat(item.artist().name()).isNotBlank();
            assertThat(item.song().songSeq()).isEqualTo(songSeq);
            assertThat(item.song().singerName()).isEqualTo("aespa");
            assertThat(item.slotCount()).isEqualTo(1);       // 서브쿼리 집계 — 행마다 다시 읽지 않는다
            assertThat(item.editedCount()).isEqualTo(1);
            assertThat(item.durationMs()).isEqualTo(ORIGIN_DURATION_MS);
            assertThat(item.isDeleted()).isFalse();
            assertThat(item.regDttm()).isNotNull();
        });

        // 다른 곡으로 거르면 걸리지 않는다 — 필터가 실제로 SQL 에 실리는지 본다
        assertThat(adminChallengeService.list(mainArtist, newSong(), false, PageResponses.pageable(0, 20))
                .list()).isEmpty();
    }

    @Test
    @DisplayName("소프트 삭제된 챌린지는 기본으로 빠지고 includeDeleted=true 로만 보인다")
    void 목록_삭제_포함() {
        ChallengeSlotsResponse created = create(List.of(), List.of());
        inTransaction(() -> {
            execute("update motif_core.challenges set is_deleted = true where challenge_seq = ?1",
                    created.challengeSeq());
            return null;
        });

        assertThat(adminChallengeService.list(mainArtist, null, false, PageResponses.pageable(0, 20)).list())
                .isEmpty();
        assertThat(adminChallengeService.list(mainArtist, null, true, PageResponses.pageable(0, 20)).list())
                .singleElement()
                .satisfies(item -> assertThat(item.isDeleted()).isTrue());
    }

    @Test
    @DisplayName("삭제(§3.7.7)는 is_deleted 만 세운다 — 참여 영상·댓글·슬롯·편집본 행은 남는다 (DB §12)")
    void 삭제는_자식을_연쇄하지_않는다() {
        ChallengeSlotsResponse created = create(List.of(slot(newArtist())), List.of(edited(0)));
        Integer seq = created.challengeSeq();
        newParticipationVideo(seq);
        newChallengeComment(seq);

        adminChallengeService.delete(ADMIN, seq);

        assertThat(deleted(seq)).isTrue();
        // 연쇄 삭제는 명세 위반이다 — 자식이 한 행이라도 사라지면 여기서 깨진다
        assertThat(rows("participation_videos", seq)).isEqualTo(1);
        assertThat(rows("challenge_comments", seq)).isEqualTo(1);
        assertThat(rows("challenge_participants", seq)).isEqualTo(1);
        assertThat(rows("edited_challenges", seq)).isEqualTo(1);
        // 참여 영상이 스스로 삭제되지도 않는다 — 비노출은 조회 쪽이 거르는 일이다
        assertThat(scalar("select count(*) from motif_core.participation_videos "
                + "where challenge_seq = ?1 and is_deleted = false", seq)).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 삭제된 챌린지의 재삭제는 404 — 없는 변경을 성공으로 보고하지 않는다")
    void 삭제_재삭제는_404() {
        ChallengeSlotsResponse created = create(List.of(), List.of());
        adminChallengeService.delete(ADMIN, created.challengeSeq());

        assertThatThrownBy(() -> adminChallengeService.delete(ADMIN, created.challengeSeq()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode().getCode())
                .isEqualTo(ChallengeErrorCode.CHALLENGE_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("size 는 §1.5 상한 100 으로 잘린다 — 운영 화면이 큰 수를 보내도 표가 비지 않는다")
    void 페이지_크기_상한() {
        assertThat(PageResponses.pageable(0, 500).getPageSize()).isEqualTo(PageResponses.MAX_SIZE);
        assertThat(PageResponses.pageable(-3, 0).getPageNumber()).isZero();
        assertThat(PageResponses.pageable(-3, 0).getPageSize()).isEqualTo(1);
    }

    // --- 픽스처 -------------------------------------------------------------

    private ChallengeSlotsResponse create(List<ParticipantRequest> participants,
                                          List<EditedChallengeRequest> edited) {
        ChallengeSlotsResponse response = adminChallengeService.create(ADMIN, request(participants, edited));
        createdChallenges.add(response.challengeSeq());
        return response;
    }

    private ChallengeCreateRequest request(List<ParticipantRequest> participants,
                                           List<EditedChallengeRequest> edited) {
        return new ChallengeCreateRequest(mainArtist, songSeq, "Supernova 챌린지", videoKey,
                (long) ORIGIN_DURATION_MS, null, 1080, 1920, participants, edited);
    }

    private ParticipantRequest slot(Integer artistSeq) {
        return new ParticipantRequest(artistSeq, new BboxRequest(120, 80, 460, 940));
    }

    /** 편집본 키는 업로드된 것으로 친다 — 부재 케이스는 별도 테스트가 직접 만든다. */
    private EditedChallengeRequest edited(Integer... removedSlots) {
        return new EditedChallengeRequest(List.of(removedSlots),
                newUploadedKey("edited/pending/" + UUID.randomUUID() + ".mp4"),
                newUploadedKey("edited/pending/" + UUID.randomUUID() + ".jpg"), null);
    }

    private static EditedChallengeRequest withDuration(EditedChallengeRequest edited, long durationMs) {
        return new EditedChallengeRequest(edited.removedSlots(), edited.videoKey(),
                edited.initialSceneKey(), durationMs);
    }

    private String newUploadedKey(String key) {
        uploadedKeys.add(key);
        return key;
    }

    @SuppressWarnings("unchecked")
    private void stubHead() {
        given(s3Client.headObject(any(Consumer.class))).willAnswer(invocation -> {
            HeadObjectRequest.Builder builder = HeadObjectRequest.builder();
            ((Consumer<HeadObjectRequest.Builder>) invocation.getArgument(0)).accept(builder);
            if (uploadedKeys.contains(builder.build().key())) {
                return HeadObjectResponse.builder().build();
            }
            throw NoSuchKeyException.builder().message("no such key").build();
        });
    }

    /**
     * {@code users(ARTIST)} → {@code companies} → {@code artists} FK 체인.
     *
     * <p>네이티브 SQL 인 이유는 온보딩 테스트와 같다 — {@code Artist} 는 부분 매핑이라
     * JPA 로는 NOT NULL 인 {@code company_seq}·{@code reg_usr_seq} 를 채울 수 없다.
     */
    private Integer newArtist() {
        return inTransaction(() -> {
            Integer userSeq = newUser("ARTIST");
            String companyName = "t-" + UUID.randomUUID().toString().substring(0, 20);
            execute("insert into motif_core.companies (company_name, reg_usr_seq) values (?1, ?2)",
                    companyName, userSeq);
            Integer companySeq = scalar(
                    "select company_seq from motif_core.companies where company_name = ?1", companyName);

            execute("insert into motif_core.artists (user_seq, company_seq, reg_usr_seq) values (?1, ?2, ?1)",
                    userSeq, companySeq);
            Integer artistSeq = scalar(
                    "select artist_seq from motif_core.artists where user_seq = ?1", userSeq);
            createdArtists.add(artistSeq);
            return artistSeq;
        });
    }

    private Integer newSong() {
        return inTransaction(() -> {
            String title = "t-" + UUID.randomUUID();
            execute("insert into motif_core.songs (title, singer_name, reg_usr_seq) values (?1, 'aespa', ?2)",
                    title, newUser("USER"));
            return scalar("select song_seq from motif_core.songs where title = ?1", title);
        });
    }

    /** 합성 이력 = {@code composite_jobs} 가 편집본을 참조하는 상태 (§3.7.5 판정 근거). */
    private void newCompositeJob(Integer challengeSeq, Integer editedChallengeSeq) {
        inTransaction(() -> {
            execute("""
                    insert into motif_core.composite_jobs
                        (job_uuid, job_type, user_seq, challenge_seq, edited_challenge_seq, raw_video_key)
                    values (?1, 'CHALLENGE', ?2, ?3, ?4, 'raw/x.mp4')
                    """, UUID.randomUUID(), newUser("USER"), challengeSeq, editedChallengeSeq);
            createdJobs.add(challengeSeq);
            return null;
        });
    }

    /**
     * 참여 영상 한 건 (§3.7.7 확인 대화상자가 세는 대상) — 삭제가 이 행을 건드리면 안 된다.
     *
     * <p>{@code job_seq} 가 NOT NULL UNIQUE FK 라 잡을 먼저 만든다. 잡은 슬롯이 아니라
     * 원본 위 합성이므로 {@code edited_challenge_seq} 는 NULL 이다.
     */
    private void newParticipationVideo(Integer challengeSeq) {
        inTransaction(() -> {
            UUID jobUuid = UUID.randomUUID();
            Integer userSeq = newUser("USER");
            execute("""
                    insert into motif_core.composite_jobs
                        (job_uuid, job_type, user_seq, challenge_seq, raw_video_key,
                         status, result_video_key, result_thumbnail_key)
                    values (?1, 'CHALLENGE', ?2, ?3, 'raw/x.mp4',
                            'DONE', 'videos/x.mp4', 'videos/x.jpg')
                    """, jobUuid, userSeq, challengeSeq);
            Integer jobSeq = scalar("select job_seq from motif_core.composite_jobs where job_uuid = ?1",
                    jobUuid);
            execute("""
                    insert into motif_core.participation_videos
                        (challenge_seq, artist_seq, user_seq, job_seq, description,
                         video_key, thumbnail_key, duration_ms)
                    values (?1, ?2, ?3, ?4, 't', 'videos/x.mp4', 'videos/x.jpg', ?5)
                    """, challengeSeq, mainArtist, userSeq, jobSeq, ORIGIN_DURATION_MS);
            createdJobs.add(challengeSeq);
            return null;
        });
    }

    /** 댓글 한 건 — 참여 영상과 함께 "연쇄 삭제하지 않는다" 규칙(DB §12)의 대상이다. */
    private void newChallengeComment(Integer challengeSeq) {
        inTransaction(() -> {
            execute("insert into motif_core.challenge_comments (challenge_seq, user_seq, content) "
                    + "values (?1, ?2, 't')", challengeSeq, newUser("USER"));
            return null;
        });
    }

    private boolean deleted(Integer challengeSeq) {
        return inTransaction(() -> (Boolean) entityManager.createNativeQuery(
                        "select is_deleted from motif_core.challenges where challenge_seq = ?1")
                .setParameter(1, challengeSeq)
                .getSingleResult());
    }

    private Integer newUser(String role) {
        String nickname = "t-" + UUID.randomUUID().toString().substring(0, 20);
        execute("insert into motif_core.users (nickname, role) values (?1, ?2)", nickname, role);
        return scalar("select user_seq from motif_core.users where nickname = ?1", nickname);
    }

    private long challengeCount() {
        return inTransaction(() -> ((Number) entityManager
                .createNativeQuery("select count(*) from motif_core.challenges")
                .getSingleResult()).longValue());
    }

    private long rows(String table, Integer challengeSeq) {
        return inTransaction(() -> ((Number) entityManager.createNativeQuery(
                        "select count(*) from motif_core." + table + " where challenge_seq = ?1")
                .setParameter(1, challengeSeq)
                .getSingleResult()).longValue());
    }

    /**
     * 만든 챌린지를 FK 역순으로 지운다 — 트랜잭션 롤백에 기댈 수 없는 테스트라
     * 뒷정리를 직접 한다. 아티스트·곡·유저는 다른 테스트와 충돌하지 않아 남겨 둔다.
     */
    @AfterEach
    void cleanUp() {
        inTransaction(() -> {
            // 참여 영상·댓글이 잡·챌린지를 참조한다 — FK 역순이라 이 둘이 가장 먼저다
            createdChallenges.forEach(seq -> {
                execute("delete from motif_core.participation_videos where challenge_seq = ?1", seq);
                execute("delete from motif_core.challenge_comments where challenge_seq = ?1", seq);
            });
            createdJobs.forEach(seq ->
                    execute("delete from motif_core.composite_jobs where challenge_seq = ?1", seq));
            createdChallenges.forEach(seq -> {
                execute("delete from motif_core.edited_challenges where challenge_seq = ?1", seq);
                execute("delete from motif_core.challenge_participants where challenge_seq = ?1", seq);
                execute("delete from motif_core.challenges where challenge_seq = ?1", seq);
            });
            return null;
        });
        createdJobs.clear();
        createdChallenges.clear();
        createdArtists.clear();
    }

    /** 네이티브 쓰기는 트랜잭션 없이는 못 한다 — 클래스에 {@code @Transactional} 이 없는 값이다. */
    private <T> T inTransaction(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }

    private void execute(String sql, Object... params) {
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
}
