package com.lkaesberg.mensaapp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Canteen(
    val id: String,
    val name: String,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class Meal(
    val id: String,
    val title: String,
    @SerialName("full_text") val fullText: String,
    val icons: List<String>? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("image_path") val imagePath: String? = null,
    @SerialName("image_path_generic") val imagePathGeneric: String? = null,

    // Richer fields added by the 2026-05-03 schema migration. Nullable so rows
    // that haven't been re-scraped still decode cleanly.
    @SerialName("clean_title") val cleanTitle: String? = null,
    val description: String? = null,
    val sides: List<String>? = null,
    val allergens: List<String>? = null,
    val additives: List<String>? = null,
)

@Serializable
data class MealDate(
    val id: String,
    @SerialName("meal_id") val mealId: String,
    @SerialName("canteen_id") val canteenId: String,
    @SerialName("served_on") val servedOn: String, // ISO yyyy-MM-dd
    val category: String,
    val note: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    // Set when the upstream stops listing this meal. Null = active. Field is
    // absent on databases without the deactivation migration; that's fine —
    // it stays null and the meal is treated as active.
    @SerialName("deactivated_at") val deactivatedAt: String? = null,
    // "lunch" | "afternoon" | null. Drives the time-separator on the feed.
    @SerialName("meal_period") val mealPeriod: String? = null,

    // Embedded join, will be null if not requested
    val meals: Meal? = null,
)

@Serializable
data class CanteenPrice(
    val id: String,
    @SerialName("canteen_id") val canteenId: String,
    val category: String,
    @SerialName("price_students") val priceStudents: String? = null,
    @SerialName("price_employees") val priceEmployees: String? = null,
    @SerialName("price_guests") val priceGuests: String? = null,
    @SerialName("price_students_cents") val priceStudentsCents: Int? = null,
    @SerialName("price_employees_cents") val priceEmployeesCents: Int? = null,
    @SerialName("price_guests_cents") val priceGuestsCents: Int? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)
