/*
 * Money.kt
 * MacroDime
 *
 * Every price in FoodCatalog is a US national-average supermarket price, so
 * every price in this app is in USD. Formatting those amounts with the device
 * locale's currency put a rand, euro or naira sign in front of a dollar figure:
 * a South African user reading "R9.00" for a day of food is being told something
 * false, and the error is invisible to anyone testing in the US. Port of
 * MacroDime/Domain/Money.swift.
 *
 * Three rules, enforced by tests:
 *  1. The catalogue's currency is named in one place (PriceBook), and a
 *     catalogue amount is never rendered with a different code.
 *  2. Conversion happens on a rate the user supplied (CurrencySettings), never
 *     on an invented exchange rate. No network, no stale table.
 *  3. Storage stays in one currency, USD. Only display converts.
 */
package com.lungelo.macrodime.domain

import java.util.Currency
import java.util.Locale
import kotlin.math.pow

/** The currency every price in the catalogue is denominated in. Describes the data. */
object PriceBook {
    const val CURRENCY_CODE = "USD"

    /**
     * Shown wherever a converted figure could otherwise be mistaken for a local
     * price, so the user knows what they are looking at.
     */
    const val RATE_DISCLAIMER =
        "Prices are US supermarket averages. The rate is your own estimate, so treat converted amounts as approximate."
}

/**
 * How many digits a currency is shown and rounded with: cents for dollars and
 * rand, none for yen, three for dinar (ISO 4217 minor units).
 *
 * One table, read by both the formatter and the rounding, so a figure is never
 * rounded to one precision and printed at another. The iOS app holds the same
 * table, so the two platforms agree on every code.
 */
object CurrencyDigits {
    private val NONE = setOf(
        "BIF", "CLP", "DJF", "GNF", "ISK", "JPY", "KMF", "KRW", "PYG",
        "RWF", "UGX", "UYI", "VND", "VUV", "XAF", "XOF", "XPF",
    )
    private val THREE = setOf("BHD", "IQD", "JOD", "KWD", "LYD", "OMR", "TND")

    fun minorUnits(code: String): Int = when (code.trim().uppercase(Locale.ROOT)) {
        in NONE -> 0
        in THREE -> 3
        else -> 2
    }

    /**
     * [amount] to [digits] decimal places, halves away from zero. The nudge,
     * a ten-millionth of the smallest unit, keeps a half cent that binary
     * floating point stores just below the half (0.19 x 1.5 is held as
     * 0.28499999...) rounding up, as a person working it out would.
     */
    fun round(amount: Double, digits: Int): Double {
        if (amount.isNaN() || amount.isInfinite()) return amount
        val scale = 10.0.pow(digits)
        val scaled = amount * scale
        val nudged = scaled + if (scaled >= 0) 1e-7 else -1e-7
        return nudged.roundedHalfAway() / scale
    }
}

/**
 * An amount in a named currency. Deliberately tiny: it exists so an amount
 * cannot be formatted without saying what it is denominated in.
 */
data class Money(val amount: Double, val currencyCode: String) {

    val isZero: Boolean get() = amount == 0.0

    /**
     * Converts at a caller-supplied rate, in units of [code] per one unit of
     * this currency. A non-positive or absurd rate is refused rather than
     * applied, because a garbage rate produces garbage output silently.
     */
    fun converted(code: String, unitsPerUnit: Double): Money {
        val target = code.trim().uppercase(Locale.ROOT)
        if (!isUsable(unitsPerUnit)) return this
        if (target.isEmpty() || target == currencyCode) return this
        return Money(amount * unitsPerUnit, target)
    }

    companion object {
        /** An amount taken from the catalogue, which is always USD. */
        fun catalogue(amount: Double) = Money(amount, PriceBook.CURRENCY_CODE)

        val ZERO = catalogue(0.0)

        /** Whether a rate is present and plausible enough to use. */
        fun isUsable(rate: Double): Boolean = rate.isFinite() && rate > 0.01 && rate < 10_000
    }
}

/**
 * How the user wants amounts shown: the catalogue's USD, or their own currency
 * at a rate they typed in themselves. [unitsPerUSD] is the only conversion
 * factor in the app. A user who never touches it sees honest USD figures rather
 * than a wrong local symbol.
 */
class CurrencySettings(displayCode: String, unitsPerUSD: Double) {

    /** The currency amounts are rendered in. */
    val displayCode: String = displayCode.trim().uppercase(Locale.ROOT).ifEmpty { PriceBook.CURRENCY_CODE }

    /** Units of [displayCode] per 1 USD. 1 when not converting. */
    val unitsPerUSD: Double = if (Money.isUsable(unitsPerUSD)) unitsPerUSD else 1.0

    /** True when a real conversion is being applied. */
    val isConverting: Boolean get() = displayCode != PriceBook.CURRENCY_CODE && unitsPerUSD != 1.0

    /**
     * The currency amounts are actually shown in. The chosen code only once a
     * real rate makes it a conversion; until then, honest USD.
     *
     * Applying the code and the rate independently was a bug, inherited from
     * the iOS app: choosing ZAR before typing a rate labelled every dollar
     * amount as rand, and switching back to USD after typing 18.5 multiplied
     * every dollar price by 18.5 under a dollar sign.
     */
    val shownCode: String get() = if (isConverting) displayCode else PriceBook.CURRENCY_CODE

    /** The factor actually applied: the user's rate while converting, otherwise 1. */
    private val appliedRate: Double get() = if (isConverting) unitsPerUSD else 1.0

    /** A catalogue amount (USD) in the shown currency. */
    fun convert(usd: Double): Double = usd * appliedRate

    /** A number typed in the shown currency, in USD for storage. The inverse of [convert]. */
    fun toStorage(displayAmount: Double): Double = displayAmount / appliedRate

    /** Digits after the decimal point in [shownCode]: 2 for dollars and rand, 0 for yen. */
    val minorUnits: Int get() = CurrencyDigits.minorUnits(shownCode)

    /**
     * A catalogue amount (USD) exactly as the screen shows it: converted, then
     * rounded to the shown currency's smallest unit.
     */
    fun shown(usd: Double): Double = CurrencyDigits.round(convert(usd), minorUnits)

    /**
     * Lines added up the way a reader adds them: each as shown, then summed.
     *
     * Summing the unrounded costs and rounding once let a total miss a cent
     * against the column above it: a breakfast of $0.22, $0.57 and $0.27 was
     * headed $1.05. Rounding in USD first would not cure it either, because a
     * converted column rounds again in the shown currency; so every total over
     * lines is built from the lines as shown, in the shown currency.
     */
    fun shownTotal(usdLines: Iterable<Double>): Double =
        CurrencyDigits.round(usdLines.sumOf { shown(it) }, minorUnits)

    /** A meal's cost as shown: the sum of its lines as shown, so the header equals the column. */
    fun shownCost(meal: MealItem): Double = shownTotal(meal.portions.map { it.cost })

    /** A day's cost as shown: every line of every meal, as shown. Equals the sum of the meal headers. */
    fun shownCost(meals: Iterable<MealItem>): Double = shownTotal(meals.flatMap { meal -> meal.portions.map { it.cost } })

    /** What a swap saves as shown: exactly the drop in the meal's shown cost. */
    fun shownSaving(from: MealItem, to: MealItem): Double =
        CurrencyDigits.round(shownCost(from) - shownCost(to), minorUnits)

    /** Formats a USD amount, converting first when the user opted in. The only path a price takes to the screen. */
    fun format(usd: Double): String = DisplayFormat.currency(shown(usd), shownCode)

    /** Formats a total of USD lines as [shownTotal] builds it. */
    fun formatTotal(usdLines: Iterable<Double>): String = DisplayFormat.currency(shownTotal(usdLines), shownCode)

    /** Formats an amount already in shown units, such as a difference of two shown totals. */
    fun formatDisplayAmount(amount: Double): String =
        DisplayFormat.currency(CurrencyDigits.round(amount, minorUnits), shownCode)

    override fun equals(other: Any?): Boolean =
        other is CurrencySettings && other.displayCode == displayCode && other.unitsPerUSD == unitsPerUSD

    override fun hashCode(): Int = 31 * displayCode.hashCode() + unitsPerUSD.hashCode()

    override fun toString(): String = "CurrencySettings($displayCode, $unitsPerUSD per USD)"

    companion object {
        /** The default: the catalogue's own currency, no conversion claimed. */
        val USD = CurrencySettings(PriceBook.CURRENCY_CODE, 1.0)

        /**
         * The device locale's currency, offered as a starting point in Settings.
         * Never applied automatically: adopting it without a rate would put the
         * wrong symbol back on a dollar amount.
         */
        fun localeSuggestion(locale: Locale = Locale.getDefault()): String {
            val code = try {
                Currency.getInstance(locale)?.currencyCode
            } catch (_: IllegalArgumentException) {
                // A locale with no country, such as plain "en".
                null
            }
            return (code ?: PriceBook.CURRENCY_CODE).uppercase(Locale.ROOT)
        }

        /** Offered alongside USD and the device's currency. Each still needs a typed rate. */
        val COMMON_CODES = listOf("ZAR", "GBP", "EUR", "CAD", "AUD", "NZD", "INR", "NGN", "KES")

        /**
         * What the Settings picker lists: USD, the device's currency, the saved
         * code, then [COMMON_CODES], each once. The saved code is always in the
         * list, or the picker would show a selection it has no row for.
         */
        fun pickerOptions(current: String, locale: Locale = Locale.getDefault()): List<String> {
            val candidates = listOf(PriceBook.CURRENCY_CODE, localeSuggestion(locale), current) + COMMON_CODES
            val seen = LinkedHashSet<String>()
            for (candidate in candidates) {
                val code = candidate.trim().uppercase(Locale.ROOT)
                if (code.isNotEmpty()) seen.add(code)
            }
            return seen.toList()
        }
    }
}
