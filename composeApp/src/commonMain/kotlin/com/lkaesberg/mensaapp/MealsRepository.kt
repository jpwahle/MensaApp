package com.lkaesberg.mensaapp

import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn

class MealsRepository(private val postgrest: Postgrest) {

    suspend fun getCanteens(): List<Canteen> = try {
        postgrest["canteens"].select().decodeList<Canteen>()
    } catch (e: Throwable) {
        println("Error fetching canteens: ${e.message}")
        emptyList()
    }

    suspend fun getMealsForCanteen(canteenId: String): Map<LocalDate, List<MealDate>> = try {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val yesterday = today.minus(1, DateTimeUnit.DAY)

        // Embed the meal information via a join. New schema columns
        // (clean_title, sides, allergens, additives, meal_period) decode
        // automatically thanks to nullable defaults in the data classes.
        val raw = postgrest["meal_dates"].select(columns = Columns.raw("*,meals(*)")) {
            filter {
                eq("canteen_id", canteenId)
                gte("served_on", yesterday.toString())
            }
            order("served_on", Order.ASCENDING)
        }.decodeList<MealDate>()

        // Keep deactivated rows from *today*: the upstream HTML drops today's
        // plan once the canteen closes, which makes the scraper soft-delete
        // those rows. They should still appear in the feed (greyed out via
        // MealCard's deactivatedAt handling) instead of vanishing. Past and
        // future deactivations stay filtered.
        //
        // Drop empty-title rows entirely — they're "Last Minute" placeholders
        // the API returns for unfilled category slots. The new scraper skips
        // them at parse time, but legacy rows linger in DB and surface as
        // weird greyed-out blank cards otherwise.
        raw.filter { md ->
            val isActiveOrToday = md.deactivatedAt == null || LocalDate.parse(md.servedOn) == today
            val title = md.meals?.cleanTitle?.ifBlank { null } ?: md.meals?.title
            isActiveOrToday && !title.isNullOrBlank()
        }
            .groupBy { LocalDate.parse(it.servedOn) }
            .mapValues { entry ->
                entry.value.sortedBy { it.category.lowercase() }
            }
    } catch (e: Throwable) {
        println("Error fetching meals for canteen $canteenId: ${e.message}")
        emptyMap()
    }

    /**
     * Full history fetch — used by the dish-stats and all-meals-archive screens.
     * Bounded by [sinceDays] so we don't pull the entire DB; the design defaults
     * to a 90-day window for stats.
     */
    suspend fun getMealsHistory(canteenId: String, sinceDays: Int = 90): List<MealDate> = try {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val since = today.minus(sinceDays, DateTimeUnit.DAY)

        val raw = postgrest["meal_dates"].select(columns = Columns.raw("*,meals(*)")) {
            filter {
                eq("canteen_id", canteenId)
                gte("served_on", since.toString())
            }
            order("served_on", Order.DESCENDING)
        }.decodeList<MealDate>()

        raw.filter { it.deactivatedAt == null }
    } catch (e: Throwable) {
        println("Error fetching meal history for canteen $canteenId: ${e.message}")
        emptyList()
    }

    /**
     * Per-canteen prices from the `canteen_prices` table introduced by the
     * 2026-05-03 migration. Returns an empty list if the table is not present
     * (older DB) or no prices have been scraped.
     */
    suspend fun getCanteenPrices(canteenId: String): List<CanteenPrice> = try {
        postgrest["canteen_prices"].select {
            filter { eq("canteen_id", canteenId) }
        }.decodeList<CanteenPrice>()
    } catch (e: Throwable) {
        println("Error fetching prices for canteen $canteenId: ${e.message}")
        emptyList()
    }

    /**
     * Weekly opening-hours pattern, populated by the 2026-05-05 API
     * migration. One row per (canteen, ISO weekday). Open/close times are
     * null on closed days. Empty list when the migration hasn't run.
     */
    suspend fun getCanteenHours(): List<CanteenHours> = try {
        postgrest["canteen_hours"].select().decodeList<CanteenHours>()
    } catch (e: Throwable) {
        println("Error fetching canteen hours: ${e.message}")
        emptyList()
    }

    /**
     * Latest occupancy snapshot per canteen from the `canteen_occupancy_latest`
     * view. Empty list outside opening hours or before the first sync.
     */
    suspend fun getOccupancyLatest(): List<CanteenOccupancy> = try {
        postgrest["canteen_occupancy_latest"].select().decodeList<CanteenOccupancy>()
    } catch (e: Throwable) {
        println("Error fetching occupancy: ${e.message}")
        emptyList()
    }
}
