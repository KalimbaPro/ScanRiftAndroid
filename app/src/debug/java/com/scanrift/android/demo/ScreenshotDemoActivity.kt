package com.scanrift.android.demo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.scanrift.android.MainActivity
import com.scanrift.android.core.log.Log
import com.scanrift.android.service.sync.DatabaseBootstrapper
import com.scanrift.android.ui.theme.ScanRiftTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Debug-only entry point that loads [DemoData] and then opens the app.
 *
 * ```
 * adb shell am force-stop com.scanrift.android.debug
 * adb shell am start -n com.scanrift.android.debug/com.scanrift.android.demo.ScreenshotDemoActivity
 * ```
 *
 * Lives in the debug source set and is declared only in the debug manifest, so release
 * builds contain neither the activity nor the seeder.
 *
 * It waits for the launch bootstrap (bundled seed, then the API version check and any
 * refresh) before writing. Seeding earlier would race the catalogue's de-duplication,
 * which can replace the very rows the demo data points at.
 */
@AndroidEntryPoint
class ScreenshotDemoActivity : ComponentActivity() {

    @Inject lateinit var bootstrapper: DatabaseBootstrapper
    @Inject lateinit var seeder: DemoSeeder

    private var status by mutableStateOf("Loading the catalogue…")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScanRiftTheme {
                Surface(Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(status, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        if (savedInstanceState == null) lifecycleScope.launch { run() }
    }

    private suspend fun run() {
        bootstrapper.start()
        // start() is asynchronous and runs once per process: see it begin, then end.
        // If this process already bootstrapped, the first wait times out harmlessly.
        withTimeoutOrNull(BOOTSTRAP_START_TIMEOUT_MS) { bootstrapper.state.first { it.isLoading } }
        withTimeoutOrNull(BOOTSTRAP_TIMEOUT_MS) { bootstrapper.state.first { !it.isLoading } }

        status = "Seeding demo data…"
        val outcome = runCatching { seeder.seed() }
        outcome.onFailure { Log.database.e(it, "Demo seeding failed") }
        status = outcome.fold(
            onSuccess = { "Seeded ${it.stacks} stacks, ${it.decks} decks, ${it.games} games" },
            onFailure = { "Demo seeding failed: ${it.message}" },
        )
        if (outcome.isFailure) return

        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        finish()
    }

    private companion object {
        const val BOOTSTRAP_START_TIMEOUT_MS = 5_000L
        const val BOOTSTRAP_TIMEOUT_MS = 120_000L
    }
}
