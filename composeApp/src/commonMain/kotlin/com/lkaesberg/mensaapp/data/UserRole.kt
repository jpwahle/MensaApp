package com.lkaesberg.mensaapp.data

/**
 * Who the user is on the price scale. Drives which column is highlighted
 * in price tables (CanteenDetailScreen, MealDetailScreen).
 *
 * The [priceColumnIndex] matches the order used in the DB columns
 * (price_students / price_employees / price_guests) and the fallback
 * triple [FallbackPrice.students] / .employees / .guests.
 */
enum class UserRole(
    val key: String,
    val shortLabel: String,
    val longLabel: String,
    val priceColumnIndex: Int,
) {
    Studierend("studierend", "Studi", "Studierend", 0),
    Bedienstet("bedienstet", "Bedi.", "Bedienstet", 1),
    Gast("gast", "Gast", "Gast", 2);

    companion object {
        const val SETTINGS_KEY = "user_role"
        fun fromKey(key: String?): UserRole = entries.firstOrNull { it.key == key } ?: Studierend
    }
}
