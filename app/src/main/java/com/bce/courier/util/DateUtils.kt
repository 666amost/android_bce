package com.bce.courier.util

import java.util.*
import java.text.SimpleDateFormat

object DateUtils {
    private const val ISO_FORMAT = "yyyy-MM-dd'T'HH:mm:ss"

    fun parseIsoDate(dateStr: String): Date? = try {
        val sdf = SimpleDateFormat(ISO_FORMAT, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val cleanDate = dateStr.replace("Z", "+0000").substringBefore(".")
        sdf.parse(cleanDate)
    } catch (e: Exception) {
        null
    }

    fun getWeekAgoDate(): Date {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -7)
        return cal.time
    }

    fun getMonthAgoDate(): Date {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -1)
        return cal.time
    }

    fun getTodayStartTime(): Date {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    fun getDateRangeLabel(startDate: Date, endDate: Date = Date()): String {
        val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
        return "${sdf.format(startDate)} - ${sdf.format(endDate)}"
    }
}

