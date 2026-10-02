/*
 * UnitConversion.kt
 * MacroDime
 *
 * Metric is the storage format everywhere: the science engine, the database
 * and the food catalogue all speak kg/cm/g. Imperial exists only at the UI
 * edge, and this is the only place the two meet. Port of
 * MacroDime/Domain/UnitConversion.swift.
 */
package com.lungelo.macrodime.domain

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object UnitConversion {

    // Mass

    const val POUNDS_PER_KILOGRAM = 2.20462262185

    fun kilogramsFromPounds(pounds: Double): Double = pounds / POUNDS_PER_KILOGRAM

    fun poundsFromKilograms(kilograms: Double): Double = kilograms * POUNDS_PER_KILOGRAM

    // Length

    const val CENTIMETRES_PER_INCH = 2.54
    const val INCHES_PER_FOOT = 12.0

    fun centimetresFromInches(inches: Double): Double = inches * CENTIMETRES_PER_INCH

    fun inchesFromCentimetres(centimetres: Double): Double = centimetres / CENTIMETRES_PER_INCH

    data class FeetAndInches(val feet: Int, val inches: Int)

    /**
     * Splits a height into whole feet plus remaining inches. Inches are rounded,
     * and a result of 12 is carried into the feet so a picker can never show
     * `5' 12"`.
     */
    fun feetAndInches(centimetres: Double): FeetAndInches {
        val totalInches = inchesFromCentimetres(centimetres)
        var feet = (totalInches / INCHES_PER_FOOT).toInt()
        var remainder = (totalInches - feet * INCHES_PER_FOOT).roundToIntHalfAway()
        if (remainder >= INCHES_PER_FOOT.toInt()) {
            feet += 1
            remainder = 0
        }
        return FeetAndInches(feet, remainder)
    }

    fun centimetres(feet: Int, inches: Int): Double =
        centimetresFromInches(feet * INCHES_PER_FOOT + inches)
}

/**
 * Display formatting used across the screens. Centralised so a unit toggle or a
 * locale change never leaves one screen disagreeing with another.
 *
 * Every function reads the device locale at call time unless one is passed, so
 * a locale change while the app runs is picked up on the next frame.
 */
object DisplayFormat {

    /**
     * Currency in an explicit currency code.
     *
     * The code is required rather than inferred from the locale. The catalogue
     * is priced in USD (see [PriceBook]), and formatting a dollar amount with
     * the device's currency put a rand or euro sign on a dollar figure, which is
     * a wrong number presented as a right one.
     */
    fun currency(amount: Double, code: String = PriceBook.CURRENCY_CODE, locale: Locale = Locale.getDefault()): String {
        val currency = try {
            Currency.getInstance(code)
        } catch (_: IllegalArgumentException) {
            // A code the platform does not know, from a damaged store: say the
            // code rather than guess a symbol.
            return "$code ${number(amount, decimals = 2, locale = locale)}"
        }
        val format = NumberFormat.getCurrencyInstance(locale)
        format.currency = currency
        // Setting the currency does not move the decimal places with it: yen
        // has none, dinar has three.
        val digits = currency.defaultFractionDigits.coerceAtLeast(0)
        format.minimumFractionDigits = digits
        format.maximumFractionDigits = digits
        return format.format(amount)
    }

    /**
     * Whole kilocalories, grouped for the locale: `1,842 kcal`. The grouping is
     * explicit because one screen interpolating a bare Int printed `1842 kcal`
     * while another printed `1,681`.
     */
    fun calories(value: Double, locale: Locale = Locale.getDefault()): String =
        "${NumberFormat.getIntegerInstance(locale).format(value.roundToIntHalfAway())} kcal"

    /** Whole grams, grouped for the locale: `148 g`. */
    fun grams(value: Double, locale: Locale = Locale.getDefault()): String =
        "${NumberFormat.getIntegerInstance(locale).format(value.roundToIntHalfAway())} g"

    /** Weight in the user's chosen system, one decimal place. */
    fun weight(kilograms: Double, system: MeasurementSystem): String = when (system) {
        MeasurementSystem.Metric -> "${number(kilograms, 1)} kg"
        MeasurementSystem.Imperial -> "${number(UnitConversion.poundsFromKilograms(kilograms), 1)} lb"
    }

    /** Height in the user's chosen system. */
    fun height(centimetres: Double, system: MeasurementSystem): String = when (system) {
        MeasurementSystem.Metric -> "${centimetres.roundToIntHalfAway()} cm"
        MeasurementSystem.Imperial -> {
            val parts = UnitConversion.feetAndInches(centimetres)
            "${parts.feet}' ${parts.inches}\""
        }
    }

    /** A percentage from a 0-1 fraction: `87%`. */
    fun percent(fraction: Double, locale: Locale = Locale.getDefault()): String {
        val format = NumberFormat.getPercentInstance(locale)
        format.maximumFractionDigits = 0
        return format.format(fraction)
    }

    /** A number at exactly [decimals] places: `24.3`. */
    fun number(value: Double, decimals: Int = 1, locale: Locale = Locale.getDefault()): String {
        val format = NumberFormat.getNumberInstance(locale)
        format.minimumFractionDigits = decimals
        format.maximumFractionDigits = decimals
        return format.format(value)
    }

    /** A number with up to [maxDecimals] places and no trailing zeros: `2.25`, `1`. */
    fun flexible(value: Double, maxDecimals: Int, locale: Locale = Locale.getDefault()): String {
        val format = NumberFormat.getNumberInstance(locale)
        format.minimumFractionDigits = 0
        format.maximumFractionDigits = maxDecimals
        return format.format(value)
    }
}
