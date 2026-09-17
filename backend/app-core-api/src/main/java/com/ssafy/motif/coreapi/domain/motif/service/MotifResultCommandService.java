package com.ssafy.motif.coreapi.domain.motif.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.motif.MotifErrorCode;
import com.ssafy.motif.coreapi.domain.motif.dto.MotifResultDetailResponse;
import com.ssafy.motif.coreapi.domain.motif.dto.MotifResultUpdateRequest;
import com.ssafy.motif.domain.core.motif.MotifResult;
import com.ssafy.motif.domain.core.motif.MotifResultRepository;
import com.ssafy.motif.domain.core.user.User;
import com.ssafy.motif.domain.core.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 모티프 결과물 수정·삭제 (API 명세 §11.8) — <b>§11 의 유일한 쓰기</b>.
 *
 * <p>{@link MotifResultQueryService} 에 섞지 않는다. 그쪽은
 * {@code @Transactional(readOnly = true)} 이고, 읽기 전용 서비스에 쓰기를 넣으면 그
 * 표시가 거짓말이 된다(아티스트 패키지가 조회와 구독을 나눠 둔 것과 같은 규칙).
 *
 * <p><b>응답은 {@link MotifResultQueryService#detail} 을 다시 부른다.</b> 수정 결과가
 * §11.6 상세와 같은 형태여야 하는데(명세), 조립을 여기서 한 벌 더 쓰면 수정 직후 화면과
 * 상세 화면이 다른 것을 그린다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MotifResultCommandService {

    private final MotifResultRepository motifResultRepository;
    private final UserRepository userRepository;
    private final MotifResultQueryService motifResultQueryService;

    /**
     * §11.8 PATCH — 문구·대표 설정.
     *
     * <p>둘 다 생략하면 아무것도 바꾸지 않고 현재 상태를 돌려준다. 그래도 <b>존재 확인은
     * 한다</b> — 없는 결과물에 빈 요청을 보내면 200 이 아니라 404 여야 한다.
     *
     * @throws BusinessException {@code MOTIF_RESULT_NOT_FOUND} — 미존재·삭제·타인 소유
     */
    public MotifResultDetailResponse update(Integer userSeq, Long resultSeq,
                                            MotifResultUpdateRequest request) {
        MotifResult result = require(userSeq, resultSeq);

        result.updateDescription(request.description());
        if (request.isRep() != null) {
            applyRep(userSeq, resultSeq, request.isRep());
        }

        // 변경을 먼저 반영해야 상세 조회가 수정본을 읽는다 — 같은 트랜잭션이라
        // 영속성 컨텍스트에는 이미 있지만, 조회가 네이티브·다른 경로를 타도 안전하게 둔다
        motifResultRepository.flush();
        return motifResultQueryService.detail(userSeq, resultSeq);
    }

    /**
     * §11.8 DELETE — 소프트 삭제.
     *
     * <p><b>대표였다면 같은 트랜잭션에서 비운다.</b> {@code users.rep_motif_result_seq} 는
     * FK 가 없는 논리 참조라 DB 가 정리해 주지 않는다 — 빠뜨리면 마이페이지(§4.1)가
     * 삭제된 결과물을 대표로 가리킨다.
     *
     * <p>이미 삭제한 결과물에 다시 보내면 404 다. 멱등으로 200 을 주지 않는 이유는 조회가
     * 삭제분을 빼고 있어 "없는 것" 과 구분할 방법이 없기 때문이다.
     *
     * @throws BusinessException {@code MOTIF_RESULT_NOT_FOUND}
     */
    public void delete(Integer userSeq, Long resultSeq) {
        MotifResult result = require(userSeq, resultSeq);

        result.softDelete();
        user(userSeq).filter(user -> Objects.equals(user.getRepMotifResultSeq(), resultSeq))
                .ifPresent(user -> user.changeRepMotif(null));
    }

    /**
     * 대표 지정·해제.
     *
     * <p><b>지정은 덮어쓰기 하나로 끝난다</b> — 칸이 사용자당 하나라 이전 대표는 그대로
     * 해제된다({@code User#changeRepMotif} javadoc).
     *
     * <p><b>해제는 현재 대표일 때만 한다.</b> 대표가 아닌 결과물에 {@code false} 를 보내면
     * 아무 일도 일어나지 않는다 — 조건 없이 비우면 다른 결과물의 지정이 지워진다.
     */
    private void applyRep(Integer userSeq, Long resultSeq, boolean isRep) {
        User user = user(userSeq).orElseThrow(() -> new IllegalStateException(
                "인증된 토큰의 사용자가 users 에 없다 — userSeq=" + userSeq));
        if (isRep) {
            user.changeRepMotif(resultSeq);
            return;
        }
        if (Objects.equals(user.getRepMotifResultSeq(), resultSeq)) {
            user.changeRepMotif(null);
        }
    }

    /**
     * 내 결과물을 집는다 — <b>타인 소유가 여기서 404 가 된다</b>.
     *
     * <p>조회 메서드가 {@code userSeq} 를 걸고 있어 소유자 분기를 따로 두지 않는다.
     * 403 으로 가르지 않는 이유는 §11.6 과 같다 — {@code resultSeq} 는 찍어 볼 수 있는
     * 값이라 "그건 남의 것" 이 응답에서 읽히면 안 된다(§1.8).
     */
    private MotifResult require(Integer userSeq, Long resultSeq) {
        return motifResultRepository
                .findByResultSeqAndUserSeqAndDeletedFalse(resultSeq, userSeq)
                .orElseThrow(() -> new BusinessException(MotifErrorCode.MOTIF_RESULT_NOT_FOUND));
    }

    private java.util.Optional<User> user(Integer userSeq) {
        return userRepository.findById(userSeq);
    }
}
