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

`orderId` 는 경로의 `orderUuid` 와 같은 값이다(토스가 돌려준 것을 그대로 싣는다). **다르면 400 `INVALID_PARAMETER` 이고 `data` 에 `orderId` 필드 오류가 실린다 (기본안).** `amount` 가 숫자가 아니면 같은 400 에 `amount` 필드 오류다. 셋 다 필수이며 `paymentKey` 는 200자까지다(토스 계약).

### 서버 처리 순서 — 전부 필수

1. `orderId = orderUuid` 로 **본인 주문**을 조회하고, **`amount` 를 `BigDecimal.compareTo` 로 대조**한다. 불일치면 **토스 승인을 호출하지 않고** 400 `AMOUNT_MISMATCH`
2. 판매 가능 상태를 재검증한다. 중단됐으면 `PENDING → EXPIRED` 로 바꾸고 409 `PRODUCT_NOT_ON_SALE`
3. **토스 승인 API 호출.** 실패면 `FAILED`(+`fail_reason`) 기록
4. 조건부 UPDATE — `status='PAID' WHERE status='PENDING' AND expires_dttm > now()`. **영향 행이 0이면** 만료·중복으로 판정
5. **같은 트랜잭션**에서 권한을 만든다 — `asset_purchases(ACTIVE)` 생성, 또는 `motif_passes` 생성/재활성(`started=now`, `expires=now+n개월`, `canceled_dttm=NULL`)

1번의 금액 대조는 **컷 불가**다. 문자열이나 `equals` 로 비교하면 `3900` 과 `3900.00` 이 다르게 판정되므로 `compareTo` 를 쓴다.

4번의 조건부 UPDATE 가 **중복 승인 방어**다 — 같은 주문으로 두 번 들어와도 두 번째는 0행이라 권한이 두 번 생기지 않는다.

### 구현 순서 *(기본안 — 조건부 UPDATE 를 토스 호출 앞에 둔다)*

위 순서에서 **4(조건부 UPDATE)가 3(토스 승인)보다 앞**이다. 같은 트랜잭션 안이라 커밋 전에는 밖에서 `PAID` 가 보이지 않고, UPDATE 가 잡은 행 잠금 덕에 같은 주문의 동시 승인이 직렬화되어 두 번째는 토스를 부르지 않고 409 로 끝난다. 원본 순서(승인 → UPDATE)로 하면 토스 승인 **뒤에** 만료·중복이 드러나 돈은 빠졌는데 권한이 없는 창이 생긴다. 토스가 거절하면 트랜잭션이 통째로 되돌아가 `PAID` 도 사라진다.

| 단계 | 하는 일 | 실패 |
|---|---|---|
| 0 | 회원 단위 잠금(`pg_advisory_xact_lock`) — 같은 회원의 승인을 한 줄로 세운다 | — |
| 1 | 본인 주문 조회 | 404 `ORDER_NOT_FOUND` (없음·타인 구분 없음) |
| 2 | `orderId`·`amount` 대조 | 400 `INVALID_PARAMETER` / 400 `AMOUNT_MISMATCH` |
| 3 | 상태 — `PAID`·`REFUNDED` / 종료 상태(`EXPIRED`·`CANCELED`·`FAILED`) / 기간 지난 `PENDING`(→ `EXPIRED` 전이) | 409 `ORDER_ALREADY_PAID` / 409 `ORDER_EXPIRED` / 409 `ORDER_EXPIRED` |
| 4 | 판매 가능 재검증 — §12.1 과 같은 판정. 실패면 `PENDING → EXPIRED` | 409 `PRODUCT_NOT_ON_SALE` · `ALREADY_OWNED` · `COVERED_BY_PASS` · `PASS_ALREADY_ACTIVE` |
| 5 | 조건부 UPDATE `PENDING → PAID` (`payment_provider='TOSS'`, `payment_key`, `approved_dttm=now()`) | 0행 → 다시 읽어 `PAID` 면 409 `ORDER_ALREADY_PAID`, 아니면 `EXPIRED` 전이 + 409 `ORDER_EXPIRED` |
| 6 | 토스 승인 API — `POST /v1/payments/confirm`, Basic 인증, KRW 정수 금액 | 확정 거절 → 롤백 후 별도 트랜잭션에서 `FAILED`(+`fail_reason`, `payment_key`) → 402 `PAYMENT_FAILED` |
| 7 | 권한 생성 — ASSET `asset_purchases(ACTIVE)` / PASS `motif_passes` 발급 또는 **같은 행 재활성** | — |

- 종료 상태 주문의 승인 시도가 전부 `ORDER_EXPIRED` 인 것은 **기본안**이다 — §15 에 더 맞는 코드가 없어 새 코드를 만들지 않았다.
- 재검증(4)에 보유·패스 판정이 들어가는 것도 **기본안**이다. 원본은 "판매 가능 상태"만 적었지만, 12.1 이 같은 상품의 `PENDING` 주문을 여러 개 허용하므로 두 주문이 연달아 승인되면 `uq_asset_purchases_active` 에서 터진다. 재검증에서 존재 코드(아티스트 삭제 등)는 `PRODUCT_NOT_ON_SALE` 로 답한다 — 주문은 있는데 상품이 사라진 것이다.
- **결과 불명(타임아웃·5xx·우리 키 오류·토스 일시 오류 `PROVIDER_ERROR`)은 `FAILED` 로 기록하지 않는다.** 롤백 뒤 500 `INTERNAL_ERROR` 이고 주문은 `PENDING` 그대로다. 클라이언트가 같은 `paymentKey` 로 다시 부르면 토스가 멱등하게 답한다.
- 토스가 `ALREADY_PROCESSED_PAYMENT` 를 주면 결제 조회(`GET /v1/payments/{paymentKey}`)로 `orderId`·금액·`DONE` 을 확인한 뒤 승인으로 간주한다 — 앞선 시도가 승인 뒤 끊긴 경우의 복구 경로다. 이 경로가 없으면 돈은 빠졌는데 402 가 나간다.
- PASS 만료 `now + n개월` 은 **KST 달력**으로 더한다 — UTC 로 세면 월말 보정이 하루 어긋나는 날이 있다. 재활성은 `started·expires·duration_months·order_seq` 를 갈고 `status=ACTIVE`, `canceled_dttm=NULL` 로 되돌린다.
- 토스에는 금액을 **정수**로 보낸다(`3900.00` → `3900`). KRW 는 소수가 없다.

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

`productType` 에 따라 `pass` 또는 `asset` 중 **하나만** 실린다 — 다른 쪽 키는 응답에 없다(§6.3 과 같은 방식). `pass.expiresDttm` 은 새 이용 기간의 만료다.

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 400 | `AMOUNT_MISMATCH` | 콜백 `amount` ≠ 주문 스냅샷 — **승인 호출 전에 차단** |
| 402 | `PAYMENT_FAILED` | 토스 승인 거절. `data` 에 토스 실패 사유 |
| 404 | `ORDER_NOT_FOUND` | 주문 없음·타인 주문 |
| 409 | `ORDER_EXPIRED` | 30분 만료(조건부 UPDATE 0행) · 종료 상태(`EXPIRED`·`CANCELED`·`FAILED`) 주문의 승인 시도 *(기본안)* |
| 409 | `ORDER_ALREADY_PAID` | 중복 승인 시도 |
| 409 | `PRODUCT_NOT_ON_SALE` | 승인 직전 판매 중단 감지 — 재검증의 `ALREADY_OWNED`·`COVERED_BY_PASS`·`PASS_ALREADY_ACTIVE` 도 같은 자리다. 주문은 `EXPIRED` 로 바뀐다 |

공통 400 `INVALID_PARAMETER` 는 필수값 누락 외에 `orderId` ≠ 경로 `orderUuid`, `amount` 가 숫자가 아닌 경우에도 난다 (기본안, 위 요청 절).

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

### 구현 메모 (S15P21D104-265)

- 서비스가 둘이다 — `OrderConfirmService`(파사드, 트랜잭션 없음)와 `OrderConfirmTransaction`(본체). 토스 거절은 본체의 트랜잭션을 되돌린 **뒤** 파사드가 새 트랜잭션으로 `FAILED` 를 쓴다. 한 빈에 두면 프록시를 타지 않아 두 트랜잭션을 가를 수 없다.
- 만료·판매 중단 거절은 `OrderRejectedException`(`noRollbackFor`) — `EXPIRED` 전이가 남아야 한다. 토스 거절은 `PaymentRejectedException`(롤백).
- 판정은 12.1 과 같은 `ProductAvailability` 다. 상태 전이는 전부 `OrderRepository` 의 네이티브 조건부 UPDATE(`markPaid`·`markExpired`·`markFailed`) — 명세의 SQL 을 그대로 옮기고 시각은 DB `now()` 다.
- 토스 클라이언트는 `domain/order/payment/` — `TossPaymentClient`(인터페이스, 테스트가 가짜로 바꾼다) + `RestClient` 구현. 설정은 `motif.payment.toss.*`, 시크릿은 `TOSS_SECRET_KEY` 환경변수만. 키가 비면 기동은 되고 승인만 500 이다.
- `AssetPurchase` 엔티티가 이 티켓에서 생겼고 `MotifPass` 에 발급·재활성이 붙었다. `MotifAssetRepository`·`MotifPassPriceRepository` 의 네이티브 보유·패스 조회는 옮기지 않았다(§4.7·§5.3·§11 코드를 건드림 — 별도 정리 이슈).
- 자동 테스트는 가짜 토스로 돈다. 실토스 샌드박스 연동은 키를 받은 뒤 별도 확인이다.

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

```json
{
  "result": "FAIL",
  "code": "ORDER_NOT_CANCELABLE",
  "message": "취소할 수 없는 주문입니다.",
  "data": null,
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

### 판정 *(기본안)*

- 요청 본문은 없다. 본인 주문 조회(404) 뒤 조건부 UPDATE `status='CANCELED' WHERE order_seq=? AND status='PENDING'` 하나가 판정이다 — 0행이면 409. 상태를 먼저 읽고 갈라 쓰지 않는다(읽는 시점과 쓰는 시점 사이에 승인이 끼어들 수 있다).
- **만료 시각은 보지 않는다.** 기간이 지났어도 배치 전이라 `PENDING` 이면 `CANCELED` 가 된다 — 기능상 차이가 없다.
- 이미 취소된 주문의 재취소도 409 `ORDER_NOT_CANCELABLE` 다 — `PENDING` 이 아닌 상태(`PAID`·`EXPIRED`·`CANCELED`·`FAILED`·`REFUNDED`)는 전부 같은 코드다.
- 승인(§12.2)이 같은 주문을 잠근 채 토스를 부르고 있으면 UPDATE 가 그 트랜잭션이 끝나기를 기다린다 — 승인이 커밋되면 409, 되돌아가 `PENDING` 으로 남았으면 취소된다. 둘이 동시에 이기는 길은 없다.
- 취소된 주문은 되살리지 않는다 — 다시 사려면 §12.1 부터 새 주문이다. 취소된 주문의 승인 시도는 409 `ORDER_EXPIRED` 다(§12.2 기본안).

### 구현 메모 (S15P21D104-265)

- `OrderCancelService` 하나다 — 본인 조회 + `OrderRepository.markCanceled`(네이티브 조건부 UPDATE). §12.2 와 달리 회원 잠금을 걸지 않는다 — 조건부 UPDATE 하나가 원자적이다.

---

## 환불은 이 문서에 없다

`PAID → REFUNDED` 전이와 그에 따른 권한 종료(`asset_purchases.status='REFUNDED'`, `motif_passes.status='REFUNDED'`)는 **어드민 API 범위**다.
