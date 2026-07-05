package com.example.pencatatankeungaan

import java.util.Locale

/**
 * ReceiptParser — Utilitas murni Kotlin untuk mengekstrak nominal belanja dari hasil OCR struk.
 * Menggunakan algoritma "Smart Proximity" yang membaca baris demi baris (line-by-line)
 * untuk mendeteksi nominal transaksi secara kontekstual.
 *
 * Didesain tanpa dependensi Android (Context, UI) untuk memudahkan Unit Testing.
 *
 * ## Urutan Prioritas (Smart Proximity Pipeline):
 * 1. **Keyword + Same Line**: Cari baris yang mengandung kata kunci → ambil angka di baris itu.
 * 2. **Keyword + Next Line(s)**: Jika baris kata kunci tidak mengandung angka,
 *    periksa 1-2 baris di bawahnya (antisipasi format kolom terpisah).
 * 3. **Fallback Cerdas**: Jika seluruh struk tidak mengandung kata kunci sama sekali
 *    (struk sobek/buram), ambil angka terbesar yang lolos validasi (maks 8 digit).
 */
object ReceiptParser {

    // Threshold nominal transaksi yang wajar
    private const val MAX_AMOUNT_THRESHOLD = 100_000_000L  // < 100 juta
    private const val MIN_AMOUNT_THRESHOLD = 100L           // >= 100 rupiah

    // Regex untuk mencocokkan angka dengan ribuan (dot/comma) atau angka polos 3-8 digit
    // Mendukung angka 3 digit (misal: 500) yang disaring oleh system scoring agar tidak bertabrakan dengan quantity.
    private val numberRegex = Regex("""\b\d{1,3}(?:[.,]\d{3})+\b|\b\d{3,8}\b""")

    // Regex untuk mendeteksi tanggal (misal: 29-06-2026, 12/05/24)
    private val dateRegex = Regex("""\b\d{1,4}[-/.]\d{1,2}[-/.]\d{1,4}\b""")

    // Regex untuk mendeteksi waktu dengan format HH:MM atau HH:MM:SS
    private val timeRegex = Regex("""\b\d{1,2}:\d{2}(?::\d{2})?\b""")

    // Regex untuk pembersihan baris
    private val rpCleanRegex = Regex("""(?i)r\s*p\s*\.?""")
    private val digitSpaceRegex = Regex("""(?<=\d)\s+(?=\d)""")
    private val separatorSpaceRegex = Regex("""(?<=\d)\s*([.,])\s*(?=\d)""")
    private val decimalSenRegex = Regex("""(?<=\d)[.,]00\b""")

    // ═════════════════════════════════════════════════════════════════════════
    // KLASIFIKASI KATA KUNCI (Scoring-based)
    // ═════════════════════════════════════════════════════════════════════════

    // 1. Kata kunci total (sangat kuat) - Skor: 100
    private val STRONG_TOTAL_KEYWORDS = listOf(
        "GRAND TOTAL", "GRND TOTAL", "TOTAL AKHIR", "TOTAL BELANJA",
        "TOTAL NETT", "TOTAL NET", "TOTAL DUE", "AMOUNT DUE", "TOTAL ALL", "NET DUE",
        "GRAND TOTA1", "GRAND TOTA|", "GRND TOTA1", "GRND TOTA|", "TOTAL AKH1R", "TOTAL BLNJA"
    )

    // 2. Kata kunci total biasa (kuat) - Skor: 80
    private val MEDIUM_TOTAL_KEYWORDS = listOf(
        "TOTAL", "SUBTOTAL", "SUB TOTAL", "JUMLAH TOTAL", "TOTAL TRANS",
        "TOTAL TRANSAKSI", "JUMLAH",
        "TOTA1", "TOTA|", "TOTAI", "T0TAL", "T0TA1", "SUBTOTA1", "SUBTOTA|"
    )

    // 3. Kata kunci total sedang (sedang) - Skor: 60
    private val WEAK_TOTAL_KEYWORDS = listOf(
        "NETTO", "NET", "NETT", "TOT", "TTL", "JML", "NILAI", "NOMINAL",
        "HARGA TOTAL", "TOTAL HARGA",
        "T0T", "NET0"
    )

    // 4. Kata kunci pembayaran / cash (lemah) - Skor: 20
    private val PAYMENT_KEYWORDS = listOf(
        "BAYAR", "JUMLAH BAYAR", "JML BAYAR", "TUNAI", "CASH", "DIBAYAR",
        "TENDERED", "CASH TENDERED", "UANG TUNAI", "DEBIT", "KREDIT", "CARD",
        "PAYMENT", "OVO", "GOPAY", "DANA", "LINKAJA", "SHOPEEPAY", "QRIS"
    )

    // 5. Kata kunci kembalian (penalti / abaikan) - Mengindikasikan uang kembalian
    private val CHANGE_KEYWORDS = listOf(
        "KEMBALI", "KEMBALIAN", "CHANGE", "DUE BACK", "SISA", "KEMBALI RP", "EXCHANGE"
    )

    // 6. Kata kunci pajak / diskon / biaya tambahan (penalti / abaikan)
    private val TAX_DISCOUNT_KEYWORDS = listOf(
        "PAJAK", "TAX", "PPN", "VAT", "SERVICE", "CHARGE", "SVC", "DISKON",
        "DISCOUNT", "DISC", "POTONGAN", "PROMO", "CASHBACK", "HEMA", "DISKON RP"
    )

    // 7. Kata kunci baris diabaikan (abaikan baris ini sepenuhnya jika cocok)
    private val IGNORE_LINE_KEYWORDS = listOf(
        "NPWP", "TELP", "TLP", "TELEPON", "PHONE", "WA ", "WHATSAPP", "KODE",
        "RESI", "MEMBER", "NO.RESI", "INV", "INVOICE", "NOMOR", "NO:", "NO ",
        "NO.", "TGL", "TANGGAL", "DATE", "TIME", "JAM", "EXPIRED", "EDC",
        "BATCH", "REF", "APPROVAL", "MID", "TID"
    )

    // Regex pencocokan kata kunci dengan batas kata (word boundary) untuk mencegah substring match salah index.
    private val keywordRegex: Regex by lazy {
        val allKeywords = STRONG_TOTAL_KEYWORDS + MEDIUM_TOTAL_KEYWORDS + WEAK_TOTAL_KEYWORDS +
                PAYMENT_KEYWORDS + CHANGE_KEYWORDS + TAX_DISCOUNT_KEYWORDS
        val pattern = allKeywords.sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }
        Regex("\\b($pattern)\\b", RegexOption.IGNORE_CASE)
    }

    private data class AmountCandidate(
        val amount: Long,
        val score: Int,
        val lineIndex: Int
    )

    /**
     * Mem-parse teks hasil OCR struk untuk menemukan nominal transaksi terbaik
     * menggunakan algoritma Proximity Scoring.
     *
     * @param rawText Teks mentah dari OCR (ML Kit Text Recognition)
     * @return Nominal transaksi yang terdeteksi, atau 0 jika tidak ada yang valid
     */
    fun parse(rawText: String?): Long {
        if (rawText.isNullOrBlank()) return 0L

        // 1. Sanitasi awal: Hapus tanggal dan jam agar tidak mengganggu pola angka
        val sanitizedText = sanitizeDateAndTime(rawText)

        // 2. Pembersihan per baris
        val lines = sanitizedText.split('\n')
        val cleanedLines = lines.map { cleanLine(it) }

        val candidates = mutableListOf<AmountCandidate>()

        // 3. Evaluasi skor untuk setiap baris dan angka yang ditemukan
        for (i in cleanedLines.indices) {
            val line = cleanedLines[i]
            val upperLine = line.uppercase(Locale.getDefault())

            // A. Lewati baris jika mengandung kata kunci metadata (NPWP, Telp, No Resi, dll)
            if (IGNORE_LINE_KEYWORDS.any { upperLine.contains(it) }) continue

            // B. Lewati baris jika terdeteksi berupa URL atau email (olshopin.com, kritik & saran, dll)
            if (isUrlOrEmail(line)) continue

            // C. Ekstrak nomor dan skor secara individual untuk setiap kecocokan angka pada baris ini
            val matches = numberRegex.findAll(line).toList()
            if (matches.isEmpty()) continue

            for (match in matches) {
                val amount = match.value.replace(".", "").replace(",", "").toLongOrNull() ?: 0L
                if (!isValidAmount(amount)) continue

                // Dapatkan skor berdasarkan teks di kiri (prefix), kanan (suffix), atau baris sebelumnya (proximity)
                val score = getScoreForMatch(match, line, i, cleanedLines)
                if (score != null) {
                    candidates.add(AmountCandidate(amount, score, i))
                }
            }
        }

        // 4. Urutkan kandidat:
        //    a. Skor tertinggi pertama (prioritas pencocokan kata kunci)
        //    b. Nominal terbesar kedua (jika skor sama, ambil angka terbesar)
        val bestCandidate = candidates.sortedWith(
            compareByDescending<AmountCandidate> { it.score }
                .thenByDescending { it.amount }
        ).firstOrNull()

        return bestCandidate?.amount ?: 0L
    }

    /**
     * Mengevaluasi skor individual untuk satu match angka di dalam baris struk.
     */
    private fun getScoreForMatch(
        match: MatchResult,
        line: String,
        lineIndex: Int,
        cleanedLines: List<String>
    ): Int? {
        val prefix = line.substring(0, match.range.first).uppercase(Locale.getDefault())
        val suffix = line.substring(match.range.last + 1).uppercase(Locale.getDefault())

        // 1. Cek keyword di prefix (paling umum, teks di sebelah kiri angka)
        val lastKwInPrefix = findLastKeyword(prefix)
        if (lastKwInPrefix != null) {
            if (CHANGE_KEYWORDS.contains(lastKwInPrefix) || TAX_DISCOUNT_KEYWORDS.contains(lastKwInPrefix)) {
                return null // Batalkan/Abaikan angka ini (misal: nominal kembalian atau diskon)
            }
            return getKeywordScore(lastKwInPrefix, isProximity = false)
        }

        // 2. Cek keyword di suffix (alternatif jika keyword di sebelah kanan angka)
        val firstKwInSuffix = findFirstKeyword(suffix)
        if (firstKwInSuffix != null) {
            if (CHANGE_KEYWORDS.contains(firstKwInSuffix) || TAX_DISCOUNT_KEYWORDS.contains(firstKwInSuffix)) {
                return null // Batalkan/Abaikan angka ini
            }
            return getKeywordScore(firstKwInSuffix, isProximity = false)
        }

        // 3. Proximity Lookback (jika tidak ada keyword di baris yang sama)
        // Cek 1 baris ke belakang
        if (lineIndex - 1 >= 0) {
            val prevLine = cleanedLines[lineIndex - 1].uppercase(Locale.getDefault())
            if (!IGNORE_LINE_KEYWORDS.any { prevLine.contains(it) }) {
                val kw = findLastKeyword(prevLine)
                if (kw != null) {
                    if (CHANGE_KEYWORDS.contains(kw) || TAX_DISCOUNT_KEYWORDS.contains(kw)) {
                        return null
                    }
                    return getKeywordScore(kw, isProximity = true, offset = 1)
                }
            }
        }

        // Cek 2 baris ke belakang
        if (lineIndex - 2 >= 0) {
            val prevPrevLine = cleanedLines[lineIndex - 2].uppercase(Locale.getDefault())
            if (!IGNORE_LINE_KEYWORDS.any { prevPrevLine.contains(it) }) {
                val kw = findLastKeyword(prevPrevLine)
                if (kw != null) {
                    if (CHANGE_KEYWORDS.contains(kw) || TAX_DISCOUNT_KEYWORDS.contains(kw)) {
                        return null
                    }
                    return getKeywordScore(kw, isProximity = true, offset = 2)
                }
            }
        }

        return 0 // Kandidat fallback jika tidak memiliki keyword kontekstual
    }

    private fun findLastKeyword(text: String): String? {
        val matches = keywordRegex.findAll(text).toList()
        return if (matches.isNotEmpty()) matches.last().value.uppercase(Locale.getDefault()) else null
    }

    private fun findFirstKeyword(text: String): String? {
        val matches = keywordRegex.findAll(text).toList()
        return if (matches.isNotEmpty()) matches.first().value.uppercase(Locale.getDefault()) else null
    }

    private fun getKeywordScore(keyword: String, isProximity: Boolean, offset: Int = 0): Int {
        val kw = keyword.uppercase(Locale.getDefault())
        val baseScore = when {
            STRONG_TOTAL_KEYWORDS.any { it.uppercase(Locale.getDefault()) == kw } -> 100
            MEDIUM_TOTAL_KEYWORDS.any { it.uppercase(Locale.getDefault()) == kw } -> 80
            WEAK_TOTAL_KEYWORDS.any { it.uppercase(Locale.getDefault()) == kw } -> 60
            PAYMENT_KEYWORDS.any { it.uppercase(Locale.getDefault()) == kw } -> 20
            else -> 0
        }
        if (isProximity) {
            return if (offset == 1) baseScore - 10 else baseScore - 20
        }
        return baseScore
    }

    private fun isUrlOrEmail(line: String): Boolean {
        val upper = line.uppercase(Locale.getDefault())
        if (upper.contains("HTTP://") || upper.contains("HTTPS://") || upper.contains("WWW.")) return true
        if (upper.contains(".COM") || upper.contains(".CO.ID") || upper.contains(".NET") || upper.contains(".ORG") || upper.contains(".ID") || upper.contains(".XYZ")) {
            return true
        }
        if (upper.contains("@")) return true
        return false
    }

    // =========================================================================
    // Helper Functions
    // =========================================================================

    /**
     * Menghapus tanggal dan jam dari teks mentah SEBELUM pembersihan digit spacing.
     * Mencegah kasus "2026 22:15" yang ter-compact menjadi "202622".
     */
    private fun sanitizeDateAndTime(text: String): String {
        var result = text
        result = dateRegex.replace(result, " ")
        result = timeRegex.replace(result, " ")
        return result
    }

    /**
     * Membersihkan teks satu baris struk dari karakter sampah dan merapatkan format angka.
     */
    fun cleanLine(line: String): String {
        var cleaned = line.trim()

        // A. Rapikan pembacaan "Rp" (misal: "R p .", "R P", "Rp." menjadi "Rp")
        cleaned = rpCleanRegex.replace(cleaned, "Rp")

        // B. Rapatkan spasi di antara digit (misal: "1 5 . 0 0 0" -> "15.000")
        cleaned = digitSpaceRegex.replace(cleaned, "")

        // C. Rapatkan spasi di sekitar separator ribuan (misal: "15 . 000" -> "15.000")
        cleaned = separatorSpaceRegex.replace(cleaned, "$1")

        // D. Hapus desimal sen ",00" di bagian akhir angka
        cleaned = decimalSenRegex.replace(cleaned, "")

        // E. Hapus kata "Rp" agar tersisa angkanya saja untuk ekstraksi
        cleaned = cleaned.replace("Rp", "")

        return cleaned
    }

    /**
     * Mengekstrak angka dari teks, lalu langsung memfilter yang valid sebagai nominal.
     */
    private fun extractValidAmounts(text: String): List<Long> {
        return numberRegex.findAll(text)
            .map { match ->
                match.value.replace(".", "").replace(",", "").toLongOrNull() ?: 0L
            }
            .filter { isValidAmount(it) }
            .toList()
    }

    /**
     * Memvalidasi apakah suatu angka merupakan nominal transaksi yang wajar.
     * Mengeliminasi:
     * - Angka yang terlalu kecil (di bawah Rp 100)
     * - Angka yang terlalu besar (di atas Rp 100 juta - NPWP, No Seri, Poin Member)
     * - Angka tahun yang umum (2024 s/d 2035)
     */
    private fun isValidAmount(amount: Long): Boolean {
        if (amount < MIN_AMOUNT_THRESHOLD) return false
        if (amount >= MAX_AMOUNT_THRESHOLD) return false
        if (amount in 2024L..2035L) return false // Abaikan tahun
        return true
    }
}
