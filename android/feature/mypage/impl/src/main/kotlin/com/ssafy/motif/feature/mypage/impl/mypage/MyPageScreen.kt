package com.ssafy.motif.feature.mypage.impl.mypage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.component.Tab
import com.ssafy.motif.core.designsystem.component.Text
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.motif.core.designsystem.component.LoadingWheel
import com.ssafy.motif.core.designsystem.component.Surface
import com.ssafy.motif.core.designsystem.component.TabRow
import com.ssafy.motif.core.designsystem.component.TabRowDefaults
import com.ssafy.motif.core.designsystem.component.TabRowDefaults.tabIndicatorOffset
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.feature.mypage.impl.mypage.component.MyProfileSection
import com.ssafy.motif.feature.mypage.impl.mypage.component.SubscribedArtistCard
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.items
import com.ssafy.motif.core.ui.challenges.ChallengeThumbnail
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import com.ssafy.motif.core.designsystem.component.Button
import com.ssafy.motif.core.designsystem.component.MotifButtonDefaults

@Composable
fun MyPageScreen(
    modifier: Modifier = Modifier,
    viewModel: MyPageViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MyPageScreen(
        uiState = uiState,
        onNotificationClick = viewModel::notificationClick,
        onSettingsClick = viewModel::settingClick,
        onTabClick = viewModel::selectTab,
        onLoadMoreChallenges = viewModel::onLoadMoreChallenges,
        onCreateClick = viewModel::onCreateClick,
        onArtistCardClick = viewModel::onArtistCardClick,
        modifier = modifier,
    )
}
@Composable
internal fun MyPageScreen(
    uiState: MyPageUiState,
    onNotificationClick:() -> Unit,
    onSettingsClick:() -> Unit,
    onTabClick:(MyPageTab) -> Unit,
    onLoadMoreChallenges:() -> Unit,
    onCreateClick:() -> Unit,
    onArtistCardClick:(Long) -> Unit,
    modifier: Modifier = Modifier,
) {

    val listState = rememberLazyListState()

    LaunchedEffect(uiState.selectedTab) {
        listState.scrollToItem(0)
    }

    val challengeSuccess = uiState.challengeState as? MyChallengeUiState.Success
    val canLoadMore = challengeSuccess != null &&
            uiState.selectedTab == MyPageTab.Challenge &&
            challengeSuccess.hasNext &&
            challengeSuccess.appendState is MyChallengeAppendUiState.Idle

    val shouldLoadMore by remember(listState, canLoadMore) {
        derivedStateOf {
            if (!canLoadMore) return@derivedStateOf false

            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index
                ?: return@derivedStateOf false

            layoutInfo.totalItemsCount > 0 &&
                    lastVisible >= layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMoreChallenges()
        }
    }
    Surface(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            when (val profileState = uiState.profileState) {
                ProfileUiState.Loading -> {
                    item{ProfileLoading()}
                }

                is ProfileUiState.Success -> {
                    item{
                        MyProfileSection(profile = profileState.profile,
                            onNotificationClick = onNotificationClick,
                            onSettingsClick = onSettingsClick,)
                    }
                }

                is ProfileUiState.Error -> {
                    item{}
                }
            }
            stickyHeader{
                TabRow(
                    selectedTabIndex = uiState.selectedTab.ordinal,
                    containerColor = MotifTheme.colors.background,
                    divider = {},
                    indicator = { tabPositions ->
                        if(uiState.selectedTab.ordinal < tabPositions.size){
                            TabRowDefaults.Indicator(
                                modifier = Modifier
                                    .tabIndicatorOffset(tabPositions[uiState.selectedTab.ordinal],)
                                    .height(2.dp)
                                    .width(50.dp),
                                color = MotifTheme.colors.pink
                            )
                        }  },
                ) {
                    MyPageTab.entries.forEach { tab ->
                        Tab(
                            selected = uiState.selectedTab == tab,
                            onClick = { onTabClick(tab) },
                            selectedContentColor = MotifTheme.colors.typo,
                            unselectedContentColor = MotifTheme.colors.gray400,
                        ) {
                            Text(
                                text = tab.label,
                                style = MotifTheme.typography.bodyR16,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    }
                }
            }
            when (uiState.selectedTab) {
                MyPageTab.Challenge -> {
                    when (val state = uiState.challengeState) {
                        MyChallengeUiState.Loading -> { item { ChallengeLoadingContent() } }

                        is MyChallengeUiState.Success -> {
                            if(state.challenges.isEmpty()){
                                item {
                                    ChallengeEmptyContent(
                                        onCreateClick = onCreateClick,
                                        // 프로필·탭을 뺀 남은 영역의 가운데에 배치
                                        modifier = Modifier.fillParentMaxHeight(0.7f),
                                    )
                                }
                            } else {
                                items(
                                    items = state.challenges.chunked(3),
                                    key = { row -> row.first().id },
                                ) { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        row.forEach { challenge ->
                                            ChallengeThumbnail(
                                                thumbnailUrl = challenge.thumbnailUrl,
                                                viewCount = challenge.view,
                                                onClick = { },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                        repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                                    }
                                }

                                if (state.appendState is MyChallengeAppendUiState.Loading) {
                                    item { ChallengeLoadingMore() }
                                }
                            }
                        }



                        is MyChallengeUiState.Error -> { item { } }
                    }
                 }
                MyPageTab.Artist -> {
                    when (val state = uiState.artistState) {
                        MyArtistUiState.Loading -> { item { ChallengeLoadingContent() } }

                        is MyArtistUiState.Success -> {
                            items(
                                items = state.artists.chunked(2),
                                key = { row -> row.first().id },
                            ) { row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp)
                                        .padding(bottom = 18.dp),
                                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                                ) {
                                    row.forEach { artist ->
                                        SubscribedArtistCard(
                                            artist = artist,
                                            isFlipped = uiState.flippedArtistId == artist.id,
                                            onClick = { onArtistCardClick(artist.id) },
                                            onSeeMoreClick = { },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    repeat(2 - row.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        is MyArtistUiState.Error -> { item { } }
                    }
                }
                MyPageTab.Liked -> { item { } }    // 모델 없음
            }

        }




    }
}
@Composable
private fun ProfileLoading(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        LoadingWheel()
    }
}
@Composable
private fun ChallengeLoadingContent(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 80.dp),
        contentAlignment = Alignment.Center,
    ) {
        LoadingWheel()
    }
}
@Composable
private fun ChallengeLoadingMore(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        LoadingWheel()
    }
}

@Composable
private fun ChallengeEmptyContent(
    onCreateClick: () -> Unit,
    modifier: Modifier=Modifier,
){
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
    ){
        Text(
            text ="아직 참여한 챌린지가 없어요. \n 마음에 드는 챌린지를 찾아 참여해보세요!",
            style = MotifTheme.typography.titleR20,
            color = MotifTheme.colors.typo,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onCreateClick,
            shape = RoundedCornerShape(4.dp),
            colors = MotifButtonDefaults.buttonColors(
                containerColor = MotifTheme.colors.pink,
                contentColor = MotifTheme.colors.typo,
            ),
        ){
            Text(
                text = "챌린지 둘러보기",
                style = MotifTheme.typography.bodySB16,
            )
        }
    }
}
private const val LOAD_MORE_THRESHOLD = 3