package com.example.pencatatankeungaan

/**
 * VoiceTransactionClassifier — Klasifikasi NLP berbasis aturan (Rule-based NLP)
 * tingkat tinggi untuk transaksi keuangan dalam bahasa Indonesia.
 *
 * Menggunakan sistem analisis morfologi-semantik (Morpho-Semantic Rule Engine)
 * yang mampu membedakan kalimat aktif vs pasif (arah transaksi) secara akurat,
 * dilengkapi dengan Contextual NLP Engine untuk preposisi ("dari"/"ke") dan pronoun.
 */
object VoiceTransactionClassifier {

    // ═══════════════════════════════════════════════════
    // DATA STRUCTURES
    // ═══════════════════════════════════════════════════

    private data class StaticRootRule(
        val name: String,
        val regex: Regex,
        val weight: Int = 3
    )

    private data class DirectionalVerbRule(
        val name: String,
        val passiveRegex: Regex, // Mengindikasikan UANG MASUK (Income)
        val activeRegex: Regex,  // Mengindikasikan UANG KELUAR (Expense)
        val passiveWeight: Int = 5,
        val activeWeight: Int = 4
    )

    // ═══════════════════════════════════════════════════
    // NEGATION SYSTEM
    // ═══════════════════════════════════════════════════

    private val negationWords = setOf(
        "tidak", "nggak", "ngga", "gak", "ga", "tak",
        "belum", "blm", "blom",
        "bukan", "jangan", "tanpa"
    )

    // ═══════════════════════════════════════════════════
    // RULE DEFINITIONS
    // ═══════════════════════════════════════════════════

    private val directionalVerbRules = listOf(
        DirectionalVerbRule(
            name = "beri_kasih",
            passiveRegex = Regex("""\b(di\w*(beri|kasih|kasi)\w*|ter\w*(beri|kasih|kasi)\w*)\b""", RegexOption.IGNORE_CASE),
            activeRegex = Regex("""\b((me|nge|pe)?(beri|kasih|kasi)(|kan|i|mu))\b""", RegexOption.IGNORE_CASE)
        ),
        DirectionalVerbRule(
            name = "bayar",
            passiveRegex = Regex("""\b(di\w*bayar\w*|ter\w*bayar\w*)\b""", RegexOption.IGNORE_CASE),
            activeRegex = Regex("""\b((me|nge|pe)?bayar(|kan|i|mu))\b""", RegexOption.IGNORE_CASE)
        ),
        DirectionalVerbRule(
            name = "transfer",
            passiveRegex = Regex("""\b(di\w*(transfer|tf)\w*|ter\w*(transfer|tf)\w*)\b""", RegexOption.IGNORE_CASE),
            activeRegex = Regex("""\b((me|nge|pe)?(transfer|tf)(|kan|i|mu))\b""", RegexOption.IGNORE_CASE)
        ),
        DirectionalVerbRule(
            name = "kirim",
            passiveRegex = Regex("""\b(di\w*kirim\w*|ter\w*kirim\w*)\b""", RegexOption.IGNORE_CASE),
            activeRegex = Regex("""\b((me|nge|pe)?kirim(|kan|i|mu))\b""", RegexOption.IGNORE_CASE)
        )
    )

    private val staticIncomeRules = listOf(
        StaticRootRule("jual", Regex("""\b(jual\w*|menjual\w*|terjual\w*|kejual\w*|dijual\w*|penjualan\w*)\b""", RegexOption.IGNORE_CASE)),
        StaticRootRule("laku", Regex("""\b(laku|terlaku|kelaku)\b""", RegexOption.IGNORE_CASE)),
        StaticRootRule("terima", Regex("""\b(terima\w*|menerima\w*|diterima\w*|penerimaan\w*|nerima\w*)\b""", RegexOption.IGNORE_CASE)),
        StaticRootRule("dapat", Regex("""\b(dapat\w*|mendapat\w*|didapat\w*|dapet\w*|kedapetan\w*|peroleh\w*|memperoleh\w*)\b""", RegexOption.IGNORE_CASE)),
        StaticRootRule("masuk", Regex("""\b(masuk\w*|pemasukan\w*|pendapatan\w*|penghasilan\w*|income)\b""", RegexOption.IGNORE_CASE)),
        StaticRootRule("gaji", Regex("""\b(gaji\w*|digaji\w*|upah\w*|honor\w*|honorarium)\b""", RegexOption.IGNORE_CASE)),
        StaticRootRule("cair", Regex("""\b(cair\w*|mencair\w*|dicairkan\w*|pencairan\w*)\b""", RegexOption.IGNORE_CASE)),
        StaticRootRule("untung", Regex("""\b(omset|omzet|revenue|laba|untung\w*|keuntungan\w*|profit|komisi)\b""", RegexOption.IGNORE_CASE)),
        StaticRootRule("pesan", Regex("""\b(orderan\w*|pesanan\w*|catering|katering)\b""", RegexOption.IGNORE_CASE)),
        StaticRootRule("bonus", Regex("""\b(bonus\w*|hadiah\w*|reward\w*|cashback)\b""", RegexOption.IGNORE_CASE)),
        StaticRootRule("transferan", Regex("""\b(transferan\w*)\b""", RegexOption.IGNORE_CASE)),
        StaticRootRule("kiriman", Regex("""\b(kiriman\w*)\b""", RegexOption.IGNORE_CASE)),
        StaticRootRule("bayaran", Regex("""\b(bayaran\w*)\b""", RegexOption.IGNORE_CASE)),
        StaticRootRule("pemberian", Regex("""\b(pemberian\w*)\b""", RegexOption.IGNORE_CASE)),
        // Tambahan Kosakata Baru (dengan bobot lebih tinggi untuk menangkal kata-kata pengeluaran terkait)
        StaticRootRule("kembali", Regex("""\b(kembali\w*|kembalian\w*)\b""", RegexOption.IGNORE_CASE), weight = 5),
        StaticRootRule("klaim", Regex("""\b(klaim|reimburse|rembes|refund)\b""", RegexOption.IGNORE_CASE), weight = 5),
        StaticRootRule("hibah", Regex("""\b(hibah|warisan)\b""", RegexOption.IGNORE_CASE)),
        StaticRootRule("bunga", Regex("""\b(bunga|dividen)\b""", RegexOption.IGNORE_CASE))
    )

    private val staticExpenseRules = listOf(
        StaticRootRule("beli", Regex("""\b(beli\w*|membeli\w*|dibeli\w*|terbeli\w*|kebeli\w*|pembelian\w*)\b""", RegexOption.IGNORE_CASE)),
        StaticRootRule("belanja", Regex("""\b(belanja\w*|berbelanja\w*|shopping|borong\w*|memborong\w*)\b""", RegexOption.IGNORE_CASE)),
        StaticRootRule("jajan", Regex("""\b(jajan\w*)\b""", RegexOption.IGNORE_CASE)),
        StaticRootRule("cicil", Regex("""\b(cicil\w*|mencicil\w*|dicicil\w*|angsuran\w*)\b""", RegexOption.IGNORE_CASE)),
        StaticRootRule("keluar", Regex("""\b(keluar\w*|pengeluaran\w*|habis\w*|abis|kehabisan|menghabiskan|expense)\b""", RegexOption.IGNORE_CASE)),
        StaticRootRule("sewa", Regex("""\b(sewa\w*|menyewa\w*|kontrak\w*)\b""", RegexOption.IGNORE_CASE)),
        StaticRootRule("servis", Regex("""\b(servis|service|perbaikan|reparasi)\b""", RegexOption.IGNORE_CASE)),
        StaticRootRule("biaya", Regex("""\b(ongkir|ongkos|biaya\w*|cost|tagihan\w*)\b""", RegexOption.IGNORE_CASE)),
        StaticRootRule("donasi", Regex("""\b(donasi|sumbangan|sedekah|infaq|zakat|amal)\b""", RegexOption.IGNORE_CASE)),
        StaticRootRule("denda", Regex("""\b(denda|pajak|retribusi)\b""", RegexOption.IGNORE_CASE)),
        // Tambahan Kosakata Baru
        StaticRootRule("talangan", Regex("""\b(talangan|nalangin|pinjamin)\b""", RegexOption.IGNORE_CASE)),
        StaticRootRule("utang", Regex("""\b(utang\w*|hutang\w*|rugi\w*|kerugian\w*)\b""", RegexOption.IGNORE_CASE)),
        StaticRootRule("patungan", Regex("""\b(patungan)\b""", RegexOption.IGNORE_CASE)),
        StaticRootRule("langganan", Regex("""\b(langganan|subscribe|member)\b""", RegexOption.IGNORE_CASE))
    )

    // ═══════════════════════════════════════════════════
    // DETEKSI KATEGORI (Keyword-matching sederhana)
    // ═══════════════════════════════════════════════════

    private val salesKeywords = listOf(
        "jual", "jualan", "penjualan", "terjual", "laku", "orderan", "pesanan",
        "omset", "omzet", "katering", "catering", "makanan", "minuman", "dagangan", "barang", "produk"
    )

    private val rawMaterialKeywords = listOf(
        "beras", "gabah", "ketan", "minyak", "tepung", "gula", "garam", "vetsin", "micin", "penyedap",
        "telur", "telor", "endog", "ayam", "daging", "ikan", "udang", "cumi", "seafood", "tahu", "tempe", "oncom",
        "sayur", "kangkung", "bayam", "wortel", "bawang", "cabai", "cabe", "lombok", "rawit", "tomat", "terong",
        "kentang", "jagung", "sawi", "kelapa", "santan", "bumbu", "rempah", "kunyit", "jahe", "lengkuas", "sereh",
        "merica", "lada", "ketumbar", "kecap", "saos", "saus", "sambal", "terasi", "mie", "mi", "indomie", "bihun",
        "roti", "mentega", "keju", "susu", "kopi", "teh", "coklat", "es batu", "bahan baku", "kulakan"
    )

    private val operationalKeywords = listOf(
        "listrik", "pln", "token", "air", "pdam", "pam", "wifi", "internet", "indihome", "telepon", "telpon",
        "pulsa", "kuota", "paket data", "top up", "topup", "bensin", "solar", "bbm", "pertamax", "pertalite",
        "parkir", "tol", "e-toll", "ojek", "ojol", "gojek", "grab", "taksi", "ongkir", "ongkos", "pengiriman",
        "ekspedisi", "sewa", "kontrak", "kos", "kost", "plastik", "kresek", "kantong", "tisu", "tissue",
        "sabun", "deterjen", "gas", "elpiji", "lpg", "galon", "servis", "service", "printer", "tinta", "kertas", "atk",
        "gaji", "upah", "honor", "lembur", "thr", "pajak", "iuran", "bpjs", "materai", "langganan"
    )

    // ═══════════════════════════════════════════════════
    // DATA CLASS HASIL KLASIFIKASI
    // ═══════════════════════════════════════════════════

    data class ClassificationResult(
        val type: TransactionType,
        val category: String,
        val amount: Long,
        val description: String,
        val rawText: String,
        val confidence: Float
    )

    // ═══════════════════════════════════════════════════
    // FUNGSI UTAMA (MAIN CLASSIFY ENTRYPOINT)
    // ═══════════════════════════════════════════════════

    fun classify(rawText: String): ClassificationResult {
        val lowerText = rawText.lowercase().trim()
        var confidence = 0.0f

        // --- 1. Deteksi Nominal ---
        val amount = IndonesianNumberParser.parse(rawText)
        if (amount > 0L) confidence += 0.3f

        // --- 2. Hitung Skor Pemasukan vs Pengeluaran ---
        var incomeScore = 0
        var expenseScore = 0

        // A. Evaluasi Verba Arah (Directional Verbs)
        for (rule in directionalVerbRules) {
            val passiveMatches = rule.passiveRegex.findAll(lowerText)
            var hasPassiveMatch = false
            for (match in passiveMatches) {
                if (!isNegated(lowerText, match.value, match.range.first)) {
                    incomeScore += rule.passiveWeight
                    hasPassiveMatch = true
                    break
                }
            }
            if (hasPassiveMatch) continue

            val activeMatches = rule.activeRegex.findAll(lowerText)
            for (match in activeMatches) {
                if (!isNegated(lowerText, match.value, match.range.first)) {
                    expenseScore += rule.activeWeight
                    break
                }
            }
        }

        // B. Evaluasi Kata Kerja/Kata Benda Statis (Static Rules)
        for (rule in staticIncomeRules) {
            val matches = rule.regex.findAll(lowerText)
            for (match in matches) {
                if (!isNegated(lowerText, match.value, match.range.first)) {
                    incomeScore += rule.weight
                    break
                }
            }
        }

        for (rule in staticExpenseRules) {
            val matches = rule.regex.findAll(lowerText)
            for (match in matches) {
                if (!isNegated(lowerText, match.value, match.range.first)) {
                    expenseScore += rule.weight
                    break
                }
            }
        }

        // C. Analisis Relasi Kontekstual (Preposisi Arah & Kata Ganti Rekening)
        val (incomeBoost, expenseBoost) = analyzeContext(lowerText)
        incomeScore += incomeBoost
        expenseScore += expenseBoost

        // --- 3. Tentukan Jenis Transaksi Berdasarkan Skor ---
        val type: TransactionType
        val scoreDifference = kotlin.math.abs(incomeScore - expenseScore)

        when {
            // Pemasukan Menang Jelas
            incomeScore > expenseScore && incomeScore > 0 -> {
                type = TransactionType.INCOME
                confidence += 0.3f
                if (scoreDifference >= 3) confidence += 0.15f
                else confidence += 0.05f
            }

            // Pengeluaran Menang Jelas
            expenseScore > incomeScore && expenseScore > 0 -> {
                type = TransactionType.EXPENSE
                confidence += 0.3f
                if (scoreDifference >= 3) confidence += 0.15f
                else confidence += 0.05f
            }

            // Ambigu (Skor Sama Kuat) -> Smart Default ke Pengeluaran (Expense)
            incomeScore > 0 && expenseScore > 0 && incomeScore == expenseScore -> {
                type = TransactionType.EXPENSE
                confidence += 0.10f
            }

            // Tidak Ada Keyword Apapun -> Smart Default ke Pengeluaran (Expense)
            else -> {
                type = TransactionType.EXPENSE
                confidence += 0.05f
            }
        }

        // --- 4. Deteksi Kategori ---
        val salesScore = countKeywordMatches(lowerText, salesKeywords)
        val rawMaterialScore = countKeywordMatches(lowerText, rawMaterialKeywords)
        val operationalScore = countKeywordMatches(lowerText, operationalKeywords)

        val categoryScores = mapOf(
            "Penjualan" to salesScore,
            "Bahan Baku" to rawMaterialScore,
            "Operasional" to operationalScore
        )

        val bestCategory = categoryScores.maxByOrNull { it.value }
        val category = if (bestCategory != null && bestCategory.value > 0) {
            confidence += 0.2f
            bestCategory.key
        } else {
            "Lainnya"
        }

        confidence = confidence.coerceIn(0.0f, 1.0f)
        val description = generateDescription(lowerText)

        return ClassificationResult(
            type = type,
            category = category,
            amount = amount,
            description = description,
            rawText = rawText,
            confidence = confidence
        )
    }

    // ═══════════════════════════════════════════════════
    // UTILITY & CONTEXT METHODS
    // ═══════════════════════════════════════════════════

    /**
     * Memindai hubungan relasional kata kerja arah dengan preposisi ("dari"/"ke") dan pronoun.
     */
    private fun analyzeContext(lowerText: String): Pair<Int, Int> {
        var incomeBoost = 0
        var expenseBoost = 0

        // Membagi kalimat menjadi token kata dasar (tanpa tanda baca)
        val tokens = lowerText.split(Regex("""\s+"""))
            .map { it.replace(Regex("""[^a-zA-Z0-9]"""), "") }
            .filter { it.isNotEmpty() }

        val directionalVerbs = setOf("transfer", "tf", "kirim", "ngirim", "bayar", "bayarin", "kirimi", "dikirimi")
        val fromPrepositions = setOf("dari", "dr")
        val toPrepositions = setOf("ke", "kepada", "kpd", "untuk", "utk", "buat")
        val internalAccounts = setOf("saya", "aku", "rekeningku", "walletku", "dompetku", "kas", "ewalletku")

        for (i in tokens.indices) {
            val token = tokens[i]

            // Periksa apakah token mengandung/merupakan kata kerja arah
            if (token in directionalVerbs || directionalVerbs.any { token.contains(it) }) {
                // Pindai ke depan hingga 3 token berikutnya
                for (j in 1..3) {
                    if (i + j < tokens.size) {
                        val nextToken = tokens[i + j]

                        // Kasus A: Diikuti preposisi "dari" / "dr" -> UANG MASUK (Income)
                        if (nextToken in fromPrepositions) {
                            incomeBoost += 6
                            break
                        }

                        // Kasus B: Diikuti preposisi tujuan "ke" / "untuk" / "buat"
                        if (nextToken in toPrepositions) {
                            // Cek apakah tujuan adalah akun internal milik user sendiri
                            if (i + j + 1 < tokens.size) {
                                val targetToken = tokens[i + j + 1]
                                if (targetToken in internalAccounts) {
                                    // "transfer ke saya" -> UANG MASUK (Income)
                                    incomeBoost += 6
                                } else {
                                    // "transfer ke budi" -> UANG KELUAR (Expense)
                                    expenseBoost += 6
                                }
                            } else {
                                // "transfer ke..." -> UANG KELUAR (Expense)
                                expenseBoost += 6
                            }
                            break
                        }
                    }
                }
            }
        }

        // Skor boost untuk kombinasi verba aktif (misal "transfer") dan deskriptor masuk (misal "menerima"/"masuk")
        val hasActiveVerb = tokens.any { it in directionalVerbs || directionalVerbs.any { v -> it.contains(v) } }
        
        val terimaRegex = Regex("""\b(terima\w*|menerima\w*|diterima\w*|penerimaan\w*|nerima\w*)\b""", RegexOption.IGNORE_CASE)
        val dapatRegex = Regex("""\b(dapat\w*|mendapat\w*|didapat\w*|dapet\w*|kedapetan\w*|peroleh\w*|memperoleh\w*)\b""", RegexOption.IGNORE_CASE)
        val masukRegex = Regex("""\b(masuk\w*|pemasukan\w*|pendapatan\w*|penghasilan\w*|income)\b""", RegexOption.IGNORE_CASE)
        
        val hasIncomeDescriptor = terimaRegex.containsMatchIn(lowerText) || 
                                   dapatRegex.containsMatchIn(lowerText) || 
                                   masukRegex.containsMatchIn(lowerText)
                                   
        if (hasActiveVerb && hasIncomeDescriptor) {
            incomeBoost += 5
        }

        return Pair(incomeBoost, expenseBoost)
    }

    /**
     * Mendeteksi apakah suatu kata dinegasikan oleh kata sebelum atau dua kata sebelumnya.
     */
    private fun isNegated(text: String, matchedWord: String, matchIndex: Int): Boolean {
        if (matchIndex <= 0) return false

        val precedingText = text.substring(0, matchIndex).trim()
        val precedingWords = precedingText.split("\\s+".toRegex())

        if (precedingWords.isNotEmpty()) {
            val lastWord = precedingWords.last().replace(Regex("""[^a-zA-Z0-9]"""), "")
            if (lastWord in negationWords) return true

            if (precedingWords.size >= 2) {
                val secondToLastWord = precedingWords[precedingWords.size - 2].replace(Regex("""[^a-zA-Z0-9]"""), "")
                if (secondToLastWord in negationWords) return true
            }
        }
        return false
    }

    private fun countKeywordMatches(text: String, keywords: List<String>): Int {
        var count = 0
        for (keyword in keywords) {
            val regex = Regex("""\b${Regex.escape(keyword)}\b""", RegexOption.IGNORE_CASE)
            if (regex.containsMatchIn(text)) count++
        }
        return count
    }

    private fun generateDescription(text: String): String {
        val cleaned = text.trim()
        return if (cleaned.isNotEmpty()) {
            cleaned.replaceFirstChar { it.uppercase() }
        } else {
            "Transaksi dari suara"
        }
    }
}
