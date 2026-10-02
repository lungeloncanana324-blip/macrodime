/*
 * MoneyTest.kt
 *
 * Port of MacroDimeTests/MoneyTests.swift: the currency bug, pinned down. The
 * catalogue is priced in USD; a different currency is a conversion at a rate
 * the user supplied, never a relabelling.
 */
package com.lungelo.macrodime.domain

import com.lungelo.macrodime.engine.FoodCatalog
import java.util.Locale
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
}
