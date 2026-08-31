package com.ssafy.modif.core.data.util

import kotlinx.coroutines.flow.Flow

/**
 * 앱의 네트워크 연결 상태를 제공한다.
 */
interface NetworkMonitor {
    val isOnline: Flow<Boolean>
}
