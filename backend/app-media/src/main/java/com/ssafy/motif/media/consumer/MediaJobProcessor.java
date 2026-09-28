package com.ssafy.motif.media.consumer;

import com.ssafy.motif.event.contract.CompositePackagedEvent;
import com.ssafy.motif.event.contract.MediaJobRequestedEvent;
import com.ssafy.motif.event.topology.Exchanges;
import com.ssafy.motif.event.topology.RoutingKeys;
import com.ssafy.motif.media.encode.HlsPackager;
import com.ssafy.motif.media.storage.MediaStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

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
 *
 * <p><b>세 소스가 같은 경로를 탄다</b> — 합성 결과 · 챌린지 원본 · 모티프 에셋
 * 미리보기. 갈리는 것은 갱신할 테이블뿐이고 가공 파라미터는 하나다(ADR-0010 D1).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaJobProcessor {

    private final HlsPackager packager;
    private final MediaStorage storage;
    private final JdbcTemplate jdbc;
    private final MediaProperties props;
    private final RabbitTemplate rabbitTemplate;

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
            packager.packageForPlayback(source, out, event.thumbnailAtMs());
            storage.uploadTree(out, event.outputPrefix());
            // 길이는 원본에서 잰다 — 합성 결과만 쓰지만, 재는 비용이 ffprobe 한 번이라
            // 분기 안으로 밀어 넣지 않는다
            report(event, packager.durationMs(source));

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
            // 에셋도 등록 시점엔 video_key 가 업로드 MP4 다 — 챌린지와 같은 판정이 선다
            case MediaJobRequestedEvent.SOURCE_ASSET_PREVIEW -> jdbc.queryForObject(
                    "SELECT COUNT(*) FROM motif_assets WHERE asset_seq = ? AND video_key = ?",
                    Integer.class, event.sourceSeq(), master);
            default -> throw new IllegalArgumentException("알 수 없는 sourceType: " + event.sourceType());
        };
        return hit != null && hit > 0;
    }

    /**
     * 가공 결과를 알린다 — 소스에 따라 <b>직접 UPDATE 하거나 이벤트를 낸다</b>.
     *
     * <p><b>{@code COMPOSITE_RESULT} 만 이벤트다</b>(S15P21D104-189). {@code composite_jobs}
     * 의 쓰기 주체는 core-api 하나여야 하는데(작업명세 §2 원칙 1), 여기서 UPDATE 하던
     * 예전 코드는 {@code PROCESSING} 행에 결과 키만 써서
     * {@code ck_composite_jobs_status_shape} 에 <b>반드시</b> 걸렸다 — 재시도 3회 뒤
     * {@code dlq.job.media}. 상태와 키는 한 UPDATE 로 가야 하고 그 판단은 core 의 몫이다
     * ({@code CompositeJob.complete}).
     *
     * <p>{@code CHALLENGE_ORIGIN}·{@code ASSET_PREVIEW} 는 그대로 둔다. 그쪽은 상태
     * 칼럼이 없어 키만 쓰면 되고, 여기서 끝내는 편이 왕복이 없다.
     *
     * <p>마스터 재생목록 경로 하나만 기록한다 — 나머지는 같은 디렉터리 규약으로 찾으므로
     * 스키마 변경이 없다 (ADR-0010 D4).
     *
     * <p>JPA 엔티티가 아니라 JdbcTemplate 을 쓴다. {@code challenges}·{@code motif_assets}
     * 엔티티는 각 도메인 티켓의 소유이고, 여기서 만들면 나중에 중복·충돌이 난다.
     *
     * <p>트랜잭션을 걸지 않는다 — UPDATE 한 문장이라 그 자체로 원자적이다.
     * (걸더라도 같은 빈 안에서 호출하면 프록시를 타지 않아 아무 효과가 없다.)
     */
    private void report(MediaJobRequestedEvent event, Integer durationMs) {
        String master = masterKey(event);
        String thumb = event.outputPrefix() + "/" + HlsPackager.THUMBNAIL;
        if (MediaJobRequestedEvent.SOURCE_COMPOSITE_RESULT.equals(event.sourceType())) {
            publishPackaged(event, master, thumb, durationMs);
            return;
        }
        int updated = switch (event.sourceType()) {
            case MediaJobRequestedEvent.SOURCE_CHALLENGE_ORIGIN -> jdbc.update("""
                    UPDATE challenges
                       SET video_key = ?, thumbnail_key = ?, mod_dttm = now()
                     WHERE challenge_seq = ?""", master, thumb, event.sourceSeq());
            case MediaJobRequestedEvent.SOURCE_ASSET_PREVIEW -> jdbc.update("""
                    UPDATE motif_assets
                       SET video_key = ?, thumbnail_key = ?, mod_dttm = now()
                     WHERE asset_seq = ?""", master, thumb, event.sourceSeq());
            default -> throw new IllegalArgumentException("알 수 없는 sourceType: " + event.sourceType());
        };
        if (updated != 1) {
            throw new IllegalStateException(
                    "갱신 대상이 " + updated + "행이다 — sourceType=" + event.sourceType()
                            + " sourceSeq=" + event.sourceSeq());
        }
    }

    /**
     * {@code composite.packaged} 발행 — core 가 {@code DONE} 을 확정한다.
     *
     * <p><b>여기서 실패하면 예외를 올린다.</b> 이 메시지가 없으면 잡이 {@code PROCESSING}
     * 에 영원히 머무르므로, 조용히 삼키면 스윕이 {@code STUCK} 으로 닫을 때까지 아무도
     * 모른다. 재시도 3회 뒤 {@code dlq.job.media} 에 남는 편이 낫다 — 산출물은 이미
     * S3 에 있어 재처리해도 같은 자리를 덮어쓴다.
     *
     * <p>{@code x-request-id} 는 {@code RabbitTemplate} 의 post-processor 가 MDC 에서
     * 꺼내 붙인다({@code MqConfig}) — 리스너가 복원해 둔 값이라 잡 하나가 한 ID 로 이어진다.
     */
    private void publishPackaged(MediaJobRequestedEvent event, String master, String thumb,
                                 Integer durationMs) {
        CompositePackagedEvent packaged = new CompositePackagedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                event.mediaJobUuid(),
                event.sourceType(),
                event.sourceSeq(),
                event.outputPrefix(),
                master,
                thumb,
                durationMs);
        rabbitTemplate.convertAndSend(
                Exchanges.MOTIF_TOPIC, RoutingKeys.COMPOSITE_PACKAGED, packaged);
        log.info("composite.packaged 발행: mediaJobUuid={} jobSeq={} durationMs={}",
                event.mediaJobUuid(), event.sourceSeq(), durationMs);
    }

    private static String masterKey(MediaJobRequestedEvent event) {
        return event.outputPrefix() + "/" + HlsPackager.MASTER;
    }
}
