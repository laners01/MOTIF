# §12 결제 (ORDER) — 상세 명세

공통 규약은 [`README.md`](README.md)에 있다. 이 문서는 **도메인 분기만** 적는다.

원본: [`MOTIF_API_명세서_v0_11.md`](../MOTIF_API_명세서_v0_11.md) §12 · DB 명세서 v0.15 §27.

| # | Method | Path | 인증 | 비고 |
|---|---|---|---|---|
| 12.1 | POST | `/api/v1/orders` | 필요 | 주문 생성(가격 스냅샷) |
| 12.2 | POST | `/api/v1/orders/{orderUuid}/confirm` | 필요 | 결제 승인 |
| 12.3 | POST | `/api/v1/orders/{orderUuid}/cancel` | 필요 | 승인 전 취소 |

## 토스 승인형이며 웹훅이 없다

결제 흐름은 **클라이언트 주도**다. 토스가 서버로 웹훅을 보내지 않으므로, 승인은 클라이언트가 성공 콜백을 받아 §12.2 를 호출해야 완료된다.

```
①  POST /orders                    서버가 가격을 스냅샷하고 orderUuid 발급
②  토스 결제 위젯                    클라이언트가 orderUuid·amount 로 위젯 실행
③  POST /orders/{uuid}/confirm     성공 콜백 파라미터를 그대로 전달 → 서버가 토스 승인 호출
```

**주문 식별자만 UUID 다.** 토스에 넘기는 `orderId` 로 쓰이는 외부 잔존 식별자라 숫자 PK 를 노출하지 않는다(§1.1).

**금액은 서버가 정한다.** 클라이언트가 보낸 금액을 믿지 않으며, §12.2 에서 스냅샷과 대조한다.

---

## 12.1 POST /api/v1/orders — 주문 생성

### 요청 — 에셋 구매

```json
{ "productType": "ASSET", "assetSeq": 21 }
```

### 요청 — 패스 구매

```json
{ "productType": "PASS", "artistSeq": 3, "passDurationMonths": 1 }
```

| 필드 | 필수 | 설명 |
|---|:---:|---|
| `productType` | Y | `ASSET` \| `PASS` |
| `assetSeq` | ASSET 일 때 | 대상 에셋 |
| `artistSeq` | PASS 일 때 | 대상 아티스트 |
| `passDurationMonths` | PASS 일 때 | 개월 수(§11.4 `prices[].durationMonths` 중 하나) |

DB CHECK(`ck_orders_product_shape`)가 두 형태를 강제한다 — ASSET 이면 `artist_seq`·`pass_duration_months` 가 NULL 이어야 하고 그 반대도 같다.

**요청도 같은 모양이어야 한다 (기본안).** ASSET 인데 `artistSeq`·`passDurationMonths` 가 오거나, PASS 인데 `assetSeq` 가 오거나, 형태의 필수 칸이 빠지면 400 `INVALID_PARAMETER` 이고 `data` 에 필드 오류 배열이 실린다(§1.2 검증 오류와 같은 봉투). 반대 형태의 칸을 무시하지 않고 거절한다 — 무시하면 클라이언트 버그가 조용히 통과해 다른 상품이 결제된다.

```json
{
  "result": "FAIL",
  "code": "INVALID_PARAMETER",
  "message": "요청 값이 올바르지 않습니다.",
  "data": [
    { "field": "artistSeq", "message": "ASSET 주문에는 보내지 않습니다." }
  ],
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

### 성공 응답 (201) — ASSET

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "orderUuid": "e3b0c442-98fc-4c14-a1b2-3d4e5f678901",
    "orderName": "카리나 모티프 에셋",
    "amount": "3900.00",
    "currency": "KRW",
    "expiresDttm": "2026-09-10T00:30:00.000Z"
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

### 성공 응답 (201) — PASS

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "orderUuid": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "orderName": "카리나 모티프 패스 1개월",
    "amount": "9900.00",
    "currency": "KRW",
    "expiresDttm": "2026-09-10T00:30:00.000Z"
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

이 값들이 **토스 결제 위젯 파라미터**다. `amount` 는 문자열이며(`NUMERIC(10,2)`), 위젯에 넘길 때도 이 값을 그대로 쓴다.

**주문 유효 시간은 30분**이다. 지나면 배치가 `EXPIRED` 로 바꾼다.

### orderName *(기본안)*

`orders` 에 칼럼이 없어 응답에서만 만든다 — 토스 결제창과 영수증에 그대로 찍힌다. 아티스트명은 `users.nickname` 이다.

| productType | 형식 | 예 |
|---|---|---|
| `ASSET` | `{아티스트명} 모티프 에셋` | 카리나 모티프 에셋 |
| `PASS` | `{아티스트명} 모티프 패스 {n}개월` | 카리나 모티프 패스 1개월 |

### 서버 처리

1. 대상이 존재하고 판매 가능한지 확인
2. **KR 활성 가격 행을 조회해 `amount`·`currency`·`countryCode`(+`passDuration`)를 스냅샷**한다 — 가격 행에 FK 를 걸지 않고 값을 복사한다. 나중에 가격이 바뀌어도 이미 만든 주문의 금액은 변하지 않는다
3. `orders` 를 `PENDING` 으로 생성

**판정 순서는 실패표 순서다 (기본안)** — 존재(404) → 판매 가능(409 `PRODUCT_NOT_ON_SALE`) → 보유·패스(409). 판매 중단(`is_active=FALSE`) 에셋은 §11.2 상세와 달리 **숨기지 않고** 409 로 답한다 — 원본 실패표가 그렇게 못박았다.

| 단계 | ASSET | PASS |
|---|---|---|
| 존재 | `motif_assets` 행 없음 → 404 `ASSET_NOT_FOUND` | `artists` 행 없음 또는 `is_deleted=TRUE` → 404 `ARTIST_NOT_FOUND` |
| 판매 가능 | `is_active=FALSE` 또는 KR 활성 가격 없음 → 409 `PRODUCT_NOT_ON_SALE` | (아티스트, KR, `passDurationMonths`) 활성 가격 없음 → 409 `PRODUCT_NOT_ON_SALE` |
| 보유 | ACTIVE 개별 구매 → 409 `ALREADY_OWNED` | 유효 패스 → 409 `PASS_ALREADY_ACTIVE` |
| 패스 | 유효 패스 + `PREMIUM` 아님 → 409 `COVERED_BY_PASS` | — |
| 저장 | `PENDING` · `asset_seq` · `country_code='KR'` · 금액·통화 스냅샷 · `expires_dttm = now + 30분` | `PENDING` · `artist_seq` · `pass_duration_months` · 같은 스냅샷 |

- ASSET 의 보유·패스 판정은 §11 `myAccess` 규칙 그대로다 — `OWNED > PASS > NONE`, `PREMIUM` 은 패스로 열리지 않고 `SECRET` 은 `NORMAL` 과 같다. 개별 구매와 패스가 둘 다 있으면 `ALREADY_OWNED` 다.
- 유효 패스는 `status='ACTIVE' AND expires_dttm > now()` 를 **DB 시각**으로 판정한다(§11.4 와 같은 조회). 만료·환불된 패스는 잡히지 않으므로 재구매가 열린다.
- 같은 상품의 `PENDING` 주문이 이미 있어도 막지 않는다 — 시도마다 새 행이다(DB §33). 위젯 이탈 후 다시 누르는 것이 정상 경로다.
- `payment_provider` 는 생성 시 NULL 이다. 승인(§12.2)이 `TOSS` 와 `payment_key` 를 함께 기록한다.

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 404 | `ASSET_NOT_FOUND` / `ARTIST_NOT_FOUND` | 대상 없음 |
| 409 | `PRODUCT_NOT_ON_SALE` | `is_active=FALSE` 또는 해당 국가의 활성 가격 없음 |
| 409 | `ALREADY_OWNED` | 이미 ACTIVE 구매 보유(ASSET) |
| 409 | `COVERED_BY_PASS` | **유효 패스로 이미 이용 가능한 에셋** |
| 409 | `PASS_ALREADY_ACTIVE` | 기존 ACTIVE 패스 존재(PASS) — 만료·환불 후 재구매는 허용 |

```json
{
  "result": "FAIL",
  "code": "COVERED_BY_PASS",
  "message": "패스로 이용 중인 에셋입니다.",
  "data": null,
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

`COVERED_BY_PASS` 는 "패스 구독 중이면 구매된 상태와 같이 취급한다"는 정책의 결과다. **단 `PREMIUM` 에셋은 패스로 열리지 않으므로 이 코드가 나오지 않는다**(§11 권한 표).

### 구현 메모 (S15P21D104-263)

- `Order` 엔티티·`OrderRepository` 는 `domain-core/.../order/` 에 새로 만들었다. 정적 팩토리 `forAsset`·`forPass` 만 있고 **상태 전이 메서드는 없다** — 승인·취소·만료가 각자의 티켓(§12.2·§12.3)에서 붙인다.
- 판정은 기존 조회를 재사용한다 — `MotifAssetPriceRepository.findByAssetSeqAndCountrySeq`, `MotifPassPriceRepository.findByArtistSeqAndCountrySeqAndDurationMonths`·`findActivePassExpiry`, `ArtistRepository.findByArtistSeqAndDeletedFalse`·`findNames`. 새로 더한 것은 `MotifAssetRepository.existsActivePurchase` 하나다(`asset_purchases` 엔티티는 권한 행을 만드는 §12.2 가 만든다).
- 보유·패스 판정은 `MyAccess.of` 를 그대로 부른다 — §11.2 상세가 `PASS` 라 답한 에셋의 주문이 여기서 열리는 일이 없다.
- 국가는 `OrderCommandService` 자체 상수 `KR` 이다(§11.2·§11.4 서비스와 같은 방식, 공유하지 않음). `orders.country_code` 에 그대로 스냅샷된다.
- 형태 검증은 `OrderCreateRequest.shapeErrors()` 가 필드 오류 목록을 만들고 서비스가 `INVALID_PARAMETER` 의 `data` 로 던진다.

---

## 12.2 POST /api/v1/orders/{orderUuid}/confirm — 결제 승인

토스 `successUrl` 콜백 파라미터를 **그대로** 전달한다.

### 요청

```json
{
  "paymentKey": "toss-payment-key",
  "orderId": "e3b0c442-98fc-4c14-a1b2-3d4e5f678901",
  "amount": "3900.00"
}
```

`orderId` 는 경로의 `orderUuid` 와 같은 값이다(토스가 돌려준 것을 그대로 싣는다).

### 서버 처리 순서 — 전부 필수

1. `orderId = orderUuid` 로 **본인 주문**을 조회하고, **`amount` 를 `BigDecimal.compareTo` 로 대조**한다. 불일치면 **토스 승인을 호출하지 않고** 400 `AMOUNT_MISMATCH`
2. 판매 가능 상태를 재검증한다. 중단됐으면 `PENDING → EXPIRED` 로 바꾸고 409 `PRODUCT_NOT_ON_SALE`
3. **토스 승인 API 호출.** 실패면 `FAILED`(+`fail_reason`) 기록
4. 조건부 UPDATE — `status='PAID' WHERE status='PENDING' AND expires_dttm > now()`. **영향 행이 0이면** 만료·중복으로 판정
5. **같은 트랜잭션**에서 권한을 만든다 — `asset_purchases(ACTIVE)` 생성, 또는 `motif_passes` 생성/재활성(`started=now`, `expires=now+n개월`, `canceled_dttm=NULL`)

1번의 금액 대조는 **컷 불가**다. 문자열이나 `equals` 로 비교하면 `3900` 과 `3900.00` 이 다르게 판정되므로 `compareTo` 를 쓴다.

4번의 조건부 UPDATE 가 **중복 승인 방어**다 — 같은 주문으로 두 번 들어와도 두 번째는 0행이라 권한이 두 번 생기지 않는다.

### 성공 응답 (200) — PASS

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "orderUuid": "e3b0c442-98fc-4c14-a1b2-3d4e5f678901",
    "status": "PAID",
    "productType": "PASS",
    "pass": { "expiresDttm": "2026-10-02T06:00:00.000Z" }
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

### 성공 응답 (200) — ASSET

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "orderUuid": "e3b0c442-98fc-4c14-a1b2-3d4e5f678901",
    "status": "PAID",
    "productType": "ASSET",
    "asset": { "assetSeq": 21 }
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

`productType` 에 따라 `pass` 또는 `asset` 중 **하나만** 실린다.

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 400 | `AMOUNT_MISMATCH` | 콜백 `amount` ≠ 주문 스냅샷 — **승인 호출 전에 차단** |
| 402 | `PAYMENT_FAILED` | 토스 승인 거절. `data` 에 토스 실패 사유 |
| 404 | `ORDER_NOT_FOUND` | 주문 없음·타인 주문 |
| 409 | `ORDER_EXPIRED` | 30분 만료(조건부 UPDATE 0행) |
| 409 | `ORDER_ALREADY_PAID` | 중복 승인 시도 |
| 409 | `PRODUCT_NOT_ON_SALE` | 승인 직전 판매 중단 감지 |

```json
{
  "result": "FAIL",
  "code": "PAYMENT_FAILED",
  "message": "결제 승인에 실패했습니다.",
  "data": "toss: REJECT_CARD_COMPANY",
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

**재시도는 새 주문**이다(§12.1 부터 다시). 실패한 주문을 다시 승인할 수 없다.

---

## 12.3 POST /api/v1/orders/{orderUuid}/cancel — 승인 전 취소

사용자가 결제 위젯에서 이탈했을 때 클라이언트가 호출한다. `PENDING → CANCELED` 이며 **본인 주문만** 가능하다.

### 성공 응답 (200)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": { "orderUuid": "e3b0c442-98fc-4c14-a1b2-3d4e5f678901", "status": "CANCELED" },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

호출하지 않고 방치해도 **30분 후 배치가 `EXPIRED`** 로 바꾼다 — 기능상 차이는 없고 상태 표기만 다르다.

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 404 | `ORDER_NOT_FOUND` | 주문 없음·타인 주문 |
| 409 | `ORDER_NOT_CANCELABLE` | `PENDING` 이 아님(`PAID`·`EXPIRED` 등) |

---

## 환불은 이 문서에 없다

`PAID → REFUNDED` 전이와 그에 따른 권한 종료(`asset_purchases.status='REFUNDED'`, `motif_passes.status='REFUNDED'`)는 **어드민 API 범위**다.
