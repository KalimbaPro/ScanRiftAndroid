package com.scanrift.android.ui.collection

import com.scanrift.android.core.time.Iso8601
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class SelectionExportFormat(val fileName: String, val mimeType: String) {
    RIFTBOUND_GG("riftbound_selection_dotgg.csv", "text/csv"),
    CSV("riftbound_selection.csv", "text/csv"),
    JSON("riftbound_selection.json", "application/json"),
    ;

    fun render(rows: List<DisplayCard>): String = when (this) {
        RIFTBOUND_GG -> SelectionExporter.riftboundGg(rows)
        CSV -> SelectionExporter.csv(rows)
        JSON -> SelectionExporter.json(rows)
    }
}

object SelectionExporter {

    private val pretty = Json { prettyPrint = true }

    fun riftboundGg(rows: List<DisplayCard>): String = buildString {
        append("CardId, Normal, Foil, Name, Set\n")
        rows.groupBy { it.card.id }.toSortedMap().values.forEach { group ->
            val card = group.first().card
            val normal = group.filter { it.entry?.isFoil != true }.sumOf { it.quantity }
            val foil = group.filter { it.entry?.isFoil == true }.sumOf { it.quantity }
            append("${card.dotGgId}, $normal, $foil, \"${card.name}\", \"${card.setLabel}\"\n")
        }
    }

    fun csv(rows: List<DisplayCard>): String = buildString {
        append("Name,Set,Rarity,Quantity,Condition,Date Added\n")
        rows.forEach { row ->
            val card = row.card
            append("\"${card.name}\",\"${card.setLabel}\",\"${card.rarity}\",")
            val entry = row.entry
            if (entry == null) {
                append("0,\"\",\"\"\n")
            } else {
                append("${entry.quantity},\"${entry.condition.value}\",\"${Iso8601.format(entry.dateAdded)}\"\n")
            }
        }
    }

    fun json(rows: List<DisplayCard>): String = pretty.encodeToString(
        buildJsonArray {
            rows.forEach { row ->
                add(
                    buildJsonObject {
                        put("name", row.card.name)
                        put("id", row.card.id)
                        put("riftbound_id", row.card.riftboundId)
                        put("set", row.card.setLabel)
                        put("set_id", row.card.setId)
                        put("rarity", row.card.rarity)
                        put("quantity", row.quantity)
                        row.entry?.let {
                            put("condition", it.condition.value)
                            put("date_added", Iso8601.format(it.dateAdded))
                        }
                    },
                )
            }
        },
    )
}
