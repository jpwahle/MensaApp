package com.lkaesberg.mensaapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIWindow

@Composable
actual fun SystemBarThemeEffect(isDarkBackground: Boolean) {
    // Force the active UIWindow into light or dark style so the status bar
    // icons (time, signal, battery) flip with the app's own dark-mode toggle
    // rather than the system theme.
    DisposableEffect(isDarkBackground) {
        val window = activeWindow()
        val previous = window?.overrideUserInterfaceStyle
        window?.overrideUserInterfaceStyle = if (isDarkBackground) {
            UIUserInterfaceStyle.UIUserInterfaceStyleDark
        } else {
            UIUserInterfaceStyle.UIUserInterfaceStyleLight
        }
        onDispose {
            window?.overrideUserInterfaceStyle =
                previous ?: UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
        }
    }
}

private fun activeWindow(): UIWindow? {
    val app = UIApplication.sharedApplication
    @Suppress("DEPRECATION")
    app.keyWindow?.let { return it }
    return app.windows.firstOrNull() as? UIWindow
}
