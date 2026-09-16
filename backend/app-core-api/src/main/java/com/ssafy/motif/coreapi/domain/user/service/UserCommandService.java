package com.ssafy.motif.coreapi.domain.user.service;

import com.ssafy.motif.coreapi.domain.user.dto.NicknameUpdateResponse;
import com.ssafy.motif.domain.core.user.User;
import com.ssafy.motif.domain.core.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마이페이지 수정 (API 명세 §4.2 …) — S15P21D104-282.
 *
 * <p><b>{@link UserQueryService} 와 나란한 별개 서비스다.</b> 그쪽은
 * {@code @Transactional(readOnly = true)} 로 선언돼 있고, 쓰기를 한 줄이라도 섞으면 그
 * 표시가 거짓말이 된다 — 읽기 전용이라고 믿고 나중에 누가 복제본 라우팅이나 플러시 모드를
 * 손대면 조용히 깨진다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UserCommandService {

    private final UserRepository userRepository;

    /**
     * §4.2 닉네임 변경.
     *
     * <p>형식 검증은 요청 DTO 가 끝냈다({@code @Nickname}). 여기서는 중복도 보지 않는다 —
     * {@code users.nickname} 에 UNIQUE 가 없다(C1 회의 확정). 그래서 실패 분기가 없고,
     * 같은 값으로 바꿔도 그대로 성공이다.
     *
     * <p><b>{@code mod_usr_seq} 를 본인 seq 로 기록한다.</b> 온보딩(§3.3)은 기록하지
     * 않는데, 규칙이 <b>"이 행을 마지막으로 수정한 주체. 최초 설정은 수정이 아니다"</b>
     * 이기 때문이다. 온보딩도 이미 있는 행을 UPDATE 하므로 {@code mod_dttm} 은 어차피
     * 찍힌다 — 그래서 시각만으로는 "온보딩에서 정해진 이름"과 "사용자가 바꾼 이름"이
     * 구분되지 않고, 이 칸의 NULL 여부가 그 둘을 가른다.
     */
    public NicknameUpdateResponse changeNickname(Integer userSeq, String nickname) {
        User user = userRepository.findById(userSeq)
                .orElseThrow(() -> new IllegalStateException(
                        "인증된 토큰의 사용자가 users 에 없다 — userSeq=" + userSeq));

        user.changeNickname(nickname);
        user.recordModifier(userSeq);

        return new NicknameUpdateResponse(user.getNickname());
    }
}
