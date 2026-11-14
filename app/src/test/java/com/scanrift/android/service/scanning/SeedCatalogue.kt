package com.scanrift.android.service.scanning

import com.scanrift.android.data.local.mapper.toDomain
import com.scanrift.android.data.remote.dto.PaginatedCardsResponse
import com.scanrift.android.di.SerializationModule
import com.scanrift.android.domain.model.Card
import com.scanrift.android.service.sync.CardDedup
import java.io.File

/** The bundled catalogue as a fresh install sees it. Unit tests run from the module directory. */
object SeedCatalogue {
    val cards: List<Card> by lazy {
        val seed = SerializationModule.provideApiJson()
            .decodeFromString<PaginatedCardsResponse>(File("src/main/res/raw/cards.json").readText())
        CardDedup.dedupedByRiftboundId(seed.items).map { it.toEntity().toDomain() }
    }

    fun card(riftboundId: String): Card = cards.single { it.riftboundId == riftboundId }
}
