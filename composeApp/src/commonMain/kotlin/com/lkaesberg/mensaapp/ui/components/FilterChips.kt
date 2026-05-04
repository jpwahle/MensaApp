package com.lkaesberg.mensaapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import com.lkaesberg.mensaapp.ui.MensaTheme

private val FILTER_KINDS = listOf(
    "vegan" to "Vegan",
    "vegetarisch" to "Vegetarisch",
    "fleisch" to "Fleisch",
    "fisch" to "Fisch",
)

@Composable
fun FilterChipsRow(
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = MensaTheme.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FILTER_KINDS.forEach { (kind, label) ->
            val isOn = kind in selected
            val bg = if (isOn) palette.forest else palette.surface
            val fg = if (isOn) Color.White else palette.ink
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(bg)
                    .border(
                        width = 1.dp,
                        color = if (isOn) Color.Transparent else palette.hair,
                        shape = RoundedCornerShape(100.dp),
                    )
                    .clickable { onToggle(kind) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DietPip(kind = kind, size = 14.dp)
                Text(
                    text = label,
                    color = fg,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
