package com.lkaesberg.mensaapp

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiImageryTest {
    @Test
    fun onlyTheChinaStorefrontHidesAiPhotos() {
        assertTrue(AiImagery.isBlocked("CHN"))
        assertTrue(AiImagery.isBlocked("chn"))
        assertFalse(AiImagery.isBlocked("HKG"))
        assertFalse(AiImagery.isBlocked("DEU"))
        assertFalse(AiImagery.isBlocked(null))
    }
}
