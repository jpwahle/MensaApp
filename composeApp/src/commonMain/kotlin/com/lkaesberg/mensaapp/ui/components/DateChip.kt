package com.lkaesberg.mensaapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.lkaesberg.mensaapp.ui.MensaTheme

@Composable
fun DateChip(
    label: String,
    day: Int,
    isToday: Boolean,
    isSelected: Boolean,
    hasFavorite: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = MensaTheme.palette
    val bg = when {
        isSelected && isToday -> palette.forest
        isSelected -> palette.forest
        else -> Color.Transparent
    }
    val fg = if (isSelected) Color.White else palette.ink
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .let {
                if (!isSelected) it.border(1.dp, palette.hair, RoundedCornerShape(14.dp))
                else it
            }
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                color = fg.copy(alpha = if (isSelected) 0.85f else 0.7f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = day.toString(),
                color = fg,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (hasFavorite) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 6.dp, top = 4.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(palette.amber)
            )
        }
    }
}
