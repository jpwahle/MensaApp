package com.lkaesberg.mensaapp.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lkaesberg.mensaapp.ui.MensaTheme

/** Small-caps eyebrow label, e.g. "MENÜ 1" or "STATUS". */
@Composable
fun Eyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    val palette = MensaTheme.palette
    Text(
        text = text.uppercase(),
        color = color ?: palette.forest,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        modifier = modifier,
    )
}
