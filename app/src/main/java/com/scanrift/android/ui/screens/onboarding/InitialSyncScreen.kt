package com.scanrift.android.ui.screens.onboarding

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scanrift.android.data.local.ScanRiftDatabase
import com.scanrift.android.data.remote.api.RetrofitInstance
import com.scanrift.android.data.repository.CardRepository
import com.scanrift.android.data.repository.UserPreferences
import com.scanrift.android.ui.screens.settings.SyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

class InitialSyncViewModel(application: Application) : AndroidViewModel(application) {
    private val database = ScanRiftDatabase.getInstance(application)
    private val cardRepository = CardRepository(
        cardDao = database.cardDao(),
        api = RetrofitInstance.api,
        context = application
    )
    private val preferences = UserPreferences(application)

    val cardCount: StateFlow<Int> = cardRepository.getCardCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    fun syncDatabase() {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing()
            val result = cardRepository.syncDatabase { current, total ->
                _syncState.value = SyncState.Syncing(current, total)
            }
            result.fold(
                onSuccess = { count ->
                    preferences.setLastDatabaseSync(System.currentTimeMillis())
                    _syncState.value = SyncState.Success(count)
                    Timber.d("Initial sync complete: %d cards", count)
                },
                onFailure = { error ->
                    _syncState.value = SyncState.Error(
                        error.localizedMessage ?: "Unknown error"
                    )
                    Timber.e(error, "Initial sync failed")
                }
            )
        }
    }
}

@Composable
fun InitialSyncScreen(
    viewModel: InitialSyncViewModel = viewModel(),
    onComplete: () -> Unit = {},
    onSkip: () -> Unit = {}
) {
    val syncState by viewModel.syncState.collectAsState()
    val cardCount by viewModel.cardCount.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.PhotoCamera,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Welcome to ScanRift!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "To scan and identify trading cards, you need to download the card database first.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        when (syncState) {
            is SyncState.Idle -> {
                Button(
                    onClick = { viewModel.syncDatabase() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Download Card Database")
                }
            }

            is SyncState.Syncing -> {
                val state = syncState as SyncState.Syncing
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                val text = if (state.totalPages > 0) {
                    "Downloading... (page ${state.page} of ${state.totalPages})"
                } else {
                    "Downloading..."
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            is SyncState.Success -> {
                val state = syncState as SyncState.Success
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Downloaded ${state.cardCount} cards!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onComplete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Get Started")
                }
            }

            is SyncState.Error -> {
                val state = syncState as SyncState.Error
                Text(
                    text = "Download failed: ${state.message}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.syncDatabase() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Retry")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (syncState !is SyncState.Success) {
            TextButton(onClick = onSkip) {
                Text("Skip for now")
            }
        }
    }
}
