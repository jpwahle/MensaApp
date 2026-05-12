package com.lkaesberg.mensaapp.ui

import androidx.compose.runtime.Composable

/**
 * Tells the host platform whether the app's background is currently dark.
 *
 * On Android, this drives `WindowInsetsControllerCompat.isAppearanceLight{StatusBars,NavigationBars}`
 * so the system clock / battery / time icons render dark on light paper and
 * light on dark paper (otherwise the default light icons disappear on our
 * pale palette).
 *
 * On iOS and Web, the host's status-bar treatment is sufficient — these
 * actuals are no-ops.
 */
@Composable
expect fun SystemBarThemeEffect(isDarkBackground: Boolean)
