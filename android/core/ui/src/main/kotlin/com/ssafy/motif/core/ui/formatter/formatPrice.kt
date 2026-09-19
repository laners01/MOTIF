package com.ssafy.motif.core.ui.formatter

import java.text.NumberFormat
import java.util.Locale

/**
 * 원 단위 가격을 ₩3,900 형식으로 변환한다.
 */
fun Int.formatPrice(): String =
    "₩" + NumberFormat.getIntegerInstance(Locale.KOREA).format(this)
