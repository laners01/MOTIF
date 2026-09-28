package com.ssafy.motif.media.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/** S3 입출력. 바이트를 힙에 올리지 않고 파일로 주고받는다 — 영상이 25MB 단위다. */
@Slf4j
@Component
public class MediaStorage {

    private final S3Client s3;
    private final String bucket;

    public MediaStorage(S3Client s3, MediaStorageProperties props) {
        this.s3 = s3;
        this.bucket = props.bucket();
    }

    public void download(String key, Path dest) {
        s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build(), dest);
    }

    /**
     * {@code dir} 아래 전부를 {@code prefix} 아래로 올린다. 디렉터리 구조를 그대로 옮긴다.
     *
     * <p>마스터 재생목록이 화질별 재생목록을 상대 경로로 참조하므로 구조가 유지돼야 한다.
     *
     * <p><b>같은 프리픽스에 다시 쓰면 CDN 무효화가 필수다.</b> 산출물에 1년
     * {@code immutable} 을 붙이는데, 이는 한 프리픽스에 한 번만 쓴다는 전제 위에 있다.
     * 정상 등록 흐름은 챌린지마다 새 uuid 프리픽스라 이 전제가 성립한다 — 깨지는 것은
     * <b>재처리</b>뿐이다.
     *
     * <p>부분 무효화로 때우려 들지 마라. 재생목록만 새로 내려보내면 그 안의
     * {@code EXT-X-BYTERANGE} 가 <b>옛 세그먼트</b>에 적용되어 재생이 깨진다 —
     * 전부 옛것인 지금 상태(화질만 낮고 재생은 정상)보다 나쁘다.
     * 2026-09-14 사다리 교체 때 실제로 겪었다: S3 의 480p 는 480×854(약 1.0MB)인데
     * CDN 은 270×480(507KB)을 계속 줬다.
     *
     * <pre>
     * aws cloudfront create-invalidation --distribution-id &lt;id&gt; \
     *     --paths '/challenges/&lt;uuid&gt;/*'
     * </pre>
     */
    public void uploadTree(Path dir, String prefix) {
        try (Stream<Path> files = Files.walk(dir)) {
            files.filter(Files::isRegularFile).forEach(f -> {
                String key = prefix + "/" + dir.relativize(f).toString().replace('\\', '/');
                s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket).key(key)
                        .contentType(contentType(key))
                        // 산출물은 불변이다 — 키가 바뀌지 값이 바뀌지 않는다.
                        // CDN 적중률을 위해 길게 잡는다 (ADR-0010)
                        // ponytail: 프리픽스 아래 트리 전체가 원자적 집합이라는 전제로
                        // 1년 immutable 이다. 재생목록이 세그먼트를 EXT-X-BYTERANGE 로
                        // 가리키므로 일부만 갱신될 수 없다 — 재인코딩하면 프리픽스
                        // 전체를 무효화해야 한다(아래 주석). 재처리가 상시 작업이 되면
                        // outputPrefix 에 mediaJobUuid 를 넣어 매번 새 트리를 쓴다
                        .cacheControl("public, max-age=31536000, immutable")
                        .build(), RequestBody.fromFile(f));
            });
        } catch (IOException e) {
            throw new MediaStorageException("업로드 실패: " + dir + " -> " + prefix, e);
        }
    }

    /** 재생목록·조각은 타입이 맞아야 플레이어가 받아들인다. */
    private static String contentType(String key) {
        if (key.endsWith(".m3u8")) {
            return "application/vnd.apple.mpegurl";
        }
        if (key.endsWith(".m4s") || key.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (key.endsWith(".jpg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }

    /** 작업 디렉터리 정리. 실패해도 잡을 실패시키지 않는다. */
    public static void deleteQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("작업 파일을 지우지 못했다: {}", p);
                }
            });
        } catch (IOException e) {
            log.warn("작업 디렉터리를 지우지 못했다: {}", dir);
        }
    }
}
