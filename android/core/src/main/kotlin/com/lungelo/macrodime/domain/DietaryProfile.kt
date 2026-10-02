/*
 * DietaryProfile.kt
 * MacroDime
 *
 * The questions onboarding has to ask, and the vocabulary the engine uses to
 * answer them. Port of MacroDime/Domain/DietaryProfile.swift.
 *
 * Targets alone do not make a plan a person can follow. A 160 g protein target
 * is useless to someone who does not eat meat if every anchor in the plan is
 * chicken, so the catalogue is filtered through this profile before the engine
 * plans anything at all. The filter lives in DietaryFilter.
 */
package com.lungelo.macrodime.domain

import java.util.Locale

// MARK: - Traits

/**
 * What an ingredient *is*, in dietary terms. A bit set rather than a list of
 * booleans because the questions compose: an allergen filter and a dietary
 * pattern read the same flags, and a food can carry several.
 *
 * The bit positions match the iOS `FoodTraits` option set, so the stored
 * integer means the same thing on both platforms.
 */
@JvmInline
value class FoodTraits(val rawValue: Int) {

    /** True when every flag in [other] is set here, as Swift's `OptionSet.contains`. */
    operator fun contains(other: FoodTraits): Boolean = rawValue and other.rawValue == other.rawValue

    fun intersection(other: FoodTraits) = FoodTraits(rawValue and other.rawValue)

    fun union(other: FoodTraits) = FoodTraits(rawValue or other.rawValue)

    /** The union, so a set reads as `MEAT + RED_MEAT`. */
    operator fun plus(other: FoodTraits) = union(other)

    val isEmpty: Boolean get() = rawValue == 0

    /** Human-readable names, in a stable order, for chips and audit copy. */
    val labels: List<String> get() = NAMED.filter { (trait, _) -> trait in this }.map { it.second }

    val summary: String
        get() = labels.ifEmpty { null }?.joinToString(", ") ?: "no declared dietary flags"

    override fun toString(): String = "FoodTraits(${labels.joinToString(", ")})"

    companion object {
        val NONE = FoodTraits(0)

        /** Flesh from a mammal or a bird. Excluded by every non-omnivore pattern. */
        val MEAT = FoodTraits(1 shl 0)

        /**
         * Beef, pork, lamb. Tagged apart from poultry because some users avoid
         * red meat specifically, and pork is excluded by several faiths while
         * beef is not.
         */
        val RED_MEAT = FoodTraits(1 shl 1)
        val PORK = FoodTraits(1 shl 2)
        val FISH = FoodTraits(1 shl 3)
        val SHELLFISH = FoodTraits(1 shl 4)
        val DAIRY = FoodTraits(1 shl 5)
        val EGG = FoodTraits(1 shl 6)
        val GLUTEN = FoodTraits(1 shl 7)
        val NUTS = FoodTraits(1 shl 8)
        val SOY = FoodTraits(1 shl 9)
        val HONEY = FoodTraits(1 shl 10)

        /** Everything that requires an animal to have died. */
        val ANIMAL_FLESH = MEAT + FISH + SHELLFISH

        /** Everything an animal produces too, which is the line veganism draws. */
        val ANIMAL_DERIVED = MEAT + FISH + SHELLFISH + DAIRY + EGG + HONEY

        val NAMED: List<Pair<FoodTraits, String>> = listOf(
            MEAT to "meat",
            RED_MEAT to "red meat",
            PORK to "pork",
            FISH to "fish",
            SHELLFISH to "shellfish",
            DAIRY to "dairy",
            EGG to "egg",
            GLUTEN to "gluten",
            NUTS to "nuts",
            SOY to "soy",
            HONEY to "honey",
        )
    }
}

// MARK: - Pattern

/** The broadest question: what does the user eat? */
enum class DietaryPattern(override val rawValue: String) : RawValued {
    Omnivore("omnivore"),
    Pescatarian("pescatarian"),
    Vegetarian("vegetarian"),
    Vegan("vegan");

    val displayName: String
        get() = when (this) {
            Omnivore -> "Omnivore"
            Pescatarian -> "Pescatarian"
            Vegetarian -> "Vegetarian"
            Vegan -> "Vegan"
        }

    val subtitle: String
        get() = when (this) {
            Omnivore -> "Everything, including meat and fish"
            Pescatarian -> "Fish and shellfish, no meat"
            Vegetarian -> "No fish or meat, dairy and eggs are fine"
            Vegan -> "Nothing from an animal"
        }

    /** Whether a food carrying these traits may appear in the plan. */
    fun allows(traits: FoodTraits): Boolean = when (this) {
        Omnivore -> true
        Pescatarian -> FoodTraits.MEAT !in traits
        Vegetarian -> FoodTraits.MEAT !in traits && FoodTraits.FISH !in traits && FoodTraits.SHELLFISH !in traits
        Vegan -> traits.intersection(FoodTraits.ANIMAL_DERIVED).isEmpty
    }
}

// MARK: - Exclusions

/**
 * A food group the user avoids, for reasons they are not asked to explain:
 * allergy, intolerance, faith, or taste.
 */
enum class FoodExclusion(override val rawValue: String) : RawValued {
    Dairy("dairy"),
    Egg("egg"),
    Gluten("gluten"),
    Nuts("nuts"),
    Soy("soy"),
    Shellfish("shellfish"),
    Fish("fish"),
    Pork("pork"),
    RedMeat("redMeat");

    /** The trait that disqualifies a food. */
    val trait: FoodTraits
        get() = when (this) {
            Dairy -> FoodTraits.DAIRY
            Egg -> FoodTraits.EGG
            Gluten -> FoodTraits.GLUTEN
            Nuts -> FoodTraits.NUTS
            Soy -> FoodTraits.SOY
            Shellfish -> FoodTraits.SHELLFISH
            Fish -> FoodTraits.FISH
            Pork -> FoodTraits.PORK
            RedMeat -> FoodTraits.RED_MEAT
        }

    val displayName: String
        get() = when (this) {
            Dairy -> "Dairy"
            Egg -> "Eggs"
            Gluten -> "Gluten"
            Nuts -> "Nuts"
            Soy -> "Soy"
            Shellfish -> "Shellfish"
            Fish -> "Fish"
            Pork -> "Pork"
            RedMeat -> "Red meat"
        }

    /** The phrase used when explaining why an ingredient was dropped. */
    val exclusionReason: String
        get() = when (this) {
            Dairy -> "contains dairy"
            Egg -> "contains egg"
            Gluten -> "contains gluten"
            Nuts -> "contains nuts"
            Soy -> "contains soy"
            Shellfish -> "is shellfish"
            Fish -> "is fish"
            Pork -> "is pork"
            RedMeat -> "is red meat"
        }
}

// MARK: - Schedule

/** Which slots the plan should fill. */
enum class EatingSchedule(override val rawValue: String) : RawValued {
    TwoMeals("twoMeals"),
    ThreeMeals("threeMeals"),
    ThreeMealsAndSnacks("threeMealsAndSnacks");

    val displayName: String
        get() = when (this) {
            TwoMeals -> "2 meals"
            ThreeMeals -> "3 meals"
            ThreeMealsAndSnacks -> "3 meals + snacks"
        }

    val subtitle: String
        get() = when (this) {
            TwoMeals -> "Lunch and dinner"
            ThreeMeals -> "Breakfast, lunch, dinner"
            ThreeMealsAndSnacks -> "Three meals plus planned snacks"
        }

    /** The slots the user is expected to plan for. */
    val slots: List<MealSlot>
        get() = when (this) {
            TwoMeals -> listOf(MealSlot.Lunch, MealSlot.Dinner)
            ThreeMeals -> listOf(MealSlot.Breakfast, MealSlot.Lunch, MealSlot.Dinner)
            ThreeMealsAndSnacks -> listOf(MealSlot.Breakfast, MealSlot.Lunch, MealSlot.Dinner, MealSlot.Snack)
        }

    /** Eating occasions a day, snacks included. */
    val occasionsPerDay: Int
        get() = when (this) {
            TwoMeals -> 2
            ThreeMeals -> 3
            ThreeMealsAndSnacks -> 4
        }

    fun includes(slot: MealSlot): Boolean = slot in slots
}

// MARK: - Cooking effort

/**
 * How much time the user will actually spend cooking. A plan full of 40-minute
 * lentils is not a plan for someone who eats out of a microwave.
 */
enum class PrepEffort(override val rawValue: String) : RawValued {
    NoCook("noCook"),
    Quick("quick"),
    Standard("standard"),
    Unlimited("unlimited");

    /** The longest single ingredient preparation this effort allows, in minutes. */
    val maximumMinutes: Int
        get() = when (this) {
            NoCook -> 5
            Quick -> 15
            Standard -> 30
            Unlimited -> 180
        }

    val displayName: String
        get() = when (this) {
            NoCook -> "No cooking"
            Quick -> "Under 15 min"
            Standard -> "Under 30 min"
            Unlimited -> "Any effort"
        }

    val subtitle: String
        get() = when (this) {
            NoCook -> "Assembly only: tinned, frozen, raw or ready to eat"
            Quick -> "One pan, nothing that needs simmering"
            Standard -> "Normal weeknight cooking"
            Unlimited -> "Include anything, long simmers and roasts too"
        }
}

// MARK: - The profile

/**
 * Everything the engine needs to know about what the user will and will not
 * eat, and when. The defaults are the unconstrained answers: a question nobody
 * has been asked yet must not remove food from the catalogue.
 */
class DietaryProfile(
    val pattern: DietaryPattern = DietaryPattern.Omnivore,
    /** Groups avoided beyond the pattern: allergies, intolerances, faith. */
    val exclusions: Set<FoodExclusion> = emptySet(),
    /** Individual catalogue ids the user has banned. */
    val blockedFoodIds: Set<String> = emptySet(),
    val schedule: EatingSchedule = EatingSchedule.ThreeMeals,
    /**
     * Unlimited, not Standard. With Standard as the default, the "unrestricted"
     * profile silently dropped dried lentils, brown rice and pork shoulder, a
     * restriction the user never chose.
     */
    val prepEffort: PrepEffort = PrepEffort.Unlimited,
    mealsOutPerWeek: Int = 0,
) {
    /**
     * Meals per week eaten away from home. Captured because it is a real part
     * of a food budget, and disclosed in the audit as *not* modelled.
     */
    val mealsOutPerWeek: Int = maxOf(0, mealsOutPerWeek)

    /** True when the user has asked for anything to be left out. */
    val isRestricted: Boolean
        get() = pattern != DietaryPattern.Omnivore || exclusions.isNotEmpty() || blockedFoodIds.isNotEmpty()

    /** Traits this profile refuses through its exclusions. */
    val refusedTraits: FoodTraits
        get() = exclusions.fold(FoodTraits.NONE) { set, exclusion -> set.union(exclusion.trait) }

    /** One line, used in the summary and the audit. */
    val summary: String
        get() {
            val parts = mutableListOf(pattern.displayName)
            if (exclusions.isNotEmpty()) {
                parts += exclusions.map { it.displayName }.sorted().joinToString(", ") + " avoided"
            }
            if (blockedFoodIds.isNotEmpty()) {
                parts += "${blockedFoodIds.size} food${if (blockedFoodIds.size == 1) "" else "s"} blocked"
            }
            parts += schedule.displayName.lowercase(Locale.ROOT)
            parts += prepEffort.displayName.lowercase(Locale.ROOT)
            return parts.joinToString(" · ")
        }

    fun copy(
        pattern: DietaryPattern = this.pattern,
        exclusions: Set<FoodExclusion> = this.exclusions,
        blockedFoodIds: Set<String> = this.blockedFoodIds,
        schedule: EatingSchedule = this.schedule,
        prepEffort: PrepEffort = this.prepEffort,
        mealsOutPerWeek: Int = this.mealsOutPerWeek,
    ) = DietaryProfile(pattern, exclusions, blockedFoodIds, schedule, prepEffort, mealsOutPerWeek)

    override fun equals(other: Any?): Boolean =
        other is DietaryProfile &&
            other.pattern == pattern &&
            other.exclusions == exclusions &&
            other.blockedFoodIds == blockedFoodIds &&
            other.schedule == schedule &&
            other.prepEffort == prepEffort &&
            other.mealsOutPerWeek == mealsOutPerWeek

    override fun hashCode(): Int =
        listOf(pattern, exclusions, blockedFoodIds, schedule, prepEffort, mealsOutPerWeek).hashCode()

    override fun toString(): String = "DietaryProfile($summary)"

    companion object {
        /** No restrictions at all: the default before onboarding asks anything. */
        val UNRESTRICTED = DietaryProfile()
    }
}
