package com.scanrift.android.service.importer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeckImportResultTest {

    @Test
    fun `a clean import reports only the card count`() {
        assertThat(DeckImportResult(cardsAdded = 56).summary).isEqualTo("Added 56 cards.")
    }

    @Test
    fun `skipped lines preview five tokens and explain unsynced sets`() {
        val result = DeckImportResult(
            cardsAdded = 3,
            skippedLines = listOf("A", "B", "C", "D", "E", "F"),
            notes = listOf("TTS codes carry no sections, so sideboard cards land in the main deck."),
        )
        assertThat(result.summary).isEqualTo(
            "Added 3 cards.\n\nSkipped 6: A, B, C, D, E…" +
                "\n\nCards from a set you have not synced yet will not resolve. " +
                "Update the card database in Settings and import again." +
                "\n\nTTS codes carry no sections, so sideboard cards land in the main deck.",
        )
    }
}
