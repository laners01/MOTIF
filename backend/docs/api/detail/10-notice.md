# §10 공지 (NOTICE) — 상세 명세

공통 규약은 [`README.md`](README.md)에 있다.

원본: [`MOTIF_API_명세서_v0_11.md`](../MOTIF_API_명세서_v0_11.md) §10 · DB 명세서 v0.15 §22·§23.

| # | Method | Path | 인증 | 비고 |
|---|---|---|---|---|
| 10.1 | — | (삭제) 공지 상세 | — | §5.5 목록 항목이 대신한다 |
| 10.2 | POST · DELETE | `/api/v1/notices/{noticeSeq}/like` | 필요 | **204 No Content** |
| 10.3 | — | 댓글 | 필요 | §9 공통 댓글(`type=notice`) — [`09-comment.md`](09-comment.md) 참조 |

## 공지 조회 API 는 하나뿐이다

**§5.5 `GET /artists/{artistSeq}/notices`** 가 전부다 — [`05-artist.md`](05-artist.md) 참조.

구 §10.1 공지 상세는 **삭제됐다**(v0.8). 공지 화면에 필요한 것(본문 전체·이미지 전체·좋아요 수·댓글 수·`isLiked`)을 목록 항목이 모두 갖기 때문에 단건 조회가 불필요하다. 홈에서 진입할 때는 §6.3 활동 피드의 `NOTICE` 항목이 같은 데이터를 준다.

따라서 공지 화면의 진입 데이터 출처는 둘이다.

| 진입 경로 | 데이터 출처 |
|---|---|
| 아티스트 상세 → 공지 탭 | §5.5 목록 항목 |
| 홈 → 구독 아티스트 활동 | §6.3 `NOTICE` 항목 |

두 항목의 필드 구성은 같다.

---

## 10.2 POST · DELETE /api/v1/notices/{noticeSeq}/like — 좋아요 토글

**성공은 `204 No Content`**, 본문 없음(§1.2 예외).

### 성공 응답

```http
HTTP/1.1 204 No Content
```

**`isLiked`·`likeCount` 를 돌려주지 않는다.** 클라이언트는 낙관적으로 하트를 반전하고 카운트를 ±1 한 뒤, 실패하면 롤백한다. 화면 진입 시의 초기 상태는 §5.5·§6.3 의 `isLiked` 로 받는다.

### 서버 처리

`notice_likes` 행 생성/삭제 + `notices.like_count` **원자적 증감**을 같은 트랜잭션에서 한다. **멱등하다** — 이미 누른 상태로 POST 하거나 누르지 않은 상태로 DELETE 해도 204 이고 카운터는 변하지 않는다. 해제는 하드 삭제다(복합 PK 토글 테이블).

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 404 | `NOTICE_NOT_FOUND` | 미존재·삭제 공지 |

```json
{
  "result": "FAIL",
  "code": "NOTICE_NOT_FOUND",
  "message": "존재하지 않는 공지입니다.",
  "data": null,
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

실패일 때는 봉투를 반환한다 — 204 예외는 **성공에만** 적용된다.
