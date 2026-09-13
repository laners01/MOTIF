package com.ssafy.motif.coreapi.domain.challenge.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.artist.ArtistErrorCode;
import com.ssafy.motif.coreapi.domain.challenge.ChallengeErrorCode;
import com.ssafy.motif.coreapi.domain.challenge.dto.ChallengeDetailResponse;
import com.ssafy.motif.coreapi.global.media.PublicMediaUrlResolver;
import com.ssafy.motif.domain.core.artist.ArtistListRow;
import com.ssafy.motif.domain.core.artist.ArtistName;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.artist.SubscriptionRepository;
import com.ssafy.motif.domain.core.challenge.Challenge;
import com.ssafy.motif.domain.core.challenge.ChallengeParticipant;
import com.ssafy.motif.domain.core.challenge.ChallengeParticipantRepository;
import com.ssafy.motif.domain.core.challenge.ChallengeRepository;
import com.ssafy.motif.domain.core.song.Song;
import com.ssafy.motif.domain.core.song.SongRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 팬 앱 챌린지 조회 (명세 §7.2) — S15P21D104-67 묶음.
 *
 * <p>단건 상세 하나뿐이라 목록용 투영({@code ChallengeListRow})을 쓰지 않는다 — 그 record
 * 는 어드민 목록 칸(편집본 수·삭제 여부)을 담고 있고 §7.2 가 필요로 하는
 * {@code videoKey}·{@code description} 은 없다. 필요한 것만 세 번 읽는다.
 *
 * <p>읽기 전용 트랜잭션이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChallengeQueryService {

    private final ChallengeRepository challengeRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final ArtistRepository artistRepository;
    private final SongRepository songRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PublicMediaUrlResolver mediaUrlResolver;

    /** §7.2 챌린지 상세. */
    public ChallengeDetailResponse detail(Integer userSeq, Integer challengeSeq) {
        // 삭제된 챌린지는 없는 것으로 답한다 (§1.8, §7.2 실패표 404)
        Challenge challenge = challengeRepository
                .findByChallengeSeqAndDeletedFalse(challengeSeq)
                .orElseThrow(() -> new BusinessException(
                        ChallengeErrorCode.CHALLENGE_NOT_FOUND, "challengeSeq: " + challengeSeq));

        ArtistListRow artist = artistRepository.findRow(challenge.getArtistSeq())
                // artist_seq 는 NOT NULL FK 라 여기까지 오면 행은 반드시 있다. 그래도
                // orElseThrow 를 두는 것은 삭제된 아티스트 때문이 아니라 Optional 을
                // 풀기 위해서다 — 데이터가 깨졌다면 NPE 가 아니라 도메인 코드로 드러난다
                .orElseThrow(() -> new BusinessException(
                        ArtistErrorCode.ARTIST_NOT_FOUND, "artistSeq: " + challenge.getArtistSeq()));
        Song song = songRepository.findById(challenge.getSongSeq()).orElseThrow();

        return new ChallengeDetailResponse(
                challenge.getChallengeSeq(),
                // 챌린지 원본은 공개 등급이라 서명 없는 CloudFront URL 이다 (§1.7)
                mediaUrlResolver.resolve(challenge.getVideoKey()),
                mediaUrlResolver.resolve(challenge.getThumbnailKey()),
                challenge.getDurationMs(),
                new ChallengeDetailResponse.Artist(
                        artist.artistSeq(), artist.name(),
                        mediaUrlResolver.resolve(artist.profileImageKey())),
                new ChallengeDetailResponse.Song(song.getTitle(), song.getSingerName()),
                challenge.getViewCount(),
                challenge.getParticipantCount(),
                challenge.getLikeCount(),
                challenge.getCommentCount(),
                challengeRepository.isLikedBy(challengeSeq, userSeq),
                subscriptionRepository.existsByIdUserSeqAndIdArtistSeq(userSeq, challenge.getArtistSeq()),
                challenge.getDescription(),
                participants(challengeSeq));
    }

    /**
     * 슬롯 목록 — 마스크 오름차순이 곧 화면 좌→우 순서다.
     *
     * <p>이름과 프로필 키를 {@code ArtistRepository.findNames} 로 <b>한 번에</b> 가져온다.
     * 슬롯은 최대 4개지만 하나씩 조회하면 그대로 N+1 이고, 그 메서드가 이 용도로 이미 있다.
     *
     * <p>{@code profileImageUrl} 은 저장값이 아니라 <b>응답 조립 시점에 만드는 값</b>이다 —
     * 메인 아티스트와 같은 방식이다. 설명 시트의 참여자 아바타가 이걸 쓴다.
     */
    private List<ChallengeDetailResponse.Participant> participants(Integer challengeSeq) {
        List<ChallengeParticipant> slots =
                participantRepository.findByIdChallengeSeqOrderByParticipantMask(challengeSeq);
        if (slots.isEmpty()) {
            return List.of();
        }

        Map<Integer, ArtistName> artists = artistRepository
                .findNames(slots.stream().map(ChallengeParticipant::artistSeq).toList())
                .stream()
                .collect(Collectors.toMap(ArtistName::artistSeq, a -> a));

        return slots.stream()
                .map(slot -> {
                    ArtistName artist = artists.get(slot.artistSeq());
                    return new ChallengeDetailResponse.Participant(
                            slot.artistSeq(),
                            artist == null ? null : artist.name(),
                            slot.getParticipantMask(),
                            artist == null ? null : mediaUrlResolver.resolve(artist.profileImageKey()),
                            toBbox(slot.getBbox()));
                })
                .toList();
    }

    private ChallengeDetailResponse.Bbox toBbox(ChallengeParticipant.Bbox bbox) {
        return new ChallengeDetailResponse.Bbox(
                bbox.getLeftTopX(), bbox.getLeftTopY(),
                bbox.getRightBottomX(), bbox.getRightBottomY());
    }
}
