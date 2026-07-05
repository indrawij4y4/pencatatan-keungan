package com.example.pencatatankeungaan

enum class TransactionType {
    INCOME,
    EXPENSE
}

enum class TransactionSource {
    VOICE,
    MANUAL,
    OCR
}

data class Transaction(
    val id: Long,
    val type: TransactionType,
    val amount: Long,
    val category: String,
    val description: String,
    val timestamp: Long,
    val source: TransactionSource = TransactionSource.MANUAL,
    val imagePath: String? = null
)
