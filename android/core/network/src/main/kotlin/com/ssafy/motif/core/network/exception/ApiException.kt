package com.ssafy.motif.core.network.exception

import com.ssafy.motif.core.network.model.FailResponse
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * 서버가 내려준 실패 코드를 담은 예외. 상위 계층은 [code]로 분기한다.
 */
class ApiException(
    val status: Int,
    val code: String,
    override val message: String,
    override val cause: Throwable? = null,
) : RuntimeException(message, cause) {

    companion object {
        const val UNKNOWN_CODE = "UNKNOWN"
    }
}

/**
 * [block] 안에서 발생한 [HttpException]을 [ApiException]으로 바꿔 던진다.
 * 그 밖의 예외(네트워크 단절, 직렬화 실패 등)는 그대로 전파한다.
 */
internal suspend fun <T> runCatchingApi(
    json: Json,
    block: suspend () -> T,
): T =
    try {
        block()
    } catch (e: HttpException) {
        throw e.toApiException(json)
    }

private fun HttpException.toApiException(
    json: Json,
): ApiException {
    val fail =
        response()
            ?.errorBody()
            ?.string()
            ?.let { body ->
                runCatching { json.decodeFromString<FailResponse>(body) }.getOrNull()
            }

    return ApiException(
        status = code(),
        code = fail?.code ?: ApiException.UNKNOWN_CODE,
        message = fail?.message ?: message(),
        cause = this,
    )
}
