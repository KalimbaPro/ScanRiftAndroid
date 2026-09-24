package com.scanrift.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.scanrift.android.service.sync.BootstrapState
import com.scanrift.android.ui.components.ProvideFoilPhase
import com.scanrift.android.ui.theme.ScanRiftTheme

@Composable
fun ScanRiftApp(viewModel: AppViewModel = hiltViewModel()) {
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val bootstrapState by viewModel.bootstrapState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.runAutomaticBackups() }
    }

    ScanRiftTheme(dynamicColor = dynamicColor) {
        // One animation clock drives every foil sheen in the app; see FoilOverlay.
        ProvideFoilPhase {
            Surface {
                if (bootstrapState is BootstrapState.SeedingBundle) LoadingCardDatabase() else MainScaffold()
            }
        }
    }
}

@Composable
private fun LoadingCardDatabase() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            text = "Loading card database…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
