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

/** Zentralmensa stops serving afternoon meals on Saturdays — drop those rows. */
internal fun shouldHideAfternoonMealsForCanteenOnDate(canteen: Canteen?, meals: List<MealDate>): Boolean {
    if (canteen?.name?.trim()?.lowercase() != "zentralmensa") return false
    val servedOn = meals.firstOrNull()?.servedOn ?: return false
    val dayOfWeek = runCatching { LocalDate.parse(servedOn).dayOfWeek }.getOrNull() ?: return false
    return dayOfWeek == DayOfWeek.SATURDAY
}

/**
 * Split a day's meals into lunch + afternoon buckets.
 *
 * The Studierendenwerk scraper marks afternoon-only items via either
 * [MealDate.mealPeriod] = "afternoon"/"nachmittag" or [MealDate.note]
 * containing "nachmittag". Items without any period hint are *served at
 * both lunch and afternoon* — we surface them in both sections so e.g.
 * a Spätzlepfanne shows in MITTAG and snacks in NACHMITTAG without any
 * meal disappearing.
 */
internal data class PeriodBuckets(val lunch: List<MealDate>, val afternoon: List<MealDate>)

internal fun separateMealsByPeriod(meals: List<MealDate>, hideAfternoon: Boolean): PeriodBuckets {
    val lunch = mutableListOf<MealDate>()
    val afternoon = mutableListOf<MealDate>()
    meals.forEach { meal ->
        val note = meal.note?.lowercase().orEmpty()
        val period = meal.mealPeriod?.lowercase().orEmpty()
        val isAfternoonOnly = "nachmittag" in note ||
            period == "afternoon" || period == "nachmittag"
        val isLunchOnly = period == "lunch" || period == "mittag"
        when {
            isAfternoonOnly -> if (!hideAfternoon) afternoon.add(meal)
            isLunchOnly || note.isNotBlank() -> lunch.add(meal)
            else -> {
                lunch.add(meal)
                if (!hideAfternoon) afternoon.add(meal)
            }
        }
    }
    return PeriodBuckets(lunch.toList(), afternoon.toList())
}
