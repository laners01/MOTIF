package com.ssafy.motif.coreapi.admin.challenge;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.challenge.dto.AnchorRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeCreateRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeListItem;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeSlotsResponse;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeValidateResponse;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeSlotsRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.ParticipantEditedResponse;
import com.ssafy.motif.coreapi.admin.challenge.dto.ParticipantEditedUpdateRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.ParticipantRequest;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.domain.challenge.ChallengeErrorCode;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.global.response.PageResponses;
import com.ssafy.motif.event.contract.ChallengeCreatedEvent;
import com.ssafy.motif.coreapi.admin.media.AdminMediaKind;
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
    @DisplayName("n=0(솔로) — 슬롯 0개로 등록된다. 원본 위 합성이 유일한 참여 경로다")
    void 솔로_등록() {
        ChallengeSlotsResponse response = create(List.of());

        assertThat(response.participants()).isEmpty();
        assertThat(rows("challenges", response.challengeSeq())).isEqualTo(1);
    }

    @Test
    @DisplayName("n=1 — 슬롯 1개, 순서 0. 편집본은 비어 있다(§3.7.6 이 채운다)")
    void 슬롯_하나_등록() {
        ChallengeSlotsResponse response = create(List.of(slot(newArtist())));

        assertThat(response.participants()).singleElement().satisfies(p -> {
            assertThat(p.slotOrder()).isZero();
            assertThat(p.challengeParticipantSeq()).isNotNull();
            assertThat(p.hasEdited()).isFalse();
        });
        assertThat(rows("challenge_participants", response.challengeSeq())).isEqualTo(1);
    }

    @Test
    @DisplayName("n=2 — 배열 순서가 곧 slotOrder 다")
    void 슬롯_둘_등록() {
        ChallengeSlotsResponse response = create(
                List.of(slot(newArtist()), slot(newArtist())));

        assertThat(response.participants()).extracting(ChallengeSlotsResponse.Participant::slotOrder)
                .containsExactly(0, 1);
        assertThat(rows("challenge_participants", response.challengeSeq())).isEqualTo(2);
    }

    /**
     * 2026-09-22 — 등록 직후에는 <b>모든 슬롯이 편집본 없는 상태</b>다. 편집본 키가 슬롯
     * seq 를 품고 있어 INSERT 전에는 정할 수 없기 때문이다(§3.7.2 → §3.7.6).
     *
     * <p>앱에서는 그 슬롯을 고를 수 없고(§7.5 {@code initialSceneUrl: null}), 고르려 하면
     * §13.1 이 {@code EDITED_CHALLENGE_NOT_FOUND} 로 막는다.
     */
    @Test
    @DisplayName("등록 직후 슬롯은 편집본이 전부 비어 있다 — 정상 상태다")
    void 등록_직후엔_편집본이_없다() {
        ChallengeSlotsResponse response = create(List.of(slot(newArtist()), slot(newArtist())));

        assertThat(response.participants()).extracting(ChallengeSlotsResponse.Participant::hasEdited)
                .containsExactly(false, false);
        assertThat(scalar("select count(*) from motif_core.challenge_participants "
                + "where challenge_seq = ?1 and edited_challenge_key is null", response.challengeSeq()))
                .isEqualTo(2);
    }

    /**
     * 0020 — §7.6 이 길이를 앱에 내보내므로 저장해 둔다. 예전에는 원본 ±500ms 대조에만
     * 쓰고 버렸다.
     */
    @Test
    @DisplayName("편집본 길이가 행에 남는다 — 앱 촬영 타이머가 쓰는 값이다")
    void 편집본_길이_저장() {
        ChallengeSlotsResponse created = create(List.of(slot(newArtist())));
        Integer slotSeq = created.participants().getFirst().challengeParticipantSeq();

        fillEdited(created.challengeSeq(), slotSeq, ORIGIN_DURATION_MS + 120);

        assertThat(scalar("select edited_duration_ms from motif_core.challenge_participants "
                + "where challenge_participant_seq = ?1", slotSeq))
                .isEqualTo(ORIGIN_DURATION_MS + 120);
    }

    @Test
    @DisplayName("등록 성공이면 커밋 후 메시지 2건 — challenge.created + job.media")
    void 메시지_두_건() {
        create(List.of());

        verify(rabbitTemplate).convertAndSend(eq(Exchanges.MOTIF_TOPIC),
                eq(RoutingKeys.CHALLENGE_CREATED), any(ChallengeCreatedEvent.class));
        verify(rabbitTemplate).convertAndSend(eq(Exchanges.MOTIF_TOPIC),
                eq(RoutingKeys.JOB_MEDIA), any(MediaJobRequestedEvent.class));
    }

    /** 슬롯 규칙에 걸리면 {@code challenges} 행도 남지 않아야 한다 — 한 트랜잭션이다. */
    @Test
    @DisplayName("슬롯 아티스트가 겹치면 400 이고 challenges 행도 메시지도 남지 않는다")
    void 중복_아티스트는_전체_롤백() {
        long before = challengeCount();
        Integer artist = newArtist();

        assertThatThrownBy(() -> create(List.of(slot(artist), slot(artist))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.PARTICIPANT_INVALID);

        assertThat(challengeCount()).isEqualTo(before);
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("원본 키가 S3 에 없으면 409 MEDIA_NOT_UPLOADED")
    void 업로드되지_않은_원본() {
        long before = challengeCount();
        String 미업로드 = "challenges/" + UUID.randomUUID() + ".mp4";

        assertThatThrownBy(() -> adminChallengeService.create(ADMIN, new ChallengeCreateRequest(
                mainArtist, songSeq, "소개글", 미업로드, (long) ORIGIN_DURATION_MS, null, 1080, 1920,
                List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.MEDIA_NOT_UPLOADED);
        assertThat(challengeCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("원본이 가로면 400 MEDIA_INVALID — 챌린지는 세로다")
    void 가로_원본_거부() {
        assertThatThrownBy(() -> adminChallengeService.create(ADMIN, new ChallengeCreateRequest(
                mainArtist, songSeq, "소개글", videoKey, (long) ORIGIN_DURATION_MS, null, 1920, 1080,
                List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.MEDIA_INVALID);
    }

    @Test
    @DisplayName("사전 검증은 DB 를 바꾸지 않는다 — 실패도 200 이고 사유가 issues 에 담긴다")
    void 사전_검증은_무변경() {
        long before = challengeCount();
        Integer artist = newArtist();

        ChallengeValidateResponse ok = adminChallengeService.validate(request(
                List.of(slot(artist), slot(newArtist()))));
        ChallengeValidateResponse 중복 = adminChallengeService.validate(request(
                List.of(slot(artist), slot(artist))));

        assertThat(ok.valid()).isTrue();
        assertThat(ok.issues()).isEmpty();
        assertThat(중복.valid()).isFalse();
        assertThat(중복.issues()).singleElement()
                .extracting(ChallengeValidateResponse.Issue::code)
                .isEqualTo(AdminErrorCode.PARTICIPANT_INVALID.getCode());
        assertThat(challengeCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("슬롯 교체 — 같은 순서를 다시 쓰므로 DELETE 가 INSERT 보다 먼저 나가야 한다")
    void 슬롯_교체() {
        ChallengeSlotsResponse created = create(List.of(slot(newArtist())));

        ChallengeSlotsResponse replaced = adminChallengeService.replaceSlots(ADMIN,
                created.challengeSeq(),
                new ChallengeSlotsRequest(List.of(slot(newArtist()), slot(newArtist()))));

        assertThat(replaced.participants()).extracting(ChallengeSlotsResponse.Participant::slotOrder)
                .containsExactly(0, 1);
        assertThat(rows("challenge_participants", created.challengeSeq())).isEqualTo(2);
    }

    @Test
    @DisplayName("합성 이력이 있는 슬롯이 하나라도 있으면 슬롯 교체는 409 EDITED_IN_USE")
    void 사용_중인_슬롯은_교체_거부() {
        ChallengeSlotsResponse created = create(List.of(slot(newArtist())));
        newCompositeJob(created.challengeSeq(),
                created.participants().getFirst().challengeParticipantSeq());

        assertThatThrownBy(() -> adminChallengeService.replaceSlots(ADMIN, created.challengeSeq(),
                new ChallengeSlotsRequest(List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.EDITED_IN_USE);

        assertThat(rows("challenge_participants", created.challengeSeq())).isEqualTo(1);
    }

    @Test
    @DisplayName("편집본 키 교체는 사용 중이어도 성공한다 — 결과물은 별도 오브젝트다")
    void 편집본_키_교체() {
        ChallengeSlotsResponse created = create(List.of(slot(newArtist())));
        Integer slotSeq = created.participants().getFirst().challengeParticipantSeq();
        fillEdited(created.challengeSeq(), slotSeq, ORIGIN_DURATION_MS);
        newCompositeJob(created.challengeSeq(), slotSeq);
        String newKey = editedKey(created.challengeSeq(), slotSeq, ".mp4");

        ParticipantEditedResponse response = adminChallengeService.replaceEditedKeys(ADMIN, slotSeq,
                new ParticipantEditedUpdateRequest(newKey, null, null, (long) ORIGIN_DURATION_MS));

        assertThat(response.slotOrder()).isZero();
        assertThat(response.durationMs()).isEqualTo(ORIGIN_DURATION_MS);
        assertThat(scalar("select 1 from motif_core.challenge_participants "
                + "where challenge_participant_seq = ?1 and edited_challenge_key = '" + newKey + "'", slotSeq))
                .isEqualTo(1);
    }

    /**
     * 0020 — 영상을 바꾸면 길이도 함께 와야 한다. 옛 길이가 남으면 §7.6 이 새 영상과
     * 어긋난 타이머 값을 앱에 내보낸다.
     */
    @Test
    @DisplayName("영상만 바꾸고 길이를 빼면 400")
    void 영상_교체에는_길이가_따라온다() {
        ChallengeSlotsResponse created = create(List.of(slot(newArtist())));
        Integer slotSeq = created.participants().getFirst().challengeParticipantSeq();
        fillEdited(created.challengeSeq(), slotSeq, ORIGIN_DURATION_MS);

        assertThatThrownBy(() -> adminChallengeService.replaceEditedKeys(ADMIN, slotSeq,
                new ParticipantEditedUpdateRequest(
                        editedKey(created.challengeSeq(), slotSeq, ".mp4"), null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_PARAMETER);
    }

    /** 0020 — 편집본 길이는 원본 ±500ms 안이어야 한다. */
    @Test
    @DisplayName("편집본 길이가 ±500ms 를 넘으면 409 EDITED_DURATION_MISMATCH")
    void 길이_불일치() {
        ChallengeSlotsResponse created = create(List.of(slot(newArtist())));
        Integer slotSeq = created.participants().getFirst().challengeParticipantSeq();

        assertThatThrownBy(() -> fillEdited(created.challengeSeq(), slotSeq, ORIGIN_DURATION_MS + 501))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.EDITED_DURATION_MISMATCH);
    }

    @Test
    @DisplayName("±500ms 경계 안쪽은 통과한다")
    void 길이_허용_오차() {
        ChallengeSlotsResponse created = create(List.of(slot(newArtist())));
        Integer slotSeq = created.participants().getFirst().challengeParticipantSeq();

        assertThat(fillEdited(created.challengeSeq(), slotSeq, ORIGIN_DURATION_MS + 500)
                .slotOrder()).isZero();
    }

    @Test
    @DisplayName("S3 에 없는 편집본 키는 409 MEDIA_NOT_UPLOADED")
    void 업로드되지_않은_편집본() {
        ChallengeSlotsResponse created = create(List.of(slot(newArtist())));
        Integer slotSeq = created.participants().getFirst().challengeParticipantSeq();
        // 경로 규약은 맞지만 PUT 을 하지 않은 키 — newUploadedKey 를 거치지 않는다
        String 미업로드 = AdminMediaKind.editedPrefix(created.challengeSeq(), slotSeq)
                + UUID.randomUUID() + ".mp4";

        assertThatThrownBy(() -> adminChallengeService.replaceEditedKeys(ADMIN, slotSeq,
                new ParticipantEditedUpdateRequest(미업로드,
                        editedKey(created.challengeSeq(), slotSeq, ".jpg"), null,
                        (long) ORIGIN_DURATION_MS)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.MEDIA_NOT_UPLOADED);
    }

    /**
     * 0020 — 앱에 나가는 편집본은 보호 프리픽스여야 한다. S3 HEAD 는 "그 자리에 파일이
     * 있나" 만 보므로 옛 경로({@code edited/…})를 걸러내지 못하는데, 그대로 저장되면
     * {@code isProtected} 가 false 라 §7.5·§7.6 이 <b>서명 없이</b> 내보낸다.
     */
    @Test
    @DisplayName("옛 edited/ 경로는 400 — 서명이 안 붙는 자리다")
    void 편집본_키는_보호_프리픽스() {
        ChallengeSlotsResponse created = create(List.of(slot(newArtist())));
        Integer slotSeq = created.participants().getFirst().challengeParticipantSeq();

        assertThatThrownBy(() -> adminChallengeService.replaceEditedKeys(ADMIN, slotSeq,
                new ParticipantEditedUpdateRequest(
                        newUploadedKey("edited/1/" + UUID.randomUUID() + ".mp4"),
                        newUploadedKey("edited/1/" + UUID.randomUUID() + ".jpg"), null,
                        (long) ORIGIN_DURATION_MS)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(reasons((BusinessException) e))
                        .contains("EDITED_KEY_NOT_PROTECTED"));
    }

    /**
     * 2026-09-22 — presign 은 두 seq 로 키를 내주지만 이 API 는 키 <b>문자열</b>을 받는다.
     * 손으로 붙여 넣다 남의 슬롯 파일을 등록하면 그 아티스트가 지워지지 않은 영상이
     * 베이스가 되어, 합성이 엉뚱한 사람을 교체한다.
     */
    @Test
    @DisplayName("다른 슬롯 자리의 키는 400 EDITED_KEY_NOT_IN_SLOT")
    void 남의_슬롯_키는_거부() {
        ChallengeSlotsResponse created = create(List.of(slot(newArtist()), slot(newArtist())));
        Integer mine = created.participants().getFirst().challengeParticipantSeq();
        Integer other = created.participants().getLast().challengeParticipantSeq();

        assertThatThrownBy(() -> adminChallengeService.replaceEditedKeys(ADMIN, mine,
                new ParticipantEditedUpdateRequest(
                        editedKey(created.challengeSeq(), other, ".mp4"),
                        editedKey(created.challengeSeq(), other, ".jpg"), null,
                        (long) ORIGIN_DURATION_MS)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(reasons((BusinessException) e))
                        .contains("EDITED_KEY_NOT_IN_SLOT"));
    }

    /**
     * 편집본이 없던 슬롯은 영상만 보내면 DB CHECK 에 걸린다 — 서버가 먼저 400 으로 거른다.
     * 2026-09-22 이후로는 <b>모든 슬롯이 이 상태로 시작</b>한다.
     */
    @Test
    @DisplayName("편집본 없던 슬롯에 영상만 보내면 400 — 첫 장면과 함께 보내야 한다")
    void 빈_슬롯은_키_둘을_함께() {
        ChallengeSlotsResponse created = create(List.of(slot(newArtist())));
        Integer slotSeq = created.participants().getFirst().challengeParticipantSeq();
        String newKey = editedKey(created.challengeSeq(), slotSeq, ".mp4");

        assertThatThrownBy(() -> adminChallengeService.replaceEditedKeys(ADMIN, slotSeq,
                new ParticipantEditedUpdateRequest(newKey, null, null, null)))
                .isInstanceOf(BusinessException.class);

        // 둘을 함께 보내면 채워진다. URL 은 CDN 설정에 달려 있어 로컬에서는 null 이라
        // 저장된 키를 본다
        ParticipantEditedResponse filled = adminChallengeService.replaceEditedKeys(ADMIN, slotSeq,
                new ParticipantEditedUpdateRequest(newKey,
                        editedKey(created.challengeSeq(), slotSeq, ".jpg"), null,
                        (long) ORIGIN_DURATION_MS));

        assertThat(filled.slotOrder()).isZero();
        assertThat(scalar("select 1 from motif_core.challenge_participants "
                + "where challenge_participant_seq = ?1 and edited_challenge_key = '" + newKey
                + "' and initial_scene_key is not null", slotSeq)).isEqualTo(1);
    }

    @Test
    @DisplayName("앵커 단독 수정은 잡 참조 중이어도 같은 행을 고친다 — 0017 이전 행을 채우는 통로 (§3.7.9)")
    void 앵커_수정() {
        Integer slotArtist = newArtist();
        ChallengeSlotsResponse created = create(List.of(slot(slotArtist)));
        newCompositeJob(created.challengeSeq(),
                created.participants().getFirst().challengeParticipantSeq());

        var detail = adminChallengeService.updateAnchor(ADMIN, created.challengeSeq(), slotArtist,
                new AnchorRequest(0.7, 0.3, 0.12));

        assertThat(detail.participants().getFirst().anchor().x()).isEqualTo(0.7);
        assertThat(scalar("select 1 from motif_core.challenge_participants "
                + "where challenge_seq = ?1 and anchor_x = 0.7 and anchor_r = 0.12", created.challengeSeq()))
                .isEqualTo(1);

        assertThatThrownBy(() -> adminChallengeService.updateAnchor(ADMIN, created.challengeSeq(), mainArtist,
                new AnchorRequest(0.5, 0.5, 0.1)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.PARTICIPANT_INVALID);
    }

    @Test
    @DisplayName("목록은 아티스트·곡·슬롯 수·편집본 수를 쿼리 한 번에 준다 — 필터는 아티스트·곡 (§3.7.1)")
    void 목록_필터와_집계() {
        ChallengeSlotsResponse created = create(List.of(slot(newArtist())));
        // editedCount 가 슬롯 수와 갈리는지 보려면 편집본이 채워져 있어야 한다
        fillEdited(created.challengeSeq(),
                created.participants().getFirst().challengeParticipantSeq(), ORIGIN_DURATION_MS);

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
        ChallengeSlotsResponse created = create(List.of());
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
    @DisplayName("삭제(§3.7.7)는 is_deleted 만 세운다 — 참여 영상·댓글·슬롯 행은 남는다 (DB §12)")
    void 삭제는_자식을_연쇄하지_않는다() {
        ChallengeSlotsResponse created = create(List.of(slot(newArtist())));
        Integer seq = created.challengeSeq();
        newParticipationVideo(seq, created.participants().getFirst().challengeParticipantSeq());
        newChallengeComment(seq);

        adminChallengeService.delete(ADMIN, seq);

        assertThat(deleted(seq)).isTrue();
        // 연쇄 삭제는 명세 위반이다 — 자식이 한 행이라도 사라지면 여기서 깨진다
        assertThat(rows("participation_videos", seq)).isEqualTo(1);
        assertThat(rows("challenge_comments", seq)).isEqualTo(1);
        assertThat(rows("challenge_participants", seq)).isEqualTo(1);
        // 참여 영상이 스스로 삭제되지도 않는다 — 비노출은 조회 쪽이 거르는 일이다
        assertThat(scalar("select count(*) from motif_core.participation_videos "
                + "where challenge_seq = ?1 and is_deleted = false", seq)).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 삭제된 챌린지의 재삭제는 404 — 없는 변경을 성공으로 보고하지 않는다")
    void 삭제_재삭제는_404() {
        ChallengeSlotsResponse created = create(List.of());
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

    private ChallengeSlotsResponse create(List<ParticipantRequest> participants) {
        ChallengeSlotsResponse response = adminChallengeService.create(ADMIN, request(participants));
        createdChallenges.add(response.challengeSeq());
        return response;
    }

    private ChallengeCreateRequest request(List<ParticipantRequest> participants) {
        return new ChallengeCreateRequest(mainArtist, songSeq, "Supernova 챌린지", videoKey,
                (long) ORIGIN_DURATION_MS, null, 1080, 1920, participants);
    }

    private static AnchorRequest anchor() {
        return new AnchorRequest(0.22, 0.36, 0.0995);
    }

    /** 슬롯 하나 — 등록 요청에는 아티스트와 앵커뿐이다(2026-09-22). */
    private ParticipantRequest slot(Integer artistSeq) {
        return new ParticipantRequest(artistSeq, anchor());
    }

    /**
     * 그 슬롯 자리의 편집본 키 — presign 이 내주는 것과 같은 규약이다(§3.2.1).
     *
     * <p>업로드된 것으로 쳐 둔다. 부재·오배치 케이스는 해당 테스트가 직접 만든다.
     */
    private String editedKey(Integer challengeSeq, Integer slotSeq, String extension) {
        return newUploadedKey(AdminMediaKind.editedPrefix(challengeSeq, slotSeq)
                + UUID.randomUUID() + extension);
    }

    /** 등록된 슬롯에 편집본을 채운다 — 실제 절차(§3.7.2 → §3.7.6) 그대로다. */
    private ParticipantEditedResponse fillEdited(Integer challengeSeq, Integer slotSeq,
                                                 long durationMs) {
        return adminChallengeService.replaceEditedKeys(ADMIN, slotSeq,
                new ParticipantEditedUpdateRequest(
                        editedKey(challengeSeq, slotSeq, ".mp4"),
                        editedKey(challengeSeq, slotSeq, ".jpg"),
                        editedKey(challengeSeq, slotSeq, ".jpg"),
                        durationMs));
    }

    @SuppressWarnings("unchecked")
    private static List<String> reasons(BusinessException e) {
        return (List<String>) e.getData();
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

    /** 합성 이력 = {@code composite_jobs} 가 슬롯을 참조하는 상태 (§3.7.5 판정 근거). */
    private void newCompositeJob(Integer challengeSeq, Integer challengeParticipantSeq) {
        inTransaction(() -> {
            execute("""
                    insert into motif_core.composite_jobs
                        (job_uuid, job_type, user_seq, challenge_seq, challenge_participant_seq, raw_video_key)
                    values (?1, 'CHALLENGE', ?2, ?3, ?4, 'raw/x.mp4')
                    """, UUID.randomUUID(), newUser("USER"), challengeSeq, challengeParticipantSeq);
            createdJobs.add(challengeSeq);
            return null;
        });
    }

    /**
     * 참여 영상 한 건 (§3.7.7 확인 대화상자가 세는 대상) — 삭제가 이 행을 건드리면 안 된다.
     *
     * <p>{@code job_seq} 가 NOT NULL UNIQUE FK 라 잡을 먼저 만든다. 잡은 <b>슬롯을
     * 가리켜야 한다</b> — 무교체가 폐기돼 {@code ck_composite_jobs_type_shape} 가
     * {@code challenge_participant_seq} 를 요구한다(changeSet 0021).
     *
     * @param slotSeq 그 챌린지의 슬롯. 호출부가 등록 응답에서 꺼내 넘긴다
     */
    private void newParticipationVideo(Integer challengeSeq, Integer slotSeq) {
        inTransaction(() -> {
            UUID jobUuid = UUID.randomUUID();
            Integer userSeq = newUser("USER");
            execute("""
                    insert into motif_core.composite_jobs
                        (job_uuid, job_type, user_seq, challenge_seq, challenge_participant_seq,
                         raw_video_key, status, result_video_key, result_thumbnail_key)
                    values (?1, 'CHALLENGE', ?2, ?3, ?4, 'raw/x.mp4',
                            'DONE', 'videos/x.mp4', 'videos/x.jpg')
                    """, jobUuid, userSeq, challengeSeq, slotSeq);
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
