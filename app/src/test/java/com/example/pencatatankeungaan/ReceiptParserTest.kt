package com.example.pencatatankeungaan

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptParserTest {

    // =========================================================================
    // cleanLine tests
    // =========================================================================

    @Test
    fun testCleanLine_rpVariations() {
        val result1 = ReceiptParser.cleanLine("R p . 1 5 . 0 0 0").trim()
        assertEquals("15.000", result1)

        val result2 = ReceiptParser.cleanLine("Rp. 15.000").trim()
        assertEquals("15.000", result2)

        val result3 = ReceiptParser.cleanLine("Rp 15.000").trim()
        assertEquals("15.000", result3)
    }

    @Test
    fun testCleanLine_spacedDigits() {
        assertEquals("15.000", ReceiptParser.cleanLine("1 5 . 0 0 0"))
        assertEquals("250.000", ReceiptParser.cleanLine("2 50 . 000"))
    }

    @Test
    fun testCleanLine_decimalSen() {
        assertEquals("15.000", ReceiptParser.cleanLine("15.000,00"))
        assertEquals("1.500.000", ReceiptParser.cleanLine("1.500.000,00"))
    }

    // =========================================================================
    // Smart Proximity — Keyword + Same Line
    // =========================================================================

    @Test
    fun testProximity_keywordAndNumberOnSameLine() {
        // Skenario paling umum: TOTAL dan angka ada di baris yang sama
        val receiptText = """
            TOKO JAYA MAKMUR
            NPWP: 01.234.567.8-999.000
            Telp: 08123456789
            -----------------------------
            Beras Premium 5kg   75.000
            Minyak Goreng 2L    38.000
            -----------------------------
            SUBTOTAL           113.000
            PAJAK 11%           12.430
            TOTAL              125.430
            TUNAI              150.000
            KEMBALI             24.570
            -----------------------------
            Tanggal: 29/06/2026 14:30
        """.trimIndent()

        val parsedAmount = ReceiptParser.parse(receiptText)
        // SUBTOTAL: 113.000, TOTAL: 125.430 → ambil terbesar = 125.430
        assertEquals(125430L, parsedAmount)
    }

    @Test
    fun testProximity_multipleKeywordsOnDifferentLines() {
        // GRAND TOTAL dan JUMLAH BAYAR di baris berbeda
        val receiptText = """
            WARUNG MAKAN SEDEP
            -----------------------------
            Nasi Goreng Spesial 15.000
            Es Teh Manis         5.000
            -----------------------------
            GRAND TOTAL         20.000
            JUMLAH BAYAR        50.000
            KEMBALI             30.000
        """.trimIndent()

        val parsedAmount = ReceiptParser.parse(receiptText)
        // GRAND TOTAL (Skor 100) mengalahkan JUMLAH BAYAR (Skor 20). Maka terdeteksi nominal yang benar: 20.000.
        assertEquals(20000L, parsedAmount)
    }

    // =========================================================================
    // Smart Proximity — Keyword + Next Line (Antisipasi Kolom)
    // =========================================================================

    @Test
    fun testProximity_numberOnNextLine() {
        // Kata kunci "TOTAL" ada di baris sendiri, angka ada di baris berikutnya
        // (Format kolom terpisah yang umum pada struk minimarket)
        val receiptText = """
            INDOMARET
            Item A   15.000
            Item B   25.000
            TOTAL
            40.000
            TUNAI    50.000
        """.trimIndent()

        val parsedAmount = ReceiptParser.parse(receiptText)
        // TOTAL tanpa angka → cek baris berikutnya → 40.000
        assertEquals(40000L, parsedAmount)
    }

    @Test
    fun testProximity_numberTwoLinesBelow() {
        // Angka ada 2 baris di bawah kata kunci (ada baris kosong pemisah)
        val receiptText = """
            ALFAMART
            Item A   15.000
            TOTAL
            
            40.000
            TUNAI    50.000
        """.trimIndent()

        val parsedAmount = ReceiptParser.parse(receiptText)
        // TOTAL → baris +1 kosong → baris +2 ada 40.000
        assertEquals(40000L, parsedAmount)
    }

    @Test
    fun testProximity_keywordNextLineStopsAtAnotherKeyword() {
        // Jika baris berikutnya berisi kata kunci lain, lookahead harus berhenti
        // (tidak "meloncati" keyword ke baris di bawahnya)
        val receiptText = """
            MINIMARKET XYZ
            Item A   10.000
            TOTAL
            BAYAR    50.000
            KEMBALI  40.000
        """.trimIndent()

        val parsedAmount = ReceiptParser.parse(receiptText)
        // TOTAL: tanpa angka → lookahead berhenti di BAYAR (keyword lain)
        // BAYAR: 50.000 → valid
        // Terbesar dari semua proximity = 50.000
        assertEquals(50000L, parsedAmount)
    }

    @Test
    fun testProximity_mixedSameLineAndNextLine() {
        // Campuran: satu keyword punya angka di baris sama, satu lagi di baris bawah
        val receiptText = """
            TOKO ABC
            Item    10.000
            JML     10.000
            TOTAL
            25.000
            BAYAR   30.000
        """.trimIndent()

        val parsedAmount = ReceiptParser.parse(receiptText)
        // TOTAL (Skor 70) mengalahkan BAYAR (Skor 20). Maka terdeteksi nominal yang benar: 25.000.
        assertEquals(25000L, parsedAmount)
    }

    // =========================================================================
    // Fallback Cerdas — Tanpa Kata Kunci
    // =========================================================================

    @Test
    fun testFallback_noKeywordsLargestNumber() {
        // Struk tanpa kata kunci (TOTAL/BAYAR hilang karena sobek/buram)
        val receiptText = """
            MART INDO
            JL. RAYA NO. 10

            Item A               15.000
            Item B               45.000

            Belanja             60.000
            Cash               100.000
            Change              40.000

            29-06-2026 22:15
        """.trimIndent()

        val parsedAmount = ReceiptParser.parse(receiptText)
        // Tidak ada keyword → fallback → angka terbesar = 100.000
        assertEquals(100000L, parsedAmount)
    }

    @Test
    fun testFallback_filterOutSerialNumbers() {
        // Nomor serial/resi panjang harus tersaring (> 8 digit → > 99.999.999)
        val receiptText = """
            MART INDO
            No Resi: 123456789012
            Kode: 9876543210

            Item    25.000
            Cash    50.000
        """.trimIndent()

        val parsedAmount = ReceiptParser.parse(receiptText)
        // Tidak ada keyword → fallback
        // No Resi dan Kode tersaring (> 8 digit, melebihi MAX_AMOUNT_THRESHOLD)
        // Terbesar = 50.000
        assertEquals(50000L, parsedAmount)
    }

    @Test
    fun testFallback_npwpPartialMatchHandled() {
        // NPWP format (XX.XXX.XXX.X-XXX.XXX) bisa ter-match sebagai angka ribuan
        // Pastikan parser tetap mengambil angka terbesar yang wajar
        val receiptText = """
            MART INDO
            NPWP: 01.234.567.8-999.000

            Item    25.000
            Cash    75.000
        """.trimIndent()

        val parsedAmount = ReceiptParser.parse(receiptText)
        // NPWP partial "01.234" → 1234 (di bawah threshold MIN 100 tapi lolos),
        // tapi 75.000 adalah yang terbesar dan valid
        assertEquals(75000L, parsedAmount)
    }

    // =========================================================================
    // Edge Cases
    // =========================================================================

    @Test
    fun testParseEmptyAndNull() {
        assertEquals(0L, ReceiptParser.parse(null))
        assertEquals(0L, ReceiptParser.parse(""))
        assertEquals(0L, ReceiptParser.parse("   "))
    }

    @Test
    fun testParseNoValidNumbers() {
        val text = "Hanya teks biasa tanpa angka"
        assertEquals(0L, ReceiptParser.parse(text))
    }

    @Test
    fun testParseFilterOutYearsAndDates() {
        val text = """
            INV-2026-999
            Tahun: 2026
            Tanggal: 2026/12/30
            Nominal: 45.000
        """.trimIndent()

        val parsedAmount = ReceiptParser.parse(text)
        assertEquals(45000L, parsedAmount)
    }

    @Test
    fun testProximity_keywordFoundButNoValidNumbers() {
        // Kata kunci ditemukan tapi tidak ada angka valid di proximity atau nearby
        val text = """
            TOTAL:
            ----
            Terima Kasih
        """.trimIndent()

        val parsedAmount = ReceiptParser.parse(text)
        // Keyword ada tapi proximity tidak menemukan angka valid → 0
        assertEquals(0L, parsedAmount)
    }

    @Test
    fun testProximity_realWorldStrukAlfamart() {
        // Simulasi struk nyata dari Alfamart/Indomaret
        val receiptText = """
            INDOMARET
            JL. MERDEKA NO.1
            TANGGAL 30-06-2026 11:05
            ==============================
            OREO 133G       x1    12.900
            AQUA 600ML      x2     6.000
            INDOMIE GORENG  x5    17.500
            ==============================
            TOTAL               36.400
            TUNAI               50.000
            KEMBALI             13.600
            ==============================
            *TERIMA KASIH*
        """.trimIndent()

        val parsedAmount = ReceiptParser.parse(receiptText)
        assertEquals(36400L, parsedAmount)
    }

    @Test
    fun testProximity_realWorldStrukWarung() {
        // Simulasi struk warung makan dengan format sederhana
        val receiptText = """
            WRM PAK JOKO
            Nasi Campur   18.000
            Es Teh         3.000
            Kerupuk         2.000
            TOT 23.000
            Bayar 25.000
            Kembali 2.000
        """.trimIndent()

        val parsedAmount = ReceiptParser.parse(receiptText)
        // Sekarang "TOT" cocok sebagai WEAK_TOTAL_KEYWORDS (Skor 60)
        // yang mengalahkan "Bayar" (Skor 20). Maka terdeteksi nominal yang benar: 23.000.
        assertEquals(23000L, parsedAmount)
    }

    @Test
    fun testProximity_rpPrefixWithKeyword() {
        // Struk yang menggunakan "Rp" sebelum angka
        val receiptText = """
            SUPERMARKET
            Item A     Rp 35.000
            Item B     Rp 15.000
            TOTAL      Rp 50.000
        """.trimIndent()

        val parsedAmount = ReceiptParser.parse(receiptText)
        assertEquals(50000L, parsedAmount)
    }

    @Test
    fun testScoring_prefersTotalOverCashPayment() {
        val receiptText = """
            TOKO SERBA ADA
            =====================
            Total Belanja  75.000
            Tunai         100.000
            Kembali        25.000
        """.trimIndent()

        val parsedAmount = ReceiptParser.parse(receiptText)
        // Total Belanja (Skor 100) mengalahkan Tunai (Skor 20) dan Kembali (diabaikan)
        assertEquals(75000L, parsedAmount)
    }

    @Test
    fun testScoring_filtersOutTaxAndDiscounts() {
        val receiptText = """
            RESTORAN RASA INDAH
            =====================
            MAKANAN        50.000
            DISCOUNT        5.000
            PPN 11%         5.500
            SERVICE CHARGE  2.500
            TOTAL DUE      53.000
        """.trimIndent()

        val parsedAmount = ReceiptParser.parse(receiptText)
        // PPN, Discount, Service Charge diabaikan karena keyword negatif.
        // TOTAL DUE (Skor 100) terpilih.
        assertEquals(53000L, parsedAmount)
    }

    @Test
    fun testScoring_ignoresMetadataLines() {
        val receiptText = """
            APOTEK SEHAT
            NPWP: 01.234.567.8-999.000
            TELP: 081299998888
            INV NO: 98765432
            TGL: 30-06-2026
            =====================
            OBAT DEWASA    45.000
            JUMLAH         45.000
        """.trimIndent()

        val parsedAmount = ReceiptParser.parse(receiptText)
        // Baris NPWP, TELP, INV NO, TGL diabaikan sehingga angkanya tidak terdeteksi.
        // JUMLAH (Skor 80) terpilih.
        assertEquals(45000L, parsedAmount)
    }

    @Test
    fun testScoring_supportsThreeDigitAmounts() {
        val receiptText = """
            KANTIN SEKOLAH
            =====================
            Kerupuk Bulat     x1
            TOTAL AKHIR      500
        """.trimIndent()

        val parsedAmount = ReceiptParser.parse(receiptText)
        // Angka 3 digit (500) didukung dan berhasil dideteksi dengan TOTAL AKHIR (Skor 100).
        assertEquals(500L, parsedAmount)
    }

    @Test
    fun testScoring_userReceiptImage() {
        val receiptText = """
            Berkaa Shop
            Jl. Medayu Utara 50, Surabaya
            81529620220414142434
            ---------------------------------
            2022-04-14                    Afi
            14:24:34                 sheila
            No.0-24
            ---------------------------------
            Nasi Ayam Geprek
            1 X 12.000               Rp 12.000
            Nasi Ayam Kremes
            1 X 15.000               Rp 15.000
            Nasi Goreng Spesial
            1 X 20.000               Rp 20.000
            ---------------------------------
            Sub Total                  47.000
            Total                      47.000
            Bayar (Cash)               47.000
            Kembali                         0
            Link Kritik dan Saran:
            olshopin.com/f/748488
        """.trimIndent()

        val parsedAmount = ReceiptParser.parse(receiptText)
        assertEquals(47000L, parsedAmount)
    }
}

