package com.ssafy.motif.coreapi.domain.composite.service;

import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobPayload;
import com.ssafy.motif.domain.core.asset.AssetType;
import com.ssafy.motif.domain.core.asset.MotifAsset;
import com.ssafy.motif.domain.core.challenge.Challenge;
import com.ssafy.motif.domain.core.challenge.ChallengeParticipant;
import com.ssafy.motif.domain.core.challenge.ChallengeParticipant.Anchor;
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
 * <p><b>0019 로 값의 범위가 좁아졌다</b> — 칸은 그대로지만 {@code participant_mask} 가 언제나
 * 단일 비트이고 {@code slots} 는 0개 또는 1개다. 편집본이 슬롯 1개당 1개라 조합이 없어졌다.
 * 명세의 예시도 그에 맞춰 바뀌었다(계약은 additive-only라 칸은 하나도 건드리지 않았다).
 *
 * <p>{@code CompositeJobPayloads} 가 순수 계산이라 DB 도 스프링 컨텍스트도 없다.
 */
class CompositeJobPayloadsTest {

    /** 웹 계층과 같은 Jackson 3 다 — {@code @JsonNaming} 이 실제로 먹는지까지 본다. */
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final UUID CHALLENGE_UUID = UUID.fromString("b7c93b4e-0a56-4d2e-9f0d-2f6e8a54d61c");
    private static final UUID MOTIF_UUID = UUID.fromString("9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f");

    /** AI 서버 명세 v0.4 §4 CHALLENGE 예시 — <b>한 글자도 고치지 않는다</b>. */
    private static final String SPEC_CHALLENGE = """
            {
              "job_uuid": "b7c93b4e-0a56-4d2e-9f0d-2f6e8a54d61c",
              "job_type": "CHALLENGE",
              "attempt": 1,
              "raw_video_key": "raw/b7c93b4e-0a56-4d2e-9f0d-2f6e8a54d61c.mp4",
              "base_video_key": "private/edited/11/31/7d2f…e1.mp4",
              "output_video_key": "results/challenge/b7c93b4e-0a56-4d2e-9f0d-2f6e8a54d61c.mp4",
              "correction_enabled": true,
              "slot": { "participant_mask": 2, "anchor": { "x": 0.72, "y": 0.34, "r": 0.0995 } },
              "base_duration_ms": 15000
            }""";

    /** AI 서버 명세 §4 MOTIF 예시. */
    private static final String SPEC_MOTIF = """
            {
              "job_uuid": "9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f",
              "job_type": "MOTIF",
              "attempt": 1,
              "raw_video_key": "raw/9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f.mp4",
              "base_video_key": "assets/7e2a1b9c-3d4e-5f60-8a1b-2c3d4e5f6071.mp4",
              "output_video_key": "results/motif/9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f.mp4",
              "correction_enabled": false,
              "slot": null,
              "base_duration_ms": 9800
            }""";

    @Test
    @DisplayName("CHALLENGE — 조립 결과가 AI 명세 §4 예시와 필드 단위로 같다")
    void challengePayloadMatchesTheSpec() {
        CompositeJob job = challengeJob(true);
        Challenge challenge = challenge("challenges/8a1b/master.m3u8", 15_000);
        // 슬롯 1(= slotOrder 1, 마스크 2)을 고른 사용자. 그 사람을 지운 편집본이 베이스다
        ChallengeParticipant edited = participants().get(1);

        CompositeJobPayload payload = CompositeJobPayloads.from(job, challenge, edited);

        assertThat(asMap(payload)).isEqualTo(parse(SPEC_CHALLENGE));
    }

    @Test
    @DisplayName("MOTIF — 조립 결과가 AI 명세 §4 예시와 필드 단위로 같다")
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

    /**
     * 편집본 키는 가공을 거치지 않는다 — 통짜 MP4 라 {@code sourceMp4Of} 가 그대로 돌려준다.
     * 되돌림이 필요한 것은 에셋·챌린지 원본처럼 app-media 가 덮어쓰는 키 쪽이다.
     */
    @Test
    @DisplayName("편집본 키는 이미 MP4 라 그대로 쓴다")
    void editedKeyIsLeftAlone() {
        CompositeJobPayload payload = CompositeJobPayloads.from(
                challengeJob(true), challenge("challenges/8a1b/master.m3u8", 15_000),
                participants().get(1));

        assertThat(payload.baseVideoKey()).isEqualTo("private/edited/11/31/7d2f…e1.mp4");
    }

    // --- slot ---------------------------------------------------------------------------

    /**
     * 2026-09-22 — 배열이 없어졌다. 사용자가 고르는 것이 한 명이고 편집본도 슬롯 1개당
     * 1개라, 한 잡이 교체하는 자리는 언제나 하나다. 바깥 합산 마스크도 같은 이유로 빠졌다.
     */
    @Test
    @DisplayName("고른 슬롯 하나가 그대로 slot 이 된다 — 어느 자리를 골라도")
    void slotCarriesTheChosenOne() {
        for (ChallengeParticipant chosen : participants()) {
            CompositeJobPayload payload = CompositeJobPayloads.from(
                    challengeJob(true), challenge("challenges/8a1b.mp4", 15_000), chosen);

            assertThat(payload.slot()).isNotNull();
            assertThat(payload.slot().participantMask()).isEqualTo(chosen.participantMask());
        }
    }

    @Test
    @DisplayName("MOTIF 는 slot 이 null 이다 — 교체할 인물이라는 개념이 없다")
    void motifHasNoSlot() {
        MotifAsset asset = MotifAsset.register(3, "에셋", "소개", "assets/7e2a.mp4",
                "assets/7e2a/thumb.jpg", 9_800, AssetType.NORMAL);

        CompositeJobPayload payload = CompositeJobPayloads.from(motifJob(), asset);

        assertThat(payload.slot()).isNull();
        // 칸은 남는다 — AI 가 "없음" 과 "안 보냄" 을 구분하지 않아도 되게
        assertThat(asMap(payload)).containsEntry("slot", null);
    }

    @Test
    @DisplayName("0017 이전 행은 anchor 가 null 이다 — 위치 힌트라 없어도 보낸다")
    void anchorlessSlotSerializesAsNull() {
        ChallengeParticipant old = participant(5, 0, null);

        CompositeJobPayload payload = CompositeJobPayloads.from(
                challengeJob(true), challenge("challenges/8a1b.mp4", 15_000), old);

        assertThat(payload.slot().anchor()).isNull();

        Map<String, Object> slot = new LinkedHashMap<>();
        slot.put("participant_mask", 1);
        slot.put("anchor", null);   // 칸은 남는다 — AI 가 "없음" 과 "안 보냄" 을 구분하지 않아도 되게
        assertThat(asMap(payload)).containsEntry("slot", slot);
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
                participant(5, 0, new Anchor(0.22, 0.36, 0.0995)),
                participant(6, 1, new Anchor(0.72, 0.34, 0.0995)),
                participant(7, 2, new Anchor(0.50, 0.30, 0.0995)));
    }

    /**
     * 편집본이 붙은 슬롯 — 베이스 키는 명세 예시의 값 그대로다.
     *
     * <p>키에 슬롯 seq(31)가 들어가지만 <b>여기서는 문자열일 뿐이다</b>. 엔티티가 아직
     * 영속되지 않아 실제 seq 가 없고, 조립기는 키를 파싱하지 않고 그대로 싣는다.
     */
    private static ChallengeParticipant participant(int artistSeq, int slotOrder, Anchor anchor) {
        ChallengeParticipant slot = ChallengeParticipant.of(11, artistSeq, slotOrder, anchor);
        slot.replaceEditedKeys("private/edited/11/31/7d2f…e1.mp4",
                "private/edited/11/31/scene.jpg", "private/edited/11/31/preview.jpg", 15_000);
        return slot;
    }

    private static Map<String, Object> asMap(CompositeJobPayload payload) {
        return parse(MAPPER.writeValueAsString(payload));
    }

    private static Map<String, Object> parse(String json) {
        return MAPPER.readValue(json, new TypeReference<>() { });
    }
}
