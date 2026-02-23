package com.scanrift.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "card_lists")
data class CardListEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: String = "#FF9500",
    val isSystem: Boolean = false,
    val systemType: String? = null,
    val createdDate: Long = System.currentTimeMillis()
)
