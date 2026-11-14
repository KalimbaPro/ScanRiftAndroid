package com.scanrift.android.ui.adaptive

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The printing chooser's columns follow the width the sheet gives it. */
class LegendPrintingColumnsTest {

    @Test
    fun `a phone-width sheet gets three columns`() {
        // 360dp phone minus the sheet's 20dp side padding.
        assertThat(AdaptiveRules.legendPrintingColumns(320.dp)).isEqualTo(3)
    }

    @Test
    fun `an unfolded fold gets five columns from the capped sheet`() {
        // The sheet caps at 640dp; minus padding the grid has 600dp.
        assertThat(AdaptiveRules.legendPrintingColumns(600.dp)).isEqualTo(5)
    }

    @Test
    fun `column count is clamped at both ends`() {
        assertThat(AdaptiveRules.legendPrintingColumns(120.dp)).isEqualTo(2)
        assertThat(AdaptiveRules.legendPrintingColumns(2000.dp)).isEqualTo(6)
    }
}
