/*
 * OnboardingTest.kt
 *
 * The intro's copy: every pain point has an answer, the answers follow what
 * was picked, and no word of it carries a dash.
 */
package com.lungelo.macrodime.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingTest {

    private val dashes = listOf(0x2010, 0x2011, 0x2012, 0x2013, 0x2014, 0x2015, 0x2212).map { it.toChar() }

    @Test
    fun theAnswersFollowWhatWasPicked() {
        val picked = setOf(PainPoint.FoodGoesToWaste, PainPoint.HealthyFoodCostsTooMuch)
        // In the order of the list, not the order of tapping, so the screen reads the same each time.
        assertEquals(listOf(PainPoint.HealthyFoodCostsTooMuch, PainPoint.FoodGoesToWaste), Intro.fixes(picked))
        assertEquals("Here's how MacroDime fixes that", Intro.fixesTitle(picked))
    }

    @Test
    fun pickingNothingStillShowsWhatTheAppDoes() {
        assertEquals(3, Intro.fixes(emptySet()).size)
        assertEquals("Here's what you get", Intro.fixesTitle(emptySet()))
    }

    @Test
    fun everyPainHasAnAnswerAndNoDashes() {
        val words = PainPoint.entries.flatMap { listOf(it.label, it.fixTitle, it.fixDetail) } +
            listOf(Intro.HEADLINE, Intro.SUBHEADLINE, Intro.GET_STARTED, Intro.PAINS_TITLE, Intro.PAINS_SUBTITLE, Intro.BUILD_MY_PLAN, Intro.FIXES_SUBTITLE)
        for (text in words) {
            assertTrue(text.isNotBlank())
            assertFalse(text.any { it in dashes }, "a dash in \"$text\"")
        }
        assertEquals(PainPoint.entries.size, PainPoint.entries.map { it.fixTitle }.distinct().size, "two pains share one answer")
    }
}
