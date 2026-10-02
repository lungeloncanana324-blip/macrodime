/*
 * Rounding.kt
 * MacroDime
 *
 * Swift's `rounded()` rounds a half away from zero. Kotlin's `round()` rounds
 * it to the nearest even number, so a straight port would turn 177.5 g into
 * 178 g but 176.5 g into 176 g, and quantise servings differently from the iOS
 * engine on exact quarter steps. Every rounding the engine and the display do
 * goes through these two functions instead.
 */
package com.lungelo.macrodime.domain

import kotlin.math.truncate

/** To the nearest whole number, halves away from zero, as Swift's `rounded()`. */
fun Double.roundedHalfAway(): Double {
    if (isNaN() || isInfinite()) return this
    val whole = truncate(this)
    // Exact: subtracting a double's own integer part never rounds.
    val fraction = this - whole
    return when {
        fraction >= 0.5 -> whole + 1
        fraction <= -0.5 -> whole - 1
        else -> whole
    }
}

/** [roundedHalfAway] as an Int, as Swift's `Int(value.rounded())`. */
fun Double.roundToIntHalfAway(): Int = roundedHalfAway().toInt()
