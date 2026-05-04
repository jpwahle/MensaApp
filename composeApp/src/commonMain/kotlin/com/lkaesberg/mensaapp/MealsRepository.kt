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

        raw.filter { it.deactivatedAt == null }
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
}
