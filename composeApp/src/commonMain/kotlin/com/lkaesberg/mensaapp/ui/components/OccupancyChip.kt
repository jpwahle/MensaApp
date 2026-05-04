package com.lkaesberg.mensaapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
    if (!isOpen) return
    val palette = MensaTheme.palette
    val s = LocalStrings.current
    val color = occupancy?.color?.let { parseHexColor(it) } ?: Color(0xFF999999)
    val label = when {
        occupancy?.statusKey == "schwacher_als_sonst" -> s.occupancyWeaker
        occupancy?.statusKey == "starker_als_sonst" -> s.occupancyStronger
        occupancy != null -> s.occupancyHere
        else -> s.occupancyNoData
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(palette.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(
            text = label,
            color = palette.sub,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
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
