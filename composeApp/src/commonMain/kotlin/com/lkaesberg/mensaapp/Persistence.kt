package com.lkaesberg.mensaapp

import com.russhwolf.settings.Settings

/**
 * Single source of truth for the app's [Settings] store.
 * Implemented per-platform so background workers (Android WorkManager) can
 * read/write the same prefs the UI uses.
 */
expect fun createAppSettings(): Settings

/** Shared SharedPreferences/NSUserDefaults/localStorage key used on Android. */
const val APP_PREFS_NAME = "mensa_app_settings"
