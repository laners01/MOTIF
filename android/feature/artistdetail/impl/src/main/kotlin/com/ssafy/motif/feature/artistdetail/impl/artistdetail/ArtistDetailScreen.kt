package com.ssafy.motif.feature.artistdetail.impl.artistdetail

import android.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.motif.core.designsystem.component.Surface
import com.ssafy.motif.core.designsystem.component.Tab
import com.ssafy.motif.core.designsystem.component.TabRow
import com.ssafy.motif.core.designsystem.component.TabRowDefaults
import com.ssafy.motif.core.designsystem.component.TabRowDefaults.tabIndicatorOffset
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.ui.notice.NoticeCard
import com.ssafy.motif.feature.artistdetail.impl.artistdetail.component.ArtistDetailCard

@Composable
fun ArtistDetailScreen(
    artistId: Long,
    modifier: Modifier = Modifier,
    viewModel: ArtistDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ArtistDetailScreen(
        uiState = uiState,
        onSubscribeClick = viewModel::toggleSubscribe,
        onAlarmClick = viewModel::toggleAlarm,
        onBackClick = viewModel::goBackScreen,
        onNoticeLikeClick = viewModel::toggleNoticeLike,
        onNoticeCommentClick = viewModel::onNoticeCommentClick,
        onTabClick = viewModel::selectTab,
        modifier = modifier,
    )
}

@Composable
internal fun ArtistDetailScreen(
    uiState: ArtistDetailUiState,
    onSubscribeClick: () -> Unit,   //구독버튼 클릭시
    onAlarmClick:() -> Unit,        //알람버튼 클릭시
    onBackClick:() -> Unit,         //뒤로가기 클릭시
    onNoticeLikeClick:(Long) -> Unit,         //좋아요 버튼
    onNoticeCommentClick:(Long) -> Unit,      //댓글 아이콘 클릭
    onTabClick:(ArtistDetailTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            uiState.artistDetail?.let{ artistDetail ->
                item{
                    ArtistDetailCard(
                        artistDetail = artistDetail,
                        isAlarmOn = artistDetail.isAlarmOn,
                        isSubscribed = artistDetail.isSubscribed,
                        onSubscribeClick = onSubscribeClick,
                        onAlarmClick = onAlarmClick,
                        onBackClick = onBackClick,
                    )
                }
            }
            item{
                TabRow(selectedTabIndex = uiState.selectedTab.ordinal,
                    divider = {},
                    indicator = {tabPositions ->
                        if(uiState.selectedTab.ordinal < tabPositions.size){
                            TabRowDefaults.Indicator(
                                modifier = Modifier
                                    .tabIndicatorOffset(tabPositions[uiState.selectedTab.ordinal],)
                                    .height(2.dp)
                                    .width(50.dp),
                                color = MotifTheme.colors.pink
                            )
                        }

                    }) {
                    ArtistDetailTab.entries.forEach { tab ->
                        Tab(
                           selected = uiState.selectedTab == tab,
                            onClick = {onTabClick(tab)},
                            selectedContentColor = MotifTheme.colors.typo,
                            unselectedContentColor = MotifTheme.colors.gray400,
                        ){
                            Text(
                                text = tab.label,
                                style = MotifTheme.typography.bodyR16,
                                modifier = Modifier.padding(top = 24.dp,bottom = 26.dp)
                            )
                        }
                    }
                }

            }


            when (uiState.selectedTab) {
                ArtistDetailTab.Post -> {
                    items(
                        items = uiState.notices,
                        key = {it.id},
                    ){ notice ->
                        NoticeCard(
                            notice = notice,
                            onArtistClick = {},
                            onLikeClick = onNoticeLikeClick,
                            onCommentClick = onNoticeCommentClick,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                }
            }
                ArtistDetailTab.Challenge -> {
                    item {

                    }
                }
            }
        }
    }
}
