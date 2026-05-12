package com.lkaesberg.mensaapp

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * Pasta/Teppan-Yaki style "choose your sauce" meals carry a `fleisch` icon
 * for the bolognese option even though they're served as a sauce-on-pasta
 * choice. Treat them as eligible for vegetarian filters.
 */
internal fun isSauceChoiceMeal(mealDate: MealDate): Boolean {
    val title = mealDate.meals?.title?.lowercase() ?: ""
    val fullText = mealDate.meals?.fullText?.lowercase() ?: ""
    val category = mealDate.category.lowercase()
    return title.contains("pastabuffet")
        || category.contains("teppan yaki")
        || fullText.contains("teppan yaki")
}

internal fun mealMatchesDietaryFilters(mealDate: MealDate, selectedDietaryFilters: Set<String>): Boolean {
    if (selectedDietaryFilters.isEmpty()) return true
    val mealIcons = mealDate.meals?.icons?.map { it.lowercase() } ?: emptyList()
    return selectedDietaryFilters.any { filter ->
        when {
            filter == "vegetarisch" && (mealIcons.contains("vegetarisch") || mealIcons.contains("vegan") || isSauceChoiceMeal(mealDate)) -> true
            filter == "fleisch" && (mealIcons.contains("fleisch") || mealIcons.contains("strohschwein") || mealIcons.contains("leinetalerrind")) -> true
            else -> mealIcons.contains(filter)
        }
    }
}

/**
 * Only Zentralmensa serves an afternoon menu — everywhere else closes at
 * 14:30 or earlier, so the upstream never ships "Nachmittagsangebot" rows
 * for them anyway and we hide the whole section. For Zentralmensa itself
 * Saturday is lunch-only (close 14:30), so hide afternoon there too.
 */
internal fun shouldHideAfternoonMealsForCanteenOnDate(canteen: Canteen?, meals: List<MealDate>): Boolean {
    if (canteen?.name?.trim()?.lowercase() != "zentralmensa") return true
    val servedOn = meals.firstOrNull()?.servedOn ?: return false
    val dayOfWeek = runCatching { LocalDate.parse(servedOn).dayOfWeek }.getOrNull() ?: return false
    return dayOfWeek == DayOfWeek.SATURDAY
}

/**
 * Split a day's meals into lunch + afternoon buckets.
 *
 * Primary signal is the upstream `<mittag>` integer (`MealDate.mittag`):
 *  - `0` → lunch only (the default for everything at non-Zentralmensa
 *          canteens, plus the proper Menü dishes at Zentralmensa).
 *  - `1` → afternoon only (rare; e.g. an Aktion exclusive to the
 *          Nachmittag service).
 *  - `2` → served all day — surface in both buckets.
 *
 * Legacy rows scraped before the 2026-05-07 migration have `mittag == null`;
 * fall back to the `note` heuristic for those:
 *  - note contains "nachmittag" → afternoon only.
 *  - note is blank              → both buckets.
 *  - any other note             → lunch only.
 *
 * When [hideAfternoon] is set (every canteen except Zentralmensa, plus
 * Zentralmensa on Saturday) the afternoon bucket stays empty regardless.
 */
internal data class PeriodBuckets(val lunch: List<MealDate>, val afternoon: List<MealDate>)

internal fun separateMealsByPeriod(meals: List<MealDate>, hideAfternoon: Boolean): PeriodBuckets {
    val lunch = mutableListOf<MealDate>()
    val afternoon = mutableListOf<MealDate>()
    meals.forEach { meal ->
        val (toLunch, toAfternoon) = when (meal.mittag) {
            0 -> true to false
            1 -> false to true
            2 -> true to true
            else -> {
                // Legacy fallback — pre-2026-05-07 rows with no mittag value.
                val note = meal.note?.lowercase().orEmpty()
                when {
                    "nachmittag" in note -> false to true
                    note.isBlank() -> true to true
                    else -> true to false
                }
            }
        }
        if (toLunch) lunch.add(meal)
        if (toAfternoon && !hideAfternoon) afternoon.add(meal)
    }
    return PeriodBuckets(lunch.toList(), afternoon.toList())
}
