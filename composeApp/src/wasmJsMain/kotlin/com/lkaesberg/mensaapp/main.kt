package com.lkaesberg.mensaapp

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
fun main() {
    document.body?.style?.setProperty("touch-action", "pan-y")
    ComposeViewport(document.body!!) {
        App()
    }
}
