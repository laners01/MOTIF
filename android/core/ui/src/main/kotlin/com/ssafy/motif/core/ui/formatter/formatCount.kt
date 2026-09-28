package com.ssafy.motif.core.ui.formatter

import java.util.Locale

fun Long.formatCount(): String = when {
    this >= 1_000_000 -> {
        val value = this / 1_000_000.0
        val formatted = String
            .format(Locale.US, "%.1f", value)
            .removeSuffix(".0")

        "${formatted}m"
    }

    this >= 1_000 -> {
        val value = this / 1_000.0
        val formatted = String
            .format(Locale.US, "%.1f", value)
            .removeSuffix(".0")

        "${formatted}k"
    }

    else -> toString()
}