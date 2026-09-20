package com.ssafy.motif.core.network.model

import kotlinx.serialization.Serializable

/**
 * 공통 실패 응답 봉투. 성공 응답인 [BaseResponse]와 달리 `data`가 비어 있을 수 있어
 * 별도 모델로 파싱한다. 모든 필드는 응답이 봉투 형태가 아닐 때를 대비해 nullable이다.
 */
@Serializable
data class FailResponse(
    val result: String? = null,
    val code: String? = null,
    val message: String? = null,
    val timestamp: String? = null,
)
