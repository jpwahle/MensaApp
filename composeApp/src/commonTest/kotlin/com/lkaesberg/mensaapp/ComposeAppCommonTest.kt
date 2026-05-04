package com.lkaesberg.mensaapp

import com.lkaesberg.mensaapp.data.MealEnrichment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposeAppCommonTest {

    @Test
    fun mealEnrichment_usesCleanTitleWhenPresent() {
        val meal = Meal(
            id = "1",
            title = "Spätzlepfanne (a.1, c, g)",
            fullText = "Spätzlepfanne (a.1, c, g) mit Röstzwiebeln und Salat",
            cleanTitle = "Spätzlepfanne",
            sides = listOf("Röstzwiebeln", "Salat"),
        )
        val enriched = MealEnrichment.enrich(meal)
        assertEquals("Spätzlepfanne", enriched.cleanTitle)
        assertEquals(listOf("Röstzwiebeln", "Salat"), enriched.sides)
    }

    @Test
    fun mealEnrichment_stripsAllergenCodesWhenCleanTitleMissing() {
        val meal = Meal(
            id = "1",
            title = "Hähnchenbrust (a.1, g) mit Pommes",
            fullText = "Hähnchenbrust (a.1, g) mit Pommes und Salat (c)",
        )
        val enriched = MealEnrichment.enrich(meal)
        assertFalse(enriched.cleanTitle.contains("(a.1"))
        assertTrue(enriched.cleanTitle.contains("Hähnchenbrust"))
    }

    @Test
    fun mealEnrichment_dropsAllergenFragmentsLeakedIntoSides() {
        val meal = Meal(
            id = "1",
            title = "Gebratenes Strohschweinschnitzel",
            fullText = "Gebratenes Strohschweinschnitzel Champignonrahmsauce, Zitronenecke, Kräuterkartoffeln, Leipziger Allerlei (Fleisch, a, a.1, c, g, i, 3)",
            cleanTitle = "Gebratenes Strohschweinschnitzel",
            sides = listOf(
                "Champignonrahmsauce", "Zitronenecke", "Kräuterkartoffeln",
                "Leipziger Allerlei", "(Fleisch", "a", "a.1", "c", "g", "i", "3)",
            ),
        )
        val enriched = MealEnrichment.enrich(meal)
        assertEquals(
            listOf("Champignonrahmsauce", "Zitronenecke", "Kräuterkartoffeln", "Leipziger Allerlei"),
            enriched.sides,
        )
    }


    @Test
    fun example() {
        assertEquals(3, 1 + 2)
    }

    @Test
    fun shouldHideAfternoonMealsForCanteenOnDate_hidesForZentralmensaOnSaturday() {
        assertTrue(
            shouldHideAfternoonMealsForCanteenOnDate(
                canteen = Canteen(id = "1", name = "Zentralmensa"),
                meals = listOf(sampleMealDate(servedOn = "2026-04-18"))
            )
        )
    }

    @Test
    fun shouldHideAfternoonMealsForCanteenOnDate_doesNotHideForZentralmensaOnWeekday() {
        assertFalse(
            shouldHideAfternoonMealsForCanteenOnDate(
                canteen = Canteen(id = "1", name = "Zentralmensa"),
                meals = listOf(sampleMealDate(servedOn = "2026-04-20"))
            )
        )
    }

    @Test
    fun shouldHideAfternoonMealsForCanteenOnDate_doesNotHideForOtherCanteens() {
        assertFalse(
            shouldHideAfternoonMealsForCanteenOnDate(
                canteen = Canteen(id = "2", name = "Nordmensa"),
                meals = listOf(sampleMealDate(servedOn = "2026-04-18"))
            )
        )
    }

    @Test
    fun pastabuffetMealMatchesVegetarianFilterEvenWithMeatIcon() {
        val mealDate = createMealDate(
            title = "Pastabuffet",
            fullText = "mit Tomatensauce oder Bolognese",
            icons = listOf("fleisch")
        )

        assertTrue(mealMatchesDietaryFilters(mealDate, setOf("vegetarisch")))
    }

    @Test
    fun teppanYakiSubtitleMatchesVegetarianFilterEvenWithMeatIcon() {
        val mealDate = createMealDate(
            title = "Nudelteller",
            fullText = "Teppan Yaki mit zwei Saucen",
            icons = listOf("fleisch")
        )

        assertTrue(mealMatchesDietaryFilters(mealDate, setOf("vegetarisch")))
    }

    @Test
    fun regularMeatMealDoesNotMatchVegetarianFilter() {
        val mealDate = createMealDate(
            title = "Schnitzel",
            fullText = "mit Pommes",
            icons = listOf("fleisch")
        )

        assertFalse(mealMatchesDietaryFilters(mealDate, setOf("vegetarisch")))
    }

    private fun createMealDate(title: String, fullText: String, icons: List<String>): MealDate = MealDate(
        id = "meal-date-id",
        mealId = "meal-id",
        canteenId = "canteen-id",
        servedOn = "2026-04-20",
        category = "Hauptgericht",
        meals = Meal(
            id = "meal-id",
            title = title,
            fullText = fullText,
            icons = icons
        )
    )

    private fun sampleMealDate(servedOn: String): MealDate = MealDate(
        id = "meal-date-1",
        mealId = "meal-1",
        canteenId = "canteen-1",
        servedOn = servedOn,
        category = "Hauptgericht"
    )
}
