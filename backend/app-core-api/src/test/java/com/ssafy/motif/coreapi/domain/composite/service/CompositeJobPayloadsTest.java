package com.ssafy.motif.coreapi.domain.composite.service;

import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobPayload;
import com.ssafy.motif.domain.core.asset.AssetType;
import com.ssafy.motif.domain.core.asset.MotifAsset;
import com.ssafy.motif.domain.core.challenge.Challenge;
import com.ssafy.motif.domain.core.challenge.ChallengeParticipant;
import com.ssafy.motif.domain.core.challenge.ChallengeParticipant.Anchor;
import com.ssafy.motif.domain.core.challenge.EditedChallenge;
import com.ssafy.motif.domain.core.job.CompositeJob;
import com.ssafy.motif.domain.core.job.JobType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 접수 페이로드가 <b>AI 서버 명세 §3.2 의 JSON 과 필드 단위로 같은지</b> 증명한다
 * (S15P21D104-187 · 247 DoD).
 *
 * <p><b>이 테스트가 계약이다.</b> 문서의 두 예시를 문자열로 그대로 박아 두고 조립 결과와
 * 맵으로 대조한다 — 필드 이름 하나가 camelCase 로 새거나, 칸이 하나 늘거나 줄면 여기서
 * 깨진다. AI 는 Python 이라 우리가 보낸 키를 그대로 읽고, 틀려도 예외 대신 <b>합성이
 * 이상하게 나오거나 조용히 실패</b>한다 — 런타임에 드러나지 않는 종류의 오류다.
 *
 * <p>맵 비교라 <b>순서가 아니라 집합</b>을 본다. 대신 누락·오타·여분 칸을 전부 잡는다.
 *
 * <p>{@code CompositeJobPayloads} 가 순수 계산이라 DB 도 스프링 컨텍스트도 없다.
 */
class CompositeJobPayloadsTest {

    /** 웹 계층과 같은 Jackson 3 다 — {@code @JsonNaming} 이 실제로 먹는지까지 본다. */
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final UUID CHALLENGE_UUID = UUID.fromString("b7c93b4e-0a56-4d2e-9f0d-2f6e8a54d61c");
    private static final UUID MOTIF_UUID = UUID.fromString("9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f");

    /** AI 서버 명세 §3.2 CHALLENGE 예시 — <b>한 글자도 고치지 않는다</b>. */
    private static final String SPEC_CHALLENGE = """
            {
              "job_uuid": "b7c93b4e-0a56-4d2e-9f0d-2f6e8a54d61c",
              "job_type": "CHALLENGE",
              "attempt": 1,
              "raw_video_key": "raw/b7c93b4e-0a56-4d2e-9f0d-2f6e8a54d61c.mp4",
              "base_video_key": "edited/11/7d2f…e1.mp4",
              "output_video_key": "results/challenge/b7c93b4e-0a56-4d2e-9f0d-2f6e8a54d61c.mp4",
              "correction_enabled": true,
              "participant_mask": 3,
              "slots": [
                { "participant_mask": 1, "anchor": { "x": 0.22, "y": 0.36, "r": 0.0995 } },
                { "participant_mask": 2, "anchor": { "x": 0.72, "y": 0.34, "r": 0.0995 } }
              ],
              "base_duration_ms": 15000
            }""";

    /** AI 서버 명세 §3.2 MOTIF 예시. */
    private static final String SPEC_MOTIF = """
            {
              "job_uuid": "9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f",
              "job_type": "MOTIF",
              "attempt": 1,
              "raw_video_key": "raw/9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f.mp4",
              "base_video_key": "assets/7e2a1b9c-3d4e-5f60-8a1b-2c3d4e5f6071.mp4",
              "output_video_key": "results/motif/9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f.mp4",
              "correction_enabled": false,
              "participant_mask": 0,
              "slots": [],
              "base_duration_ms": 9800
            }""";

    @Test
    @DisplayName("CHALLENGE — 조립 결과가 AI 명세 §3.2 예시와 필드 단위로 같다")
    void challengePayloadMatchesTheSpec() {
        CompositeJob job = challengeJob(true);
        Challenge challenge = challenge("challenges/8a1b/master.m3u8", 15_000);
        EditedChallenge edited = EditedChallenge.of(
                11, 3, "edited/11/7d2f…e1.mp4", "edited/11/scene.jpg", "edited/11/preview.jpg");

        CompositeJobPayload payload = CompositeJobPayloads.from(job, challenge, edited, participants());

        assertThat(asMap(payload)).isEqualTo(parse(SPEC_CHALLENGE));
    }

    @Test
    @DisplayName("MOTIF — 조립 결과가 AI 명세 §3.2 예시와 필드 단위로 같다")
    void motifPayloadMatchesTheSpec() {
        CompositeJob job = motifJob();
        MotifAsset asset = MotifAsset.register(3, "에셋", "소개",
                // app-media 가 다녀간 값 — sourceMp4Of 가 MP4 로 되돌려야 명세와 같아진다
                "assets/7e2a1b9c-3d4e-5f60-8a1b-2c3d4e5f6071/master.m3u8",
                "assets/7e2a1b9c-3d4e-5f60-8a1b-2c3d4e5f6071/thumb.jpg", 9_800, AssetType.NORMAL);

        CompositeJobPayload payload = CompositeJobPayloads.from(job, asset);

        assertThat(asMap(payload)).isEqualTo(parse(SPEC_MOTIF));
    }

    // --- base_video_key 는 언제나 MP4 다 (작업명세 §7 함정 3) -------------------------

    @Test
    @DisplayName("무교체 — 베이스가 챌린지 원본이고 master.m3u8 가 .mp4 로 되돌아간다")
    void noReplacementUsesTheChallengeOriginAsMp4() {
        CompositeJobPayload payload = CompositeJobPayloads.from(
                challengeJob(false),
                challenge("challenges/8a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9/master.m3u8", 15_000),
                null, participants());

        assertThat(payload.baseVideoKey())
                .isEqualTo("challenges/8a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9.mp4");
        assertThat(payload.participantMask()).isZero();
        assertThat(payload.slots()).isEmpty();
    }

    @Test
    @DisplayName("가공 전 챌린지 — video_key 가 이미 MP4 면 그대로 쓴다")
    void unprocessedChallengeKeyIsLeftAlone() {
        CompositeJobPayload payload = CompositeJobPayloads.from(
                challengeJob(false), challenge("challenges/8a1b.mp4", 15_000), null, List.of());

        assertThat(payload.baseVideoKey()).isEqualTo("challenges/8a1b.mp4");
    }

    // --- slots -------------------------------------------------------------------------

    @Test
    @DisplayName("교체 대상 슬롯만 담는다 — 마스크에 없는 슬롯은 빠진다")
    void slotsCarryOnlyTheSelectedOnes() {
        EditedChallenge onlySecond = EditedChallenge.of(11, 2, "edited/11/b.mp4", "s.jpg", "p.jpg");

        CompositeJobPayload payload = CompositeJobPayloads.from(
                challengeJob(true), challenge("challenges/8a1b.mp4", 15_000),
                onlySecond, participants());

        assertThat(payload.participantMask()).isEqualTo(2);
        assertThat(payload.slots()).singleElement()
                .satisfies(slot -> assertThat(slot.participantMask()).isEqualTo(2));
    }

    @Test
    @DisplayName("0017 이전 행은 anchor 가 null 이다 — 위치 힌트라 없어도 보낸다")
    void anchorlessSlotSerializesAsNull() {
        List<ChallengeParticipant> old = List.of(ChallengeParticipant.of(11, 5, 1, null));

        CompositeJobPayload payload = CompositeJobPayloads.from(
                challengeJob(true), challenge("challenges/8a1b.mp4", 15_000),
                EditedChallenge.of(11, 1, "edited/11/a.mp4", "s.jpg", "p.jpg"), old);

        assertThat(payload.slots()).singleElement()
                .satisfies(slot -> assertThat(slot.anchor()).isNull());

        Map<String, Object> slot = new LinkedHashMap<>();
        slot.put("participant_mask", 1);
        slot.put("anchor", null);   // 칸은 남는다 — AI 가 "없음" 과 "안 보냄" 을 구분하지 않아도 되게
        assertThat(asMap(payload)).containsEntry("slots", List.of(slot));
    }

    // --- 픽스처 ---------------------------------------------------------------------------

    private static CompositeJob challengeJob(boolean correctionEnabled) {
        CompositeJob job = CompositeJob.create(CHALLENGE_UUID, JobType.CHALLENGE, 7, 11, null,
                null, "raw/" + CHALLENGE_UUID + ".mp4");
        job.queue(correctionEnabled);
        return job;
    }

    private static CompositeJob motifJob() {
        CompositeJob job = CompositeJob.create(MOTIF_UUID, JobType.MOTIF, 7, null, 21,
                null, "raw/" + MOTIF_UUID + ".mp4");
        job.queue(false);
        return job;
    }

    private static Challenge challenge(String videoKey, int durationMs) {
        return Challenge.register(3, 9, "소개", videoKey, "challenges/8a1b/thumb.jpg", durationMs);
    }

    private static List<ChallengeParticipant> participants() {
        return List.of(
                ChallengeParticipant.of(11, 5, 1, new Anchor(0.22, 0.36, 0.0995)),
                ChallengeParticipant.of(11, 6, 2, new Anchor(0.72, 0.34, 0.0995)),
                ChallengeParticipant.of(11, 7, 4, new Anchor(0.50, 0.30, 0.0995)));
    }

    private static Map<String, Object> asMap(CompositeJobPayload payload) {
        return parse(MAPPER.writeValueAsString(payload));
    }

    private static Map<String, Object> parse(String json) {
        return MAPPER.readValue(json, new TypeReference<>() { });
    }
}
