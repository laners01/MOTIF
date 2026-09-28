package com.ssafy.motif.feature.ranking.impl.ranking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.motif.core.common.result.Result
import com.ssafy.motif.core.common.result.asResult
import com.ssafy.motif.core.data.repository.ArtistRepository
import com.ssafy.motif.core.model.artist.ArtistFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RankingViewModel @Inject constructor(
    private val artistRepository: ArtistRepository,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(
            RankingUiState(),
        )

    internal val uiState: StateFlow<RankingUiState> =
        _uiState.asStateFlow()

    init {
        loadRanking()
    }

    fun updateQuery(
        query: String,
    ) {
        _uiState.update { state ->
            state.copy(
                query = query,
            )
        }
    }

    fun removeQuery() {
        _uiState.update { state ->
            state.copy(
                query = "",
            )
        }
    }

    fun retry() {
        if (_uiState.value.isLoading) {
            return
        }

        loadRanking()
    }

    fun loadMore() {
        val state =
            _uiState.value

        if (
            state.isLoading ||
            state.isLoadingMore ||
            !state.hasNext
        ) {
            return
        }

        loadRanking(
            page = state.nextPage,
            append = true,
        )
    }

    private fun loadRanking(
        page: Int = FIRST_PAGE,
        append: Boolean = false,
    ) {
        viewModelScope.launch {
            artistRepository
                .getArtistRanking(
                    filter = ArtistFilter.ALL,
                    page = page,
                    size = PAGE_SIZE,
                )
                .asResult()
                .collect { result ->
                    when (result) {
                        Result.Loading -> {
                            _uiState.update { state ->
                                if (append) {
                                    state.copy(
                                        isLoadingMore = true,
                                    )
                                } else {
                                    state.copy(
                                        isLoading = true,
                                        isError = false,
                                    )
                                }
                            }
                        }

                        is Result.Success -> {
                            val pageResult =
                                result.data

                            _uiState.update { state ->
                                state.copy(
                                    ranking =
                                        if (append) {
                                            (
                                                    state.ranking +
                                                            pageResult.items
                                                    ).distinctBy { ranking ->
                                                    ranking.artist.id
                                                }
                                        } else {
                                            pageResult.items
                                        },
                                    isLoading = false,
                                    isLoadingMore = false,
                                    hasNext = pageResult.hasNext,
                                    nextPage = pageResult.page + 1,
                                    isError = false,
                                )
                            }
                        }

                        is Result.Error -> {
                            _uiState.update { state ->
                                if (append) {
                                    state.copy(
                                        isLoadingMore = false,
                                    )
                                } else {
                                    state.copy(
                                        isLoading = false,
                                        isError = true,
                                    )
                                }
                            }
                        }
                    }
                }
        }
    }

    private companion object {
        const val FIRST_PAGE = 0
        const val PAGE_SIZE = 100
    }
}