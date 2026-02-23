package com.scanrift.android.service.export

import com.scanrift.android.data.local.entity.CardCondition
import com.scanrift.android.data.local.entity.CardEntity
import com.scanrift.android.data.local.entity.CollectionEntryWithCard
import com.google.gson.GsonBuilder

object CollectionExporter {

    fun exportAsCSV(entries: List<CollectionEntryWithCard>): String {
        val header = "Name,Set,Collector Number,Public Code,Quantity,Foil,Condition,Notes"
        val rows = entries.map { (entry, card) ->
            listOf(
                card.name.csvEscape(),
                card.setLabel.csvEscape(),
                card.collectorNumber.toString(),
                card.publicCode,
                entry.quantity.toString(),
                if (entry.isFoil) "Yes" else "No",
                CardCondition.fromValue(entry.condition).value,
                entry.notes?.csvEscape() ?: ""
            ).joinToString(",")
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    fun exportAsJSON(entries: List<CollectionEntryWithCard>): String {
        val items = entries.map { (entry, card) ->
            mapOf(
                "name" to card.name,
                "set" to card.setLabel,
                "setId" to card.setId,
                "collectorNumber" to card.collectorNumber,
                "publicCode" to card.publicCode,
                "quantity" to entry.quantity,
                "foil" to entry.isFoil,
                "condition" to CardCondition.fromValue(entry.condition).value,
                "notes" to (entry.notes ?: "")
            )
        }
        return GsonBuilder().setPrettyPrinting().create().toJson(items)
    }

    fun exportAsRiftboundGG(entries: List<CollectionEntryWithCard>): String {
        val header = "quantity,name,set,number,foil"
        val rows = entries.map { (entry, card) ->
            listOf(
                entry.quantity.toString(),
                card.name.csvEscape(),
                card.setId,
                card.collectorNumber.toString(),
                if (entry.isFoil) "true" else "false"
            ).joinToString(",")
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    private fun String.csvEscape(): String {
        return if (contains(",") || contains("\"") || contains("\n")) {
            "\"${replace("\"", "\"\"")}\""
        } else {
            this
        }
    }
}
