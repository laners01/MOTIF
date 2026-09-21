package com.ssafy.motif.coreapi.admin.challenge;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.error.ErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.audit.AdminAuditLogger;
import com.ssafy.motif.coreapi.admin.challenge.dto.AnchorRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeCreateRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeDetailResponse;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeListItem;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeSlotsRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeSlotsResponse;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeUpdateRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeValidateResponse;
import com.ssafy.motif.coreapi.admin.challenge.dto.ParticipantEditedResponse;
import com.ssafy.motif.coreapi.admin.challenge.dto.ParticipantEditedUpdateRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.ParticipantRequest;
import com.ssafy.motif.coreapi.admin.media.AdminMediaKind;
import com.ssafy.motif.coreapi.admin.media.AdminMediaService;
import com.ssafy.motif.coreapi.admin.media.VideoRules;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.domain.media.MediaJobPublisher;
import com.ssafy.motif.coreapi.domain.media.MediaKeys;
import com.ssafy.motif.coreapi.domain.artist.ArtistErrorCode;
import com.ssafy.motif.coreapi.domain.challenge.ChallengeErrorCode;
import com.ssafy.motif.coreapi.global.media.PublicMediaUrlResolver;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.global.response.PageResponses;
import com.ssafy.motif.domain.core.artist.ArtistName;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.challenge.Challenge;
import com.ssafy.motif.domain.core.challenge.ChallengeListRow;
import com.ssafy.motif.domain.core.challenge.ChallengeParticipant;
import com.ssafy.motif.domain.core.challenge.ChallengeParticipantRepository;
import com.ssafy.motif.domain.core.challenge.ChallengeRepository;
import com.ssafy.motif.domain.core.song.Song;
import com.ssafy.motif.domain.core.song.SongRepository;
import com.ssafy.motif.event.contract.ChallengeCreatedEvent;
import com.ssafy.motif.event.contract.MediaJobRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 챌린지 등록 (어드민 명세 §3.7.2, 부록 A) — S15P21D104-559.
 *
 * <p><b>all-or-nothing 이다.</b> {@code challenges} 와 {@code challenge_participants} 를 한
 * 트랜잭션에 넣는다. 부분 등록을 허용하면 슬롯이 덜 채워진 챌린지가 앱에 노출된다.
 * {@code challenges} 에 상태 컬럼이 없는 이유이기도 하다.
 *
 * <p><b>편집본은 슬롯 행에 같이 들어간다</b>(0019) — {@code edited_challenges} 테이블은
 * 없어졌고 조합 완결성 강제도 함께 사라졌다. 편집본이 비어 있는 슬롯은 오류가 아니라
 * "아직 안 만든 자리" 이고, 사용자가 그 슬롯을 고르면 §13.1 이 404 로 막는다.
 *
 * <p><b>검증이 전부 끝난 뒤에 첫 INSERT 가 나간다</b>(§3.7.2 "전부 통과해야 트랜잭션
 * 진입"). 그래서 어느 단계에서 걸려도 남는 행이 없다 — 롤백에 기대기 전에 순서로
 * 보장하고, 롤백은 그 뒤의 안전망이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminChallengeService {

    /** {@code challenges/{uuid}.mp4} 만 원본으로 받는다 — {@code raw/} 는 사용자 원본 전용이다(§1.4). */
    private static final String ORIGIN_PREFIX = "challenges/";
    private static final String ORIGIN_EXTENSION = ".mp4";

    private final ChallengeRepository challengeRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final ArtistRepository artistRepository;
    private final SongRepository songRepository;
    private final AdminMediaService adminMediaService;
    private final VideoRules videoRules;
    private final PublicMediaUrlResolver mediaUrlResolver;
    private final AdminAuditLogger auditLogger;
    private final MediaJobPublisher mediaJobPublisher;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 등록 (§3.7.2). 검증 → 트랜잭션 INSERT 순서다.
     *
     * <p>썸네일 파일은 아직 없다 — 키만 산출물 규약으로 계산해 넣고 app-media 가
     * 나중에 만든다(§1.4 검증 3항). 그때까지 썸네일 URL 은 404 다(§8 #19).
     *
     * <p>커밋 후 메시지 <b>2건</b>이 나간다 — {@code challenge.created}(구독자 팬아웃)와
     * {@code job.media}(렌디션·HLS·썸네일). 롤백되면 0건이다({@link #publishCreated}).
     */
    @Transactional
    public ChallengeSlotsResponse create(AuthenticatedAdmin admin, ChallengeCreateRequest request) {
        verify(request);

        Challenge challenge = challengeRepository.save(Challenge.register(
                request.artistSeq(), request.songSeq(), request.description(),
                request.videoKey(), thumbnailKey(request.videoKey()),
                Math.toIntExact(request.durationMs())));
        saveSlots(challenge.getChallengeSeq(), request.slots());
        publishCreated(challenge, request.thumbnailAtMs());

        auditLogger.write(admin, "CREATE_CHALLENGE", "CHALLENGE",
                challenge.getChallengeSeq(), AdminAuditLogger.RESULT_OK);
        return slotsResponse(challenge);
    }

    /**
     * 등록 전 사전 검증 (§3.7.8) — DB 를 바꾸지 않는다.
     *
     * <p>화면이 편집본 매트릭스를 채우는 동안 계속 부르는 dry-run 이라, <b>실패도 200</b>
     * 으로 돌려준다. 조합이 덜 찬 상태는 오류가 아니라 정상적인 중간 상태다.
     *
     * <p><b>등록과 같은 {@link #verify} 를 탄다.</b> 검증 로직을 따로 쓰면 사전 검증은
     * 통과하는데 등록은 실패하는 입력이 생긴다 — 화면의 "등록" 버튼이 거짓말을 하게 된다.
     * 그래서 예외를 잡아 결과로 옮기는 모양을 택했다.
     */
    public ChallengeValidateResponse validate(ChallengeCreateRequest request) {
        try {
            verify(request);
            return new ChallengeValidateResponse(true, List.of());
        } catch (BusinessException e) {
            ErrorCode code = e.getErrorCode();
            log.info("사전 검증 실패: code={} message={}", code.getCode(), e.getMessage());
            return new ChallengeValidateResponse(false,
                    List.of(new ChallengeValidateResponse.Issue(
                            code.getCode(), code.getMessage(), e.getData())));
        }
    }

    /**
     * 목록 (§3.7.1) — 아티스트·곡 필터, offset 페이지.
     *
     * <p>쿼리 한 번이다. 아티스트 이름·곡·슬롯 수·편집본 수를 행마다 따로 읽으면
     * 20행짜리 한 페이지에 40번을 더 왕복한다({@link ChallengeListRow}).
     */
    @Transactional(readOnly = true)
    public PageResponse<ChallengeListItem> list(Integer artistSeq, Integer songSeq,
                                                boolean includeDeleted, Pageable pageable) {
        return PageResponses.of(challengeRepository.search(artistSeq, songSeq, includeDeleted, pageable)
                .map(row -> ChallengeListItem.of(row, mediaUrlResolver.resolve(row.thumbnailKey()))));
    }

    /** 상세 (§3.7.3). */
    @Transactional(readOnly = true)
    public ChallengeDetailResponse detail(Integer challengeSeq) {
        return detailOf(requireChallenge(challengeSeq));
    }

    /**
     * 소개글·곡 수정 (§3.7.4).
     *
     * <p>원본 영상·메인 아티스트가 요청에 들어 있으면 <b>바꾸지 않고 거절한다</b> —
     * 이유는 {@link ChallengeUpdateRequest} 에 적었다.
     */
    @Transactional
    public ChallengeDetailResponse update(AuthenticatedAdmin admin, Integer challengeSeq,
                                          ChallengeUpdateRequest request) {
        List<String> immutable = new ArrayList<>();
        if (request.videoKey() != null) {
            immutable.add("videoKey");
        }
        if (request.artistSeq() != null) {
            immutable.add("artistSeq");
        }
        if (!immutable.isEmpty()) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER,
                    "불변 필드는 수정할 수 없다: " + immutable, Map.of("immutableFields", immutable));
        }

        Challenge challenge = requireChallenge(challengeSeq);
        if (request.songSeq() != null && !songRepository.existsById(request.songSeq())) {
            throw new BusinessException(AdminErrorCode.SONG_NOT_FOUND,
                    "존재하지 않는 곡: songSeq=" + request.songSeq());
        }
        challenge.update(request.description(), request.songSeq());

        auditLogger.write(admin, "UPDATE_CHALLENGE", "CHALLENGE",
                challengeSeq, AdminAuditLogger.RESULT_OK);
        return detailOf(challenge);
    }

    /**
     * 소프트 삭제 (§3.7.7) — {@code challenges.is_deleted} 만 세운다. {@code data} 는 null 이다.
     *
     * <p><b>자식 행을 건드리지 않는다</b>(DB SPEC §12 서비스 규칙). 참여 영상·댓글·좋아요는
     * 그대로 남고, 일반 API 가 "삭제된 챌린지에 속한 참여 영상" 을 거르는 쪽에서 비노출을
     * 책임진다. 여기서 연쇄로 지우면 되돌릴 수 없고 {@code participant_count} 도 어긋난다 —
     * 그래서 화면 확인 대화상자가 "참여 영상 n건이 앱에서 비노출됨" 이라고만 말한다.
     *
     * <p>건수는 목록 항목의 {@code participantCount}(= {@code challenges.participant_count})
     * 를 화면이 이미 갖고 있다 — 별도 조회도, 전용 엔드포인트도 두지 않는다.
     *
     * <p>이미 삭제된 챌린지는 {@link #requireChallenge} 가 404 로 막는다 — 재삭제가 성공한
     * 것처럼 보이면 화면이 없는 변경을 보고하게 된다.
     */
    @Transactional
    public void delete(AuthenticatedAdmin admin, Integer challengeSeq) {
        requireChallenge(challengeSeq).softDelete();
        auditLogger.write(admin, "DELETE_CHALLENGE", "CHALLENGE",
                challengeSeq, AdminAuditLogger.RESULT_OK);
    }

    /** 삭제된 챌린지는 어드민에게도 404 다 (§3.7 실패표 "대상 없음·삭제"). */
    Challenge requireChallenge(Integer challengeSeq) {
        return challengeRepository.findByChallengeSeqAndDeletedFalse(challengeSeq)
                .orElseThrow(() -> new BusinessException(ChallengeErrorCode.CHALLENGE_NOT_FOUND,
                        "존재하지 않는 챌린지: challengeSeq=" + challengeSeq));
    }

    private ChallengeDetailResponse detailOf(Challenge challenge) {
        Integer seq = challenge.getChallengeSeq();
        List<ChallengeParticipant> participants =
                participantRepository.findByChallengeSeqOrderBySlotOrderAsc(seq);

        List<Integer> artistSeqs = new ArrayList<>();
        artistSeqs.add(challenge.getArtistSeq());
        participants.forEach(p -> artistSeqs.add(p.getArtistSeq()));
        Map<Integer, ArtistName> artists = artistRepository.findNames(Set.copyOf(artistSeqs)).stream()
                .collect(Collectors.toMap(ArtistName::artistSeq, a -> a));

        Map<Integer, Long> jobRefs = jobRefCounts(participants);
        Song song = songRepository.findById(challenge.getSongSeq()).orElse(null);

        return new ChallengeDetailResponse(
                seq,
                artistSummary(challenge.getArtistSeq(), artists),
                song == null ? null : new ChallengeDetailResponse.SongSummary(
                        song.getSongSeq(), song.getTitle(), song.getSingerName()),
                challenge.getDescription(),
                mediaUrlResolver.resolve(challenge.getVideoKey()),
                mediaUrlResolver.resolve(challenge.getThumbnailKey()),
                challenge.getDurationMs(),
                participants.size(),
                (int) participants.stream().filter(ChallengeParticipant::hasEdited).count(),
                challenge.getViewCount(),
                challenge.getParticipantCount(),
                challenge.getLikeCount(),
                challenge.getCommentCount(),
                challenge.isDeleted(),
                challenge.getRegDttm(),
                participants.stream().map(p -> new ChallengeDetailResponse.Participant(
                        p.getChallengeParticipantSeq(),
                        artistSummary(p.getArtistSeq(), artists),
                        p.getSlotOrder(),
                        anchorOf(p),
                        mediaUrlResolver.resolve(p.getEditedChallengeKey()),
                        mediaUrlResolver.resolve(p.getInitialSceneKey()),
                        mediaUrlResolver.resolve(p.getPreviewImageKey()),
                        p.getEditedChallengeKey(), p.getInitialSceneKey(), p.getPreviewImageKey(),
                        p.getEditedDurationMs(),
                        jobRefs.getOrDefault(p.getChallengeParticipantSeq(), 0L))).toList(),
                challengeRepository.countActiveJobs(seq));
    }

    /** 프로필 URL 은 저장값이 아니라 조립 시점 변환이다 — 상세 화면이 슬롯 원 안에 넣는다. 삭제된 아티스트면 이름·사진이 비어 온다. */
    private ChallengeDetailResponse.ArtistSummary artistSummary(Integer artistSeq, Map<Integer, ArtistName> artists) {
        ArtistName a = artists.get(artistSeq);
        return new ChallengeDetailResponse.ArtistSummary(artistSeq,
                a == null ? null : a.name(),
                a == null ? null : mediaUrlResolver.resolve(a.profileImageKey()));
    }

    /** 0017 이전 행은 앵커가 없다 — {@code null} 로 내려 화면이 "위치 지정" 을 재촉하게 한다. */
    private static ChallengeDetailResponse.AnchorResponse anchorOf(ChallengeParticipant p) {
        ChallengeParticipant.Anchor a = p.anchorOrNull();
        return a == null ? null : new ChallengeDetailResponse.AnchorResponse(a.getX(), a.getY(), a.getR());
    }

    /** 참조 0 건인 슬롯은 group by 결과에 행이 없다 — 호출부가 기본값 0 을 쓴다. */
    private Map<Integer, Long> jobRefCounts(List<ChallengeParticipant> participants) {
        if (participants.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Long> counts = new HashMap<>();
        participantRepository.countJobRefs(participants.stream()
                        .map(ChallengeParticipant::getChallengeParticipantSeq).toList())
                .forEach(row -> counts.put(row.getChallengeParticipantSeq(), row.getJobCount()));
        return counts;
    }

    /**
     * 등록 완료 통지 2건 (§1.6 표 · §4 계약) — 둘 다 <b>커밋 후에</b> 나간다.
     *
     * <p>여기서 부르는 것은 발행이 아니라 스프링 애플리케이션 이벤트다. MQ 로 나가는
     * 시점은 {@code @TransactionalEventListener(AFTER_COMMIT)} 인 릴레이가 정한다
     * ({@link com.ssafy.motif.coreapi.domain.challenge.ChallengeEventRelay} ·
     * {@link com.ssafy.motif.coreapi.domain.media.MediaJobEventRelay}). 커밋 전에 보내면
     * app-media 가 아직 없는 행을 갱신하려 들고, 알림은 없던 챌린지를 광고한다.
     *
     * <p>{@code job.media} 는 <b>새로 짜지 않고</b> {@link MediaJobPublisher} 를 부른다 —
     * 합성 결과와 어드민 원본이 같은 한 곳을 거쳐 같은 큐로 가야 인코딩 파라미터가
     * 갈라지지 않는다(ADR-0010 D1).
     */
    private void publishCreated(Challenge challenge, Long thumbnailAtMs) {
        Integer artistSeq = challenge.getArtistSeq();
        String artistName = artistRepository.findNames(Set.of(artistSeq)).stream()
                .findFirst().map(ArtistName::name).orElse(null);
        String songTitle = songRepository.findById(challenge.getSongSeq())
                .map(Song::getTitle).orElse(null);

        eventPublisher.publishEvent(new ChallengeCreatedEvent(
                UUID.randomUUID().toString(), Instant.now(),
                challenge.getChallengeSeq(), artistSeq, artistName, songTitle,
                challenge.getThumbnailKey()));

        mediaJobPublisher.publish(
                MediaJobRequestedEvent.SOURCE_CHALLENGE_ORIGIN,
                challenge.getChallengeSeq(),
                challenge.getVideoKey(),
                MediaKeys.forChallengeOrigin(originUuid(challenge.getVideoKey())),
                thumbnailAtMs);
    }

    /** {@code challenges/{uuid}.mp4} → {@code uuid}. 키 규약은 {@link #requireOriginKey} 가 이미 확인했다. */
    private static String originUuid(String videoKey) {
        return videoKey.substring(ORIGIN_PREFIX.length(), videoKey.length() - ORIGIN_EXTENSION.length());
    }

    /**
     * 슬롯 INSERT — 등록(§3.7.2)과 슬롯 교체(§3.7.5)가 공유한다.
     *
     * <p>편집본이 같은 행에 들어가므로 INSERT 가 한 종류다(0019). <b>배열 인덱스가 곧
     * {@code slotOrder}</b> 이고, 그래서 어드민이 순서를 손으로 넣을 자리가 없다.
     */
    void saveSlots(Integer challengeSeq, ChallengeSlotsRequest slots) {
        List<ParticipantRequest> participants = slots.participants();
        for (int i = 0; i < participants.size(); i++) {
            ParticipantRequest p = participants.get(i);
            participantRepository.save(ChallengeParticipant.of(
                    challengeSeq, p.artistSeq(), i, toAnchor(p.anchor()),
                    p.editedChallengeKey(), p.initialSceneKey(), p.previewImageKey(),
                    // 편집본이 있으면 SlotRules 가 이미 필수로 확인했다
                    p.durationMs() == null ? null : Math.toIntExact(p.durationMs())));
        }
    }

    private static ChallengeParticipant.Anchor toAnchor(AnchorRequest a) {
        return new ChallengeParticipant.Anchor(a.x(), a.y(), a.r());
    }

    /**
     * 슬롯 앵커 단독 수정 (§3.7.9) — 같은 행 UPDATE 다.
     *
     * <p>§3.7.5 슬롯 교체는 편집본까지 다시 보내야 하고 합성 이력이 있으면 409 라, 0017 이전
     * 행의 빈 앵커를 채울 길이 없다. 앵커는 슬롯 순서·편집본과 무관하므로 잡 참조 중이어도
     * 허용된다 — 바뀌는 것은 선택 화면의 원 위치뿐이다.
     */
    @Transactional
    public ChallengeDetailResponse updateAnchor(AuthenticatedAdmin admin, Integer challengeSeq,
                                                Integer artistSeq, AnchorRequest anchor) {
        Challenge challenge = requireChallenge(challengeSeq);
        if (!SlotRules.validAnchor(anchor)) {
            throw new BusinessException(AdminErrorCode.PARTICIPANT_INVALID,
                    "앵커 규칙 위반: " + anchor, List.of("ANCHOR_INVALID"));
        }
        ChallengeParticipant slot = participantRepository
                .findByChallengeSeqAndArtistSeq(challengeSeq, artistSeq)
                .orElseThrow(() -> new BusinessException(AdminErrorCode.PARTICIPANT_INVALID,
                        "슬롯이 아닌 아티스트: challengeSeq=%d artistSeq=%d".formatted(challengeSeq, artistSeq),
                        List.of("SLOT_NOT_FOUND")));
        slot.updateAnchor(toAnchor(anchor));

        auditLogger.write(admin, "UPDATE_SLOT_ANCHOR", "CHALLENGE",
                challengeSeq, AdminAuditLogger.RESULT_OK);
        return detailOf(challenge);
    }

    /**
     * 슬롯 구성 전체 교체 (§3.7.5) — 기존 슬롯·편집본을 하드 삭제하고 재생성한다.
     *
     * <p><b>합성 이력이 있으면 거절한다.</b> {@code composite_jobs.challenge_participant_seq} 의
     * FK 가 {@code ON DELETE RESTRICT} 라 지울 수도 없거니와, 지울 수 있다 해도 지워서는
     * 안 된다 — 이미 그 편집본 위에 합성된 참여 영상들이 근거를 잃는다. 결국
     * <b>참여가 시작된 챌린지의 슬롯 구성은 사실상 고정</b>이고, 화면은 이 응답으로
     * 버튼을 잠근다.
     *
     * <p>원본은 건드리지 않는다 — 길이·해상도 재검증도, 원본 키 HEAD 도 하지 않는다.
     */
    @Transactional
    public ChallengeSlotsResponse replaceSlots(AuthenticatedAdmin admin, Integer challengeSeq,
                                               ChallengeSlotsRequest slots) {
        Challenge challenge = requireChallenge(challengeSeq);

        List<Integer> inUse = participantRepository.findSeqsReferencedByJobs(challengeSeq);
        if (!inUse.isEmpty()) {
            throw new BusinessException(AdminErrorCode.EDITED_IN_USE,
                    "합성 잡이 참조 중인 슬롯: " + inUse, Map.of("challengeParticipantSeqs", inUse));
        }

        verifySlots(challenge.getDurationMs(), slots, List.of());

        participantRepository.deleteByChallengeSeq(challengeSeq);
        // DELETE 를 INSERT 보다 먼저 DB 에 내보낸다 — 이유는 리포지토리 javadoc
        participantRepository.flush();

        saveSlots(challengeSeq, slots);

        auditLogger.write(admin, "REPLACE_CHALLENGE_SLOTS", "CHALLENGE",
                challengeSeq, AdminAuditLogger.RESULT_OK);
        return slotsResponse(challenge);
    }

    /**
     * 슬롯 편집본 키 교체 (§3.7.6) — 같은 행 UPDATE 라 인물·순서가 유지된다.
     *
     * <p>{@link #replaceSlots} 와 달리 <b>사용 중이어도 허용</b>한다. 결과물은 별도
     * 오브젝트라 이미 합성된 참여 영상이 바뀌지 않고, 이미 발급된 잡은 큐 메시지를
     * 잡 행에서 재구성하므로 다음 합성부터 새 키를 쓴다.
     *
     * <p><b>편집본이 없던 슬롯을 채우는 통로이기도 하다</b>(0019). 그때는 영상과 첫 장면을
     * <b>함께</b> 보내야 한다 — DB CHECK 가 짝을 강제한다.
     */
    @Transactional
    public ParticipantEditedResponse replaceEditedKeys(AuthenticatedAdmin admin,
                                                       Integer challengeParticipantSeq,
                                                       ParticipantEditedUpdateRequest request) {
        ChallengeParticipant slot = participantRepository.findById(challengeParticipantSeq)
                .orElseThrow(() -> new BusinessException(ChallengeErrorCode.EDITED_CHALLENGE_NOT_FOUND,
                        "존재하지 않는 슬롯: challengeParticipantSeq=" + challengeParticipantSeq));
        Challenge challenge = requireChallenge(slot.getChallengeSeq());

        List<String> keys = Stream.of(request.editedChallengeKey(), request.initialSceneKey(),
                        request.previewImageKey())
                .filter(Objects::nonNull)
                .toList();
        if (keys.isEmpty()) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER,
                    "editedChallengeKey·initialSceneKey·previewImageKey 중 하나는 있어야 한다");
        }
        // HEAD 만으로는 옛 경로(edited/…)를 걸러내지 못한다 — 저장되면 §7.5·§7.6 이
        // 서명 없이 내보낸다(0020). 등록(§3.7.2)은 SlotRules 가 같은 검사를 한다
        List<String> unprotected = keys.stream().filter(k -> !MediaKeys.isProtected(k)).toList();
        if (!unprotected.isEmpty()) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER,
                    "편집본 키는 보호 프리픽스여야 한다: " + unprotected,
                    List.of("EDITED_KEY_NOT_PROTECTED"));
        }
        // 편집본이 없던 슬롯은 영상·첫 장면을 함께 받아야 한다 — 한쪽만 채우면
        // ck_challenge_participants_edited 가 거부하고 500 으로 샌다
        if (!slot.hasEdited()
                && (request.editedChallengeKey() == null || request.initialSceneKey() == null)) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER,
                    "편집본이 없는 슬롯은 editedChallengeKey·initialSceneKey 를 함께 보내야 한다",
                    List.of("EDITED_KEYS_INCOMPLETE"));
        }
        // 영상을 새로 올리면 길이도 함께 받는다 — §7.6 이 앱에 내보내는 값이라
        // 옛 길이가 남으면 촬영 타이머가 새 영상과 어긋난다 (0020)
        if (request.editedChallengeKey() != null && request.durationMs() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER,
                    "편집본 영상을 바꾸면 durationMs 도 함께 보내야 한다",
                    List.of("EDITED_DURATION_REQUIRED"));
        }
        adminMediaService.requireUploaded(keys);
        if (request.durationMs() != null) {
            videoRules.checkEditedDuration(request.durationMs(), challenge.getDurationMs());
        }

        slot.replaceEditedKeys(request.editedChallengeKey(), request.initialSceneKey(),
                request.previewImageKey(),
                request.durationMs() == null ? null : Math.toIntExact(request.durationMs()));

        auditLogger.write(admin, "REPLACE_EDITED_KEYS", "CHALLENGE_PARTICIPANT",
                challengeParticipantSeq, AdminAuditLogger.RESULT_OK);
        return new ParticipantEditedResponse(challengeParticipantSeq, slot.getArtistSeq(),
                slot.getSlotOrder(),
                mediaUrlResolver.resolve(slot.getEditedChallengeKey()),
                mediaUrlResolver.resolve(slot.getInitialSceneKey()),
                mediaUrlResolver.resolve(slot.getPreviewImageKey()),
                slot.getEditedDurationMs());
    }

    /** 등록 전 검증 전체 (§3.7.2 검증 1~5항) — 순서는 명세 그대로다. */
    void verify(ChallengeCreateRequest request) {
        requireArtists(List.of(request.artistSeq()));
        if (!songRepository.existsById(request.songSeq())) {
            throw new BusinessException(AdminErrorCode.SONG_NOT_FOUND,
                    "존재하지 않는 곡: songSeq=" + request.songSeq());
        }
        requireOriginKey(request.videoKey());
        videoRules.checkChallengeOrigin(new VideoRules.Meta(
                request.durationMs(), request.width(), request.height()),
                request.thumbnailAtMs());
        verifySlots(request.durationMs(), request.slots(), List.of(request.videoKey()));
    }

    /**
     * 슬롯·편집본 검증 (§3.7.2 검증 2~5항) — 등록과 슬롯 교체(§3.7.5)가 공유한다.
     *
     * <p>0019 이후 편집본이 슬롯에 붙으므로 검증도 슬롯 배열 한 번을 훑는다. 조합 완결성
     * ({@code EDITED_SET_INCOMPLETE})은 없어졌고, 키 짝 검사가 {@link SlotRules} 로 들어갔다.
     *
     * @param originDurationMs 편집본 길이를 대조할 원본 길이. 교체 시에는 기존 행의 값이다
     * @param extraKeys        원본 키처럼 함께 HEAD 할 키 — 교체에서는 비어 있다(원본 재검증 없음)
     */
    void verifySlots(long originDurationMs, ChallengeSlotsRequest slots, List<String> extraKeys) {
        SlotRules.checkParticipants(slots.participants());
        requireArtists(slots.participants().stream().map(ParticipantRequest::artistSeq).toList());

        // 보내지 않은 키는 HEAD 목록에서 뺀다 — 편집본이 없는 슬롯은 정상이다
        List<String> keys = new ArrayList<>(extraKeys);
        slots.participants().forEach(p -> Stream.of(
                        p.editedChallengeKey(), p.initialSceneKey(), p.previewImageKey())
                .filter(Objects::nonNull)
                .forEach(keys::add));
        adminMediaService.requireUploaded(keys);

        slots.participants().stream()
                .filter(p -> p.durationMs() != null)
                .forEach(p -> videoRules.checkEditedDuration(p.durationMs(), originDurationMs));
    }

    /** 아티스트가 전부 존재하고 비삭제인지 — 개수 비교로 끝낸다(어느 것인지는 응답에 담지 않는다). */
    private void requireArtists(List<Integer> artistSeqs) {
        if (artistSeqs.isEmpty()) {
            return;
        }
        Set<Integer> distinct = Set.copyOf(artistSeqs);
        if (artistRepository.countByArtistSeqInAndDeletedFalse(distinct) != distinct.size()) {
            throw new BusinessException(ArtistErrorCode.ARTIST_NOT_FOUND,
                    "존재하지 않거나 삭제된 아티스트가 있다: " + distinct);
        }
    }

    /**
     * 원본 키가 {@code CHALLENGE_VIDEO} 프리픽스 규약을 지키는지 (§1.4 표).
     *
     * <p>presign 이 발급한 키를 그대로 되돌려받는 것이 정상 흐름이지만, 폼 값이라
     * 임의의 키를 적어 보낼 수 있다. 규약이 깨지면 산출물 프리픽스({@code 확장자를 뗀 키})
     * 가 엉뚱한 곳을 가리켜 app-media 가 남의 프리픽스에 파일을 깐다.
     */
    private void requireOriginKey(String videoKey) {
        if (!videoKey.startsWith(ORIGIN_PREFIX) || !videoKey.endsWith(ORIGIN_EXTENSION)
                || videoKey.indexOf('/', ORIGIN_PREFIX.length()) >= 0) {
            throw new BusinessException(AdminErrorCode.MEDIA_INVALID,
                    "챌린지 원본 키 규약 위반: " + videoKey, List.of("ORIGIN_KEY_INVALID"));
        }
    }

    /**
     * 산출물 프리픽스 아래의 썸네일 (§1.4 검증 3항 — {@code {산출물 프리픽스}/thumb.jpg}).
     *
     * <p>{@code challenges/{uuid}.mp4} 에서 확장자를 떼면 산출물 프리픽스가 된다 —
     * {@link AdminMediaKind} 가 확장자를 표로 고정한 이유다.
     */
    static String thumbnailKey(String videoKey) {
        return videoKey.substring(0, videoKey.length() - ORIGIN_EXTENSION.length()) + "/thumb.jpg";
    }

    /** 등록·슬롯 교체 공통 응답 (§3.7.2 · §3.7.5). */
    ChallengeSlotsResponse slotsResponse(Challenge challenge) {
        Integer seq = challenge.getChallengeSeq();
        return new ChallengeSlotsResponse(
                seq, challenge.getDurationMs(),
                mediaUrlResolver.resolve(challenge.getThumbnailKey()),
                participantRepository.findByChallengeSeqOrderBySlotOrderAsc(seq).stream()
                        .map(p -> new ChallengeSlotsResponse.Participant(
                                p.getChallengeParticipantSeq(), p.getArtistSeq(),
                                p.getSlotOrder(), p.hasEdited()))
                        .toList());
    }
}
