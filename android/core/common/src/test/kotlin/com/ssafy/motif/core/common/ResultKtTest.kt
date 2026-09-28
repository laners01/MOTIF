package com.ssafy.motif.core.common

import app.cash.turbine.test
import com.ssafy.motif.core.common.result.Result
import com.ssafy.motif.core.common.result.asResult
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ResultKtTest {

    @Test
    fun result_catches_errors() = runTest {
        flow {
            emit(1)
            throw Exception("Test Done")
        }
            .asResult()
            .test {
                assertEquals(Result.Loading, awaitItem())
                assertEquals(Result.Success(1), awaitItem())

                when (val errorResult = awaitItem()) {
                    is Result.Error -> assertEquals(
                        "Test Done",
                        errorResult.exception.message,
                    )

                    Result.Loading,
                    is Result.Success,
                        -> throw IllegalStateException(
                        "The flow should have emitted an Error Result",
                    )
                }

                awaitComplete()
            }
    }
}