package com.ssafy.motif.core.navigation

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator

/**
 * 화면 회전이나 앱 프로세스 재생성 이후에도 기존 화면 이동 상태가 복원될 수 있도록 Navigation 상태를 생성한다.
 */
@Composable
fun rememberNavigationState(
    startKey: NavKey,
    topLevelKeys: Set<NavKey>,
): NavigationState {
    val topLevelStack = rememberNavBackStack(startKey)
    val subStacks = topLevelKeys.associateWith { key -> rememberNavBackStack(key) }

    return remember(startKey, topLevelKeys) {
        NavigationState(
            startKey = startKey,
            topLevelStack = topLevelStack,
            subStacks = subStacks,
        )
    }
}

/**
 * Navigation 상태를 보관하는 객체.
 *
 * @param startKey 앱의 시작 화면을 나타내는 Key. 해당 화면에서 뒤로 가면 앱을 종료하게 된다.
 * @param topLevelStack 현재까지 이동한 Top Level 화면의 Back Stack. Top Level Key만 저장한다.
 * @param subStacks 각 Top Level 화면별로 관리되는 Back Stack.
 */
class NavigationState(
    val startKey: NavKey,
    val topLevelStack: NavBackStack<NavKey>,
    val subStacks: Map<NavKey, NavBackStack<NavKey>>,
) {
    val currentTopLevelKey: NavKey by derivedStateOf { topLevelStack.last() }

    val topLevelKeys
        get() = subStacks.keys

    @get:VisibleForTesting
    val currentSubStack: NavBackStack<NavKey>
        get() = subStacks[currentTopLevelKey]
            ?: error("Sub stack for $currentTopLevelKey does not exist")

    @get:VisibleForTesting
    val currentKey: NavKey by derivedStateOf { currentSubStack.last() }
}

/**
 * NavigationState에 저장된 Back Stack을 실제 화면을 구성하는 NavEntry 목록으로 변환한다.
 */
@Composable
fun NavigationState.toEntries(
    entryProvider: (NavKey) -> NavEntry<NavKey>,
): SnapshotStateList<NavEntry<NavKey>> {
    val decoratedEntries = subStacks.mapValues { (_, stack) ->
        val decorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
            rememberViewModelStoreNavEntryDecorator<NavKey>(),
        )
        rememberDecoratedNavEntries(
            backStack = stack,
            entryDecorators = decorators,
            entryProvider = entryProvider,
        )
    }

    return topLevelStack
        .flatMap { decoratedEntries[it] ?: emptyList() }
        .toMutableStateList()
}
