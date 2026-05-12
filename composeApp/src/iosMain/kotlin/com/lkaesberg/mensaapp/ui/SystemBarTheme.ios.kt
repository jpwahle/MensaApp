package com.lkaesberg.mensaapp.ui

import androidx.compose.runtime.Composable

@Composable
actual fun SystemBarThemeEffect(isDarkBackground: Boolean) {
    // iOS: Compose Multiplatform doesn't expose UIApplication.statusBarStyle
    // directly; the host UIViewController handles status-bar appearance. The
    // default light-content vs dark-content choice already follows the
    // system theme, which is fine for now.
}
