package com.lungelo.macrodime.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lungelo.macrodime.data.MacroDimeRepository
import com.lungelo.macrodime.data.UserProfileEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the root shows. Loading is its own state, so a returning user never sees onboarding flash past. */
sealed interface RootState {
    data object Loading : RootState
    /**
     * [session] rises each time the app enters onboarding, so a draft from an
     * earlier run (onboard, then Delete All My Data) is never reopened.
     */
    data class Onboarding(val existing: UserProfileEntity?, val session: Int) : RootState
    data class Main(val profile: UserProfileEntity) : RootState
}

/**
 * Gates onboarding, then the tabs. Watches the profile, so Delete All My Data
 * returns the app to onboarding on its own, as RootView does on iOS.
 */
class AppViewModel(repository: MacroDimeRepository) : ViewModel() {

    private var onboardingSession = 0
    private var wasOnboarding = false

    val state: StateFlow<RootState> = repository.profile
        .map { profile ->
            val needsOnboarding = profile == null || !profile.hasCompletedOnboarding
            if (needsOnboarding && !wasOnboarding) onboardingSession += 1
            wasOnboarding = needsOnboarding
            if (needsOnboarding) RootState.Onboarding(profile, onboardingSession) else RootState.Main(profile)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RootState.Loading)
}
