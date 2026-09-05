package com.scanrift.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.scanrift.android.ui.theme.ScanRiftTheme

@Composable
fun ScanRiftApp() {
    ScanRiftTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            // Placeholder shell. Replaced by MainScaffold once navigation lands.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("ScanRift")
            }
        }
    }
}
