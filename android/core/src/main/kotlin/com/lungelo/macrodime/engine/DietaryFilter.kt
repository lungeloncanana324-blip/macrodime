/*
 * DietaryFilter.kt
 * MacroDime
 *
 * One gate between the catalogue and everything that plans food. Port of
 * MacroDime/Engine/DietaryFilter.swift.
 *
 * Given a food and a profile, either it is allowed or it is rejected with the
 * reason. The reason matters as much as the verdict: the onboarding summary and
 * the plan audit both explain *why* the plan looks the way it does, and an
 * unexplained exclusion looks like a bug.
 *
 * A filter is only as good as its tags: a food with no traits is treated as
 * plant-only, so a missing trait means an unsafe suggestion rather than a
 * missing one. The catalogue tests check the tags directly.
 */
package com.lungelo.macrodime.engine

import com.lungelo.macrodime.domain.DietaryPattern
import com.lungelo.macrodime.domain.DietaryProfile
import com.lungelo.macrodime.domain.FoodCategory
import com.lungelo.macrodime.domain.FoodExclusion
import com.lungelo.macrodime.domain.FoodSnapshot
import com.lungelo.macrodime.domain.PrepEffort
import java.util.Locale

object DietaryFilter {

    /** Why a food was taken out of the running. */
    sealed interface Rejection {
        data class Pattern(val pattern: DietaryPattern) : Rejection
        data class Exclusion(val exclusion: FoodExclusion) : Rejection
        data object BlockedByUser : Rejection
        data class TooMuchPreparation(val effort: PrepEffort, val minutes: Int) : Rejection

        /** Plain-language reason, safe to show a user. */
        val reason: String
            get() = when (this) {
                is Pattern -> "not ${pattern.displayName.lowercase(Locale.ROOT)}"
                is Exclusion -> exclusion.exclusionReason
                BlockedByUser -> "on your avoid list"
                is TooMuchPreparation ->
                    "takes $minutes min, and you asked for ${effort.displayName.lowercase(Locale.ROOT)}"
            }

        /** A short label used to group rejections in counts. */
        val label: String
            get() = when (this) {
                is Pattern -> "Not ${pattern.displayName.lowercase(Locale.ROOT)}"
                is Exclusion -> "Contains ${exclusion.displayName.lowercase(Locale.ROOT)}"
                BlockedByUser -> "On your avoid list"
                is TooMuchPreparation -> "Over your ${effort.displayName.lowercase(Locale.ROOT)} limit"
            }
    }

    /**
     * The first reason this food cannot be suggested, or null if it can.
     *
     * Order is deliberate: a food the user banned is reported as banned, since
     * their own reason is the useful one, and what a food *is* outranks how
     * long it takes. Exclusions are checked in declaration order, so a food
     * that trips two of them always reports the same one.
     */
    fun rejection(food: FoodSnapshot, profile: DietaryProfile): Rejection? {
        if (food.id in profile.blockedFoodIds) return Rejection.BlockedByUser

        FoodExclusion.entries
            .firstOrNull { it in profile.exclusions && it.trait in food.traits }
            ?.let { return Rejection.Exclusion(it) }

        if (!profile.pattern.allows(food.traits)) return Rejection.Pattern(profile.pattern)

        if (food.prepMinutes > profile.prepEffort.maximumMinutes) {
            return Rejection.TooMuchPreparation(profile.prepEffort, food.prepMinutes)
        }
        return null
    }

    fun allows(food: FoodSnapshot, profile: DietaryProfile): Boolean = rejection(food, profile) == null

    /** The catalogue as this user may eat it. Ingredient order is preserved. */
    fun allowed(foods: List<FoodSnapshot>, profile: DietaryProfile): List<FoodSnapshot> =
        foods.filter { allows(it, profile) }

    fun rejected(foods: List<FoodSnapshot>, profile: DietaryProfile): List<FoodSnapshot> =
        foods.filterNot { allows(it, profile) }

    data class RejectionCount(val label: String, val count: Int)

    /** Rejections counted by reason, biggest first, then alphabetically. */
    fun rejectionBreakdown(foods: List<FoodSnapshot>, profile: DietaryProfile): List<RejectionCount> {
        val counts = LinkedHashMap<String, Int>()
        for (food in foods) {
            val rejection = rejection(food, profile) ?: continue
            counts[rejection.label] = (counts[rejection.label] ?: 0) + 1
        }
        return counts.map { (label, count) -> RejectionCount(label, count) }
            .sortedWith(compareByDescending<RejectionCount> { it.count }.thenBy { it.label })
    }

    /** Everything in one category this user may eat, cheapest first. */
    fun allowedIn(category: FoodCategory, foods: List<FoodSnapshot>, profile: DietaryProfile): List<FoodSnapshot> =
        allowed(foods, profile)
            .filter { it.category == category }
            .sortedBy { it.costPerServing }
}
