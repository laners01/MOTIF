package com.ssafy.motif.coreapi.domain.video.view;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 조회 이벤트 접수 (API 명세 §8.5 · ADR-0015).
 *
 * <p><b>DB 를 건드리지 않는다.</b> Redis 버퍼에 누적만 하고 202 로 답한다 — 집계는
 * 20분 주기 배치가 한다.
 *
 * <p><b>트랜잭션이 없다.</b> 이 경로에 DB 접근이 없어 걸 대상이 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoViewService {

    private final VideoViewBuffer buffer;
    private final VideoViewProperties properties;

    /**
     * 조회수를 버퍼에 누적한다.
     *
     * <p><b>검증 실패와 Redis 실패의 취급이 정반대다.</b> 검증은 던지고(400), Redis 는
     * 삼킨다(202). 전자는 클라이언트가 고쳐야 하는 버그이고 후자는 클라이언트가 할 수
     * 있는 일이 없다 — 영상은 정상 재생되는데 조회수 집계 실패로 에러를 보는 것이 더
     * 나쁘다(fail-open, ADR-0015). {@code ProfileImagePresignRegistry} 가 fail-closed 인
     * 것과 반대인데, 그쪽은 실패했을 때 사용자가 무엇을 해야 할지 알 수 있는 실패였다.
     *
     * <p><b>존재하지 않는 {@code videoSeq} 를 거르지 않는다.</b> 조회 이벤트마다 DB 를
     * 확인하면 집계를 Redis 로 뺀 의미가 없다. 없는 키는 배치의 {@code UPDATE} 가 0행으로
     * 버리고, 랭킹 모집단은 DB 에서 나오므로 순위에도 오르지 않는다(§8.5).
     */
    public void accumulate(Map<Long, Integer> views) {
        validate(views);

        try {
            buffer.add(views);
        } catch (Exception e) {
            // 여기서 던지면 재생 중인 클라이언트가 에러를 본다. 조회수는 잃어도 사용자가
            // 복구할 필요가 없는 값이라 삼킨다 — 대신 로그는 error 로 남긴다
            log.error("조회수 적재 실패 — {}건을 버린다", views.size(), e);
        }
    }

    /**
     * <b>부분 수용하지 않는다.</b> 항목 하나가 어긋나면 요청 전체를 거절한다. 어긋난
     * 것만 조용히 버리면 클라이언트 버그가 응답에도 로그에도 남지 않아 안드로이드에서
     * 원인을 찾을 수 없다(§8.5).
     *
     * <p>{@code data} 에 어긴 항목을 실어 보낸다 — 어느 영상인지 모르면 50개짜리 요청에서
     * 범인을 찾을 수 없다({@code BusinessException.data} 의 용도).
     */
    private void validate(Map<Long, Integer> views) {
        if (views.size() > properties.maxVideosPerRequest()) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER,
                    "한 요청에 담을 수 있는 영상은 %d개다: %d개"
                            .formatted(properties.maxVideosPerRequest(), views.size()),
                    Map.of("maxVideosPerRequest", properties.maxVideosPerRequest()));
        }

        for (Map.Entry<Long, Integer> entry : views.entrySet()) {
            Long videoSeq = entry.getKey();
            Integer count = entry.getValue();

            // 키가 null 인 경우는 {"": 1} 같은 본문에서 나온다 — Jackson 이 여기까지 통과시킨다
            if (videoSeq == null || videoSeq <= 0) {
                throw new BusinessException(CommonErrorCode.INVALID_PARAMETER,
                        "videoSeq 가 올바르지 않다: " + videoSeq,
                        Map.of("videoSeq", String.valueOf(videoSeq)));
            }
            if (count == null || count <= 0 || count > properties.maxCountPerVideo()) {
                throw new BusinessException(CommonErrorCode.INVALID_PARAMETER,
                        "count 는 1 이상 %d 이하다: videoSeq=%d count=%s"
                                .formatted(properties.maxCountPerVideo(), videoSeq, count),
                        Map.of("videoSeq", videoSeq, "maxCountPerVideo", properties.maxCountPerVideo()));
            }
        }
    }
}
