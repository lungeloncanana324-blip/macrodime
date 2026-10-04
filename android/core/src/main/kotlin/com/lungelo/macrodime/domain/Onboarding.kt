/*
 * Onboarding.kt
 * MacroDime
 *
 * The first three screens, before any question is asked: what the app is,
 * what has been getting in the person's way, and what the app does about
 * each thing they picked. Pure, so the copy is tested like every other word
 * the app says, and so every promise here can be checked against what the
 * app actually does.
 */
package com.lungelo.macrodime.domain

/**
 * Why someone installs a budget meal planner, in their own words, and the
 * part of the app that answers it. Every fix names something the app does
 * today: the starter plan, the budget meter, the swap engine, the grocery
 * list.
 */
enum class PainPoint(val label: String, val fixTitle: String, val fixDetail: String) {
    HealthyFoodCostsTooMuch(
        "Eating healthy costs too much",
        "Every meal priced before you shop",
        "Your plan is built inside a daily budget you set, from foods priced at US supermarket averages.",
    ),
    ProteinIsHard(
        "I struggle to hit my protein",
        "Protein planned into every day",
        "Your target comes from your body and your goal, and each day of the plan is built to reach it.",
    ),
    DontKnowWhatToCook(
        "I never know what to cook",
        "A week of meals, ready",
        "Simple meals planned for your whole week the moment you finish setup. Change anything you like.",
    ),
    FoodGoesToWaste(
        "I buy food that goes to waste",
        "A shopping list that matches",
        "Exact amounts for the week you planned, with what you already have taken off.",
    ),
    PlansIgnoreCost(
        "Diet plans ignore what food costs",
        "Cheaper swaps, same macros",
        "Swap an ingredient for a cheaper one that keeps your macros within 10%.",
    ),
}

object Intro {

    const val HEADLINE = "Hit your macros on a real budget."

    const val SUBHEADLINE = "A week of meals planned to your calories and protein, and priced before you shop."

    const val GET_STARTED = "Get started"

    const val PAINS_TITLE = "What's been getting in the way?"

    const val PAINS_SUBTITLE = "Pick any that sound familiar."

    const val BUILD_MY_PLAN = "Build my plan"

    /** What answers to show for what the person picked: the three that matter most when they picked nothing. */
    fun fixes(picked: Set<PainPoint>): List<PainPoint> =
        if (picked.isEmpty()) {
            listOf(PainPoint.HealthyFoodCostsTooMuch, PainPoint.ProteinIsHard, PainPoint.DontKnowWhatToCook)
        } else {
            PainPoint.entries.filter { it in picked }
        }

    /** Over the answers: an answer to what they said, or a plain statement of what they get. */
    fun fixesTitle(picked: Set<PainPoint>): String =
        if (picked.isEmpty()) "Here's what you get" else "Here's how MacroDime fixes that"

    const val FIXES_SUBTITLE = "Next, a few quick questions so the plan fits you. About a minute."
}
