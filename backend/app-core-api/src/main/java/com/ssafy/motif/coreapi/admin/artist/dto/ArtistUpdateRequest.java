package com.ssafy.motif.coreapi.admin.artist.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;

/**
 * 아티스트 수정 요청 (어드민 명세 §3.5.4) — 부분 수정.
 *
 * <p><b>record 가 아닌 이유</b> — {@code groupSeq} 는 "생략"과 "명시적 null"의 뜻이 다르다.
 * 생략은 소속 그대로, {@code null} 은 <b>솔로 전환</b>이다(§3.5.4). record 로 받으면 둘 다
 * 필드가 {@code null} 이라 구분할 수 없다. Jackson 이 <b>JSON 에 키가 있을 때만</b>
 * setter 를 부르는 점을 이용해 {@link #groupSeqPresent} 로 표시한다.
 *
 * <p>이 구분이 {@code ARTIST_COMPANY_LOCKED}(409)의 근거다 — 그룹에 속한 아티스트에게
 * {@code companySeq} 만 보내면(=그룹 키 생략) 소속사 단독 변경이고, 이건 그룹 변경
 * 흐름을 타야 한다(DB §9). {@code groupSeq: null} 과 함께 오면 솔로 전환이라 허용된다.
 */
@Getter
public class ArtistUpdateRequest {

    @Size(min = 1, max = 50)
    private String name;

    private String bio;

    private Integer companySeq;

    /** {@code null}(또는 생략) 은 "이미지 그대로" 다 — 교체할 때만 새 키를 보낸다. */
    private String profileImageKey;

    private String motifBgImageKey;

    private Integer groupSeq;

    /** JSON 에 {@code groupSeq} 키가 있었는가 — 생략과 명시적 {@code null} 을 가른다. */
    private boolean groupSeqPresent;

    public void setName(String name) {
        this.name = name;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public void setCompanySeq(Integer companySeq) {
        this.companySeq = companySeq;
    }

    public void setProfileImageKey(String profileImageKey) {
        this.profileImageKey = profileImageKey;
    }

    public void setMotifBgImageKey(String motifBgImageKey) {
        this.motifBgImageKey = motifBgImageKey;
    }

    public void setGroupSeq(Integer groupSeq) {
        this.groupSeq = groupSeq;
        this.groupSeqPresent = true;
    }
}
