package com.ssafy.motif.coreapi.domain.video;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.video.dto.VideoItem;
import com.ssafy.motif.coreapi.domain.video.dto.VideoUpdateRequest;
import com.ssafy.motif.coreapi.domain.video.service.VideoCommandService;
import com.ssafy.motif.coreapi.domain.video.service.VideoQueryService;
import com.ssafy.motif.coreapi.global.media.MediaSigningTestSupport;
import com.ssafy.motif.domain.core.video.Visibility;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §8.9 게시 정보 수정을 실제 DB에 대고 검증한다 (S15P21D104-207).
 *
 * <p>고정하는 것은 셋이다 — <b>부분 수정</b>(생략과 빈 문자열이 다르다), <b>403/404 우선
 * 순위</b>(남의 PRIVATE 은 404), <b>등급 전환이 키를 건드리지 않는다</b>.
 *
 * <p>세 번째가 핵심이다. 산출물은 게시 여부와 무관하게 {@code private/} 한 곳에 있고
 * (ADR-0013) {@code visibility} 는 앱 내 공개 범위만 뜻한다. 키를 움직이는 코드가 다시
 * 들어오면 {@code visibility} 와 키가 어긋나는 구간이 되살아난다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class VideoUpdateLocalDbTest {

    @DynamicPropertySource
    static void media(DynamicPropertyRegistry registry) {
        MediaSigningTestSupport.register(registry);
    }

    @Autowired
    private VideoCommandService videoCommandService;
    @Autowired
    private VideoQueryService videoQueryService;
    @PersistenceContext
    private EntityManager entityManager;

    private VideoTestData data;
    private Integer me;
    private Integer other;
    private Integer artistSeq;
    private Integer challengeSeq;

    @BeforeEach
    void setUp() {
        data = new VideoTestData(entityManager);
        me = data.newUser();
        other = data.newUser();
        artistSeq = data.newArtist();
        challengeSeq = data.newChallenge(artistSeq, data.newSong(me), false);
    }

    // --- 부분 수정 -----------------------------------------------------------

    @Test
    @DisplayName("보낸 필드만 반영한다 — 나머지는 그대로다")
    void updatesOnlyGivenFields() {
        Long 영상 = video().owner(me).insert();

        VideoItem updated = update(영상, new VideoUpdateRequest("수정한 문구", null, null));

        assertThat(updated.description()).isEqualTo("수정한 문구");
        assertThat(updated.commentEnabled()).isTrue();                 // 건드리지 않았다
        assertThat(updated.visibility()).isEqualTo(Visibility.PUBLIC); // 건드리지 않았다
    }

    @Test
    @DisplayName("빈 문자열은 지움이다 — 생략과 다르다")
    void blankDescriptionClearsIt() {
        Long 영상 = video().owner(me).insert();

        assertThat(update(영상, new VideoUpdateRequest("", null, null)).description()).isEmpty();
    }

    @Test
    @DisplayName("commentEnabled=false 로 댓글을 동결한다")
    void freezesComments() {
        Long 영상 = video().owner(me).insert();

        assertThat(update(영상, new VideoUpdateRequest(null, null, false)).commentEnabled())
                .isFalse();
    }

    @Test
    @DisplayName("셋 다 생략하면 아무것도 바꾸지 않고 현재 상태를 돌려준다")
    void emptyRequestIsNoOp() {
        Long 영상 = video().owner(me).insert();
        VideoItem before = videoQueryService.detail(me, 영상);

        assertSameItem(update(영상, new VideoUpdateRequest(null, null, null)), before);
    }

    @Test
    @DisplayName("수정 결과가 §8.2 단건과 같다 — 조립 지점이 하나다")
    void responseMatchesDetail() {
        Long 영상 = video().owner(me).insert();

        VideoItem updated = update(영상, new VideoUpdateRequest("바뀐 문구", null, false));

        assertSameItem(updated, videoQueryService.detail(me, 영상));
    }

    // --- 등급 전환 -----------------------------------------------------------

    @Test
    @DisplayName("PUBLIC → PRIVATE: 값만 바뀌고 키는 그대로다")
    void goingPrivateKeepsKeys() {
        Long 영상 = video().owner(me).insert();
        String 키 = videoKey(영상);
        String 썸네일키 = thumbnailKey(영상);

        VideoItem updated = update(영상, new VideoUpdateRequest(null, Visibility.PRIVATE, null));

        assertThat(updated.visibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(videoKey(영상)).isEqualTo(키);
        assertThat(thumbnailKey(영상)).isEqualTo(썸네일키);
        assertThat(updated.videoUrl())
                .startsWith(MediaSigningTestSupport.CDN + "/private/")
                .contains("/master.m3u8?")
                .contains("Policy=", "Signature=", "Key-Pair-Id=");
    }

    @Test
    @DisplayName("PRIVATE → PUBLIC: 값만 바뀌고 키는 그대로다 — 옮길 자리가 없다")
    void goingPublicKeepsKeys() {
        Long 영상 = video().owner(me).privateVisibility().insert();
        String 키 = videoKey(영상);

        VideoItem updated = update(영상, new VideoUpdateRequest(null, Visibility.PUBLIC, null));

        assertThat(updated.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(videoKey(영상)).isEqualTo(키);
        // PUBLIC 이어도 보호 등급이라 서명이 붙는다 (ADR-0013)
        assertThat(updated.videoUrl())
                .startsWith(MediaSigningTestSupport.CDN + "/private/")
                .contains("Policy=");
    }

    @Test
    @DisplayName("같은 등급으로 다시 보내도 달라지는 것이 없다")
    void sameVisibilityIsHarmless() {
        Long 영상 = video().owner(me).insert();
        String before = videoKey(영상);

        VideoItem updated = update(영상, new VideoUpdateRequest(null, Visibility.PUBLIC, null));

        assertThat(updated.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(videoKey(영상)).isEqualTo(before);
    }

    // --- 권한 ---------------------------------------------------------------

    @Test
    @DisplayName("남의 PUBLIC 영상은 403 이다")
    void othersPublicVideoIs403() {
        Long 남의것 = video().owner(other).insert();

        assertThatThrownBy(() -> update(남의것, new VideoUpdateRequest("가로채기", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("남의 PRIVATE 영상은 403 이 아니라 404 다 — 존재 은닉이 앞선다")
    void othersPrivateVideoIs404() {
        assertNotFound(video().owner(other).privateVisibility().insert());
    }

    @Test
    @DisplayName("없는 영상은 404 다")
    void unknownVideoIs404() {
        assertNotFound(9_999_999_999L);
    }

    @Test
    @DisplayName("삭제된 영상은 404 다 — 내 것이어도 그렇다")
    void deletedVideoIs404() {
        assertNotFound(video().owner(me).deleted().insert());
    }

    @Test
    @DisplayName("삭제된 챌린지에 속한 영상은 404 다")
    void videoOfDeletedChallengeIs404() {
        Integer 삭제된챌린지 = data.newChallenge(artistSeq, data.newSong(me), true);

        assertNotFound(data.video(삭제된챌린지, artistSeq).owner(me).insert());
    }

    // --- 도우미 -------------------------------------------------------------

    private VideoItem update(Long videoSeq, VideoUpdateRequest request) {
        return videoCommandService.update(me, videoSeq, request);
    }

    /**
     * 두 항목이 같은가 — <b>서명 파라미터는 빼고 본다</b>.
     *
     * <p>참여 영상은 전부 보호 등급이라 URL 에 서명이 붙는데(ADR-0013), 만료가 초 단위로
     * 인코딩돼 <b>두 번의 조립이 초 경계를 넘으면 문자열이 달라진다</b>. 그대로 비교하면
     * 대부분 통과하다 가끔 깨지는 테스트가 된다. 여기서 보려는 것은 "같은 조립 경로를
     * 탔는가" 이므로 주소만 견준다.
     */
    private static void assertSameItem(VideoItem actual, VideoItem expected) {
        assertThat(actual).usingRecursiveComparison()
                .ignoringFields("videoUrl", "thumbnailUrl")
                .isEqualTo(expected);
        assertThat(addressOf(actual.videoUrl())).isEqualTo(addressOf(expected.videoUrl()));
        assertThat(addressOf(actual.thumbnailUrl()))
                .isEqualTo(addressOf(expected.thumbnailUrl()));
    }

    /** 서명 쿼리스트링을 뗀 주소. */
    private static String addressOf(String url) {
        return url == null ? null : url.split("\\?", 2)[0];
    }

    private void assertNotFound(Long videoSeq) {
        assertThatThrownBy(() -> update(videoSeq, new VideoUpdateRequest("x", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(VideoErrorCode.VIDEO_NOT_FOUND);
    }

    private String videoKey(Long videoSeq) {
        return column("video_key", videoSeq);
    }

    private String thumbnailKey(Long videoSeq) {
        return column("thumbnail_key", videoSeq);
    }

    /** 엔티티 캐시가 아니라 DB 를 본다 — 키 전환이 실제로 저장됐는지가 요점이다. */
    private String column(String name, Long videoSeq) {
        entityManager.flush();
        return (String) entityManager.createNativeQuery(
                        "select " + name + " from motif_core.participation_videos where video_seq = ?1")
                .setParameter(1, videoSeq)
                .getSingleResult();
    }

    private VideoTestData.VideoFixture video() {
        return data.video(challengeSeq, artistSeq);
    }
}
