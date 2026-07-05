package com.example.pencatatankeungaan

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testVoiceTransactionClassifier() {
        // Test Income sentences (static)
        val income1 = VoiceTransactionClassifier.classify("dapat uang dari penjualan katering 50000")
        assertEquals(TransactionType.INCOME, income1.type)

        val income2 = VoiceTransactionClassifier.classify("gaji bulanan masuk 5000000")
        assertEquals(TransactionType.INCOME, income2.type)

        val income3 = VoiceTransactionClassifier.classify("terima transferan dari pak budi seratus ribu rupiah")
        assertEquals(TransactionType.INCOME, income3.type)

        // Test Expense sentences (static)
        val expense1 = VoiceTransactionClassifier.classify("beli bahan baku beras 20000")
        assertEquals(TransactionType.EXPENSE, expense1.type)

        val expense2 = VoiceTransactionClassifier.classify("bayar tagihan listrik seharga seratus lima puluh ribu")
        assertEquals(TransactionType.EXPENSE, expense2.type)

        val expense3 = VoiceTransactionClassifier.classify("jajan bakso 15000")
        assertEquals(TransactionType.EXPENSE, expense3.type)

        // Test Negation handling
        val negation1 = VoiceTransactionClassifier.classify("belum dibayar oleh pelanggan 10000")
        assertEquals(TransactionType.EXPENSE, negation1.type)

        val negation2 = VoiceTransactionClassifier.classify("tidak jual barang hari ini")
        assertEquals(TransactionType.EXPENSE, negation2.type)

        // Test Smart Default (ambiguous / no keywords)
        val defaultCase = VoiceTransactionClassifier.classify("piring pecah 15000")
        assertEquals(TransactionType.EXPENSE, defaultCase.type)

        // Test Directional Active vs Passive Verbs (e.g. beri/kasih, bayar, transfer, kirim)
        // 1. beri/kasih
        val passiveBeri = VoiceTransactionClassifier.classify("diberi uang 5 jt")
        assertEquals(TransactionType.INCOME, passiveBeri.type)
        assertEquals(5000000L, passiveBeri.amount)

        val passiveKasih = VoiceTransactionClassifier.classify("dikasih uang 2 juta sama ibu")
        assertEquals(TransactionType.INCOME, passiveKasih.type)

        val activeBeri = VoiceTransactionClassifier.classify("memberi uang jajan anak 50 ribu")
        assertEquals(TransactionType.EXPENSE, activeBeri.type)

        val activeKasih = VoiceTransactionClassifier.classify("ngasih uang saku ke adek sepuluh ribu")
        assertEquals(TransactionType.EXPENSE, activeKasih.type)

        // 2. bayar
        val passiveBayar = VoiceTransactionClassifier.classify("sudah dibayar oleh klien 1 juta")
        assertEquals(TransactionType.INCOME, passiveBayar.type)

        val activeBayar = VoiceTransactionClassifier.classify("membayar uang sewa ruko 10 juta")
        assertEquals(TransactionType.EXPENSE, activeBayar.type)

        // 3. transfer
        val passiveTransfer = VoiceTransactionClassifier.classify("ditransfer bapak 500 ribu")
        assertEquals(TransactionType.INCOME, passiveTransfer.type)

        val activeTransfer = VoiceTransactionClassifier.classify("mentransfer uang kuliah 3 juta")
        assertEquals(TransactionType.EXPENSE, activeTransfer.type)

        // 4. kirim
        val passiveKirim = VoiceTransactionClassifier.classify("dikirimi uang belanja oleh suami 2 juta")
        assertEquals(TransactionType.INCOME, passiveKirim.type)

        val activeKirim = VoiceTransactionClassifier.classify("mengirim uang bantuan bencana 500000")
        assertEquals(TransactionType.EXPENSE, activeKirim.type)
    }

    @Test
    fun testIndonesianNumberParserOptimizations() {
        // Test Decimal Shorthand parsing
        assertEquals(1500000L, IndonesianNumberParser.parse("1.5 jt"))
        assertEquals(1500000L, IndonesianNumberParser.parse("1,5 juta"))
        assertEquals(1250000L, IndonesianNumberParser.parse("1.25 jt"))
        assertEquals(50500L, IndonesianNumberParser.parse("50.5 rb"))
        assertEquals(50500L, IndonesianNumberParser.parse("50,5 rb"))
        assertEquals(1500000L, IndonesianNumberParser.parse("1.500 rb")) // 1500 ribu = 1.5 juta
        
        // Test "setengah" word logic
        assertEquals(500000L, IndonesianNumberParser.parse("setengah juta"))
        assertEquals(500000000L, IndonesianNumberParser.parse("setengah miliar"))
        assertEquals(1500000L, IndonesianNumberParser.parse("satu juta setengah"))
        assertEquals(2500000L, IndonesianNumberParser.parse("dua juta setengah"))
        
        // Test Non-Monetary (Year/Date) Filtering
        assertEquals(1000L, IndonesianNumberParser.parse("beli obat seharga seribu rupiah untuk tahun 2026"))
        assertEquals(50000L, IndonesianNumberParser.parse("bayar tagihan listrik tahun 2024 sebesar 50 ribu"))
        assertEquals(100000L, IndonesianNumberParser.parse("beli 3 unit meja seharga seratus ribu"))
        
        // Test Phone Number Filtering
        assertEquals(50000L, IndonesianNumberParser.parse("beli pulsa nomor 081234567890 sebesar 50 ribu"))
        assertEquals(10000L, IndonesianNumberParser.parse("transfer ke 081987654321 sebesar ceban"))
    }

    @Test
    fun testClassifierContextualAccuracy() {
        // 1. Preposition "dari" indicating Income
        val incomeDari1 = VoiceTransactionClassifier.classify("transfer dari bapak 50rb")
        assertEquals(TransactionType.INCOME, incomeDari1.type)
        assertEquals(50000L, incomeDari1.amount)

        val incomeDari2 = VoiceTransactionClassifier.classify("kiriman uang dari toko sebesar dua juta")
        assertEquals(TransactionType.INCOME, incomeDari2.type)
        assertEquals(2000000L, incomeDari2.amount)

        // 2. Preposition "ke" with external accounts indicating Expense
        val expenseKe1 = VoiceTransactionClassifier.classify("transfer ke adek 100k")
        assertEquals(TransactionType.EXPENSE, expenseKe1.type)
        assertEquals(100000L, expenseKe1.amount)

        val expenseKe2 = VoiceTransactionClassifier.classify("kirim uang ke budi sebesar satu juta")
        assertEquals(TransactionType.EXPENSE, expenseKe2.type)
        assertEquals(1000000L, expenseKe2.amount)

        // 3. Preposition "ke" with internal pronoun indicating Income
        val incomeKeSaya = VoiceTransactionClassifier.classify("transfer ke rekeningku 250rb")
        assertEquals(TransactionType.INCOME, incomeKeSaya.type)
        assertEquals(250000L, incomeKeSaya.amount)

        val incomeKeAku = VoiceTransactionClassifier.classify("kirim ke e-walletku sebesar seratus ribu")
        assertEquals(TransactionType.INCOME, incomeKeAku.type)
        assertEquals(100000L, incomeKeAku.amount)

        // 4. Boost scoring (Active verb + Income descriptor like "masuk" / "terima")
        val boostMasuk = VoiceTransactionClassifier.classify("ada transfer masuk sebesar seratus ribu")
        assertEquals(TransactionType.INCOME, boostMasuk.type)
        assertEquals(100000L, boostMasuk.amount)

        val boostTerima = VoiceTransactionClassifier.classify("terima pembayaran sebesar lima puluh ribu")
        assertEquals(TransactionType.INCOME, boostTerima.type)
        assertEquals(50000L, boostTerima.amount)

        // 5. New Static rules vocabulary
        val staticIncomeKembali = VoiceTransactionClassifier.classify("uang kembalian belanja 15rb")
        assertEquals(TransactionType.INCOME, staticIncomeKembali.type)
        assertEquals(15000L, staticIncomeKembali.amount)

        val staticIncomeKlaim = VoiceTransactionClassifier.classify("reimburse biaya kantor 200rb")
        assertEquals(TransactionType.INCOME, staticIncomeKlaim.type)
        assertEquals(200000L, staticIncomeKlaim.amount)

        val staticExpenseTalangan = VoiceTransactionClassifier.classify("dana talangan patungan 100rb")
        assertEquals(TransactionType.EXPENSE, staticExpenseTalangan.type)
        assertEquals(100000L, staticExpenseTalangan.amount)

        // 6. User reported issue: "menerima transfer 2.000"
        val userIssue = VoiceTransactionClassifier.classify("menerima transfer 2.000")
        assertEquals(TransactionType.INCOME, userIssue.type)
        assertEquals(2000L, userIssue.amount)
    }
}