package com.lkaesberg.mensaapp.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Direction-M palette: forest moss + amber on warm paper.
 * Used directly where the Material ColorScheme can't carry the nuance
 * (status pills, decorative gradients, diet pips, hairline rules).
 */
data class MensaPalette(
    val forest: Color,
    val forestDark: Color,
    val forestDeep: Color,
    val moss: Color,
    val mossLight: Color,
    val amber: Color,
    val amberDark: Color,
    val amberLight: Color,
    val paper: Color,
    val paperWarm: Color,
    val surface: Color,
    val ink: Color,
    val sub: Color,
    val hair: Color,
    val hairWarm: Color,
    val open: Color,
    val closed: Color,
    val isDark: Boolean,
)

val LightMensaPalette = MensaPalette(
    forest = Color(0xFF2F5D4A),
    forestDark = Color(0xFF1C3D2F),
    forestDeep = Color(0xFF0F2A20),
    moss = Color(0xFFDDE8DF),
    mossLight = Color(0xFFEAF1E9),
    amber = Color(0xFFC08A3E),
    amberDark = Color(0xFF8A5E1F),
    amberLight = Color(0xFFF5E8CF),
    paper = Color(0xFFFAFAF3),
    paperWarm = Color(0xFFF5F0E2),
    surface = Color(0xFFFFFFFF),
    ink = Color(0xFF161E18),
    sub = Color(0xFF5A6760),
    hair = Color(0xFFE2E6DC),
    hairWarm = Color(0xFFE8E1CF),
    open = Color(0xFF5A9A6A),
    closed = Color(0xFFB86B5E),
    isDark = false,
)

val DarkMensaPalette = MensaPalette(
    forest = Color(0xFF8DC9A8),
    forestDark = Color(0xFFB6DCC1),
    forestDeep = Color(0xFFD8ECDF),
    moss = Color(0xFF22332B),
    mossLight = Color(0xFF1B2A23),
    amber = Color(0xFFE6B873),
    amberDark = Color(0xFFF5D9A0),
    amberLight = Color(0xFF3D2F18),
    paper = Color(0xFF11150F),
    paperWarm = Color(0xFF181C15),
    surface = Color(0xFF1A201B),
    ink = Color(0xFFECE9E0),
    sub = Color(0xFF98A39B),
    hair = Color(0xFF2A322C),
    hairWarm = Color(0xFF332E22),
    open = Color(0xFF8DC9A8),
    closed = Color(0xFFE08A7B),
    isDark = true,
)

/** Diet-pip colours used across all diet badges. Stable across light/dark. */
object DietColors {
    val Vegan = Color(0xFF4CAF50)
    val Vegetarisch = Color(0xFF8BC34A)
    val Fleisch = Color(0xFFC0584B)
    val Fisch = Color(0xFF4A90C0)
    val Strohschwein = Color(0xFFD97A6C)
    val Leinetalerrind = Color(0xFF8B4513)
    val Bio = Color(0xFF9CCC65)
    val Regional = Color(0xFFE08E45)
    val Klimaessen = Color(0xFF26A69A)
    val Nds = Color(0xFF2196F3)
    val Unknown = Color(0xFF999999)

    fun of(kind: String): Color = when (kind.lowercase()) {
        "vegan" -> Vegan
        "vegetarisch" -> Vegetarisch
        "fleisch" -> Fleisch
        "fisch" -> Fisch
        "strohschwein" -> Strohschwein
        "leinetalerrind" -> Leinetalerrind
        "bio" -> Bio
        "regional" -> Regional
        "klimaessen" -> Klimaessen
        "nds" -> Nds
        else -> Unknown
    }

    fun shortLabel(kind: String): String = when (kind.lowercase()) {
        "vegan" -> "V"
        "vegetarisch" -> "VG"
        "fleisch" -> "M"
        "fisch" -> "F"
        "strohschwein" -> "P"
        "leinetalerrind" -> "B"
        "bio" -> "O"
        "regional" -> "R"
        "klimaessen" -> "C"
        "nds" -> "NDS"
        else -> "?"
    }

    fun longLabel(kind: String): String = when (kind.lowercase()) {
        "vegan" -> "Vegan"
        "vegetarisch" -> "Vegetarisch"
        "fleisch" -> "Fleisch"
        "fisch" -> "Fisch"
        "strohschwein" -> "Strohschwein"
        "leinetalerrind" -> "Leinetaler Rind"
        "bio" -> "Bio"
        "regional" -> "Regional"
        "klimaessen" -> "Klimaessen"
        "nds" -> "Niedersachsen"
        else -> kind.replaceFirstChar { it.uppercase() }
    }
}

/**
 * Material icon for each diet/origin slug. Returns null for unknown slugs so
 * callers can fall back to the short letter label from [DietColors.shortLabel].
 * Color coding from [DietColors] still distinguishes the meat sub-types
 * (Fleisch / Strohschwein) that share an icon.
 */
object DietIcons {
    fun of(kind: String): ImageVector? = when (kind.lowercase()) {
        "vegan" -> Icons.Filled.Eco
        "vegetarisch" -> Icons.Filled.Spa
        "fleisch" -> Icons.Filled.LunchDining
        "fisch" -> Icons.Filled.SetMeal
        "strohschwein" -> Icons.Filled.LunchDining
        "leinetalerrind" -> Icons.Filled.Agriculture
        "bio" -> Icons.Filled.EnergySavingsLeaf
        "regional" -> Icons.Filled.Storefront
        "klimaessen" -> Icons.Filled.Public
        "nds" -> Icons.Filled.Place
        else -> null
    }
}

val LocalMensaPalette = compositionLocalOf { LightMensaPalette }

object MensaTheme {
    val palette: MensaPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalMensaPalette.current

    val colorScheme
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme

    val typography
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography
}
