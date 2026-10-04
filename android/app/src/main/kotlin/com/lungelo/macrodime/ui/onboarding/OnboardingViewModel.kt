/*
 * OnboardingViewModel.kt
 * MacroDime
 *
 * Drives the onboarding wizard and the profile editor. Port of
 * MacroDime/ViewModels/UserProfileViewModel.swift.
 *
 * It holds a *draft*, not the stored profile: onboarding must be abandonable,
 * and a half-typed height should never reach the database. The draft is
 * committed once, in save().
 */
package com.lungelo.macrodime.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lungelo.macrodime.data.DemoData
import com.lungelo.macrodime.data.MacroDimeRepository
import com.lungelo.macrodime.data.UserProfileEntity
import com.lungelo.macrodime.data.activity
import com.lungelo.macrodime.data.budgetTier
import com.lungelo.macrodime.data.currency
import com.lungelo.macrodime.data.dietaryProfile
import com.lungelo.macrodime.data.goal
import com.lungelo.macrodime.data.measurementSystem
import com.lungelo.macrodime.data.sex
import com.lungelo.macrodime.data.withDietaryProfile
import com.lungelo.macrodime.domain.ActivityLevel
import com.lungelo.macrodime.domain.BiologicalSex
import com.lungelo.macrodime.domain.BudgetTier
import com.lungelo.macrodime.domain.CurrencySettings
import com.lungelo.macrodime.domain.DietaryPattern
import com.lungelo.macrodime.domain.DietaryProfile
import com.lungelo.macrodime.domain.DisplayFormat
import com.lungelo.macrodime.domain.EatingSchedule
import com.lungelo.macrodime.domain.FitnessGoal
import com.lungelo.macrodime.domain.FoodExclusion
import com.lungelo.macrodime.domain.Intro
import com.lungelo.macrodime.domain.PainPoint
import com.lungelo.macrodime.domain.MeasurementSystem
import com.lungelo.macrodime.domain.PrepEffort
import com.lungelo.macrodime.domain.UnitConversion
import com.lungelo.macrodime.engine.BodyScienceEngine
import com.lungelo.macrodime.engine.DietaryFilter
import com.lungelo.macrodime.engine.FoodCatalog
import com.lungelo.macrodime.engine.PlanAudit
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

class OnboardingViewModel(private val repository: MacroDimeRepository) : ViewModel() {

    /**
     * The wizard. The first three steps come before any question: what the app
     * is, what has been getting in the way, and how the app answers that. They
     * only run on first launch; editing a profile starts at the body metrics.
     */
    enum class Step(val title: String, val subtitle: String, val isIntro: Boolean = false) {
        Hook(Intro.HEADLINE, Intro.SUBHEADLINE, isIntro = true),
        Pains(Intro.PAINS_TITLE, Intro.PAINS_SUBTITLE, isIntro = true),
        Fixes("", Intro.FIXES_SUBTITLE, isIntro = true),
        Welcome("Welcome", "Six quick questions, then your week is planned."),
        BodyMetrics("About you", "Used to work out what your body burns"),
        Goal("Your goal", "This sets your calories and your protein"),
        Activity("How active are you?", "Be honest: most people overestimate this one"),
        Diet("What you eat", "So the plan never suggests something you won't eat"),
        Routine("How you eat", "How many meals, and how much cooking you'll really do"),
        Budget("Your budget", "Every meal in your plan is priced against this"),
        Summary("Your plan", "Here's what the numbers say, and anything that doesn't add up"),
    }

    var step by mutableStateOf(Step.Hook)
        private set

    /** What has been getting in the way, from the intro. Answered on the next screen; not stored. */
    var pains by mutableStateOf(emptySet<PainPoint>())
        private set

    fun togglePain(pain: PainPoint) {
        pains = if (pain in pains) pains - pain else pains + pain
    }

    /** The stored profile being edited, or null on first run. */
    private var existing: UserProfileEntity? = null
    private var started = false

    /** True when this draft edits a profile that already finished onboarding. */
    var isEditing by mutableStateOf(false)
        private set

    // Body

    var displayName by mutableStateOf("")
    var measurementSystem by mutableStateOf(MeasurementSystem.Metric)
        private set

    /** Canonical, always metric. NaN while the field holds something that is not a number. */
    var heightCm by mutableDoubleStateOf(175.0)
        private set
    var weightKg by mutableDoubleStateOf(75.0)
        private set

    /** What the fields show, kept apart so a half-typed "7" is not rewritten under the cursor. */
    var heightText by mutableStateOf("175")
        private set
    var weightText by mutableStateOf("75")
        private set

    var age by mutableIntStateOf(30)
        private set
    var sex by mutableStateOf(BiologicalSex.Male)
    var goal by mutableStateOf(FitnessGoal.FatLoss)
    var activity by mutableStateOf(ActivityLevel.LightlyActive)

    // Diet. The *asked* defaults: "3 meals, under 30 minutes" is the common
    // answer and changing it is one tap. DietaryProfile's own defaults stay
    // unconstrained, so a profile that never answers filters nothing.

    var dietaryPattern by mutableStateOf(DietaryPattern.Omnivore)
    var foodExclusions by mutableStateOf(emptySet<FoodExclusion>())
        private set
    var blockedFoodIds by mutableStateOf(emptySet<String>())
        private set
    var eatingSchedule by mutableStateOf(EatingSchedule.ThreeMeals)
    var prepEffort by mutableStateOf(PrepEffort.Standard)
    var mealsOutPerWeek by mutableIntStateOf(0)
        private set

    // Money

    /** Held on the draft because onboarding runs before a profile exists to read it from. */
    var currency by mutableStateOf(CurrencySettings.USD)
        private set
    var budgetTier by mutableStateOf(BudgetTier.Strict)
        private set
    var dailyFoodBudget by mutableDoubleStateOf(BudgetTier.Strict.defaultDailyAllowance)
        private set
    private var hasEditedBudget = false

    /** Gates the final step: the honest thing to do for an app that prescribes a deficit. */
    var hasAcknowledgedDisclaimer by mutableStateOf(false)

    var saveError by mutableStateOf<String?>(null)
        private set
    var isSaving by mutableStateOf(false)
        private set

    /**
     * First run: loads a stored profile into the draft, once. With no profile
     * the draft keeps its defaults and starts at Welcome.
     */
    fun start(profile: UserProfileEntity?) {
        if (started) return
        started = true
        if (profile != null) load(profile)
    }

    /** Replaces the draft with [profile] and starts at the body metrics. Used each time the editor opens. */
    fun load(profile: UserProfileEntity) {
        started = true
        saveError = null
        existing = profile
        isEditing = profile.hasCompletedOnboarding
        displayName = profile.displayName
        measurementSystem = profile.measurementSystem
        heightCm = profile.heightCm
        weightKg = profile.weightKg
        age = profile.age.coerceIn(BodyScienceEngine.Constants.AGE_RANGE)
        sex = profile.sex
        goal = profile.goal
        activity = profile.activity
        val diet = profile.dietaryProfile
        dietaryPattern = diet.pattern
        foodExclusions = diet.exclusions
        blockedFoodIds = diet.blockedFoodIds
        eatingSchedule = diet.schedule
        prepEffort = diet.prepEffort
        mealsOutPerWeek = diet.mealsOutPerWeek
        currency = profile.currency
        hasAcknowledgedDisclaimer = profile.hasAcknowledgedHealthDisclaimer
        // An existing budget is the user's own number, not a tier default.
        hasEditedBudget = true
        budgetTier = profile.budgetTier
        dailyFoodBudget = profile.dailyFoodBudget
        refreshFieldText()
        step = Step.BodyMetrics
    }

    // Body inputs

    fun updateMeasurementSystem(system: MeasurementSystem) {
        measurementSystem = system
        refreshFieldText()
    }

    fun updateHeightText(text: String) {
        heightText = text
        heightCm = parseDecimal(text) ?: Double.NaN
    }

    fun updateWeightText(text: String) {
        weightText = text
        val value = parseDecimal(text)
        weightKg = when {
            value == null -> Double.NaN
            measurementSystem == MeasurementSystem.Imperial -> UnitConversion.kilogramsFromPounds(value)
            else -> value
        }
    }

    val heightFeet: Int get() = if (heightCm.isNaN()) 5 else UnitConversion.feetAndInches(heightCm).feet
    val heightInches: Int get() = if (heightCm.isNaN()) 9 else UnitConversion.feetAndInches(heightCm).inches

    fun updateHeight(feet: Int, inches: Int) {
        heightCm = UnitConversion.centimetres(feet, inches)
        heightText = fieldText(heightCm, decimals = 0)
    }

    fun updateAge(value: Int) {
        age = value.coerceIn(BodyScienceEngine.Constants.AGE_RANGE)
    }

    private fun refreshFieldText() {
        heightText = if (heightCm.isNaN()) "" else fieldText(heightCm, decimals = 0)
        weightText = when {
            weightKg.isNaN() -> ""
            measurementSystem == MeasurementSystem.Imperial -> fieldText(UnitConversion.poundsFromKilograms(weightKg), 1)
            else -> fieldText(weightKg, 1)
        }
    }

    // Diet inputs

    fun toggle(exclusion: FoodExclusion) {
        foodExclusions = if (exclusion in foodExclusions) foodExclusions - exclusion else foodExclusions + exclusion
    }

    fun toggleBlocked(foodId: String) {
        blockedFoodIds = if (foodId in blockedFoodIds) blockedFoodIds - foodId else blockedFoodIds + foodId
    }

    fun updateMealsOut(value: Int) {
        mealsOutPerWeek = value.coerceIn(0, 21)
    }

    val dietaryProfile: DietaryProfile
        get() = DietaryProfile(dietaryPattern, foodExclusions, blockedFoodIds, eatingSchedule, prepEffort, mealsOutPerWeek)

    /** Foods the current answers remove, for the live count on the diet step. */
    val excludedFoodCount: Int get() = DietaryFilter.rejected(FoodCatalog.all, dietaryProfile).size

    // Budget inputs

    /** Switches tier, carrying its default allowance across until the user has set their own. */
    fun selectBudgetTier(tier: BudgetTier) {
        budgetTier = tier
        if (!hasEditedBudget) dailyFoodBudget = tier.defaultDailyAllowance
    }

    /** Called by the slider, which hands ownership of the number to the user. Takes USD. */
    fun budgetWasEdited(usd: Double) {
        hasEditedBudget = true
        dailyFoodBudget = usd.coerceAtLeast(0.0)
    }

    // Derived

    val scienceInput: BodyScienceEngine.Input
        get() = BodyScienceEngine.Input(weightKg, heightCm, age, sex, activity, goal)

    /** Live preview: a few dozen multiplications, recomputed as the user types. */
    val prescription: BodyScienceEngine.Prescription
        get() = BodyScienceEngine.prescribeUnchecked(scienceInput)

    val validationError: BodyScienceEngine.ValidationError? get() = scienceInput.validationError

    val weeklyBudget: Double get() = dailyFoodBudget * 7

    /** What the allowance buys per 1,000 kcal: whether a budget and a goal are compatible at all. */
    val costPer1000Calories: Double
        get() {
            val calories = prescription.targets.calories
            return if (calories > 0) dailyFoodBudget / (calories / 1_000) else 0.0
        }

    /** The gaps in this combination of target, budget and restrictions, before anything is committed. */
    val feasibility: PlanAudit.Report
        get() = PlanAudit.feasibility(prescription.targets, dailyFoodBudget, dietaryProfile, currency = currency)

    /**
     * The cheapest plausible day for this protein target with the foods this
     * profile allows. Delegates to PlanAudit, so the figure here and the one in
     * the feasibility gap are one computation.
     */
    fun minimumViableDailyCost(): Double =
        PlanAudit.minimumDailyCostUSD(prescription.targets, dietaryProfile)?.cost ?: 0.0

    /**
     * Quoted in the draft's own currency. The iOS app formats this one figure in
     * USD regardless, so a profile edited in rand shows a dollar warning beside
     * rand meters; here it follows the rest of the screen.
     */
    val budgetWarning: String?
        get() {
            val minimum = minimumViableDailyCost()
            if (minimum <= 0 || dailyFoodBudget >= minimum) return null
            return "Hitting ${DisplayFormat.grams(prescription.targets.protein)} of protein a day costs around " +
                "${currency.format(minimum)} even on the cheapest foods you allow. Consider raising your allowance."
        }

    // Navigation

    val canAdvance: Boolean
        get() = when (step) {
            Step.BodyMetrics -> validationError == null
            Step.Budget -> dailyFoodBudget > 0
            Step.Summary -> hasAcknowledgedDisclaimer && validationError == null && !isSaving
            else -> true
        }

    /** The first step this draft can go back to: the intro on first run, body metrics when editing. */
    private val firstStep: Step get() = if (isEditing) Step.BodyMetrics else Step.Hook

    val isFirstStep: Boolean get() = step == firstStep
    val isLastStep: Boolean get() = step == Step.Summary

    /** Through the questions only: the intro is not part of "how much is left". */
    val progress: Float
        get() = if (step.isIntro) 0f else (step.ordinal - Step.Welcome.ordinal).toFloat() / (Step.Summary.ordinal - Step.Welcome.ordinal)

    fun advance() {
        if (!canAdvance) return
        Step.entries.getOrNull(step.ordinal + 1)?.let { step = it }
    }

    /** False when there is nowhere further back, so the caller can close or leave. */
    fun goBack(): Boolean {
        if (isFirstStep) return false
        step = Step.entries[step.ordinal - 1]
        return true
    }

    fun dismissSaveError() {
        saveError = null
    }

    /** Commits the draft, creating the profile row on first run. */
    fun save(onSaved: () -> Unit) {
        validationError?.let {
            saveError = it.message
            return
        }
        isSaving = true
        viewModelScope.launch {
            try {
                val base = existing ?: DemoData.blankProfile(System.currentTimeMillis())
                val profile = base.copy(
                    displayName = displayName.trim(),
                    measurementSystemRaw = measurementSystem.rawValue,
                    heightCm = heightCm,
                    weightKg = weightKg,
                    age = age,
                    sexRaw = sex.rawValue,
                    goalRaw = goal.rawValue,
                    activityRaw = activity.rawValue,
                    budgetTierRaw = budgetTier.rawValue,
                    dailyFoodBudget = dailyFoodBudget,
                    currencyCodeRaw = currency.displayCode,
                    currencyUnitsPerUSD = currency.unitsPerUSD,
                    hasCompletedOnboarding = true,
                    hasAcknowledgedHealthDisclaimer = hasAcknowledgedDisclaimer,
                ).withDietaryProfile(dietaryProfile)
                val firstRun = !isEditing
                if (firstRun) {
                    // The week is planned before the profile is saved, because
                    // saving it moves the app on to the paywall at once, and the
                    // paywall opens on this week. A failure costs the starter
                    // plan, not the profile: every empty day still offers
                    // "Plan this day for me".
                    runCatching { repository.planEmptyDays(profile, LocalDate.now()) }
                    // Told before the save for the same reason: the paywall must
                    // open as "Your week is ready", never as "Welcome back".
                    onSaved()
                }
                repository.saveProfile(profile)
                existing = profile
                if (!firstRun) onSaved()
            } catch (error: Exception) {
                saveError = error.message ?: "Your profile could not be saved."
            } finally {
                isSaving = false
            }
        }
    }

    companion object {
        /** Accepts `.` and `,` as the decimal mark, so the field works in every locale. */
        fun parseDecimal(text: String): Double? {
            val normalised = text.trim().replace(',', '.')
            if (normalised.isEmpty()) return null
            return normalised.toDoubleOrNull()?.takeIf { it.isFinite() }
        }

        private fun fieldText(value: Double, decimals: Int): String =
            DisplayFormat.flexible(value, decimals, Locale.ROOT).replace(",", "")
    }
}
