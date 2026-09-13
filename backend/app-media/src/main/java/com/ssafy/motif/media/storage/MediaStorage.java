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
