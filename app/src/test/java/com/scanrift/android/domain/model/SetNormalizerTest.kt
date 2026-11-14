package com.scanrift.android.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SetNormalizerTest {

    @Test
    fun `the three promo sets collapse into one`() {
        listOf("OPP", "JDG", "PR").forEach { setId ->
            val result = SetNormalizer.normalize(setId, "Whatever The API Said")
            assertThat(result.id).isEqualTo("PROMO")
            assertThat(result.label).isEqualTo("Promos")
        }
    }

    @Test
    fun `known sets get their pinned label regardless of the api label`() {
        assertThat(SetNormalizer.normalize("OGN", "Something Else").label).isEqualTo("Origins")
        assertThat(SetNormalizer.normalize("OGS", "Origins: Proving Grounds").label)
            .isEqualTo("Proving Grounds")
        assertThat(SetNormalizer.normalize("SFD", "x").label).isEqualTo("Spiritforged")
        assertThat(SetNormalizer.normalize("UNL", "x").label).isEqualTo("Unleashed")
    }

    @Test
    fun `an unknown set keeps its id and the api label`() {
        // VEN shipped after the bundled catalogue was captured; it must pass through.
        val result = SetNormalizer.normalize("VEN", "Vendetta")
        assertThat(result.id).isEqualTo("VEN")
        assertThat(result.label).isEqualTo("Vendetta")
    }

    @Test
    fun `radiance and secret garden get pinned labels`() {
        assertThat(SetNormalizer.normalize("RAD", "x").label).isEqualTo("Radiance")
        assertThat(SetNormalizer.normalize("SGN", "x").label).isEqualTo("Secret Garden")
    }

    @Test
    fun `secret garden is not merged into promos so its printed code still scans`() {
        val result = SetNormalizer.normalize("SGN", "Secret Garden")
        assertThat(result.id).isEqualTo("SGN")
        assertThat(SetNormalizer.isPromoSet("SGN")).isFalse()
    }
}
