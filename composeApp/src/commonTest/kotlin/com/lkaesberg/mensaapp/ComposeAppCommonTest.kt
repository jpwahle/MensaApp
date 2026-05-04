package com.lkaesberg.mensaapp

import com.lkaesberg.mensaapp.data.CanteenInfo
import com.lkaesberg.mensaapp.data.FallbackPrice
import com.lkaesberg.mensaapp.data.Locale
import com.lkaesberg.mensaapp.data.MealEnrichment
import com.lkaesberg.mensaapp.data.PriceResolver
import com.lkaesberg.mensaapp.i18n.titleFor
import com.lkaesberg.mensaapp.i18n.sidesFor
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
                canteen = Canteen(id = "2", name = "CGiN"),
                meals = listOf(sampleMealDate(servedOn = "2026-04-18"))
            )
        )
    }

    // ─── 2026-05-05 API migration: per-dish prices, locale-aware text ───

    @Test
    fun forMealDate_prefersPerDishPricesOverFallback() {
        val md = MealDate(
            id = "x",
            mealId = "m",
            canteenId = "c",
            servedOn = "2026-05-05",
            category = "Menü",
            priceStudents = "3,95",
            priceEmployees = "5,70",
            priceGuests = "6,90",
        )
        val fallbackInfo = CanteenInfo(
            slug = "test", name = "Test", address = "",
            lat = 0.0, lng = 0.0, walkMinFromCenter = 0, distance = "",
            hours = emptyList(),
            fallbackPrices = listOf(FallbackPrice("Menü", "1,00", "2,00", "3,00")),
        )
        val resolved = PriceResolver.forMealDate(md, fallbackInfo)
        assertEquals("3,95", resolved?.students)
        assertEquals("5,70", resolved?.employees)
        assertEquals("6,90", resolved?.guests)
    }

    @Test
    fun forMealDate_fallsBackWhenPerDishPricesAreNull() {
        val md = MealDate(
            id = "x",
            mealId = "m",
            canteenId = "c",
            servedOn = "2026-05-05",
            category = "Menü",
        )
        val fallbackInfo = CanteenInfo(
            slug = "test", name = "Test", address = "",
            lat = 0.0, lng = 0.0, walkMinFromCenter = 0, distance = "",
            hours = emptyList(),
            fallbackPrices = listOf(FallbackPrice("Menü", "1,00", "2,00", "3,00")),
        )
        val resolved = PriceResolver.forMealDate(md, fallbackInfo)
        assertEquals("1,00", resolved?.students)
    }

    @Test
    fun forMealDate_stripsEuroSuffixFromLegacyData() {
        // Old rows scraped before the API migration have "Euro" baked into
        // the price text; sanitisation must remove it so the UI doesn't
        // double-stamp the unit.
        val md = MealDate(
            id = "x",
            mealId = "m",
            canteenId = "c",
            servedOn = "2026-05-05",
            category = "Menü",
            priceStudents = "3,95 Euro",
            priceEmployees = "---",
        )
        val resolved = PriceResolver.forMealDate(md, info = null)
        assertEquals("3,95", resolved?.students)
        assertEquals("", resolved?.employees)
    }

    @Test
    fun titleFor_returnsEnglishWhenAvailable() {
        val meal = Meal(
            id = "1",
            title = "Vegane Rote-Bete-Puffer",
            fullText = "Vegane Rote-Bete-Puffer",
            cleanTitle = "Vegane Rote-Bete-Puffer",
            titleEn = "Vegan beetroot patty",
        )
        assertEquals("Vegan beetroot patty", meal.titleFor(Locale.En))
        assertEquals("Vegane Rote-Bete-Puffer", meal.titleFor(Locale.De))
    }

    @Test
    fun titleFor_fallsBackToGermanWhenEnglishIsMissing() {
        val meal = Meal(
            id = "1",
            title = "Schnitzel",
            fullText = "Schnitzel",
            cleanTitle = "Schnitzel",
            titleEn = null,
        )
        assertEquals("Schnitzel", meal.titleFor(Locale.En))
    }

    @Test
    fun sidesFor_returnsEnglishWhenAvailableElseGerman() {
        val meal = Meal(
            id = "1",
            title = "Pasta",
            fullText = "Pasta",
            sides = listOf("Tomatensauce", "Salat"),
            sidesEn = listOf("tomato sauce", "salad"),
        )
        assertEquals(listOf("tomato sauce", "salad"), meal.sidesFor(Locale.En))
        assertEquals(listOf("Tomatensauce", "Salat"), meal.sidesFor(Locale.De))
        // English-empty rows fall back to German.
        val germanOnly = meal.copy(sidesEn = null)
        assertEquals(listOf("Tomatensauce", "Salat"), germanOnly.sidesFor(Locale.En))
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
