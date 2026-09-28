package com.ssafy.motif.core.navigation

import androidx.navigation3.runtime.NavKey

/**
 * Navigation 상태를 변경하여 화면 이동과 뒤로 가기 동작을 처리한다.
 *
 * @param state 화면 이동에 따라 변경될 Navigation 상태.
 */
class Navigator(val state: NavigationState) {

    /**
     * 전달받은 Navigation Key에 해당하는 화면으로 이동한다.
     *
     * @param key 이동할 화면의 Navigation Key.
     */
    fun navigate(key: NavKey) {
        when (key) {
            state.currentTopLevelKey -> clearSubStack()
            in state.topLevelKeys -> goToTopLevel(key)
            else -> goToKey(key)
        }
    }

    /**
     * 현재 화면에서 이전 화면으로 이동한다.
     */
    fun goBack() {
        when (state.currentKey) {
            state.startKey -> error("You cannot go back from the start route")
            state.currentTopLevelKey -> {
                // We're at the base of the current substack, go back to the previous top level
                // stack.
                state.topLevelStack.removeLastOrNull()
            }

            else -> state.currentSubStack.removeLastOrNull()
        }
    }

    /**
     * 현재 Top Level 화면의 Back Stack에 일반 화면을 추가하여 이동한다.
     */
    private fun goToKey(key: NavKey) {
        state.currentSubStack.apply {
            // Remove it if it's already in the stack so it's added at the end.
            remove(key)
            add(key)
        }
    }

    /**
     * 선택한 Top Level 화면으로 이동하고 해당 화면을 현재 Top Level Stack의 최상단으로 설정한다.
     */
    private fun goToTopLevel(key: NavKey) {
        state.topLevelStack.apply {
            if (key == state.startKey) {
                // This is the start key. Clear the stack so it's added as the only key.
                clear()
            } else {
                // Remove it if it's already in the stack so it's added at the end.
                remove(key)
            }
            add(key)
        }
    }

    /**
     * 현재 Sub Stack의 Root 화면만 남기고 나머지 화면을 모두 제거한다.
     */
    private fun clearSubStack() {
        state.currentSubStack.run {
            if (size > 1) subList(1, size).clear()
        }
    }
}
