package com.scanrift.android.service.import_

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.scanrift.android.data.local.dao.CardDao
import com.scanrift.android.data.local.dao.CollectionEntryDao
import com.scanrift.android.data.local.entity.CardCondition
import com.scanrift.android.data.local.entity.CollectionEntryEntity
import timber.log.Timber

data class ImportResult(
    val imported: Int,
    val updated: Int,
    val skippedNames: List<String>
) {
    val summary: String
        get() = "Imported $imported new, updated $updated existing."
}

class CollectionImportService(
    private val cardDao: CardDao,
    private val collectionEntryDao: CollectionEntryDao
) {
    suspend fun importCollection(data: String, fileExtension: String): ImportResult {
        return when {
            fileExtension.equals("json", ignoreCase = true) -> importJSON(data)
            fileExtension.equals("csv", ignoreCase = true) ||
                    fileExtension.equals("txt", ignoreCase = true) -> {
                if (isRiftboundGGFormat(data)) importRiftboundGG(data) else importCSV(data)
            }
            else -> throw IllegalArgumentException("Unsupported file format: $fileExtension")
        }
    }

    private fun isRiftboundGGFormat(data: String): Boolean {
        val firstLine = data.lines().firstOrNull()?.lowercase() ?: return false
        return firstLine.startsWith("quantity,name,set,number")
    }

    private suspend fun importCSV(data: String): ImportResult {
        val lines = data.lines().drop(1).filter { it.isNotBlank() }
        var imported = 0
        var updated = 0
        val skipped = mutableListOf<String>()

        for (line in lines) {
            val fields = parseCsvLine(line)
            if (fields.size < 4) continue

            val name = fields[0]
            val publicCode = fields.getOrNull(3) ?: ""
            val quantity = fields.getOrNull(4)?.toIntOrNull() ?: 1
            val isFoil = fields.getOrNull(5)?.equals("Yes", ignoreCase = true) ?: false
            val condition = fields.getOrNull(6)?.let { condVal ->
                CardCondition.entries.firstOrNull { it.value.equals(condVal, ignoreCase = true) }
            } ?: CardCondition.NEAR_MINT

            val card = if (publicCode.isNotBlank()) {
                cardDao.findByPublicCode(publicCode)
            } else {
                null
            }

            if (card == null) {
                skipped.add(name)
                continue
            }

            val existing = collectionEntryDao.findEntry(card.id, isFoil)
            if (existing != null) {
                collectionEntryDao.update(existing.copy(quantity = existing.quantity + quantity))
                updated++
            } else {
                collectionEntryDao.insert(
                    CollectionEntryEntity(
                        cardId = card.id,
                        quantity = quantity,
                        isFoil = isFoil,
                        condition = condition.value
                    )
                )
                imported++
            }
        }

        Timber.d("CSV import: %d imported, %d updated, %d skipped", imported, updated, skipped.size)
        return ImportResult(imported, updated, skipped)
    }

    private suspend fun importJSON(data: String): ImportResult {
        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        val items: List<Map<String, Any>> = Gson().fromJson(data, type)

        var imported = 0
        var updated = 0
        val skipped = mutableListOf<String>()

        for (item in items) {
            val name = item["name"]?.toString() ?: continue
            val publicCode = item["publicCode"]?.toString() ?: ""
            val setId = item["setId"]?.toString() ?: ""
            val collectorNumber = (item["collectorNumber"] as? Double)?.toInt()
            val quantity = (item["quantity"] as? Double)?.toInt() ?: 1
            val isFoil = item["foil"] as? Boolean ?: false
            val conditionStr = item["condition"]?.toString()
            val condition = conditionStr?.let { c ->
                CardCondition.entries.firstOrNull { it.value.equals(c, ignoreCase = true) }
            } ?: CardCondition.NEAR_MINT

            val card = when {
                publicCode.isNotBlank() -> cardDao.findByPublicCode(publicCode)
                setId.isNotBlank() && collectorNumber != null ->
                    cardDao.findBySetAndNumber(setId, collectorNumber)
                else -> null
            }

            if (card == null) {
                skipped.add(name)
                continue
            }

            val existing = collectionEntryDao.findEntry(card.id, isFoil)
            if (existing != null) {
                collectionEntryDao.update(existing.copy(quantity = existing.quantity + quantity))
                updated++
            } else {
                collectionEntryDao.insert(
                    CollectionEntryEntity(
                        cardId = card.id,
                        quantity = quantity,
                        isFoil = isFoil,
                        condition = condition.value
                    )
                )
                imported++
            }
        }

        Timber.d("JSON import: %d imported, %d updated, %d skipped", imported, updated, skipped.size)
        return ImportResult(imported, updated, skipped)
    }

    private suspend fun importRiftboundGG(data: String): ImportResult {
        val lines = data.lines().drop(1).filter { it.isNotBlank() }
        var imported = 0
        var updated = 0
        val skipped = mutableListOf<String>()

        for (line in lines) {
            val fields = parseCsvLine(line)
            if (fields.size < 4) continue

            val quantity = fields[0].toIntOrNull() ?: 1
            val name = fields[1]
            val setId = fields[2]
            val collectorNumber = fields[3].toIntOrNull() ?: continue
            val isFoil = fields.getOrNull(4)?.equals("true", ignoreCase = true) ?: false

            val card = cardDao.findBySetAndNumber(setId, collectorNumber)

            if (card == null) {
                skipped.add(name)
                continue
            }

            val existing = collectionEntryDao.findEntry(card.id, isFoil)
            if (existing != null) {
                collectionEntryDao.update(existing.copy(quantity = existing.quantity + quantity))
                updated++
            } else {
                collectionEntryDao.insert(
                    CollectionEntryEntity(
                        cardId = card.id,
                        quantity = quantity,
                        isFoil = isFoil
                    )
                )
                imported++
            }
        }

        Timber.d("riftbound.gg import: %d imported, %d updated, %d skipped", imported, updated, skipped.size)
        return ImportResult(imported, updated, skipped)
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false

        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString().trim())
        return result
    }
}
