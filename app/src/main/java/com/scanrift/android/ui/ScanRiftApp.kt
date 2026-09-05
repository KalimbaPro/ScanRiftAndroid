package com.scanrift.android.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanrift.android.ui.components.ProvideFoilPhase
import com.scanrift.android.ui.theme.ScanRiftTheme

@Composable
fun ScanRiftApp(viewModel: AppViewModel = hiltViewModel()) {
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()

    ScanRiftTheme(dynamicColor = dynamicColor) {
        // One animation clock drives every foil sheen in the app; see FoilOverlay.
        ProvideFoilPhase {
            Surface {
                MainScaffold()
            }
        }
    }
}
