package com.ssafy.motif.core.ui.formatter

import java.util.Calendar

fun Long.formatCreatedAt(
    now: Long = System.currentTimeMillis(),
): String {
    val elapsedTime = (now - this).coerceAtLeast(0L)

    return when {
        elapsedTime < MINUTE -> "방금 전"
        elapsedTime < HOUR -> "${elapsedTime / MINUTE}분 전"
        elapsedTime < DAY -> "${elapsedTime / HOUR}시간 전"
        elapsedTime < WEEK -> "${elapsedTime / DAY}일 전"
        else -> formatDate(now)
    }
}

private fun Long.formatDate(now: Long): String {
    val createdAt = Calendar.getInstance().apply {
        timeInMillis = this@formatDate
    }

    val current = Calendar.getInstance().apply {
        timeInMillis = now
    }

    val year = createdAt.get(Calendar.YEAR)
    val month = createdAt.get(Calendar.MONTH) + 1
    val day = createdAt.get(Calendar.DAY_OF_MONTH)

    return if (year == current.get(Calendar.YEAR)) {
        "${month}월 ${day}일"
    } else {
        "${year}년 ${month}월 ${day}일"
    }
}

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR
private const val WEEK = 7 * DAY