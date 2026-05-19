package com.lkaesberg.mensaapp.ui

import androidx.compose.runtime.Composable

/**
 * Tells the host platform whether the app's background is currently dark.
 *
 * On Android, this drives `WindowInsetsControllerCompat.isAppearanceLight{StatusBars,NavigationBars}`.
 * On iOS, it sets `UIWindow.overrideUserInterfaceStyle` so the status-bar
 * clock / signal / battery flip with the app's dark-mode toggle independent
 * of the device theme. Web is a no-op.
 */
@Composable
expect fun SystemBarThemeEffect(isDarkBackground: Boolean)
