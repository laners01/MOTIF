package com.ssafy.motif.coreapi.global.media;

import com.ssafy.motif.coreapi.domain.media.MediaKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.util.Set;

/**
 * 보호 등급 HLS 재생목록을 서명해 다시 쓴다 (API 명세 §1.7).
 *
 * <p>플레이어가 재생목록을 읽고 조각을 <b>추가로</b> 요청하는데, 재생목록 안의 주소는
 * 상대 경로라 서명이 따라붙지 않는다. 그래서 내려보내기 전에 주소를 바꿔 박는다.
 *
 * <pre>
 * master  → 화질별 주소를 core-api 로   (자식도 다시 써야 하므로)
 * variant → 조각 주소를 서명된 CDN URL 로 (바이트는 CDN 이 낸다)
 * </pre>
 *
 * <p><b>오가는 것은 텍스트뿐이다.</b> 재생목록은 수백 바이트~수 KB 이고 영상 바이트는
 * 여전히 CDN 이 낸다 — 설계 원칙 ①(바이트가 API 서버를 통과하지 않는다)은 유지된다.
 *
 * <p><b>컨트롤러는 아직 없다</b> — `composite_jobs` 가 core-api 에 없어 {@code jobUuid} 로
 * 타입도 소유자도 못 찾는다. 붙일 것의 목록은 ADR-0010 "남은 배선" 에 있다.
 *
 * <p><b>인가는 여기 없다.</b> 이 서비스는 "이 프리픽스를 서명해 달라"만 받는다. 누가
 * 볼 수 있는지는 프리픽스를 정하는 쪽(호출자)이 판단한다 — 그래야 소유자 조회 방식이
 * 바뀌어도 여기를 안 고친다.
 */
@Service
@RequiredArgsConstructor
public class MediaPlaylistService {

    /** 파일명 규약의 주인은 {@link MediaKeys} 다 — 여기서 다시 정의하지 않는다. */
    public static final String MASTER = MediaKeys.MASTER_PLAYLIST;
    public static final String VARIANT = "index.m3u8";
    private static final String SEGMENT_CONTENT_TYPE = "application/vnd.apple.mpegurl";

    /** app-media 의 {@code Rendition.LADDER} 와 같은 값이다. 여기가 뒤처지면 새 화질이 404 가 된다. */
    private static final Set<String> RENDITIONS = Set.of("480p", "720p", "1080p");

    private final S3Client s3;
    private final CloudFrontUrlSigner signer;

    @Value("${motif.media.s3.bucket}")
    private String bucket;

    @Value("${motif.media.public-base-url:}")
    private String cdnBaseUrl;

    /** 응답 {@code Content-Type} — 틀리면 플레이어가 재생목록으로 받아들이지 않는다. */
    public static String contentType() {
        return SEGMENT_CONTENT_TYPE;
    }

    /**
     * 마스터 재생목록 — 화질별 주소를 {@code apiBasePath} 아래로 돌린다.
     *
     * @param prefix      산출물 프리픽스 (예 {@code private/motif/{jobUuid}}) — {@code MediaKeys} 가 만든다
     * @param apiBasePath 자식 재생목록을 받을 core-api 경로 (예 {@code /api/v1/media/{jobUuid}})
     */
    public String master(String prefix, String apiBasePath) {
        String base = apiBasePath.endsWith("/")
                ? apiBasePath.substring(0, apiBasePath.length() - 1)
                : apiBasePath;
        return HlsPlaylistRewriter.rewriteMaster(read(prefix + "/" + MASTER), uri -> base + "/" + uri);
    }

    /**
     * 화질별 재생목록 — 조각 주소를 서명된 CDN 절대 URL 로 바꾼다.
     *
     * <p>조각이 {@code single_file} 구조라 실제로는 {@code data.m4s} 하나를 가리키는
     * 줄이 반복된다. 같은 주소를 여러 번 서명하게 되므로 <b>한 번만 서명해 재사용한다</b> —
     * 줄마다 서명하면 60초 영상에서 30번을 돈다.
     *
     * @param prefix    산출물 프리픽스
     * @param rendition 화질 디렉터리 (예 {@code 720p})
     */
    public String variant(String prefix, String rendition) {
        requireKnownRendition(rendition);
        String body = read(prefix + "/" + rendition + "/" + VARIANT);
        String renditionPrefix = prefix + "/" + rendition + "/";
        // uri -> signed 를 캐시한다. 값이 하나뿐인 것이 보통이라 Map 을 쓸 필요가 없다
        String[] lastUri = new String[2];
        return HlsPlaylistRewriter.rewriteVariant(body, uri -> {
            if (!uri.equals(lastUri[0])) {
                lastUri[0] = uri;
                lastUri[1] = signer.sign(cdn(renditionPrefix + uri));
            }
            return lastUri[1];
        });
    }

    /**
     * 화질 디렉터리는 <b>화이트리스트로만</b> 받는다.
     *
     * <p>이 값은 URL 경로에서 와서 S3 키에 그대로 이어붙는다. 검증하지 않으면
     * {@code ../../../private/motif/남의uuid} 같은 값으로 <b>프리픽스를 벗어나
     * 임의의 오브젝트에 서명을 받아낼 수 있다</b> — 인가를 통과한 뒤라 더 위험하다.
     * 사다리는 {@code Rendition.LADDER} 고정이라 목록으로 막는 것이 가장 싸다.
     */
    private static void requireKnownRendition(String rendition) {
        if (!isKnownRendition(rendition)) {
            throw new IllegalArgumentException("알 수 없는 화질: " + rendition);
        }
    }

    /**
     * 사다리에 있는 화질인가 — 호출자가 <b>404 로 먼저 끊고 싶을 때</b> 쓴다.
     *
     * <p>{@link #variant} 는 이 검사를 다시 한다. 밖에서 걸렀다고 안에서 빼면, 다른
     * 호출자가 생겼을 때 검증 없이 S3 키가 조립된다 — 막는 것이 경로 순회라 한 겹으로
     * 두지 않는다.
     */
    public static boolean isKnownRendition(String rendition) {
        return RENDITIONS.contains(rendition);
    }

    private String cdn(String key) {
        String base = cdnBaseUrl.endsWith("/")
                ? cdnBaseUrl.substring(0, cdnBaseUrl.length() - 1)
                : cdnBaseUrl;
        return base + "/" + key;
    }

    /**
     * S3 에서 재생목록 본문을 읽는다.
     *
     * <p>바이트를 힙에 올리지만 재생목록은 수 KB 다 — 조각({@code data.m4s}, 수 MB)은
     * 여기를 지나지 않는다. 그 구분이 원칙 ① 을 지키는 경계다.
     */
    private String read(String key) {
        try {
            return s3.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket).key(key).build()).asUtf8String();
        } catch (NoSuchKeyException e) {
            // 가공이 아직 안 끝났거나 프리픽스 규약이 어긋난 경우다. 둘 다 "없음" 으로
            // 같게 보이지만, 조용히 빈 재생목록을 내면 플레이어가 이유 없이 멈춘다
            throw new MediaPlaylistNotFoundException(key, e);
        }
    }

    /** 재생목록이 S3 에 없다. 호출자가 404 로 옮긴다(§1.8 비노출 규칙). */
    public static class MediaPlaylistNotFoundException extends RuntimeException {
        public MediaPlaylistNotFoundException(String key, Throwable cause) {
            super("재생목록이 없다: " + key, cause);
        }
    }
}
