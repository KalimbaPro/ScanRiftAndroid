package com.scanrift.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanrift.android.data.prefs.UserPreferences
import com.scanrift.android.service.sync.DatabaseBootstrapper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AppViewModel @Inject constructor(
    userPreferences: UserPreferences,
    bootstrapper: DatabaseBootstrapper,
) : ViewModel() {

    val dynamicColor: StateFlow<Boolean> =
        userPreferences.dynamicColor.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val bootstrapState = bootstrapper.state

    init {
        // Seeds the catalogue on a cold start and runs the TTL-gated delta sync.
        bootstrapper.start()
    }
}
