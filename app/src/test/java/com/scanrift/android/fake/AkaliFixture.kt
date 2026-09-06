package com.scanrift.android.fake

import com.scanrift.android.data.remote.dto.CardDto
import kotlinx.serialization.json.Json

/**
 * The fixture from iOS `CardDatabaseDeduplicationTests`.
 *
 * Real "Akali, Deadly Weapon" data: a normal and an alternate-art printing, each
 * duplicated across a finalised batch (clean_name set, newer updated_on) and a
 * preliminary stub (clean_name null, older updated_on). Four records, two real cards.
 */
object AkaliFixture {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    const val JSON = """
    [
      {
        "id": "final-021", "name": "Akali, Deadly Weapon", "riftbound_id": "ven-021-166",
        "collector_number": 21, "attributes": {"energy": 3, "might": 2, "power": null},
        "classification": {"type": "Unit", "supertype": null, "rarity": "Epic", "domain": ["Fury"]},
        "text": {"rich": "<p>x</p>", "plain": "x", "flavour": null},
        "set": {"set_id": "VEN", "label": "Vendetta"},
        "media": {"image_url": "https://x/normal.png", "artist": "A", "accessibility_text": "x"},
        "tags": ["Akali"], "orientation": "portrait",
        "metadata": {"clean_name": "Akali Deadly Weapon", "updated_on": "2026-07-14T21:35:18.000000+00:00", "alternate_art": false, "overnumbered": false, "signature": false}
      },
      {
        "id": "final-021a", "name": "Akali, Deadly Weapon (Alternate Art)", "riftbound_id": "ven-021a-166",
        "collector_number": 21, "attributes": {"energy": 3, "might": 2, "power": null},
        "classification": {"type": "Unit", "supertype": null, "rarity": "Epic", "domain": ["Fury"]},
        "text": {"rich": "<p>x</p>", "plain": "x", "flavour": null},
        "set": {"set_id": "VEN", "label": "Vendetta"},
        "media": {"image_url": "https://x/alt.png", "artist": "A", "accessibility_text": "x"},
        "tags": ["Akali"], "orientation": "portrait",
        "metadata": {"clean_name": "Akali Deadly Weapon Alternate Art", "updated_on": "2026-07-14T21:35:18.000000+00:00", "alternate_art": true, "overnumbered": false, "signature": false}
      },
      {
        "id": "stub-021", "name": "Akali, Deadly Weapon", "riftbound_id": "ven-021-166",
        "collector_number": 21, "attributes": {"energy": 3, "might": 2, "power": null},
        "classification": {"type": "Unit", "supertype": null, "rarity": "Epic", "domain": ["Fury"]},
        "text": {"rich": "<p>x</p>", "plain": "x", "flavour": null},
        "set": {"set_id": "VEN", "label": "Vendetta"},
        "media": {"image_url": "https://x/normal.png", "artist": "A", "accessibility_text": "x"},
        "tags": ["Akali"], "orientation": "portrait",
        "metadata": {"clean_name": null, "updated_on": "2026-07-10T22:45:08.000000+00:00", "alternate_art": false, "overnumbered": false, "signature": false}
      },
      {
        "id": "stub-021a", "name": "Akali, Deadly Weapon", "riftbound_id": "ven-021a-166",
        "collector_number": 21, "attributes": {"energy": 3, "might": 2, "power": null},
        "classification": {"type": "Unit", "supertype": null, "rarity": "Epic", "domain": ["Fury"]},
        "text": {"rich": "<p>x</p>", "plain": "x", "flavour": null},
        "set": {"set_id": "VEN", "label": "Vendetta"},
        "media": {"image_url": "https://x/alt.png", "artist": "A", "accessibility_text": "x"},
        "tags": ["Akali"], "orientation": "portrait",
        "metadata": {"clean_name": null, "updated_on": "2026-07-10T22:45:08.000000+00:00", "alternate_art": false, "overnumbered": false, "signature": false}
      }
    ]
    """

    fun decode(): List<CardDto> = json.decodeFromString(JSON)
}
