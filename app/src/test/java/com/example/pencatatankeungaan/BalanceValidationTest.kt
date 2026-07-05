package com.example.pencatatankeungaan

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BalanceValidationTest {

    private lateinit var transactionsList: MutableList<Transaction>

    @Before
    fun setUp() {
        // Initialize with default mock transactions matching MainActivity
        transactionsList = mutableListOf(
            // June 2026: Total Income 1.5M, Total Expense 1.62M (June-only balance = -120k)
            Transaction(1, TransactionType.INCOME, 1500000, "Penjualan", "Penjualan katering siang", 1765800000000L),
            Transaction(2, TransactionType.EXPENSE, 320000, "Bahan Baku", "Beli beras & minyak goreng", 1765886400000L),
            Transaction(3, TransactionType.EXPENSE, 450000, "Operasional", "Bayar listrik ruko", 1766232000000L),
            Transaction(4, TransactionType.EXPENSE, 850000, "Operasional", "Servis RAM & SSD laptop kasir", 1766664000000L),
            
            // May 2026: Total Income 5M, Total Expense 2M
            Transaction(5, TransactionType.INCOME, 5000000, "Penjualan", "Gaji bulanan kantor", 1763974800000L),
            Transaction(6, TransactionType.EXPENSE, 2000000, "Operasional", "Bayar sewa kosan", 1761902400000L),
            
            // March 2026: Total Income 8M, Total Expense 2.5M
            Transaction(7, TransactionType.INCOME, 6000000, "Penjualan", "Penjualan borongan toko", 1757004000000L),
            Transaction(8, TransactionType.INCOME, 2000000, "Penjualan", "Bonus proyek sampingan", 1757436000000L),
            Transaction(9, TransactionType.EXPENSE, 2500000, "Bahan Baku", "Restock supplies bulanan", 1756569600000L)
        )
    }

    private fun getOverallBalance(list: List<Transaction>): Long {
        var balance = 0L
        for (tx in list) {
            if (tx.type == TransactionType.INCOME) {
                balance += tx.amount
            } else {
                balance -= tx.amount
            }
        }
        return balance
    }

    private fun wouldBeNegativeBalanceAfterAdd(newTx: Transaction): Boolean {
        val tempTransactions = transactionsList.toMutableList()
        tempTransactions.add(newTx)
        return getOverallBalance(tempTransactions) < 0
    }

    private fun wouldBeNegativeBalanceAfterEdit(oldTxId: Long, updatedTx: Transaction): Boolean {
        val tempTransactions = transactionsList.toMutableList()
        val index = tempTransactions.indexOfFirst { it.id == oldTxId }
        if (index != -1) {
            tempTransactions[index] = updatedTx
        }
        return getOverallBalance(tempTransactions) < 0
    }

    private fun wouldBeNegativeBalanceAfterDelete(txToDelete: Transaction): Boolean {
        val tempTransactions = transactionsList.toMutableList()
        tempTransactions.remove(txToDelete)
        return getOverallBalance(tempTransactions) < 0
    }

    @Test
    fun testInitialBalanceIsPositive() {
        val currentBalance = getOverallBalance(transactionsList)
        // June Balance: -120k
        // May Balance: +3M
        // March Balance: +5.5M
        // Total Balance: 8.38M
        assertEquals(8380000L, currentBalance)
        assertTrue(currentBalance >= 0)
    }

    @Test
    fun testAddExpenseWithinLimit() {
        // Adding an expense of 5,000,000 should keep the balance positive (8.38M - 5M = 3.38M)
        val newExpense = Transaction(10, TransactionType.EXPENSE, 5000000, "Bahan Baku", "Beli daging sapi", System.currentTimeMillis())
        assertFalse(wouldBeNegativeBalanceAfterAdd(newExpense))
    }

    @Test
    fun testAddExpenseExceedsBalance() {
        // Adding an expense of 9,000,000 should make the balance negative (8.38M - 9M = -620k)
        val newExpense = Transaction(10, TransactionType.EXPENSE, 9000000, "Bahan Baku", "Beli mesin kopi", System.currentTimeMillis())
        assertTrue(wouldBeNegativeBalanceAfterAdd(newExpense))
    }

    @Test
    fun testAddIncomeIsAlwaysAllowed() {
        // Adding any income should never make the balance negative
        val newIncome = Transaction(10, TransactionType.INCOME, 100000000, "Penjualan", "Investasi Modal", System.currentTimeMillis())
        assertFalse(wouldBeNegativeBalanceAfterAdd(newIncome))
    }

    @Test
    fun testEditExpenseToUnderLimit() {
        // Edit transaction 2 (Expense 320k) to Expense 1.0M
        val originalTx = transactionsList.first { it.id == 2L }
        val updatedTx = originalTx.copy(amount = 1000000)
        assertFalse(wouldBeNegativeBalanceAfterEdit(2L, updatedTx))
    }

    @Test
    fun testEditExpenseToOverLimit() {
        // Edit transaction 2 (Expense 320k) to Expense 9.0M (reduces balance by 8.68M, causing overall balance to go below 0)
        val originalTx = transactionsList.first { it.id == 2L }
        val updatedTx = originalTx.copy(amount = 9000000)
        assertTrue(wouldBeNegativeBalanceAfterEdit(2L, updatedTx))
    }

    @Test
    fun testEditIncomeToLowerOverLimit() {
        // Edit transaction 7 (Income 6.0M) to Income 500k (reduces balance by 5.5M, which is safe since total balance is 8.38M)
        val originalTx = transactionsList.first { it.id == 7L }
        var updatedTx = originalTx.copy(amount = 500000)
        assertFalse(wouldBeNegativeBalanceAfterEdit(7L, updatedTx))

        // Edit transaction 7 (Income 6.0M) to Income 100 (reduces balance by 5.999M, which is also safe since total is 8.38M)
        updatedTx = originalTx.copy(amount = 100)
        assertFalse(wouldBeNegativeBalanceAfterEdit(7L, updatedTx))
    }

    @Test
    fun testDeleteIncomeCausesNegativeBalance() {
        // Total balance is 8.38M.
        // Deleting transaction 7 (Income 6.0M) reduces balance to 2.38M (safe).
        val tx7 = transactionsList.first { it.id == 7L }
        assertFalse(wouldBeNegativeBalanceAfterDelete(tx7))

        // Deleting transaction 8 (Income 2.0M) reduces balance to 6.38M (safe).
        val tx8 = transactionsList.first { it.id == 8L }
        assertFalse(wouldBeNegativeBalanceAfterDelete(tx8))

        // Deleting transaction 1 (Income 1.5M) reduces balance to 6.88M (safe).
        val tx1 = transactionsList.first { it.id == 1L }
        assertFalse(wouldBeNegativeBalanceAfterDelete(tx1))

        // Modify list to simulate a lower balance first
        // If we remove May income (5M) and March income (6M), we would have a negative balance.
        transactionsList.clear()
        transactionsList.add(Transaction(1, TransactionType.INCOME, 2000000, "Penjualan", "A", 0))
        transactionsList.add(Transaction(2, TransactionType.INCOME, 1000000, "Penjualan", "B", 0))
        transactionsList.add(Transaction(3, TransactionType.EXPENSE, 2500000, "Operasional", "C", 0))
        // Overall balance is now 500k.
        // Deleting transaction 1 (Income 2.0M) would result in balance of 1.0M - 2.5M = -1.5M (negative).
        val txToDelete = transactionsList.first { it.id == 1L }
        assertTrue(wouldBeNegativeBalanceAfterDelete(txToDelete))
    }
}
