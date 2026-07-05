package com.example.pencatatankeungaan.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val type: String,
    val amount: Long,
    val category: String,
    val description: String,
    val timestamp: Long,
    val source: String,
    val imagePath: String?
)
