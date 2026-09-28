package com.ssafy.motif.coreapi.domain.challenge.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.artist.ArtistErrorCode;
import com.ssafy.motif.coreapi.domain.challenge.ChallengeErrorCode;
import com.ssafy.motif.common.response.CursorResponse;
import com.ssafy.motif.common.response.ListResponse;
import com.ssafy.motif.coreapi.domain.challenge.dto.ChallengeDetailResponse;
import com.ssafy.motif.coreapi.domain.challenge.dto.ChallengeFeedItem;
import com.ssafy.motif.coreapi.domain.challenge.dto.ChallengeParticipantItem;
import com.ssafy.motif.coreapi.domain.challenge.dto.EditedVideoResponse;
import com.ssafy.motif.coreapi.global.response.Cursors;
import com.ssafy.motif.coreapi.global.media.MediaSigningProperties;
import com.ssafy.motif.coreapi.global.media.MediaUrlResolver;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
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
    /**
     * 등급 판정 지점 (0020 — 구 {@code PublicMediaUrlResolver}).
     *
     * <p>§7.5·§7.6 이 {@code private/edited/…} 를 내보내게 되면서 공개 전용 리졸버로는
     * 서명이 붙지 않는다. 호출부가 두 리졸버 중 하나를 직접 고르지 않는 것이 규약이다 —
     * 한 곳만 고쳤을 때 보호돼야 할 것이 무기명으로 새거나 공개 것이 403 이 된다.
     */
    private final MediaUrlResolver mediaUrlResolver;
    private final MediaSigningProperties signingProperties;

    /** §1.9 size 상한 — 커서 목록 공통. */
    private static final int MAX_SIZE = 100;

    /**
     * §7.1 숏폼 피드 — 커서, 최신순.
     *
     * <p><b>명세의 추천은 아직 없다.</b> §7.1 은 "구독 아티스트 + 인기·관심 기반 추천
     * 종합(서버 내부 로직, {@code rank:challenge} 활용)" 이라고 적었는데 랭킹이 만들어지지
     * 않았다. 지금은 최신순으로 낸다 — 커서가 opaque 토큰이라(§1.9) 정렬 키가 바뀌어도
     * 클라이언트 계약은 깨지지 않는다. 랭킹 티켓이 이 메서드만 갈아끼우면 된다.
     *
     * <p>{@code size + 1} 건을 읽어 넘치면 잘라내고 그 사실을 {@code hasNext} 로 쓴다 —
     * 총 개수를 세지 않는 것이 커서 방식의 요점이다.
     *
     * <p><b>한 페이지에 조회 5번으로 끝낸다.</b> 챌린지 · 아티스트 · 곡 · 좋아요 · 구독
     * 각각 배치다. 항목마다 읽으면 20행에 80번을 더 왕복한다.
     */
    public CursorResponse<ChallengeFeedItem> feed(Integer userSeq, String cursor, int size) {
        int limit = Math.clamp(size, 1, MAX_SIZE);
        Integer after = Cursors.decode(cursor);

        // 커서 유무로 메서드를 가른다 — null 을 쿼리 파라미터로 흘리지 않는다
        // (ChallengeRepository javadoc: 타입 없는 null → Postgres 추론 → 운영 500)
        PageRequest page = PageRequest.of(0, limit + 1);
        List<Challenge> rows = after == null
                ? challengeRepository.findByDeletedFalseOrderByChallengeSeqDesc(page)
                : challengeRepository.findByDeletedFalseAndChallengeSeqLessThanOrderByChallengeSeqDesc(
                        after, page);

        boolean hasNext = rows.size() > limit;
        List<Challenge> challenges = hasNext ? rows.subList(0, limit) : rows;
        if (challenges.isEmpty()) {
            return CursorResponse.forward(List.of(), null);
        }

        List<Integer> challengeSeqs = challenges.stream().map(Challenge::getChallengeSeq).toList();
        List<Integer> artistSeqs = challenges.stream().map(Challenge::getArtistSeq).distinct().toList();
        List<Integer> songSeqs = challenges.stream().map(Challenge::getSongSeq).distinct().toList();

        Map<Integer, ArtistName> artists = artistRepository.findNames(artistSeqs).stream()
                .collect(Collectors.toMap(ArtistName::artistSeq, Function.identity()));
        Map<Integer, Song> songs = songRepository.findAllById(songSeqs).stream()
                .collect(Collectors.toMap(Song::getSongSeq, Function.identity()));
        Set<Integer> liked = Set.copyOf(
                challengeRepository.findLikedChallengeSeqs(userSeq, challengeSeqs));
        Set<Integer> subscribed = Set.copyOf(
                subscriptionRepository.findSubscribedArtistSeqs(userSeq, artistSeqs));

        List<ChallengeFeedItem> list = challenges.stream()
                .map(c -> toFeedItem(c, artists.get(c.getArtistSeq()), songs.get(c.getSongSeq()),
                        liked.contains(c.getChallengeSeq()), subscribed.contains(c.getArtistSeq())))
                .toList();

        String nextCursor = hasNext ? Cursors.encode(challengeSeqs.getLast()) : null;
        return CursorResponse.forward(list, nextCursor);
    }

    private ChallengeFeedItem toFeedItem(Challenge c, ArtistName artist, Song song,
                                         boolean liked, boolean subscribed) {
        return new ChallengeFeedItem(
                c.getChallengeSeq(),
                // 챌린지 원본은 HLS 다 — 공개 등급이라 서명은 붙지 않지만, 재생목록은
                // 조각을 더 요청하므로 단일 파일용 resolve 가 아니라 이쪽이다 (§1.7, ADR-0012)
                mediaUrlResolver.resolvePlaylist(c.getVideoKey()),
                mediaUrlResolver.resolve(c.getThumbnailKey()),
                c.getDurationMs(),
                new ChallengeFeedItem.Artist(
                        c.getArtistSeq(),
                        artist == null ? null : artist.name(),
                        artist == null ? null : mediaUrlResolver.resolveProfile(artist.profileImageKey())),
                new ChallengeFeedItem.Song(
                        song == null ? null : song.getTitle(),
                        song == null ? null : song.getSingerName()),
                c.getViewCount(),
                c.getParticipantCount(),
                c.getLikeCount(),
                c.getCommentCount(),
                liked,
                subscribed,
                c.getDescription(),
                c.getRegDttm());
    }

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
                // 챌린지 원본은 HLS 다 — 공개 등급이라 서명은 붙지 않는다 (§1.7, ADR-0012)
                mediaUrlResolver.resolvePlaylist(challenge.getVideoKey()),
                mediaUrlResolver.resolve(challenge.getThumbnailKey()),
                challenge.getDurationMs(),
                new ChallengeDetailResponse.Artist(
                        artist.artistSeq(), artist.name(),
                        mediaUrlResolver.resolveProfile(artist.profileImageKey())),
                new ChallengeDetailResponse.Song(song.getTitle(), song.getSingerName()),
                challenge.getViewCount(),
                challenge.getParticipantCount(),
                challenge.getLikeCount(),
                challenge.getCommentCount(),
                challengeRepository.isLikedBy(challengeSeq, userSeq),
                subscriptionRepository.existsByIdUserSeqAndIdArtistSeq(userSeq, challenge.getArtistSeq()),
                challenge.getDescription(),
                challenge.getRegDttm());
    }

    /**
     * §7.5 슬롯 목록 — {@code slotOrder} 오름차순이 곧 화면 좌→우 순서다.
     *
     * <p><b>§7.2 에서 떼어 냈다</b>(0020). 촬영 전 선택 화면(시안 10-1~3)이 쓰는 데이터라
     * 상세 Bottom Sheet 와 수명이 다르고, 편집본 이미지가 서명 URL 이 되면서 만료가 붙었다 —
     * 상세를 열어 둔 채 오래 머물면 이미지가 죽는다. 필요한 화면이 직접 부르게 한다.
     *
     * <p><b>영상은 여기 없다.</b> 고른 뒤 §7.6 으로 따로 받는다.
     *
     * <p>이름과 프로필 키를 {@code ArtistRepository.findNames} 로 <b>한 번에</b> 가져온다.
     * 슬롯은 최대 4개지만 하나씩 조회하면 그대로 N+1 이고, 그 메서드가 이 용도로 이미 있다.
     */
    public ListResponse<ChallengeParticipantItem> participants(Integer challengeSeq) {
        // 삭제된 챌린지는 없는 것으로 답한다 (§1.8)
        if (!challengeRepository.existsByChallengeSeqAndDeletedFalse(challengeSeq)) {
            throw new BusinessException(
                    ChallengeErrorCode.CHALLENGE_NOT_FOUND, "challengeSeq: " + challengeSeq);
        }
        List<ChallengeParticipant> slots =
                participantRepository.findByChallengeSeqOrderBySlotOrderAsc(challengeSeq);
        if (slots.isEmpty()) {
            return ListResponse.of(List.of());
        }

        Map<Integer, ArtistName> artists = artistRepository
                .findNames(slots.stream().map(ChallengeParticipant::getArtistSeq).toList())
                .stream()
                .collect(Collectors.toMap(ArtistName::artistSeq, a -> a));

        return ListResponse.of(slots.stream()
                .map(slot -> {
                    ArtistName artist = artists.get(slot.getArtistSeq());
                    return new ChallengeParticipantItem(
                            slot.getChallengeParticipantSeq(),
                            slot.getArtistSeq(),
                            artist == null ? null : artist.name(),
                            slot.getSlotOrder(),
                            artist == null ? null : mediaUrlResolver.resolveProfile(artist.profileImageKey()),
                            toAnchor(slot.anchorOrNull()),
                            mediaUrlResolver.resolve(slot.getInitialSceneKey()),
                            mediaUrlResolver.resolve(slot.getPreviewImageKey()));
                })
                .toList());
    }

    /**
     * §7.6 편집본 영상 — 촬영 화면이 배경으로 재생한다.
     *
     * <p><b>통짜 MP4 다.</b> 편집본은 {@code job.media} 를 타지 않아 렌디션이 없으므로
     * {@code resolvePlaylist} 가 아니라 {@code resolveVideoFile} 이다.
     *
     * <p>404 는 세 경우를 한 코드로 묶는다 — 슬롯 없음 · 삭제된 챌린지 소속 · 편집본 미등록.
     * 가르면 남의 챌린지 슬롯 seq 를 넣어 보고 존재를 읽을 수 있다(§1.8).
     */
    public EditedVideoResponse editedVideo(Integer challengeParticipantSeq) {
        ChallengeParticipant slot = participantRepository.findById(challengeParticipantSeq)
                .filter(ChallengeParticipant::hasEdited)
                .filter(p -> challengeRepository.existsByChallengeSeqAndDeletedFalse(p.getChallengeSeq()))
                .orElseThrow(() -> new BusinessException(
                        ChallengeErrorCode.EDITED_CHALLENGE_NOT_FOUND,
                        "challengeParticipantSeq: " + challengeParticipantSeq));

        return new EditedVideoResponse(
                slot.getChallengeParticipantSeq(),
                slot.getChallengeSeq(),
                slot.getArtistSeq(),
                mediaUrlResolver.resolveVideoFile(slot.getEditedChallengeKey()),
                slot.getEditedDurationMs(),
                // resolveVideoFile 이 실제로 쓰는 만료와 같은 값이어야 한다 — 재생목록
                // (playlist-ttl)이 아니라 단일 파일 쪽이다. 섞으면 앱이 아직 살아 있다고
                // 믿는 URL 이 403 이 된다 (ADR-0017)
                signingProperties.videoFileTtl().toSeconds());
    }

    /** 0017 이전 슬롯은 앵커가 없다 — null 로 내려 클라이언트가 원을 숨긴다. */
    private static ChallengeDetailResponse.Anchor toAnchor(ChallengeParticipant.Anchor a) {
        return a == null ? null : new ChallengeDetailResponse.Anchor(a.getX(), a.getY(), a.getR());
    }
}
