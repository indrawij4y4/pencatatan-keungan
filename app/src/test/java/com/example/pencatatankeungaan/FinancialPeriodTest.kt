package com.example.pencatatankeungaan

import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class FinancialPeriodTest {

    private fun getAvailablePeriods(transactions: List<Transaction>, currentMonthStr: String): List<String> {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.forLanguageTag("id-ID"))
        val periods = mutableSetOf<String>()

        // Always include current month
        periods.add(currentMonthStr)

        // Add periods from transactions
        for (tx in transactions) {
            periods.add(sdf.format(Date(tx.timestamp)))
        }

        // Chronological sort
        return periods.map { periodStr ->
            try {
                sdf.parse(periodStr) to periodStr
            } catch (e: Exception) {
                Date(0L) to periodStr
            }
        }.sortedBy { it.first }.map { it.second }
    }

    private fun getMockTimestamp(year: Int, monthIndex: Int, day: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, monthIndex)
        cal.set(Calendar.DAY_OF_MONTH, day)
        cal.set(Calendar.HOUR_OF_DAY, 12)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    @Test
    fun testGetAvailablePeriodsEmptyTransactions() {
        val transactions = emptyList<Transaction>()
        val currentMonth = "Juli 2026"
        val periods = getAvailablePeriods(transactions, currentMonth)

        assertEquals(1, periods.size)
        assertEquals("Juli 2026", periods[0])
    }

    @Test
    fun testGetAvailablePeriodsChronologicalSorting() {
        val currentMonth = "Juli 2026"
        val transactions = listOf(
            Transaction(1, TransactionType.INCOME, 1000, "A", "Desc A", getMockTimestamp(2026, Calendar.JUNE, 15)),
            Transaction(2, TransactionType.EXPENSE, 500, "B", "Desc B", getMockTimestamp(2026, Calendar.MARCH, 10)),
            Transaction(3, TransactionType.INCOME, 2000, "C", "Desc C", getMockTimestamp(2026, Calendar.MAY, 20))
        )

        val periods = getAvailablePeriods(transactions, currentMonth)

        // Expected sorted chronologically: Maret 2026, Mei 2026, Juni 2026, Juli 2026
        assertEquals(4, periods.size)
        assertEquals("Maret 2026", periods[0])
        assertEquals("Mei 2026", periods[1])
        assertEquals("Juni 2026", periods[2])
        assertEquals("Juli 2026", periods[3])
    }

    @Test
    fun testGetAvailablePeriodsReverseChronologicalSorting() {
        val currentMonth = "Juli 2026"
        val transactions = listOf(
            Transaction(1, TransactionType.INCOME, 1000, "A", "Desc A", getMockTimestamp(2026, Calendar.JUNE, 15)),
            Transaction(2, TransactionType.EXPENSE, 500, "B", "Desc B", getMockTimestamp(2026, Calendar.MARCH, 10)),
            Transaction(3, TransactionType.INCOME, 2000, "C", "Desc C", getMockTimestamp(2026, Calendar.MAY, 20))
        )

        val periods = getAvailablePeriods(transactions, currentMonth)
        val reversedPeriods = periods.reversed()

        // Expected sorted reverse-chronologically (newer on top): Juli 2026, Juni 2026, Mei 2026, Maret 2026
        assertEquals(4, reversedPeriods.size)
        assertEquals("Juli 2026", reversedPeriods[0])
        assertEquals("Juni 2026", reversedPeriods[1])
        assertEquals("Mei 2026", reversedPeriods[2])
        assertEquals("Maret 2026", reversedPeriods[3])
    }

    @Test
    fun testDetermineEmptyPeriods() {
        val currentMonth = "Juli 2026"
        val transactions = listOf(
            Transaction(1, TransactionType.INCOME, 1000, "A", "Desc A", getMockTimestamp(2026, Calendar.JUNE, 15)),
            Transaction(2, TransactionType.EXPENSE, 500, "B", "Desc B", getMockTimestamp(2026, Calendar.MARCH, 10))
        )

        val periods = getAvailablePeriods(transactions, currentMonth)
        
        // Calculate empty periods
        val emptyPeriods = mutableSetOf<String>()
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.forLanguageTag("id-ID"))
        for (period in periods) {
            val transactionsInPeriod = transactions.filter { tx ->
                sdf.format(Date(tx.timestamp)) == period
            }
            if (transactionsInPeriod.isEmpty()) {
                emptyPeriods.add(period)
            }
        }

        // Maret 2026 and Juni 2026 should not be empty. Juli 2026 should be empty.
        assertFalse(emptyPeriods.contains("Maret 2026"))
        assertFalse(emptyPeriods.contains("Juni 2026"))
        assertTrue(emptyPeriods.contains("Juli 2026"))
    }
}
