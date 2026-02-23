package com.scanrift.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.scanrift.android.ui.navigation.ScanRiftNavHost
import com.scanrift.android.ui.theme.ScanRiftTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScanRiftTheme {
                ScanRiftNavHost()
            }
        }
    }
}
