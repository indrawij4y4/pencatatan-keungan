package com.example.pencatatankeungaan.data

import com.example.pencatatankeungaan.Transaction
import com.example.pencatatankeungaan.TransactionSource
import com.example.pencatatankeungaan.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TransactionRepository(private val dao: TransactionDao) {

    val allTransactionsFlow: Flow<List<Transaction>> = dao.getAllTransactionsFlow().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun getTransactionById(id: Long): Transaction? {
        return dao.getTransactionById(id)?.toDomain()
    }

    suspend fun insertTransaction(transaction: Transaction): Long {
        return dao.insertTransaction(transaction.toEntity())
    }

    suspend fun updateTransaction(transaction: Transaction) {
        dao.updateTransaction(transaction.toEntity())
    }

    suspend fun deleteTransaction(transaction: Transaction) {
        dao.deleteTransaction(transaction.toEntity())
    }

    suspend fun clearAll() {
        dao.clearAll()
    }

    suspend fun getBalanceBeforeTimestamp(startDate: Long): Long {
        return dao.getBalanceBeforeTimestamp(startDate) ?: 0L
    }

    suspend fun getTransactionsPagedInRange(startDate: Long, endDate: Long, limit: Int, offset: Int): List<Transaction> {
        return dao.getTransactionsPagedInRange(startDate, endDate, limit, offset).map { it.toDomain() }
    }

    private fun TransactionEntity.toDomain(): Transaction {
        return Transaction(
            id = id,
            type = TransactionType.valueOf(type),
            amount = amount,
            category = category,
            description = description,
            timestamp = timestamp,
            source = TransactionSource.valueOf(source),
            imagePath = imagePath
        )
    }

    private fun Transaction.toEntity(): TransactionEntity {
        return TransactionEntity(
            id = id,
            type = type.name,
            amount = amount,
            category = category,
            description = description,
            timestamp = timestamp,
            source = source.name,
            imagePath = imagePath
        )
    }
}
