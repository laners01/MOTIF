package com.ssafy.motif.coreapi.domain.user.service;

import com.ssafy.motif.coreapi.domain.user.dto.UserMeResponse;
import com.ssafy.motif.coreapi.global.media.CloudFrontUrlSigner;
import com.ssafy.motif.coreapi.global.media.MediaSigningProperties;
import com.ssafy.motif.coreapi.global.media.MediaUrlResolver;
import com.ssafy.motif.coreapi.global.media.ProtectedMediaUrlResolver;
import com.ssafy.motif.coreapi.global.media.PublicMediaUrlResolver;
import com.ssafy.motif.domain.core.job.CompositeJob;
import com.ssafy.motif.domain.core.job.CompositeJobRepository;
import com.ssafy.motif.domain.core.motif.MotifResult;
import com.ssafy.motif.domain.core.motif.MotifResultRepository;
import com.ssafy.motif.domain.core.user.User;
import com.ssafy.motif.domain.core.user.UserRepository;
import com.ssafy.motif.domain.core.video.ParticipationVideo;
import com.ssafy.motif.domain.core.video.ParticipationVideoRepository;
import com.ssafy.motif.domain.core.video.Visibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 마이페이지 원콜 (S15P21D104-278 · 명세 §4.1).
 *
 * <p><b>URL 변환은 목으로 세우지 않는다.</b> 진짜 {@link MediaUrlResolver} 에 진짜
 * 서명기를 물리고(키는 테스트에서 만든다) 나오는 URL 을 본다 — 이 티켓에서 가장
 * 틀리기 쉬운 자리가 "어느 키를 서명하고 어느 키를 안 하는가"이고, 목으로 두면
 * 그 판정이 테스트에서 통째로 빠진다.
 *
 * <p>서명은 로컬 RSA 연산이라 네트워크가 필요 없다.
 */
class UserQueryServiceTest {

    private static final int ME = 7;
    private static final String CDN = "https://cdn.motif.test";
    private static final String JOB_UUID = "9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f";

    private UserRepository userRepository;
    private MotifResultRepository motifResultRepository;
    private CompositeJobRepository compositeJobRepository;
    private ParticipationVideoRepository participationVideoRepository;
    private UserQueryService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        motifResultRepository = mock(MotifResultRepository.class);
        compositeJobRepository = mock(CompositeJobRepository.class);
        participationVideoRepository = mock(ParticipationVideoRepository.class);

        CloudFrontUrlSigner signer = new CloudFrontUrlSigner(new MediaSigningProperties(
                "KTESTKEYPAIRID", generatePkcs8Pem(), Duration.ofMinutes(10), Duration.ofHours(6)));
        MediaUrlResolver resolver = new MediaUrlResolver(
                new PublicMediaUrlResolver(CDN), new ProtectedMediaUrlResolver(CDN, signer));

        service = new UserQueryService(userRepository, motifResultRepository,
                compositeJobRepository, participationVideoRepository, resolver);

        givenUser(user(null, null));
        given(participationVideoRepository.findMyPreview(anyInt(), any())).willReturn(List.of());
    }

    @Test
    @DisplayName("프로필 이미지는 무서명 공개 CDN URL 이다 — presign 을 쓰지 않는다")
    void profileImageIsPlainCdnUrl() {
        givenUser(user("profiles/3f9c1a2b.jpg", null));

        assertThat(service.me(ME).profileImageUrl())
                .isEqualTo(CDN + "/profiles/3f9c1a2b.jpg")
                .doesNotContain("Signature=");
    }

    @Test
    @DisplayName("프로필 이미지가 없으면 null 이다")
    void profileImageMayBeNull() {
        assertThat(service.me(ME).profileImageUrl()).isNull();
    }

    @Test
    @DisplayName("대표 모티프 미설정이면 repMotif 는 null 이다")
    void repMotifNullWhenUnset() {
        assertThat(service.me(ME).repMotif()).isNull();
    }

    @Test
    @DisplayName("가리키는 결과물이 삭제·부재면 repMotif 는 null 이다 — FK 가 없는 논리 참조다")
    void repMotifNullWhenDangling() {
        givenUser(user(null, 10L));
        given(motifResultRepository.findByResultSeqAndUserSeqAndDeletedFalse(10L, ME))
                .willReturn(Optional.empty());

        assertThat(service.me(ME).repMotif()).isNull();
    }

    @Test
    @DisplayName("대표 모티프의 videoUrl 은 core-api 재생목록 경로이고 썸네일은 서명된다")
    void repMotifUsesPlaylistPathAndSignedThumbnail() {
        givenUser(user(null, 10L));
        givenRepMotif("private/motif/" + JOB_UUID + "/thumb.jpg");

        UserMeResponse.RepMotif repMotif = service.me(ME).repMotif();

        assertThat(repMotif.resultSeq()).isEqualTo(10L);
        assertThat(repMotif.videoUrl())
                .isEqualTo("/api/v1/media/" + JOB_UUID + "/master.m3u8");
        assertThat(repMotif.thumbnailUrl())
                .startsWith(CDN + "/private/motif/" + JOB_UUID + "/thumb.jpg?")
                .contains("Signature=", "Key-Pair-Id=KTESTKEYPAIRID");
    }

    @Test
    @DisplayName("영상이 없으면 빈 배열이다 — null 로 내리지 않는다")
    void videosAreEmptyArrayWhenNone() {
        assertThat(service.me(ME).videos()).isEmpty();
    }

    @Test
    @DisplayName("썸네일 등급은 키 프리픽스가 정한다 — visibility 가 아니다")
    void thumbnailGradeFollowsKeyPrefixNotVisibility() {
        given(participationVideoRepository.findMyPreview(anyInt(), any())).willReturn(List.of(
                // PUBLIC 인데 키는 아직 private/ — 게시는 복사일 뿐 키를 바꾸지 않는다(§1.7).
                // visibility 로 판정했다면 여기서 무서명 URL 이 나가 403 이 된다
                video(88L, "private/challenge/aaa/thumb.jpg", Visibility.PUBLIC, 3400L),
                video(87L, "public/challenge/bbb/thumb.jpg", Visibility.PUBLIC, 120L),
                video(86L, "private/challenge/ccc/thumb.jpg", Visibility.PRIVATE, 7L)));

        List<UserMeResponse.VideoPreview> videos = service.me(ME).videos();

        assertThat(videos).hasSize(3);
        assertThat(videos.get(0).thumbnailUrl()).contains("Signature=");
        assertThat(videos.get(1).thumbnailUrl())
                .isEqualTo(CDN + "/public/challenge/bbb/thumb.jpg")
                .doesNotContain("Signature=");
        assertThat(videos.get(2).thumbnailUrl()).contains("Signature=");
    }

    @Test
    @DisplayName("본인 화면이라 PRIVATE 도 내려보내고 visibility 를 그대로 싣는다")
    void privateVideosAreIncluded() {
        given(participationVideoRepository.findMyPreview(anyInt(), any())).willReturn(List.of(
                video(86L, "private/challenge/ccc/thumb.jpg", Visibility.PRIVATE, 7L)));

        assertThat(service.me(ME).videos())
                .singleElement()
                .satisfies(preview -> {
                    assertThat(preview.videoSeq()).isEqualTo(86L);
                    assertThat(preview.viewCount()).isEqualTo(7L);
                    assertThat(preview.visibility()).isEqualTo(Visibility.PRIVATE);
                });
    }

    @Test
    @DisplayName("프리뷰는 9건까지만 요청한다 (3×3)")
    void previewIsCappedAtNine() {
        service.me(ME);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(participationVideoRepository).findMyPreview(any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(UserQueryService.PREVIEW_SIZE);
        assertThat(UserQueryService.PREVIEW_SIZE).isEqualTo(9);
    }

    // ── 도우미 ──────────────────────────────────────────────────────────

    private void givenUser(User user) {
        given(userRepository.findById(ME)).willReturn(Optional.of(user));
    }

    private void givenRepMotif(String thumbnailKey) {
        MotifResult result = newInstance(MotifResult.class);
        ReflectionTestUtils.setField(result, "resultSeq", 10L);
        ReflectionTestUtils.setField(result, "userSeq", ME);
        ReflectionTestUtils.setField(result, "jobSeq", 5L);
        ReflectionTestUtils.setField(result, "thumbnailKey", thumbnailKey);
        given(motifResultRepository.findByResultSeqAndUserSeqAndDeletedFalse(10L, ME))
                .willReturn(Optional.of(result));

        CompositeJob job = newInstance(CompositeJob.class);
        ReflectionTestUtils.setField(job, "jobSeq", 5L);
        ReflectionTestUtils.setField(job, "jobUuid", UUID.fromString(JOB_UUID));
        given(compositeJobRepository.findById(5L)).willReturn(Optional.of(job));
    }

    private static User user(String profileImageKey, Long repMotifResultSeq) {
        User user = User.signUp();
        ReflectionTestUtils.setField(user, "userSeq", ME);
        ReflectionTestUtils.setField(user, "nickname", "모티프러버");
        ReflectionTestUtils.setField(user, "profileImageKey", profileImageKey);
        ReflectionTestUtils.setField(user, "repMotifResultSeq", repMotifResultSeq);
        return user;
    }

    private static ParticipationVideo video(Long seq, String thumbnailKey,
                                            Visibility visibility, Long viewCount) {
        ParticipationVideo video = newInstance(ParticipationVideo.class);
        ReflectionTestUtils.setField(video, "videoSeq", seq);
        ReflectionTestUtils.setField(video, "thumbnailKey", thumbnailKey);
        ReflectionTestUtils.setField(video, "visibility", visibility);
        ReflectionTestUtils.setField(video, "viewCount", viewCount);
        return video;
    }

    /** 엔티티는 생성 경로가 없다(§8.1·§13 이 만든다) — 읽기용 인스턴스는 리플렉션으로 세운다. */
    private static <T> T newInstance(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(type.getSimpleName() + " 인스턴스를 만들지 못했다", e);
        }
    }

    /** 진짜 서명을 내려면 진짜 키가 필요하다. 테스트마다 새로 만든다 — 저장할 이유가 없다. */
    private static String generatePkcs8Pem() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            byte[] pkcs8 = generator.generateKeyPair().getPrivate().getEncoded();
            return "-----BEGIN PRIVATE KEY-----\n"
                    + Base64.getMimeEncoder().encodeToString(pkcs8)
                    + "\n-----END PRIVATE KEY-----";
        } catch (Exception e) {
            throw new IllegalStateException("테스트용 RSA 키를 만들지 못했다", e);
        }
    }
}
