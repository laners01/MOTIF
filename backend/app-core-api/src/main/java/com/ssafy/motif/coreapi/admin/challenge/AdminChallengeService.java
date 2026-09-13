package com.ssafy.motif.coreapi.admin.challenge;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.audit.AdminAuditLogger;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeCreateRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeSlotsRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeSlotsResponse;
import com.ssafy.motif.coreapi.admin.challenge.dto.EditedChallengeRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.ParticipantRequest;
import com.ssafy.motif.coreapi.admin.media.AdminMediaKind;
import com.ssafy.motif.coreapi.admin.media.AdminMediaService;
import com.ssafy.motif.coreapi.admin.media.VideoRules;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.domain.artist.ArtistErrorCode;
import com.ssafy.motif.coreapi.global.media.PublicMediaUrlResolver;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.challenge.Challenge;
import com.ssafy.motif.domain.core.challenge.ChallengeParticipant;
import com.ssafy.motif.domain.core.challenge.ChallengeParticipantRepository;
import com.ssafy.motif.domain.core.challenge.ChallengeRepository;
import com.ssafy.motif.domain.core.challenge.EditedChallenge;
import com.ssafy.motif.domain.core.challenge.EditedChallengeRepository;
import com.ssafy.motif.domain.core.song.SongRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    /**
     * 등록 (§3.7.2). 검증 → 트랜잭션 INSERT 순서다.
     *
     * <p>썸네일 파일은 아직 없다 — 키만 산출물 규약으로 계산해 넣고 app-media 가
     * 나중에 만든다(§1.4 검증 3항). 그때까지 썸네일 URL 은 404 다(§8 #19).
     */
    @Transactional
    public ChallengeSlotsResponse create(AuthenticatedAdmin admin, ChallengeCreateRequest request) {
        verify(request);

        Challenge challenge = challengeRepository.save(Challenge.register(
                request.artistSeq(), request.songSeq(), request.description(),
                request.videoKey(), thumbnailKey(request.videoKey()),
                Math.toIntExact(request.durationMs())));
        saveSlots(challenge.getChallengeSeq(), request.slots());

        auditLogger.write(admin, "CREATE_CHALLENGE", "CHALLENGE",
                challenge.getChallengeSeq(), AdminAuditLogger.RESULT_OK);
        return slotsResponse(challenge);
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

    /** 등록 전 검증 전체 (§3.7.2 검증 1~5항) — 순서는 명세 그대로다. */
    void verify(ChallengeCreateRequest request) {
        requireArtists(List.of(request.artistSeq()));
        if (!songRepository.existsById(request.songSeq())) {
            throw new BusinessException(AdminErrorCode.SONG_NOT_FOUND,
                    "존재하지 않는 곡: songSeq=" + request.songSeq());
        }
        requireOriginKey(request.videoKey());
        videoRules.checkChallengeOrigin(new VideoRules.Meta(
                request.durationMs(), request.width(), request.height()));
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
