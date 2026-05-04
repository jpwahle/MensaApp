package com.lkaesberg.mensaapp

import com.russhwolf.settings.Settings
import com.russhwolf.settings.StorageSettings

actual fun createAppSettings(): Settings = StorageSettings()
