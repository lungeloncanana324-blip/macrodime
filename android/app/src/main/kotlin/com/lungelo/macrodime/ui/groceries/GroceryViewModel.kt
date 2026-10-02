/*
 * GroceryViewModel.kt
 * MacroDime
 *
 * The week's shopping list. Two pieces of state belong to the user and survive
 * regeneration: what has been ticked, and what they already own.
 *
 * The list rebuilds itself from the plan whenever this screen opens or the week
 * changes, so a meal added on the Plan tab is already on it. iOS used to wait
 * for a tap on Regenerate and was changed to match on 2026-10-02. Rebuilding is
 * safe to do automatically because it never loses a tick or an "already have".
 */
package com.lungelo.macrodime.ui.groceries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lungelo.macrodime.data.GroceryItemEntity
import com.lungelo.macrodime.data.MacroDimeRepository
import com.lungelo.macrodime.domain.GrocerySection
import com.lungelo.macrodime.domain.rawValueOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

val GroceryItemEntity.section: GrocerySection get() = rawValueOf<GrocerySection>(sectionRaw) ?: GrocerySection.Pantry

data class GroceryState(
    val weekStart: LocalDate,
    val items: List<GroceryItemEntity> = emptyList(),
    val showChecked: Boolean = true,
    val weeklyBudget: Double = 0.0,
    val isLoaded: Boolean = false,
) {
    val outstandingTotal: Double get() = items.sumOf { it.outstandingCost }
    val fullTotal: Double get() = items.sumOf { it.estimatedCost }
    val checkedCount: Int get() = items.count { it.isChecked }

    /** The week's items in store-walk order: unticked first, then most expensive, within each aisle. */
    val sections: List<Pair<GrocerySection, List<GroceryItemEntity>>>
        get() = GrocerySection.entries.sortedBy { it.aisleOrder }.mapNotNull { section ->
            val lines = items
                .filter { it.section == section && (showChecked || !it.isChecked) }
                .sortedWith(
                    compareBy<GroceryItemEntity> { it.isChecked }
                        .thenByDescending { it.estimatedCost }
                        .thenBy { it.name },
                )
            if (lines.isEmpty()) null else section to lines
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GroceryViewModel(private val repository: MacroDimeRepository) : ViewModel() {

    private val weekStart = MutableStateFlow(MacroDimeRepository.weekStart(LocalDate.now()))
    private val showChecked = MutableStateFlow(true)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val state: StateFlow<GroceryState> = combine(
        weekStart.flatMapLatest { week -> repository.groceries(week).map { week to it } },
        showChecked,
        repository.profile,
    ) { (week, items), show, profile ->
        GroceryState(week, items, show, (profile?.dailyFoodBudget ?: 0.0) * 7, isLoaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroceryState(weekStart.value))

    /** Called when the screen appears. */
    fun refresh() = regenerate()

    fun shiftWeek(weeks: Long) {
        weekStart.value = weekStart.value.plusWeeks(weeks)
        regenerate()
    }

    fun thisWeek() {
        weekStart.value = MacroDimeRepository.weekStart(LocalDate.now())
        regenerate()
    }

    fun regenerate() = perform("Could not build your list") { repository.regenerateGroceries(weekStart.value) }

    fun toggleChecked(item: GroceryItemEntity) = perform("Could not update that item") {
        repository.setChecked(item.id, !item.isChecked)
    }

    fun toggleOwned(item: GroceryItemEntity) = perform("Could not update that item") {
        repository.setAlreadyOwned(item.id, !item.isAlreadyOwned)
    }

    fun delete(item: GroceryItemEntity) = perform("Could not delete that item") { repository.deleteGrocery(item.id) }

    fun uncheckAll() = perform("Could not update the list") { repository.uncheckWeek(weekStart.value) }

    fun setShowChecked(show: Boolean) {
        showChecked.value = show
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
