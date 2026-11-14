package com.scanrift.android.ui.adaptive

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Single-card sheets: bottom sheet on a phone, centred pop-up on a wide window. */
class CardPopUpTest {

    @Test
    fun `a phone in portrait keeps the bottom sheet`() {
        assertThat(AdaptiveRules.useCardPopUp(windowWidth = 411.dp, windowHeight = 891.dp)).isFalse()
    }

    @Test
    fun `a folded Fold cover screen keeps the bottom sheet`() {
        assertThat(AdaptiveRules.useCardPopUp(windowWidth = 360.dp, windowHeight = 860.dp)).isFalse()
    }

    @Test
    fun `an unfolded Fold in portrait gets the pop-up`() {
        assertThat(AdaptiveRules.useCardPopUp(windowWidth = 700.dp, windowHeight = 840.dp)).isTrue()
    }

    @Test
    fun `an unfolded Fold in landscape and a tablet get the pop-up`() {
        assertThat(AdaptiveRules.useCardPopUp(windowWidth = 840.dp, windowHeight = 700.dp)).isTrue()
        assertThat(AdaptiveRules.useCardPopUp(windowWidth = 1280.dp, windowHeight = 800.dp)).isTrue()
    }

    @Test
    fun `a phone in landscape is wide but too short, so it keeps the sheet`() {
        assertThat(AdaptiveRules.useCardPopUp(windowWidth = 891.dp, windowHeight = 411.dp)).isFalse()
    }

    @Test
    fun `the breakpoints are inclusive`() {
        assertThat(AdaptiveRules.useCardPopUp(windowWidth = 600.dp, windowHeight = 480.dp)).isTrue()
        assertThat(AdaptiveRules.useCardPopUp(windowWidth = 599.dp, windowHeight = 480.dp)).isFalse()
        assertThat(AdaptiveRules.useCardPopUp(windowWidth = 600.dp, windowHeight = 479.dp)).isFalse()
    }
}
