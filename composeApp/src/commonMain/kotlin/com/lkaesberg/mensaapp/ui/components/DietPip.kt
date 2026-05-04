package com.lkaesberg.mensaapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lkaesberg.mensaapp.ui.DietColors
import com.lkaesberg.mensaapp.ui.DietIcons

/**
 * Small filled circle with the diet/origin marker — a Material icon when one
 * is mapped for the slug, otherwise the legacy 1–3 letter abbreviation.
 */
@Composable
fun DietPip(
    kind: String,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier,
) {
    val color = DietColors.of(kind)
    val icon = DietIcons.of(kind)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = DietColors.longLabel(kind),
                tint = Color.White,
                modifier = Modifier.size(size * 0.7f),
            )
        } else {
            Text(
                text = DietColors.shortLabel(kind),
                color = Color.White,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
