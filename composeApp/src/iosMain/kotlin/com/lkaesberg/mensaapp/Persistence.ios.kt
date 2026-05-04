package com.lkaesberg.mensaapp

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import platform.Foundation.NSUserDefaults

actual fun createAppSettings(): Settings =
    NSUserDefaultsSettings(NSUserDefaults(suiteName = APP_PREFS_NAME) ?: NSUserDefaults.standardUserDefaults)
