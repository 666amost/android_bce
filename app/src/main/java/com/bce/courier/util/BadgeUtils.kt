package com.bce.courier.util

object BadgeUtils {
    fun formatCount(count: Int): String = when {
        count == 0 -> ""
        else -> count.toString()
    }
}

