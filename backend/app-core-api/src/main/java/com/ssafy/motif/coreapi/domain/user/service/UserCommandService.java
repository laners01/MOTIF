package com.ssafy.motif.coreapi.domain.user.service;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.media.MediaErrorCode;
import com.ssafy.motif.coreapi.domain.user.dto.NicknameUpdateResponse;
import com.ssafy.motif.coreapi.domain.user.dto.ProfileImageUpdateResponse;
import com.ssafy.motif.coreapi.global.media.MediaObjectStore;
import com.ssafy.motif.coreapi.global.media.MediaUrlResolver;
import com.ssafy.motif.domain.core.user.User;
import com.ssafy.motif.domain.core.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마이페이지 수정 (API 명세 §4.2 · §4.4).
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
    private final ProfileImagePresignRegistry presignRegistry;
    private final MediaObjectStore objectStore;
    private final MediaUrlResolver mediaUrlResolver;

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
        User user = requireUser(userSeq);

        user.changeNickname(nickname);
        user.recordModifier(userSeq);

        return new NicknameUpdateResponse(user.getNickname());
    }

    /**
     * §4.4 프로필 이미지 반영 — presigned PUT 업로드를 <b>마친 뒤</b> 호출된다.
     *
     * <p><b>검사 순서가 중요하다.</b> 발급 기록 대조가 먼저고 S3 HEAD 가 나중이다.
     * 뒤집으면 남의 키를 보냈을 때 <b>오브젝트의 존재 여부가 응답 시간에 드러난다</b> —
     * 있으면 HEAD 왕복만큼 느리고 없으면 빠르다. 어차피 거부할 요청에 스토리지를
     * 두드리는 것도 아깝다.
     *
     * <p>{@code IMAGE_NOT_UPLOADED}(409)와 {@code FORBIDDEN}(403)을 합치지 않는 이유는
     * 둘이 사용자에게 다른 행동을 요구하기 때문이다 — 409 는 "다시 올려라", 403 은
     * "이 키는 네 것이 아니다" 다. 여기서 키의 존재는 비밀이 아니다. 프로필 이미지는
     * 공개 등급이고 §4.6 이 남의 것도 URL 로 내보낸다.
     *
     * <p><b>옛 이미지는 지우지 않는다.</b> 산출물에 1년 {@code immutable} 이 붙어 있어
     * S3 에서 지워도 엣지가 계속 서빙하고, 무효화까지 걸면 이 API 가 무거워진다.
     * 정리는 별도 배치 주제다.
     */
    public ProfileImageUpdateResponse changeProfileImage(Integer userSeq, String imageKey) {
        if (!presignRegistry.isIssuedTo(userSeq, imageKey)) {
            // 만료도 여기로 떨어진다 — 발급된 적 없는 것과 같게 본다
            throw new BusinessException(CommonErrorCode.FORBIDDEN,
                    "본인에게 발급된 키가 아니다: userSeq=%d key=%s".formatted(userSeq, imageKey));
        }
        if (!objectStore.exists(imageKey)) {
            throw new BusinessException(MediaErrorCode.IMAGE_NOT_UPLOADED,
                    "업로드되지 않은 키: " + imageKey);
        }

        User user = requireUser(userSeq);
        user.changeProfileImageKey(imageKey);
        user.recordModifier(userSeq);

        presignRegistry.forget(userSeq, imageKey);

        return new ProfileImageUpdateResponse(mediaUrlResolver.resolveProfile(user.getProfileImageKey()));
    }

    private User requireUser(Integer userSeq) {
        return userRepository.findById(userSeq)
                .orElseThrow(() -> new IllegalStateException(
                        "인증된 토큰의 사용자가 users 에 없다 — userSeq=" + userSeq));
    }
}
