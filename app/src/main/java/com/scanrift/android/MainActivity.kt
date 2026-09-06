package com.scanrift.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.scanrift.android.ui.ScanRiftApp
import dagger.hilt.android.AndroidEntryPoint

/**
 * The app's only activity.
 *
 * The manifest declares a broad `configChanges` set plus `resizeableActivity`, so
 * folding, unfolding and rotating never destroy this activity — which is what keeps
 * the CameraX session and an in-progress scan alive across a fold.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ScanRiftApp() }
    }
}
