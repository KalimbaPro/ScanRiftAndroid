package com.scanrift.android.service.scanning

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The crash marker around a Chinese/Korean OCR pass. A native crash (SIGILL in Play
 * Services' TFLite) kills the process outright, so "crashed" can only be seen from the
 * next launch: a marker that was set and never cleared.
 */
class CjkOcrGuardTest {

    private val store = CjkOcrGuard.MemoryStore()

    @Test
    fun `a pass that finishes leaves no marker behind`() = runTest {
        val guard = CjkOcrGuard(store, appVersion = 8)
        assertThat(guard.guard { "zh" }).isEqualTo("zh")
        assertThat(store.values).doesNotContainKey(CjkOcrGuard.KEY_IN_FLIGHT)
        assertThat(CjkOcrGuard(store, appVersion = 8).isEnabled).isTrue()
    }

    @Test
    fun `the marker is on disk while the pass runs`() = runTest {
        val guard = CjkOcrGuard(store, appVersion = 8)
        guard.guard { assertThat(store.values[CjkOcrGuard.KEY_IN_FLIGHT]).isEqualTo(8L) }
    }

    @Test
    fun `a process that died mid-pass disables CJK on the next launch`() = runTest {
        // What a SIGILL leaves behind: the marker, never cleared.
        store.write(CjkOcrGuard.KEY_IN_FLIGHT, 8)
        val nextLaunch = CjkOcrGuard(store, appVersion = 8)
        assertThat(nextLaunch.isEnabled).isFalse()
        var ran = false
        assertThat(nextLaunch.guard { ran = true }).isNull()
        assertThat(ran).isFalse()
        // And it stays off for later launches of the same version.
        assertThat(CjkOcrGuard(store, appVersion = 8).isEnabled).isFalse()
    }

    @Test
    fun `an app update tries CJK again`() {
        store.write(CjkOcrGuard.KEY_IN_FLIGHT, 8)
        CjkOcrGuard(store, appVersion = 8).isEnabled
        assertThat(CjkOcrGuard(store, appVersion = 9).isEnabled).isTrue()
    }

    @Test
    fun `an exception is not a crash`() = runTest {
        val guard = CjkOcrGuard(store, appVersion = 8)
        runCatching { guard.guard { error("model not downloaded") } }
        assertThat(store.values).doesNotContainKey(CjkOcrGuard.KEY_IN_FLIGHT)
        assertThat(CjkOcrGuard(store, appVersion = 8).isEnabled).isTrue()
    }

    @Test
    fun `overlapping passes clear the marker only when the last one ends`() = runTest {
        val guard = CjkOcrGuard(store, appVersion = 8)
        guard.guard {
            guard.guard { }
            assertThat(store.values).containsKey(CjkOcrGuard.KEY_IN_FLIGHT)
        }
        assertThat(store.values).doesNotContainKey(CjkOcrGuard.KEY_IN_FLIGHT)
    }
}
