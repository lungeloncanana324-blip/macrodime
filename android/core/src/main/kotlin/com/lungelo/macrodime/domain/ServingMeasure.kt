/*
 * ServingMeasure.kt
 * MacroDime
 *
 * A serving description, understood well enough to be multiplied. Port of
 * MacroDime/Domain/ServingMeasure.swift.
 *
 * The catalogue describes one serving in words ("2 large eggs", "150 g raw"),
 * and the engine plans in quarter servings. Multiplying the words produced
 * "1.5 × 2 large eggs" on the plan. This type reads the words once into a
 * number, a unit and a noun, so 1.5 servings of eggs renders as "3 large eggs".
 * It is derived from the stored text rather than stored, so the schema does not
 * change, and ServingMeasureTest requires every catalogue serving to parse and
 * render back unchanged.
 */
package com.lungelo.macrodime.domain

import kotlin.math.abs

class ServingMeasure private constructor(val shape: Shape) {

    enum class Unit(val symbol: String) {
        Grams("g"),
        Millilitres("ml"),
    }

    /** A weight or volume and the state it is weighed in: `150 g raw`. */
    data class Amount(
        val value: Double,
        val unit: Unit,
        /** Trailing words such as `raw`, `dry` or `drained`. Null when bare. */
        val qualifier: String?,
    ) {
        fun scaled(factor: Double) = copy(value = value * factor)

        val text: String
            get() {
                val base = format(value, unit)
                return if (qualifier != null) "$base $qualifier" else base
            }
    }

    /** The thing being counted, in both numbers: `large egg` and `large eggs`. */
    data class Noun(val singular: String, val plural: String) {
        /** Plural above one: "1 can", "½ can", "1¼ cans". */
        fun formFor(count: Double): String = if (count > 1) plural else singular
    }

    sealed interface Shape {
        /** Weighed or poured: `150 g raw`, `240 ml`. */
        data class Measured(val amount: Amount) : Shape

        /** Counted, with an optional weight note: `2 large eggs`, `1 can (142 g drained)`. */
        data class Counted(val count: Double, val noun: Noun, val note: Amount?) : Shape
    }

    /** The serving multiplied by [factor], in the catalogue's own style. */
    fun text(times: Double): String = when (val shape = shape) {
        is Shape.Measured -> shape.amount.scaled(times).text
        is Shape.Counted -> {
            val total = shape.count * times
            val head = "${count(total)} ${shape.noun.formFor(total)}"
            if (shape.note == null) head else "$head (${shape.note.scaled(times).text})"
        }
    }

    override fun equals(other: Any?): Boolean = other is ServingMeasure && other.shape == shape

    override fun hashCode(): Int = shape.hashCode()

    companion object {

        /**
         * Reads `<number> g|ml [qualifier]` or
         * `<number> <noun> [(<number> g|ml [qualifier])]`. Null for anything else.
         */
        fun parse(raw: String): ServingMeasure? {
            var head = raw.trim()
            var note: Amount? = null

            if (head.endsWith(")")) {
                val open = head.lastIndexOf(" (")
                if (open >= 0) {
                    val inside = head.substring(open + 2, head.length - 1)
                    note = amount(inside) ?: return null
                    head = head.substring(0, open)
                }
            }

            amount(head)?.let { measured ->
                if (note != null) return null
                return ServingMeasure(Shape.Measured(measured))
            }

            val words = words(head)
            if (words.size < 2) return null
            val count = number(words[0]) ?: return null
            if (count <= 0) return null
            val phrase = words.drop(1).joinToString(" ")
            if (!phrase.first().isLetter()) return null

            // The catalogue writes the noun to agree with its own count: "1 can",
            // "½ medium", "2 slices". The other form is derived from that one.
            val noun = if (count > 1) {
                Noun(singular = inflect(phrase, plural = false), plural = phrase)
            } else {
                Noun(singular = phrase, plural = inflect(phrase, plural = true))
            }
            return ServingMeasure(Shape.Counted(count, noun, note))
        }

        /**
         * Any serving description multiplied out. Text the parser cannot read,
         * which only a user-created food can have, falls back to naming the
         * servings rather than multiplying words: `1½ servings (1 bowl)`.
         */
        fun describe(text: String, times: Double): String {
            parse(text)?.let { return it.text(times) }
            if (times == 1.0) return text
            val unit = if (times > 1) "servings" else "serving"
            return "${count(times)} $unit ($text)"
        }

        /** Splits on spaces and drops empty pieces, as Swift's `split(separator:)` does. */
        private fun words(text: String): List<String> = text.split(' ').filter { it.isNotEmpty() }

        /** `150 g raw`, `240 ml`, `142 g drained`. */
        private fun amount(text: String): Amount? {
            val words = words(text)
            if (words.size < 2) return null
            val value = number(words[0]) ?: return null
            val unit = Unit.entries.firstOrNull { it.symbol == words[1] } ?: return null
            val rest = words.drop(2).joinToString(" ")
            return Amount(value, unit, rest.ifEmpty { null })
        }

        private val GLYPHS = mapOf(
            '⅛' to 0.125, '¼' to 0.25, '⅜' to 0.375, '½' to 0.5,
            '⅝' to 0.625, '¾' to 0.75, '⅞' to 0.875,
        )

        private val PLAIN_NUMBER = Regex("""\d+(\.\d+)?|\.\d+""")

        /** `2`, `1.5`, `1/2`, `½`, `1½`. */
        private fun number(token: String): Double? {
            if (PLAIN_NUMBER.matches(token)) return token.toDouble()
            val fraction = token.lastOrNull()?.let { GLYPHS[it] }
            if (fraction != null) {
                val whole = token.dropLast(1)
                if (whole.isEmpty()) return fraction
                return if (PLAIN_NUMBER.matches(whole)) whole.toDouble() + fraction else null
            }
            val parts = token.split('/')
            if (parts.size == 2 && PLAIN_NUMBER.matches(parts[0]) && PLAIN_NUMBER.matches(parts[1])) {
                val bottom = parts[1].toDouble()
                if (bottom > 0) return parts[0].toDouble() / bottom
            }
            return null
        }

        /** Words that read the same in both numbers: `1 tbsp`, `2 tbsp`, `2 medium`. */
        private val INVARIANT = setOf("tbsp", "tsp", "medium", "small", "large", "oz")

        /**
         * Inflects the head noun of a phrase: `large eggs` to `large egg`, and
         * `bowl of soup` to `bowls of soup` (the noun before "of", which a
         * user-created food is likely to use). Deliberately simple: the
         * catalogue's phrases are all covered by the round-trip test.
         */
        private fun inflect(phrase: String, plural: Boolean): String {
            val of = phrase.indexOf(" of ")
            if (of >= 0) {
                return inflect(phrase.substring(0, of), plural) + phrase.substring(of)
            }
            val words = words(phrase).toMutableList()
            val last = words.lastOrNull() ?: return phrase
            if (last in INVARIANT) return phrase
            if (plural) {
                if (!last.endsWith("s")) words[words.lastIndex] = last + "s"
            } else if (last.endsWith("s")) {
                words[words.lastIndex] = last.dropLast(1)
            }
            return words.joinToString(" ")
        }

        private val EIGHTHS = listOf("", "⅛", "¼", "⅜", "½", "⅝", "¾", "⅞")

        /**
         * A count in kitchen fractions: `3`, `½`, `2½`, `1¼`. The engine steps in
         * quarter servings, so eighths cover every count it produces; anything
         * else falls back to at most two decimals.
         */
        fun count(value: Double): String {
            val eighths = (value * 8).roundedHalfAway()
            if (eighths <= 0 || abs(value * 8 - eighths) >= 0.001) {
                return DisplayFormat.flexible(value, maxDecimals = 2)
            }
            val whole = eighths.toInt() / 8
            val glyph = EIGHTHS[eighths.toInt() % 8]
            return if (whole == 0) glyph else "$whole$glyph"
        }

        /** Whole grams or millilitres, switching to kg or L from 1,000. */
        fun format(value: Double, unit: Unit): String {
            if (value < 1_000) return "${value.roundToIntHalfAway()} ${unit.symbol}"
            val large = DisplayFormat.flexible(value / 1_000, maxDecimals = 1)
            return "$large ${if (unit == Unit.Grams) "kg" else "L"}"
        }
    }
}
