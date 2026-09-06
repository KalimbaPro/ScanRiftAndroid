package com.scanrift.android.service.importer

import androidx.room.withTransaction
import com.scanrift.android.data.local.ScanRiftDatabase
import com.scanrift.android.data.local.entity.CardEntity
import com.scanrift.android.data.local.entity.CollectionEntryEntity
import com.scanrift.android.di.IoDispatcher
import com.scanrift.android.domain.model.CardCondition
import com.scanrift.android.domain.model.EntryKey
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Imports a collection from riftbound.gg CSV, a generic CSV, or JSON.
 *
 * Imports are **additive**: an existing stack has the imported quantity added to it,
 * never replaced. Someone importing a partial list should not lose what they already
 * had recorded.
 *
 * Decks and history in a full JSON export are deliberately not imported — that is the
 * backup/restore path's job, and it has the merge rules to do it safely.
 */
class CollectionImportService @Inject constructor(
    private val db: ScanRiftDatabase,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun import(content: String, fileExtension: String?, now: Long): ImportResult =
        withContext(io) {
            when (detectFormat(content, fileExtension)) {
                ImportFormat.JSON -> importJson(content, now)
                ImportFormat.RIFTBOUND_GG -> importRiftboundGg(content, now)
                ImportFormat.CSV -> importCsv(content, now)
            }
        }

    /**
     * Sniffs the format from the content rather than trusting the extension, because
     * SAF providers routinely report `application/octet-stream` and a `.txt` name.
     */
    fun detectFormat(content: String, fileExtension: String?): ImportFormat {
        val trimmed = content.trimStart()
        if (fileExtension?.lowercase() == "json" || trimmed.startsWith("[") || trimmed.startsWith("{")) {
            return ImportFormat.JSON
        }
        val firstLine = trimmed.lineSequence().firstOrNull().orEmpty()
        if (firstLine.contains("CardId", ignoreCase = true)) return ImportFormat.RIFTBOUND_GG
        return ImportFormat.CSV
    }

    // ── Formats ──────────────────────────────────────────────────────────────

    private suspend fun importRiftboundGg(content: String, now: Long): ImportResult {
        val rows = CsvParser.lines(content).drop(1)
        return applyRows(now) { session ->
            rows.forEach { line ->
                val fields = CsvParser.parseLine(line)
                if (fields.size < 4) return@forEach

                val parts = fields[0].split('-')
                val setId = parts.getOrNull(0)?.uppercase()
                val number = parts.getOrNull(1)?.toIntOrNull()
                val name = fields.getOrNull(3).orEmpty()
                if (setId == null || number == null) {
                    session.skip(name.ifBlank { fields[0] })
                    return@forEach
                }

                val card = session.findBySetAndNumber(setId, number)
                if (card == null) {
                    session.skip(name.ifBlank { fields[0] })
                    return@forEach
                }

                val normal = fields.getOrNull(1)?.toIntOrNull() ?: 0
                val foil = fields.getOrNull(2)?.toIntOrNull() ?: 0
                if (normal > 0) session.add(card.id, normal, isFoil = false, condition = CardCondition.NEAR_MINT)
                if (foil > 0) session.add(card.id, foil, isFoil = true, condition = CardCondition.NEAR_MINT)
            }
        }
    }

    private suspend fun importCsv(content: String, now: Long): ImportResult {
        val rows = CsvParser.lines(content).drop(1)
        return applyRows(now) { session ->
            rows.forEach { line ->
                val fields = CsvParser.parseLine(line)
                if (fields.size < 5) return@forEach

                val name = fields[0]
                val setLabel = fields.getOrNull(1)
                val quantity = fields.getOrNull(3)?.toIntOrNull() ?: 1
                val condition = CardCondition.fromValueIgnoringCase(fields.getOrNull(4))

                val card = session.findByName(name, setLabel)
                if (card == null) {
                    session.skip(name)
                    return@forEach
                }
                session.add(card.id, quantity, isFoil = false, condition = condition)
            }
        }
    }

    private suspend fun importJson(content: String, now: Long): ImportResult {
        val root = LENIENT.parseToJsonElement(content)
        // Accepts both the legacy bare array and the current { collection: [...] }.
        val items: JsonArray = when {
            root is JsonArray -> root
            root is JsonObject && root["collection"] != null -> root["collection"]!!.jsonArray
            else -> throw ImportException("Unrecognised JSON structure")
        }

        return applyRows(now) { session ->
            items.forEach { element ->
                val item = element.jsonObject
                val name = item["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val id = item["id"]?.jsonPrimitive?.contentOrNull
                val quantity = item["quantity"]?.jsonPrimitive?.intOrNull ?: 1
                val isFoil = item["is_foil"]?.jsonPrimitive?.booleanOrNull ?: false
                val condition = CardCondition.fromValueIgnoringCase(
                    item["condition"]?.jsonPrimitive?.contentOrNull,
                )

                val card = id?.let { session.findById(it) } ?: session.findByName(name, null)
                if (card == null) {
                    session.skip(name.ifBlank { id.orEmpty() })
                    return@forEach
                }
                session.add(card.id, quantity, isFoil, condition)
            }
        }
    }

    // ── Shared write path ────────────────────────────────────────────────────

    private suspend fun applyRows(now: Long, block: suspend (ImportSession) -> Unit): ImportResult =
        db.withTransaction {
            val session = ImportSession(db, now)
            session.prime()
            block(session)
            session.flush()
        }

    /**
     * Accumulates rows against a mutable index.
     *
     * The index is updated on every insert, which is the fix iOS documents: reusing a
     * pre-import snapshot means two rows for the same card in one file each miss the
     * other and produce duplicates.
     */
    private class ImportSession(private val db: ScanRiftDatabase, private val now: Long) {
        private val index = HashMap<EntryKey, CollectionEntryEntity>()
        private val pending = HashMap<EntryKey, CollectionEntryEntity>()
        private val skippedNames = mutableListOf<String>()
        private var created = 0
        private var updated = 0

        suspend fun prime() {
            db.collectionEntryDao().getAll().forEach { entry ->
                val cardId = entry.cardId ?: return@forEach
                index[EntryKey(cardId, entry.isFoil, entry.condition)] = entry
            }
        }

        suspend fun findById(id: String): CardEntity? = db.cardDao().getById(id)

        suspend fun findBySetAndNumber(setId: String, number: Int): CardEntity? =
            db.cardDao().findBySetAndNumber(setId, number)

        /** Exact name match, preferring a card from the named set when one is given. */
        suspend fun findByName(name: String, setLabel: String?): CardEntity? {
            if (name.isBlank()) return null
            val candidates = db.cardDao().findByName(name)
            if (candidates.isEmpty()) return null
            if (setLabel.isNullOrBlank()) return candidates.first()
            return candidates.firstOrNull {
                it.setLabel.equals(setLabel, ignoreCase = true) || it.setId.equals(setLabel, ignoreCase = true)
            } ?: candidates.first()
        }

        fun skip(name: String) {
            if (name.isNotBlank()) skippedNames += name
        }

        fun add(cardId: String, quantity: Int, isFoil: Boolean, condition: CardCondition) {
            if (quantity <= 0) return
            val key = EntryKey(cardId, isFoil, condition.value)
            val existing = pending[key] ?: index[key]

            if (existing == null) {
                val entry = CollectionEntryEntity(
                    cardId = cardId, quantity = quantity, isFoil = isFoil,
                    dateAdded = now, condition = condition.value,
                )
                pending[key] = entry
                index[key] = entry
                created++
            } else {
                // Additive, never replace: an import must not shrink a stack.
                val merged = existing.copy(quantity = existing.quantity + quantity)
                pending[key] = merged
                index[key] = merged
                if (existing.id != 0L) updated++
            }
        }

        suspend fun flush(): ImportResult {
            val dao = db.collectionEntryDao()
            pending.values.forEach { entry ->
                if (entry.id == 0L) dao.insert(entry) else dao.update(entry)
            }
            return ImportResult(
                created = created,
                updated = updated,
                skipped = skippedNames.size,
                skippedNames = skippedNames.toList(),
            )
        }
    }

    private companion object {
        val LENIENT = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
