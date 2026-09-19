package com.ssafy.motif.core.ui.formatter

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * epoch millis를 2026.08.31 형식으로 변환한다.
 */
fun Long.formatDotDate(
    zoneId: ZoneId = ZoneId.systemDefault(),
): String =
    Instant.ofEpochMilli(this)
        .atZone(zoneId)
        .format(DotDateFormatter)

private val DotDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy.MM.dd")
