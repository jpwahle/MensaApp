package com.lkaesberg.mensaapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lkaesberg.mensaapp.data.MealEnrichment
import com.lkaesberg.mensaapp.ui.MensaTheme

/** Pill row displaying allergen + additive labels in human-readable German. */
@Composable
fun AllergenChips(
    allergens: List<String>,
    additives: List<String>,
    modifier: Modifier = Modifier,
) {
    val palette = MensaTheme.palette
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        allergens.forEach { code ->
            val label = MealEnrichment.allergenLabels[code.lowercase()] ?: code
            ChipText(label, palette.mossLight, palette.forestDark)
        }
        additives.forEach { code ->
            val label = MealEnrichment.additiveLabels[code] ?: code
            ChipText(label, palette.amberLight, palette.amberDark)
        }
    }
}

@Composable
private fun ChipText(text: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        color = fg,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
