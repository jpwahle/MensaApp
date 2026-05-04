package com.lkaesberg.mensaapp.data

enum class Locale(val tag: String) {
    De("de"),
    En("en");

    companion object {
        fun fromTag(tag: String?): Locale = entries.firstOrNull { it.tag == tag } ?: De
    }
}
