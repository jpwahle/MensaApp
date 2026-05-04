package com.lkaesberg.mensaapp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun lightSchemeFor(p: MensaPalette) = lightColorScheme(
    primary = p.forest,
    onPrimary = Color.White,
    primaryContainer = p.moss,
    onPrimaryContainer = p.forestDark,
    secondary = p.amber,
    onSecondary = Color.White,
    secondaryContainer = p.amberLight,
    onSecondaryContainer = p.amberDark,
    tertiary = p.forestDeep,
    onTertiary = Color.White,
    tertiaryContainer = p.mossLight,
    onTertiaryContainer = p.forestDark,
    error = p.closed,
    onError = Color.White,
    errorContainer = Color(0xFFF7D9D2),
    onErrorContainer = Color(0xFF5C1A14),
    background = p.paper,
    onBackground = p.ink,
    surface = p.surface,
    onSurface = p.ink,
    surfaceVariant = p.mossLight,
    onSurfaceVariant = p.sub,
    outline = p.sub,
    outlineVariant = p.hair,
    scrim = Color(0x99000000),
    inverseSurface = p.forestDark,
    inverseOnSurface = p.paper,
    inversePrimary = Color(0xFF8DC9A8),
    surfaceTint = p.forest,
    surfaceContainerHighest = Color(0xFFEFEFE6),
    surfaceContainer = Color(0xFFF3F2EA),
    surfaceContainerHigh = Color(0xFFE9E9E0),
    surfaceContainerLow = Color(0xFFF6F5ED),
    surfaceContainerLowest = Color.White,
    surfaceBright = p.surface,
    surfaceDim = Color(0xFFE6E5DC),
)

private fun darkSchemeFor(p: MensaPalette) = darkColorScheme(
    primary = p.forest,
    onPrimary = Color(0xFF11201A),
    primaryContainer = p.moss,
    onPrimaryContainer = p.forestDark,
    secondary = p.amber,
    onSecondary = Color(0xFF2E1F08),
    secondaryContainer = p.amberLight,
    onSecondaryContainer = p.amberDark,
    tertiary = p.forestDeep,
    onTertiary = Color(0xFF11201A),
    tertiaryContainer = p.mossLight,
    onTertiaryContainer = p.forestDark,
    error = p.closed,
    onError = Color(0xFF3A0F0A),
    errorContainer = Color(0xFF552620),
    onErrorContainer = Color(0xFFF7D9D2),
    background = p.paper,
    onBackground = p.ink,
    surface = p.surface,
    onSurface = p.ink,
    surfaceVariant = p.mossLight,
    onSurfaceVariant = p.sub,
    outline = p.sub,
    outlineVariant = p.hair,
    scrim = Color(0xCC000000),
    inverseSurface = p.ink,
    inverseOnSurface = p.paper,
    inversePrimary = Color(0xFF2F5D4A),
    surfaceTint = p.forest,
    surfaceContainerHighest = Color(0xFF26302A),
    surfaceContainer = Color(0xFF1F2620),
    surfaceContainerHigh = Color(0xFF222A24),
    surfaceContainerLow = Color(0xFF181D17),
    surfaceContainerLowest = Color(0xFF0E120D),
    surfaceBright = Color(0xFF252B22),
    surfaceDim = p.paper,
)

private val mensaTypography = Typography().run {
    val display = FontFamily.Default
    val body = FontFamily.Default
    copy(
        displayLarge = displayLarge.copy(fontFamily = display, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).sp),
        displayMedium = displayMedium.copy(fontFamily = display, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).sp),
        displaySmall = displaySmall.copy(fontFamily = display, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).sp),
        headlineLarge = headlineLarge.copy(fontFamily = display, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).sp),
        headlineMedium = headlineMedium.copy(fontFamily = display, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.01).sp),
        headlineSmall = headlineSmall.copy(fontFamily = display, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).sp),
        titleLarge = titleLarge.copy(fontFamily = body, fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontFamily = body, fontWeight = FontWeight.Bold),
        titleSmall = titleSmall.copy(fontFamily = body, fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(fontFamily = body),
        bodyMedium = bodyMedium.copy(fontFamily = body),
        bodySmall = bodySmall.copy(fontFamily = body),
        labelLarge = labelLarge.copy(fontFamily = body, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontFamily = body, fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.copy(fontFamily = body, fontWeight = FontWeight.Bold, letterSpacing = 0.08.sp),
    )
}

/** Mono style used for prices, frequencies, and other numerics. */
val MonoNumericStyle: TextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
)

/** Small-caps eyebrow style — uppercased category labels with letter-spacing. */
val EyebrowStyle: TextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 10.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 0.6.sp,
)

@Composable
fun MensaAppTheme(
    useDarkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = if (useDarkTheme) DarkMensaPalette else LightMensaPalette
    val colors = if (useDarkTheme) darkSchemeFor(palette) else lightSchemeFor(palette)

    CompositionLocalProvider(LocalMensaPalette provides palette) {
        MaterialTheme(
            colorScheme = colors,
            typography = mensaTypography,
            content = content
        )
    }
}
