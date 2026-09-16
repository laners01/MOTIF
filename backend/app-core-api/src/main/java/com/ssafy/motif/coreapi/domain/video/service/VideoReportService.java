package com.ssafy.motif.coreapi.domain.video.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.report.ReportErrorCode;
import com.ssafy.motif.domain.core.report.ReportRepository;
import com.ssafy.motif.domain.core.video.VideoFeedRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 참여 영상 신고 (API 명세 §8.7).
 *
 * <p><b>{@link VideoQueryService} 와 나란한 별개 서비스다.</b> 그쪽은
 * {@code @Transactional(readOnly = true)} 로 선언돼 있어 쓰기를 한 줄이라도 섞으면 그
 * 표시가 거짓말이 된다({@code SubscriptionCommandService} 가 갈라진 것과 같은 근거).
 *
 * <p><b>접수만 한다.</b> 신고했다고 영상이 숨겨지거나 가려지지 않는다 — 판단과 조치는
 * 어드민이 {@code status} 를 옮기며 하는 일이고, 접수가 곧 제재면 신고 하나로 남의
 * 영상을 내릴 수 있게 된다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class VideoReportService {

    private final VideoAccessGuard accessGuard;
    private final ReportRepository reportRepository;

    /**
     * §8.7 영상 신고 접수.
     *
     * <p><b>보이지 않는 영상은 신고 대상도 아니다.</b> 판정을 §8.2 와 공유하는 이유는
     * {@link VideoAccessGuard} javadoc 에 있다 — 여기만 느슨하면 신고 응답이 타인의
     * {@code PRIVATE} 영상의 존재를 알려 주는 탐지 수단이 된다.
     *
     * <p><b>본인 영상 신고를 막지 않는다 (기본안).</b> 명세에 없고, 화면 흐름상 일어나기
     * 어렵다. 막으려면 "왜 막는가"를 클라이언트에 설명할 코드가 하나 더 필요한데 §15
     * 표에 그런 코드가 없다.
     *
     * <p>중복은 애플리케이션이 아니라 DB 가 가른다 —
     * {@link ReportRepository#insertVideoReport} javadoc 참조.
     */
    public void report(Integer userSeq, Long videoSeq, String reasonCode) {
        VideoFeedRow video = accessGuard.requireVisible(userSeq, videoSeq);

        if (reportRepository.insertVideoReport(userSeq, video.videoSeq(), reasonCode) == 0) {
            throw new BusinessException(
                    ReportErrorCode.ALREADY_REPORTED, "videoSeq: " + videoSeq);
        }
    }
}
