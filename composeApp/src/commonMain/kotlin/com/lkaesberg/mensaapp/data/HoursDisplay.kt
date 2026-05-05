package com.lkaesberg.mensaapp.data

import com.lkaesberg.mensaapp.CanteenHours
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/**
 * Display-side representation of opening hours for one or more weekdays.
 *
 * Built from `canteen_hours` rows by grouping consecutive days that share
 * the same open/close times — so a canteen with Mon–Thu 11:30–18:00 and
 * Fri 11:30–14:30 renders as two lines, not five.
 */
data class HoursLine(
    val daysLabel: String,    // "Mo–Do" / "Fr"
    val timeLabel: String,    // "11:30–18:00" / "Geschlossen"
    val days: Set<DayOfWeek>, // for "is this today?" highlighting
)

object HoursDisplay {

    /**
     * Group a canteen's 7-day schedule into compact display lines.
     *
     * @param weekdayShortLabels seven entries in ISO order (Mon..Sun) —
     *        usually `Strings.weekdaysShort`.
     * @param closedLabel rendered when a day has no open/close time
     *        (e.g. NULL columns or no row at all). Usually `Strings.closedLabel`.
     */
    fun groupAll(
        rows: List<CanteenHours>,
        weekdayShortLabels: List<String>,
        closedLabel: String,
    ): List<HoursLine> {
        val byDow: Array<Pair<LocalTime?, LocalTime?>> = Array(7) { dowIdx ->
            val isoDow = dowIdx + 1
            val row = rows.firstOrNull { it.dayOfWeek == isoDow }
            parseTime(row?.openTime) to parseTime(row?.closeTime)
        }
        val out = mutableListOf<HoursLine>()
        var i = 0
        while (i < 7) {
            val (openA, closeA) = byDow[i]
            var j = i + 1
            while (j < 7 && byDow[j] == byDow[i]) j++
            val end = j - 1
            val daysLabel = if (i == end) weekdayShortLabels[i]
            else "${weekdayShortLabels[i]}–${weekdayShortLabels[end]}"
            val timeLabel = if (openA != null && closeA != null)
                "${formatTime(openA)}–${formatTime(closeA)}"
            else closedLabel
            val days = (i..end).map { DayOfWeek.entries[it] }.toSet()
            out += HoursLine(daysLabel, timeLabel, days)
            i = j
        }
        return out
    }

    /** Single line covering today only — used for the unselected canteens in the picker. */
    fun lineForToday(
        rows: List<CanteenHours>,
        today: DayOfWeek,
        weekdayShortLabels: List<String>,
        closedLabel: String,
    ): HoursLine? {
        val isoDow = today.ordinal + 1
        val row = rows.firstOrNull { it.dayOfWeek == isoDow } ?: return null
        val open = parseTime(row.openTime)
        val close = parseTime(row.closeTime)
        val timeLabel = if (open != null && close != null)
            "${formatTime(open)}–${formatTime(close)}"
        else closedLabel
        return HoursLine(
            daysLabel = weekdayShortLabels[today.ordinal],
            timeLabel = timeLabel,
            days = setOf(today),
        )
    }

    /** Bridge: convert legacy `CanteenInfo.hours` (static fallback) into HoursLine. */
    fun fromStatic(hours: List<HoursEntry>): List<HoursLine> = hours.map { h ->
        HoursLine(daysLabel = h.daysLabel, timeLabel = h.time, days = h.days)
    }

    private fun parseTime(s: String?): LocalTime? {
        if (s.isNullOrBlank()) return null
        val parts = s.split(":")
        return runCatching { LocalTime(parts[0].toInt(), parts[1].toInt()) }.getOrNull()
    }

    private fun formatTime(t: LocalTime): String =
        "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
}
