package com.scanrift.android.service.scanning

import com.scanrift.android.core.log.Log
import java.util.concurrent.atomic.AtomicInteger

/**
 * Keeps a crash in the Chinese/Korean recognizers from taking the scanner down twice.
 *
 * Those recognizers are Play Services' unbundled models, running native TFLite code in
 * our process. On the arm64 emulator (Apple Silicon host) that code dies with SIGILL —
 * an instruction the virtual CPU advertises but cannot execute — and a signal is not an
 * exception: no `runCatching` sees it, the process is simply gone. Real devices report
 * their CPU features truthfully, so this is not expected there, but nothing we ship can
 * rule it out for every SoC and every Play Services model update.
 *
 * So a CJK pass is bracketed by a marker written **synchronously** to disk before it
 * starts and cleared when it ends. A marker still present at the next launch means the
 * process died inside a pass; CJK OCR is then switched off for this app version and the
 * scanner falls back to the language marker and the Latin name — a card still scans,
 * only a zh/ko print may need its language picked by hand. A new app version tries again,
 * since a Play Services or app update may have fixed the cause.
 *
 * What it cannot do: stop the *first* crash. A false positive is possible too — the
 * system killing the app mid-pass (a swipe away, low memory) looks the same — and costs
 * only the automatic CJK detection, until the next update.
 *
 * Pure apart from [Store], so the bookkeeping runs on plain JUnit.
 */
class CjkOcrGuard(private val store: Store, private val appVersion: Long) {

    /** Synchronous key-value storage. [write] must be on disk when it returns. */
    interface Store {
        fun read(key: String): Long?
        fun write(key: String, value: Long?)
    }

    private val inFlight = AtomicInteger(0)

    private val disabledVersion: Long? by lazy {
        val crashedIn = store.read(KEY_IN_FLIGHT)
        if (crashedIn != null) {
            // The last process died inside a CJK pass.
            Log.ocr.w("Chinese/Korean OCR crashed the app (version %d); disabled for this version", crashedIn)
            store.write(KEY_DISABLED, crashedIn)
            store.write(KEY_IN_FLIGHT, null)
            crashedIn
        } else {
            store.read(KEY_DISABLED)
        }
    }

    /** False once a CJK pass has killed this app version. */
    val isEnabled: Boolean get() = disabledVersion != appVersion

    /**
     * Runs [block] — a CJK recognizer call — with the crash marker set, or returns null
     * without running it when CJK OCR is disabled.
     */
    suspend fun <T> guard(block: suspend () -> T): T? {
        if (!isEnabled) return null
        begin()
        try {
            return block()
        } finally {
            end()
        }
    }

    private fun begin() {
        if (inFlight.getAndIncrement() == 0) store.write(KEY_IN_FLIGHT, appVersion)
    }

    private fun end() {
        if (inFlight.decrementAndGet() == 0) store.write(KEY_IN_FLIGHT, null)
    }

    companion object {
        const val KEY_IN_FLIGHT = "cjkPassInFlightVersion"
        const val KEY_DISABLED = "cjkDisabledVersion"

        /** No bookkeeping: for the instrumented benchmark, which wants every pass to run. */
        fun unguarded(): CjkOcrGuard = CjkOcrGuard(MemoryStore(), appVersion = 0)
    }

    /** In-memory [Store], for tests and the benchmark. */
    class MemoryStore : Store {
        val values = mutableMapOf<String, Long>()
        override fun read(key: String): Long? = values[key]
        override fun write(key: String, value: Long?) {
            if (value == null) values.remove(key) else values[key] = value
        }
    }
}
