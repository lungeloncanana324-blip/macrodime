/*
 * DayPlanViewModel.kt
 * MacroDime
 *
 * Owns one day being planned. Port of MacroDime/ViewModels/MealPlannerViewModel.swift.
 *
 * The day is read out of the database into value types, the engines work on
 * those, and the repository writes results back. Working in value types is
 * what lets the swap engine propose an entire alternative meal without touching
 * the store, so the user sees the preview before anything is committed.
 *
 * The catalogue is filtered through the user's dietary profile once, here: the
 * plan, the swap engine and the ingredient picker all read the filtered list,
 * so a prohibited food cannot be suggested in the first place.
 */
package com.lungelo.macrodime.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lungelo.macrodime.data.BodyMeasurementEntity
import com.lungelo.macrodime.data.MacroDimeRepository
import com.lungelo.macrodime.data.UserProfileEntity
import com.lungelo.macrodime.data.budgetTier
import com.lungelo.macrodime.data.currency
import com.lungelo.macrodime.data.dietaryProfile
import com.lungelo.macrodime.data.prescription
import com.lungelo.macrodime.data.toSnapshot
import com.lungelo.macrodime.domain.BudgetTier
import com.lungelo.macrodime.domain.CurrencySettings
import com.lungelo.macrodime.domain.DietaryProfile
import com.lungelo.macrodime.domain.FoodSnapshot
import com.lungelo.macrodime.domain.MealItem
import com.lungelo.macrodime.domain.MealSlot
import com.lungelo.macrodime.domain.NutritionFacts
import com.lungelo.macrodime.domain.Portion
import com.lungelo.macrodime.domain.totalNutrition
import com.lungelo.macrodime.engine.BudgetFoodEngine
import com.lungelo.macrodime.engine.DietaryFilter
import com.lungelo.macrodime.engine.MacroAxis
import com.lungelo.macrodime.engine.MealSwap
import com.lungelo.macrodime.engine.PlanAudit
import com.lungelo.macrodime.engine.PortionSwap
import com.lungelo.macrodime.engine.SwapPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/** Everything one day's screens draw, computed off the main thread. */
data class DayPlanState(
    val date: LocalDate,
    val isLoaded: Boolean = false,
    val profile: UserProfileEntity? = null,
    /** Null until a profile is loaded. */
    val targets: NutritionFacts? = null,
    val dailyBudget: Double = 0.0,
    val budgetTier: BudgetTier = BudgetTier.Strict,
    val dietary: DietaryProfile = DietaryProfile.UNRESTRICTED,
    val currency: CurrencySettings = CurrencySettings.USD,
    /** The catalogue as this user may eat it, alphabetical. */
    val catalog: List<FoodSnapshot> = emptyList(),
    val meals: List<MealItem> = emptyList(),
    /** The best whole-meal swap for each meal that has one, by meal id. */
    val swaps: Map<UUID, MealSwap> = emptyMap(),
    val audit: PlanAudit.Report = PlanAudit.Report(emptyList()),
    val powerhouses: List<FoodSnapshot> = emptyList(),
    val measurements: List<BodyMeasurementEntity> = emptyList(),
) {
    val consumed: NutritionFacts get() = meals.totalNutrition

    /** Remaining allowance per macro. Negative means over target. */
    val remaining: NutritionFacts get() = targets?.let { it - consumed } ?: NutritionFacts.ZERO

    /** The swap worth showing first: the one that saves the most. */
    val bestSwap: MealSwap? get() = swaps.values.maxByOrNull { it.savings }

    fun meal(slot: MealSlot): MealItem? = meals.firstOrNull { it.slot == slot }

    /** 0 to 1 and beyond: over target is drawn, not clamped. */
    fun progress(axis: MacroAxis): Double {
        val target = targets?.let { axis.valueIn(it) } ?: return 0.0
        return if (target > 0) axis.valueIn(consumed) / target else 0.0
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DayPlanViewModel(
    private val repository: MacroDimeRepository,
    initialDate: LocalDate = LocalDate.now(),
) : ViewModel() {

    private val date = MutableStateFlow(initialDate)
    val selectedDate: StateFlow<LocalDate> = date.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)

    /** The last failure, shown once and then cleared. */
    val message: StateFlow<String?> = _message.asStateFlow()

    /** The engine the latest state was built with, so per-portion alternatives match what is on screen. */
    @Volatile
    private var engine = BudgetFoodEngine(catalog = emptyList())

    private val measurements = repository.profile.flatMapLatest { profile ->
        if (profile == null) flowOf(emptyList()) else repository.measurements(profile.id)
    }

    val state: StateFlow<DayPlanState> = combine(
        repository.profile,
        repository.foods,
        date.flatMapLatest { day -> repository.meals(day).map { day to it } },
        measurements,
    ) { profile, foods, (day, meals), measurements ->
        build(profile, foods.map { it.toSnapshot() }, day, meals, measurements)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayPlanState(initialDate))

    private fun build(
        profile: UserProfileEntity?,
        foods: List<FoodSnapshot>,
        day: LocalDate,
        meals: List<MealItem>,
        measurements: List<BodyMeasurementEntity>,
    ): DayPlanState {
        val dietary = profile?.dietaryProfile ?: DietaryProfile.UNRESTRICTED
        val tier = profile?.budgetTier ?: BudgetTier.Strict
        val currency = profile?.currency ?: CurrencySettings.USD
        val targets = profile?.prescription?.targets
        val dailyBudget = profile?.dailyFoodBudget ?: 0.0

        // Filtered once: nothing downstream has to remember the restrictions.
        val catalog = DietaryFilter.allowed(foods, dietary).sortedBy { it.name }
        val engine = BudgetFoodEngine(catalog, SwapPolicy.cuttingCosts(tier))
        this.engine = engine

        return DayPlanState(
            date = day,
            isLoaded = true,
            profile = profile,
            targets = targets,
            dailyBudget = dailyBudget,
            budgetTier = tier,
            dietary = dietary,
            currency = currency,
            catalog = catalog,
            meals = meals,
            swaps = meals.mapNotNull { engine.bestSwap(it) }.associateBy { it.original.id },
            audit = PlanAudit.day(meals, targets, dailyBudget, dietary, currency = currency),
            powerhouses = engine.budgetPowerhouses(tier = tier, limit = 5),
            measurements = measurements,
        )
    }

    fun selectDate(day: LocalDate) {
        date.value = day
    }

    /** Ranked alternatives for one ingredient, for the per-portion picker. */
    fun alternatives(portion: Portion, meal: MealItem): List<PortionSwap> = engine.rankedReplacements(portion, meal)

    fun addFood(food: FoodSnapshot, servings: Double, slot: MealSlot) = perform("Could not add that food") {
        repository.addFood(date.value, slot, food.id, servings)
    }

    fun removePortion(portionId: UUID) = perform("Could not remove that ingredient") {
        repository.removePortion(portionId)
    }

    fun updateServings(portionId: UUID, servings: Double) = perform("Could not update that portion") {
        repository.updateServings(portionId, servings)
    }

    /** Commits a whole-meal swap the user reviewed. */
    fun apply(swap: MealSwap) = perform("Could not save that change") {
        repository.applySwap(swap.original.id, swap.swapped, swap.savings)
    }

    /**
     * Commits one ingredient's swap. Writes the swap's own resulting meal, so
     * any oil or rice the engine re-portioned is kept.
     */
    fun apply(portionSwap: PortionSwap, meal: MealItem) = perform("Could not save that change") {
        repository.applySwap(meal.id, portionSwap.resultingMeal, portionSwap.savings)
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun perform(failure: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (error: Exception) {
                _message.value = "$failure: ${error.message ?: "unknown error"}"
            }
        }
    }
}
