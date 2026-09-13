package com.ssafy.motif.coreapi.admin.challenge;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.error.ErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.audit.AdminAuditLogger;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeCreateRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeDetailResponse;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeListItem;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeSlotsRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeSlotsResponse;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeUpdateRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeValidateResponse;
import com.ssafy.motif.coreapi.admin.challenge.dto.EditedChallengeRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.EditedChallengeResponse;
import com.ssafy.motif.coreapi.admin.challenge.dto.EditedChallengeUpdateRequest;
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
import com.ssafy.motif.domain.core.challenge.EditedChallenge;
import com.ssafy.motif.domain.core.challenge.EditedChallengeRepository;
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
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * 챌린지 등록 (어드민 명세 §3.7.2, 부록 A) — S15P21D104-559.
 *
 * <p><b>all-or-nothing 이다.</b> {@code challenges} · {@code challenge_participants} ·
 * {@code edited_challenges} 를 한 트랜잭션에 넣는다. 부분 등록을 허용하면 편집본이
 * 덜 채워진 챌린지가 앱에 노출되고, 사용자가 조합을 골랐을 때 촬영 직전에 404 를
 * 만난다(API 명세 §13.1). {@code challenges} 에 상태 컬럼이 없는 이유이기도 하다.
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
    private final EditedChallengeRepository editedChallengeRepository;
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
     * 통과하는데 등록은 실패하는 조합이 생긴다 — 화면의 "등록" 버튼이 거짓말을 하게 된다.
     * 그래서 예외를 잡아 결과로 옮기는 모양을 택했다.
     */
    public ChallengeValidateResponse validate(ChallengeCreateRequest request) {
        List<Integer> computedMasks = expectedMasks(request.participants().size());
        try {
            verify(request);
            return new ChallengeValidateResponse(true, computedMasks, List.of());
        } catch (BusinessException e) {
            ErrorCode code = e.getErrorCode();
            log.info("사전 검증 실패: code={} message={}", code.getCode(), e.getMessage());
            return new ChallengeValidateResponse(false, computedMasks,
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

    /** 삭제된 챌린지는 어드민에게도 404 다 (§3.7 실패표 "대상 없음·삭제"). */
    Challenge requireChallenge(Integer challengeSeq) {
        return challengeRepository.findByChallengeSeqAndDeletedFalse(challengeSeq)
                .orElseThrow(() -> new BusinessException(ChallengeErrorCode.CHALLENGE_NOT_FOUND,
                        "존재하지 않는 챌린지: challengeSeq=" + challengeSeq));
    }

    /** 슬롯 n 개가 요구하는 조합 {@code 1 … 2ⁿ−1} — 화면 매트릭스의 행 목록이다. */
    static List<Integer> expectedMasks(int slotCount) {
        return IntStream.rangeClosed(1, (1 << slotCount) - 1).boxed().toList();
    }

    private ChallengeDetailResponse detailOf(Challenge challenge) {
        Integer seq = challenge.getChallengeSeq();
        List<ChallengeParticipant> participants =
                participantRepository.findByIdChallengeSeqOrderByParticipantMask(seq);
        List<EditedChallenge> edited =
                editedChallengeRepository.findByChallengeSeqOrderByParticipantMask(seq);

        List<Integer> artistSeqs = new ArrayList<>();
        artistSeqs.add(challenge.getArtistSeq());
        participants.forEach(p -> artistSeqs.add(p.artistSeq()));
        Map<Integer, String> names = artistRepository.findNames(Set.copyOf(artistSeqs)).stream()
                .collect(Collectors.toMap(ArtistName::artistSeq, ArtistName::name));

        Map<Integer, Long> jobRefs = jobRefCounts(edited);
        Song song = songRepository.findById(challenge.getSongSeq()).orElse(null);

        return new ChallengeDetailResponse(
                seq,
                new ChallengeDetailResponse.ArtistSummary(
                        challenge.getArtistSeq(), names.get(challenge.getArtistSeq())),
                song == null ? null : new ChallengeDetailResponse.SongSummary(
                        song.getSongSeq(), song.getTitle(), song.getSingerName()),
                challenge.getDescription(),
                mediaUrlResolver.resolve(challenge.getVideoKey()),
                mediaUrlResolver.resolve(challenge.getThumbnailKey()),
                challenge.getDurationMs(),
                participants.size(),
                edited.size(),
                challenge.getViewCount(),
                challenge.getParticipantCount(),
                challenge.getLikeCount(),
                challenge.getCommentCount(),
                challenge.isDeleted(),
                challenge.getRegDttm(),
                participants.stream().map(p -> new ChallengeDetailResponse.Participant(
                        new ChallengeDetailResponse.ArtistSummary(p.artistSeq(), names.get(p.artistSeq())),
                        p.getParticipantMask(),
                        new ChallengeDetailResponse.BboxResponse(
                                p.getBbox().getLeftTopX(), p.getBbox().getLeftTopY(),
                                p.getBbox().getRightBottomX(), p.getBbox().getRightBottomY()))).toList(),
                edited.stream().map(e -> new ChallengeDetailResponse.Edited(
                        e.getEditedChallengeSeq(), e.getParticipantMask(),
                        mediaUrlResolver.resolve(e.getVideoKey()),
                        mediaUrlResolver.resolve(e.getInitialSceneKey()),
                        jobRefs.getOrDefault(e.getEditedChallengeSeq(), 0L))).toList(),
                challengeRepository.countActiveJobs(seq));
    }

    /** 참조 0 건인 편집본은 group by 결과에 행이 없다 — 호출부가 기본값 0 을 쓴다. */
    private Map<Integer, Long> jobRefCounts(List<EditedChallenge> edited) {
        if (edited.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Long> counts = new HashMap<>();
        editedChallengeRepository.countJobRefs(
                        edited.stream().map(EditedChallenge::getEditedChallengeSeq).toList())
                .forEach(row -> counts.put(row.getEditedChallengeSeq(), row.getJobCount()));
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

    /** 슬롯·편집본 INSERT — 등록(§3.7.2)과 슬롯 교체(§3.7.5)가 공유한다. */
    void saveSlots(Integer challengeSeq, ChallengeSlotsRequest slots) {
        List<ParticipantRequest> participants = slots.participants();
        for (int i = 0; i < participants.size(); i++) {
            ParticipantRequest p = participants.get(i);
            participantRepository.save(ChallengeParticipant.of(
                    challengeSeq, p.artistSeq(), SlotRules.participantMask(i),
                    new ChallengeParticipant.Bbox(p.bbox().leftTopX(), p.bbox().leftTopY(),
                            p.bbox().rightBottomX(), p.bbox().rightBottomY())));
        }

        List<EditedChallengeRequest> edited = slots.editedChallenges();
        List<Integer> masks = SlotRules.editedMasks(participants.size(), edited);
        for (int i = 0; i < edited.size(); i++) {
            editedChallengeRepository.save(EditedChallenge.of(challengeSeq, masks.get(i),
                    edited.get(i).videoKey(), edited.get(i).initialSceneKey()));
        }
    }

    /**
     * 슬롯 구성 전체 교체 (§3.7.5) — 기존 슬롯·편집본을 하드 삭제하고 재생성한다.
     *
     * <p><b>합성 이력이 있으면 거절한다.</b> {@code composite_jobs.edited_challenge_seq} 의
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

        List<Integer> inUse = editedChallengeRepository.findSeqsReferencedByJobs(challengeSeq);
        if (!inUse.isEmpty()) {
            throw new BusinessException(AdminErrorCode.EDITED_IN_USE,
                    "합성 잡이 참조 중인 편집본: " + inUse, Map.of("editedChallengeSeqs", inUse));
        }

        verifySlots(challenge.getArtistSeq(), challenge.getDurationMs(), slots, List.of());

        editedChallengeRepository.deleteByChallengeSeq(challengeSeq);
        participantRepository.deleteByIdChallengeSeq(challengeSeq);
        // DELETE 를 INSERT 보다 먼저 DB 에 내보낸다 — 이유는 리포지토리 javadoc
        editedChallengeRepository.flush();
        participantRepository.flush();

        saveSlots(challengeSeq, slots);

        auditLogger.write(admin, "REPLACE_CHALLENGE_SLOTS", "CHALLENGE",
                challengeSeq, AdminAuditLogger.RESULT_OK);
        return slotsResponse(challenge);
    }

    /**
     * 편집본 키 교체 (§3.7.6) — 같은 행 UPDATE 라 조합이 유지된다.
     *
     * <p>{@link #replaceSlots} 와 달리 <b>사용 중이어도 허용</b>한다. 결과물은 별도
     * 오브젝트라 이미 합성된 참여 영상이 바뀌지 않고, 이미 발급된 잡은 큐 메시지를
     * 잡 행에서 재구성하므로 다음 합성부터 새 키를 쓴다.
     */
    @Transactional
    public EditedChallengeResponse replaceEditedKeys(AuthenticatedAdmin admin, Integer editedChallengeSeq,
                                                     EditedChallengeUpdateRequest request) {
        EditedChallenge edited = editedChallengeRepository.findById(editedChallengeSeq)
                .orElseThrow(() -> new BusinessException(ChallengeErrorCode.EDITED_CHALLENGE_NOT_FOUND,
                        "존재하지 않는 편집본: editedChallengeSeq=" + editedChallengeSeq));
        Challenge challenge = requireChallenge(edited.getChallengeSeq());

        List<String> keys = Stream.of(request.videoKey(), request.initialSceneKey())
                .filter(Objects::nonNull)
                .toList();
        if (keys.isEmpty()) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER,
                    "videoKey·initialSceneKey 중 하나는 있어야 한다");
        }
        adminMediaService.requireUploaded(keys);
        if (request.durationMs() != null) {
            videoRules.checkEditedDuration(request.durationMs(), challenge.getDurationMs());
        }

        edited.replaceKeys(request.videoKey(), request.initialSceneKey());

        auditLogger.write(admin, "REPLACE_EDITED_KEYS", "EDITED_CHALLENGE",
                editedChallengeSeq, AdminAuditLogger.RESULT_OK);
        return new EditedChallengeResponse(editedChallengeSeq, edited.getParticipantMask(),
                mediaUrlResolver.resolve(edited.getVideoKey()),
                mediaUrlResolver.resolve(edited.getInitialSceneKey()));
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
        verifySlots(request.artistSeq(), request.durationMs(), request.slots(),
                List.of(request.videoKey()));
    }

    /**
     * 슬롯·편집본 검증 (§3.7.2 검증 2~5항) — 등록과 슬롯 교체(§3.7.5)가 공유한다.
     *
     * @param originDurationMs 편집본 길이를 대조할 원본 길이. 교체 시에는 기존 행의 값이다
     * @param extraKeys        원본 키처럼 함께 HEAD 할 키 — 교체에서는 비어 있다(원본 재검증 없음)
     */
    void verifySlots(Integer mainArtistSeq, long originDurationMs,
                     ChallengeSlotsRequest slots, List<String> extraKeys) {
        SlotRules.checkParticipants(mainArtistSeq, slots.participants());
        requireArtists(slots.participants().stream().map(ParticipantRequest::artistSeq).toList());

        // 마스크 계산이 곧 완결성 검증이다 — 결과는 버리고 예외만 본다(INSERT 때 다시 계산)
        SlotRules.editedMasks(slots.participants().size(), slots.editedChallenges());

        List<String> keys = new ArrayList<>(extraKeys);
        slots.editedChallenges().forEach(edited -> {
            keys.add(edited.videoKey());
            keys.add(edited.initialSceneKey());
        });
        adminMediaService.requireUploaded(keys);

        slots.editedChallenges().stream()
                .filter(edited -> edited.durationMs() != null)
                .forEach(edited -> videoRules.checkEditedDuration(edited.durationMs(), originDurationMs));
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
                participantRepository.findByIdChallengeSeqOrderByParticipantMask(seq).stream()
                        .map(p -> new ChallengeSlotsResponse.Participant(p.artistSeq(), p.getParticipantMask()))
                        .toList(),
                editedChallengeRepository.findByChallengeSeqOrderByParticipantMask(seq).stream()
                        .map(e -> new ChallengeSlotsResponse.Edited(
                                e.getEditedChallengeSeq(), e.getParticipantMask()))
                        .toList());
    }
}
