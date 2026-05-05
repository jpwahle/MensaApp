package com.lkaesberg.mensaapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lkaesberg.mensaapp.CanteenOccupancy
import com.lkaesberg.mensaapp.i18n.LocalStrings
import com.lkaesberg.mensaapp.ui.MensaTheme

/**
 * Compact "Auslastung" pill rendered in the Feed status row, the canteen
 * picker, and the canteen-detail card. Pulls the traffic-light colour
 * straight from the upstream API (`color` is a hex string like `#1ACC28`)
 * and resolves the i18n status key to a German label.
 *
 * Hidden entirely when the canteen is closed — live occupancy isn't
 * meaningful while the place is shut. Call sites should also gate the
 * surrounding container if they want to avoid leaving empty space.
 */
@Composable
fun OccupancyChip(
    occupancy: CanteenOccupancy?,
    isOpen: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!isOpen || occupancy == null) return
    val palette = MensaTheme.palette
    val s = LocalStrings.current
    val color = parseHexColor(occupancy.color ?: "") ?: palette.sub
    val label = when (occupancy.statusKey) {
        "schwacher_als_sonst" -> s.occupancyWeaker
        "starker_als_sonst" -> s.occupancyStronger
        else -> s.occupancyHere
    }
    // Flat, inline-style row that visually matches the StatusRow's other
    // text (small dot + label). No background pill — keeps the header from
    // feeling like a stack of distinct widgets.
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(
            text = label,
            color = palette.sub,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun parseHexColor(hex: String): Color? {
    val normalised = hex.trim().removePrefix("#")
    if (normalised.length != 6 && normalised.length != 8) return null
    return runCatching {
        val argb = if (normalised.length == 6) "FF$normalised" else normalised
        Color(argb.toLong(16))
    }.getOrNull()
}

/**
 * Three-cell mini stats table — `Aktuell`, `Sonst um diese Zeit`,
 * `Tagesschnitt` — exposing the raw counters from `/api/frequenz`.
 * Used on the canteen picker for the currently-active canteen.
 */
@Composable
fun OccupancyStats(
    occupancy: CanteenOccupancy,
    modifier: Modifier = Modifier,
) {
    val palette = MensaTheme.palette
    val s = LocalStrings.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.moss)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatCell(s.occupancyNow, formatStat(occupancy.salesCurrent), highlight = true)
        StatDivider()
        StatCell(s.occupancyTypical, formatStat(occupancy.salesAvgWeekday), highlight = false)
        StatDivider()
        StatCell(s.occupancyDailyAvg, formatStat(occupancy.salesAvgYearly), highlight = false)
    }
}

@Composable
private fun StatCell(label: String, value: String, highlight: Boolean) {
    val palette = MensaTheme.palette
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        Text(
            label,
            color = palette.sub,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            color = if (highlight) palette.forestDark else palette.ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun StatDivider() {
    val palette = MensaTheme.palette
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(24.dp)
            .background(palette.hair),
    )
}

private fun formatStat(value: Double?): String {
    if (value == null) return "—"
    val rounded = kotlin.math.round(value).toInt()
    return rounded.toString()
}
