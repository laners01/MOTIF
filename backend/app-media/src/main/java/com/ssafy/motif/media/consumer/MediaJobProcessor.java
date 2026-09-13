package com.ssafy.motif.media.consumer;

import com.ssafy.motif.event.contract.MediaJobRequestedEvent;
import com.ssafy.motif.media.encode.HlsPackager;
import com.ssafy.motif.media.storage.MediaStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 후처리 한 건 — 내려받고, 가공하고, 올리고, 키를 기록한다 (ADR-0010 D1·D4).
 *
 * <pre>
 * S3 원본(MP4) → 렌디션 2종 + HLS + 썸네일 → S3 산출물 → DB 키 갱신
 * </pre>
 *
 * <p>실측 비용은 60초 영상 기준 10.5초다(영상 길이의 0.1배속). 합성 자체가 훨씬
 * 비싸므로 배치로 미루지 않는다 — 미루면 갓 올라온 영상에 화질 사다리가 없어
 * ABR 이 동작하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaJobProcessor {

    private final HlsPackager packager;
    private final MediaStorage storage;
    private final JdbcTemplate jdbc;
    private final MediaProperties props;

    public void process(MediaJobRequestedEvent event) {
        if (alreadyDone(event)) {
            log.info("이미 처리된 잡 — 건너뛴다: mediaJobUuid={}", event.mediaJobUuid());
            return;
        }

        Path work = null;
        try {
            work = Files.createDirectories(
                    Path.of(props.workDir()).resolve(event.mediaJobUuid()));
            Path source = work.resolve("source.mp4");
            Path out = work.resolve("out");

            storage.download(event.sourceKey(), source);
            packager.packageForPlayback(source, out);
            storage.uploadTree(out, event.outputPrefix());
            recordKeys(event);

            log.info("후처리 완료: mediaJobUuid={} sourceType={} prefix={}",
                    event.mediaJobUuid(), event.sourceType(), event.outputPrefix());
        } catch (IOException e) {
            throw new IllegalStateException("작업 디렉터리를 만들지 못했다: " + props.workDir(), e);
        } finally {
            MediaStorage.deleteQuietly(work);
        }
    }

    /**
     * 멱등 가드. 재전달 시 같은 키로 덮어써도 결과는 같지만, 10초짜리 인코딩을
     * 다시 돌릴 이유가 없다.
     */
    private boolean alreadyDone(MediaJobRequestedEvent event) {
        String master = masterKey(event);
        Integer hit = switch (event.sourceType()) {
            case MediaJobRequestedEvent.SOURCE_COMPOSITE_RESULT -> jdbc.queryForObject(
                    "SELECT COUNT(*) FROM composite_jobs WHERE job_seq = ? AND result_video_key = ?",
                    Integer.class, event.sourceSeq(), master);
            case MediaJobRequestedEvent.SOURCE_CHALLENGE_ORIGIN -> jdbc.queryForObject(
                    "SELECT COUNT(*) FROM challenges WHERE challenge_seq = ? AND video_key = ?",
                    Integer.class, event.sourceSeq(), master);
            default -> throw new IllegalArgumentException("알 수 없는 sourceType: " + event.sourceType());
        };
        return hit != null && hit > 0;
    }

    /**
     * 산출물 키를 기록한다. <b>마스터 재생목록 경로 하나만</b> 넣는다 — 나머지는 같은
     * 디렉터리 규약으로 찾으므로 스키마 변경이 없다 (ADR-0010 D4).
     *
     * <p>JPA 엔티티가 아니라 JdbcTemplate 을 쓴다. {@code composite_jobs}·
     * {@code challenges} 엔티티는 각 도메인 티켓의 소유이고, 여기서 만들면 나중에
     * 중복·충돌이 난다. 엔티티가 생기면 이 메서드만 갈아끼우면 된다.
     *
     * <p>트랜잭션을 걸지 않는다 — UPDATE 한 문장이라 그 자체로 원자적이다.
     * (걸더라도 같은 빈 안에서 호출하면 프록시를 타지 않아 아무 효과가 없다.)
     */
    private void recordKeys(MediaJobRequestedEvent event) {
        String master = masterKey(event);
        String thumb = event.outputPrefix() + "/" + HlsPackager.THUMBNAIL;
        int updated = switch (event.sourceType()) {
            case MediaJobRequestedEvent.SOURCE_COMPOSITE_RESULT -> jdbc.update("""
                    UPDATE composite_jobs
                       SET result_video_key = ?, result_thumbnail_key = ?, mod_dttm = now()
                     WHERE job_seq = ?""", master, thumb, event.sourceSeq());
            case MediaJobRequestedEvent.SOURCE_CHALLENGE_ORIGIN -> jdbc.update("""
                    UPDATE challenges
                       SET video_key = ?, thumbnail_key = ?, mod_dttm = now()
                     WHERE challenge_seq = ?""", master, thumb, event.sourceSeq());
            default -> throw new IllegalArgumentException("알 수 없는 sourceType: " + event.sourceType());
        };
        if (updated != 1) {
            throw new IllegalStateException(
                    "갱신 대상이 " + updated + "행이다 — sourceType=" + event.sourceType()
                            + " sourceSeq=" + event.sourceSeq());
        }
    }

    private static String masterKey(MediaJobRequestedEvent event) {
        return event.outputPrefix() + "/" + HlsPackager.MASTER;
    }
}
