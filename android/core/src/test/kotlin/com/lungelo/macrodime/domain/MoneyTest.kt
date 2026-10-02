/*
 * MoneyTest.kt
 *
 * Port of MacroDimeTests/MoneyTests.swift: the currency bug, pinned down. The
 * catalogue is priced in USD; a different currency is a conversion at a rate
 * the user supplied, never a relabelling.
 */
package com.lungelo.macrodime.domain

import com.lungelo.macrodime.engine.FoodCatalog
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MoneyTest {

    // The catalogue's currency

    @Test
    fun catalogueAmountsAreTaggedAsUSD() {
        assertEquals("USD", PriceBook.CURRENCY_CODE)
        assertEquals("USD", Money.catalogue(9.0).currencyCode)
        assertEquals(9.0, Money.catalogue(9.0).amount)
        assertTrue(Money.catalogue(0.0).isZero)
    }

    @Test
    fun foodPricesCarryTheirCurrency() {
        val tuna = assertNotNull(FoodCatalog.food("canned-tuna-water"))
        assertEquals(PriceBook.CURRENCY_CODE, tuna.price.currencyCode)
        assertEquals(tuna.costPerServing, tuna.price.amount, 0.0001)
    }

    @Test
    fun defaultFormattingNeverInventsACurrency() {
        assertEquals(PriceBook.CURRENCY_CODE, CurrencySettings.USD.displayCode)
        assertFalse(CurrencySettings.USD.isConverting)
        assertEquals(DisplayFormat.currency(9.0, PriceBook.CURRENCY_CODE), CurrencySettings.USD.format(9.0))
        assertTrue("9" in DisplayFormat.currency(9.0))
    }

    /** Not in the iOS suite: the actual string a US user sees. */
    @Test
    fun usdRendersAsDollarsInTheUS() {
        assertEquals("$9.00", DisplayFormat.currency(9.0, "USD", Locale.US))
        assertEquals("$1,234.50", DisplayFormat.currency(1234.5, "USD", Locale.US))
    }

    /**
     * Not in the iOS suite: a South African device must still show a dollar
     * amount as dollars. This is the original bug, asserted on the output.
     */
    @Test
    fun aSouthAfricanDeviceIsNotShownARandSignOnADollarAmount() {
        val shown = DisplayFormat.currency(9.0, "USD", Locale.forLanguageTag("en-ZA"))
        assertFalse(shown.startsWith("R"), "a dollar amount rendered as $shown")
        assertTrue("9" in shown)
    }

    @Test
    fun currencyDecimalPlacesFollowTheCurrency() {
        assertEquals("¥1,850", DisplayFormat.currency(1850.0, "JPY", Locale.US))
    }

    @Test
    fun anUnknownCodeIsNamedRatherThanCrashing() {
        assertEquals("ABC 9.00", DisplayFormat.currency(9.0, "ABC", Locale.US))
    }

    // Conversion

    @Test
    fun conversionUsesTheSuppliedRate() {
        val rand = CurrencySettings("ZAR", 18.5)

        assertTrue(rand.isConverting)
        assertEquals(185.0, rand.convert(10.0), 0.0001)
        assertEquals(DisplayFormat.currency(185.0, "ZAR"), rand.format(10.0))
        assertNotEquals(CurrencySettings.USD.format(10.0), rand.format(10.0))
    }

    @Test
    fun storageConversionIsTheInverseOfDisplay() {
        val rand = CurrencySettings("ZAR", 18.5)
        assertEquals(12.0, rand.toStorage(rand.convert(12.0)), 0.000001)
        assertEquals(10.0, rand.toStorage(185.0), 0.000001)
    }

    @Test
    fun sameCurrencyOrUnitRateIsNotAConversion() {
        assertFalse(CurrencySettings("USD", 18.5).isConverting)
        assertFalse(CurrencySettings("ZAR", 1.0).isConverting)
        assertTrue(CurrencySettings("ZAR", 18.5).isConverting)
    }

    /**
     * Not in the iOS suite, and the iOS app fails it. A rate left over from an
     * earlier choice must not multiply dollar prices under a dollar sign:
     * choose ZAR, type 18.5, switch back to USD, and $9.00 read as $166.50.
     */
    @Test
    fun aLeftoverRateIsNotAppliedToDollars() {
        val leftover = CurrencySettings("USD", 18.5)
        assertEquals(10.0, leftover.convert(10.0), 0.0)
        assertEquals(CurrencySettings.USD.format(9.0), leftover.format(9.0))
        assertEquals(10.0, leftover.toStorage(10.0), 0.0, "a typed dollar budget must be stored as typed")
    }

    /**
     * Not in the iOS suite, and the iOS app fails it. Choosing a currency
     * before typing its rate must not put that currency's code on a dollar
     * amount, which is the original bug in a new place.
     */
    @Test
    fun aCurrencyWithNoRateYetStillShowsDollars() {
        val unrated = CurrencySettings("ZAR", 1.0)
        assertEquals("USD", unrated.shownCode)
        assertEquals(CurrencySettings.USD.format(9.0), unrated.format(9.0))
        assertEquals("ZAR", CurrencySettings("ZAR", 18.5).shownCode)
    }

    // Refusing bad rates

    @Test
    fun unusableRatesAreIgnored() {
        for (rate in listOf(0.0, -1.0, -18.5, Double.NaN, Double.POSITIVE_INFINITY, 0.0001, 1_000_000.0)) {
            assertFalse(Money.isUsable(rate), "$rate should be refused")
            assertEquals(10.0, Money.catalogue(10.0).converted("ZAR", rate).amount, 0.0001)
            assertFalse(CurrencySettings("ZAR", rate).isConverting)
        }
    }

    @Test
    fun plausibleRatesAreAccepted() {
        for (rate in listOf(0.5, 1.0, 1.08, 18.5, 150.0, 1500.0)) {
            assertTrue(Money.isUsable(rate), "$rate should be accepted")
        }
    }

    @Test
    fun convertingIntoTheSameCurrencyIsAnIdentity() {
        val money = Money.catalogue(12.34)
        assertEquals(money, money.converted("USD", 18.5))
        assertEquals(money, money.converted("usd", 18.5))
    }

    // Settings hygiene

    @Test
    fun emptyCodeFallsBackAndCodeIsNormalised() {
        assertEquals("USD", CurrencySettings("", 1.0).displayCode)
        assertEquals("ZAR", CurrencySettings("zar", 18.5).displayCode)
        assertEquals("USD", CurrencySettings("  ", 1.0).displayCode)
    }

    @Test
    fun localeSuggestionIsAUsableCode() {
        val suggestion = CurrencySettings.localeSuggestion()
        assertFalse(suggestion.isEmpty())
        assertEquals(suggestion.uppercase(Locale.ROOT), suggestion)
    }

    /** Not in the iOS suite: a locale with no country must not throw. */
    @Test
    fun aLocaleWithNoCountryFallsBackToUSD() {
        assertEquals("USD", CurrencySettings.localeSuggestion(Locale.forLanguageTag("en")))
    }

    @Test
    fun pickerOptionsLeadWithUSDAndNeverRepeat() {
        val options = CurrencySettings.pickerOptions("ZAR", Locale.forLanguageTag("en-ZA"))
        assertEquals("USD", options.first())
        assertEquals("ZAR", options[1])
        assertEquals(options.toSet().size, options.size)
    }

    @Test
    fun pickerOptionsAlwaysContainTheSavedCode() {
        val options = CurrencySettings.pickerOptions(" chf ", Locale.US)
        assertEquals(1, options.count { it == "CHF" })
        assertEquals(1, options.count { it == "USD" })
        assertFalse("" in options)
    }

    @Test
    fun rateIsNeverZeroSoArithmeticCannotDivideByIt() {
        val settings = CurrencySettings("ZAR", 0.0)
        assertEquals(1.0, settings.unitsPerUSD)
        assertEquals(10.0, settings.toStorage(10.0), 0.0001)
    }

    @Test
    fun disclaimerNamesTheSourceOfThePrices() {
        assertTrue("US" in PriceBook.RATE_DISCLAIMER)
        assertTrue("estimate" in PriceBook.RATE_DISCLAIMER.lowercase())
    }

    // Totals that add up (gap 22). A total over lines must equal the lines as
    // shown. Twin of the same section in MoneyTests.swift.

    private fun meal(vararg costs: Double): MealItem {
        val food = FoodCatalog.all.first()
        return MealItem("Test", MealSlot.Breakfast, costs.map { Portion(food.withCost(it)) })
    }

    /**
     * The bug from the first emulator screenshots: lines of $0.22, $0.57 and
     * $0.27 under a $1.05 header, because the header summed unrounded costs.
     */
    @Test
    fun aMealHeaderEqualsTheColumnUnderIt() {
        val usd = CurrencySettings.USD
        val breakfast = meal(0.215, 0.565, 0.265)

        assertEquals(listOf(0.22, 0.57, 0.27), breakfast.portions.map { usd.shown(it.cost) })
        assertEquals(1.06, usd.shownCost(breakfast), 1e-9)
        assertEquals(DisplayFormat.currency(1.06, "USD"), usd.formatTotal(breakfast.portions.map { it.cost }))
    }

    @Test
    fun aDayEqualsTheSumOfItsMealHeaders() {
        val usd = CurrencySettings.USD
        val day = listOf(meal(0.215, 0.565), meal(0.265, 1.005), meal(2.345))
        assertEquals(day.sumOf { usd.shownCost(it) }, usd.shownCost(day), 1e-9)
    }

    /**
     * Rounding in USD first would not have been enough: a converted column
     * rounds again. At R18.50 the lines show R4.07, R10.55 and R5.00, which add
     * to R19.62; converting the dollar total would have said R19.61.
     */
    @Test
    fun aConvertedColumnAddsUpInTheShownCurrency() {
        val rand = CurrencySettings("ZAR", 18.5)
        val lines = listOf(0.22, 0.57, 0.27)

        assertEquals(listOf(4.07, 10.55, 5.0), lines.map { rand.shown(it) })
        assertEquals(19.62, rand.shownTotal(lines), 1e-9)
        assertEquals(DisplayFormat.currency(19.62, "ZAR"), rand.formatTotal(lines))
    }

    /** Every amount from $0.000 to $3.000 in tenths of a cent rounds as a person would, halves up. */
    @Test
    fun halfCentsRoundUpAsAPersonWouldWorkItOut() {
        for (k in 0..3_000) {
            val expected = BigDecimal(k).movePointLeft(3).setScale(2, RoundingMode.HALF_UP).toDouble()
            assertEquals(expected, CurrencySettings.USD.shown(k / 1_000.0), "${k / 1_000.0}")
        }
        // Held in binary as 0.28499999..., which a plain rounding sends down.
        assertEquals(0.29, CurrencySettings.USD.shown(0.19 * 1.5))
    }

    @Test
    fun yenRoundsToWholeYenAndTheColumnStillAddsUp() {
        val yen = CurrencySettings("JPY", 150.0)
        assertEquals(0, yen.minorUnits)
        assertEquals(50.0, yen.shown(0.333))
        // Three lines of 0.45 yen each show as nothing, so the total is nothing
        // too, rather than the 1 yen their unrounded sum would print.
        assertEquals(0.0, yen.shownTotal(listOf(0.003, 0.003, 0.003)))
    }

    @Test
    fun dinarKeepsThreeDecimalsInRoundingAndPrinting() {
        assertEquals(3, CurrencyDigits.minorUnits("KWD"))
        assertEquals(3, CurrencyDigits.minorUnits(" kwd "))
        assertEquals(2, CurrencyDigits.minorUnits("ZAR"))
        assertEquals(2, CurrencyDigits.minorUnits("not a code"))
        assertEquals(0.002, CurrencySettings("KWD", 0.31).shown(0.005), 1e-12)
        assertTrue("1.500" in DisplayFormat.currency(1.5, "KWD", Locale.US))
    }

    /**
     * Two thousand random columns in five currencies, checked against exact
     * decimal arithmetic: every line shown is a whole number of the smallest
     * unit, and the total shown is exactly their sum.
     */
    @Test
    fun randomColumnsAlwaysAddUp() {
        val random = Random(22)
        val currencies = listOf(
            CurrencySettings.USD,
            CurrencySettings("ZAR", 18.5),
            CurrencySettings("GBP", 0.79),
            CurrencySettings("JPY", 151.3),
            CurrencySettings("KWD", 0.307),
        )
        repeat(2_000) {
            val lines = List(random.nextInt(1, 8)) { random.nextDouble(0.0, 6.0) }
            for (settings in currencies) {
                val scale = settings.minorUnits
                val exactSum = lines
                    .map { BigDecimal.valueOf(settings.shown(it)).setScale(scale, RoundingMode.UNNECESSARY) }
                    .fold(BigDecimal.ZERO, BigDecimal::add)
                val total = BigDecimal.valueOf(settings.shownTotal(lines)).setScale(scale, RoundingMode.UNNECESSARY)
                assertEquals(exactSum, total, "$lines in ${settings.shownCode}")
                assertEquals(DisplayFormat.currency(exactSum.toDouble(), settings.shownCode), settings.formatTotal(lines))
            }
        }
    }

    /**
     * A whole-meal swap is a chain of single swaps, and the sheet lists each
     * step's saving under a title with the total. Each step is the drop in the
     * shown cost, so the steps add up to the title exactly, in any currency.
     */
    @Test
    fun swapStepsAddUpToTheSavingInTheTitle() {
        val engine = com.lungelo.macrodime.engine.BudgetFoodEngine(catalog = FoodCatalog.all)
        val dinner = MealItem(
            "Salmon dinner",
            MealSlot.Dinner,
            listOf("salmon-fillet", "white-rice", "fresh-broccoli", "olive-oil").map { Portion(assertNotNull(FoodCatalog.food(it))) },
        )
        val swap = assertNotNull(engine.bestSwap(dinner))
        assertTrue(swap.portionSwaps.isNotEmpty())

        for (settings in listOf(CurrencySettings.USD, CurrencySettings("ZAR", 18.5))) {
            // The sheet's premise: the last step's meal is the swapped meal.
            assertEquals(settings.shownCost(swap.swapped), settings.shownCost(swap.portionSwaps.last().resultingMeal), 1e-9)

            val steps = swap.shownStepSavings(settings)
            assertEquals(swap.portionSwaps.size, steps.size)
            val title = swap.shownSaving(settings)
            assertEquals(title, steps.sum(), 1e-9)
            assertEquals(settings.shownCost(swap.original) - settings.shownCost(swap.swapped), title, 1e-9)
            assertTrue(title > 0, "a swap the engine offers must show a saving")
        }
    }
}
