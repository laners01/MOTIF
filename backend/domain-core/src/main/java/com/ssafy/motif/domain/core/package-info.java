/**
 * motif_core 스키마 도메인 (엔티티·리포지토리·QueryDSL).
 *
 * <p>Phase 0 스캐폴드에는 코드가 없다 — 스키마 경계(모듈 의존 방향)만 고정한다.
 * app-notification은 이 모듈에 의존할 수 없다(컴파일 타임 강제, ADR-0004).
 * 알림 팬아웃의 subscriptions 읽기는 DB GRANT(런타임 2차 방어)로만 허용된다.
 */
package com.ssafy.motif.domain.core;
