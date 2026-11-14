package com.scanrift.android.ui.adaptive

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The scan-session row moves its controls under the name when the row is narrow. */
class ScanRowLayoutTest {

    @Test
    fun `a phone-width row stacks its controls under the name`() {
        // A 411dp phone, and the 360dp sidebar beside the camera on a tablet.
        assertThat(AdaptiveRules.stackScanRowControls(411.dp)).isTrue()
        assertThat(AdaptiveRules.stackScanRowControls(360.dp)).isTrue()
    }

    @Test
    fun `a wide row keeps its controls inline`() {
        assertThat(AdaptiveRules.stackScanRowControls(600.dp)).isFalse()
        assertThat(AdaptiveRules.stackScanRowControls(840.dp)).isFalse()
    }
}
