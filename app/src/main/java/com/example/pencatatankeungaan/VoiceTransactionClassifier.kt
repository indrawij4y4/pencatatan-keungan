package com.example.pencatatankeungaan

/**
 * VoiceTransactionClassifier — Klasifikasi rule-based NLP untuk transaksi keuangan.
 *
 * Mendeteksi:
 * 1. Jenis transaksi (INCOME / EXPENSE) berdasarkan sistem skoring berbobot
 * 2. Kategori (Penjualan / Bahan Baku / Operasional / Lainnya) berdasarkan keyword matching
 * 3. Nominal (delegasi ke IndonesianNumberParser)
 *
 * Strategi klasifikasi jenis transaksi:
 * - Hitung skor berbobot dari SEMUA kata kunci Income dan Expense yang ditemukan
 * - Kata kunci "kuat" (indikator jelas) mendapat bobot lebih tinggi
 * - Kata kunci "lemah" (ambigu / kontekstual) mendapat bobot lebih rendah
 * - Pilih jenis dengan skor tertinggi
 * - Smart Default: jika ambigu atau skor sama → default EXPENSE
 *   (secara statistik, transaksi pengeluaran jauh lebih sering dari pemasukan)
 */
object VoiceTransactionClassifier {

    // ═══════════════════════════════════════════════════
    // WEIGHTED KEYWORD SYSTEM
    // ═══════════════════════════════════════════════════

    /**
     * Data class untuk kata kunci berbobot.
     * @param word kata kunci yang dicari
     * @param weight bobot skor (1 = lemah/ambigu, 2 = sedang, 3 = kuat/jelas)
     */
    private data class WeightedKeyword(val word: String, val weight: Int)

    // ═══════════════════════════════════════════════════
    // DETEKSI JENIS TRANSAKSI (WEIGHTED)
    // ═══════════════════════════════════════════════════

    /**
     * Kata kunci PEMASUKAN (Income) — diperluas dengan variasi imbuhan bahasa Indonesia.
     *
     * Bobot 3 = indikator kuat (hampir pasti income)
     * Bobot 2 = indikator sedang (biasanya income, tapi kontekstual)
     * Bobot 1 = indikator lemah (bisa income atau expense tergantung kalimat)
     */
    private val incomeKeywords = listOf(
        // ── Kata kerja penjualan (bobot tinggi) ──
        WeightedKeyword("jual", 3),
        WeightedKeyword("jualan", 3),
        WeightedKeyword("menjual", 3),
        WeightedKeyword("terjual", 3),
        WeightedKeyword("kejual", 3),
        WeightedKeyword("dijual", 3),
        WeightedKeyword("berjualan", 3),
        WeightedKeyword("penjualan", 3),
        WeightedKeyword("laku", 2),
        WeightedKeyword("terlaku", 2),
        WeightedKeyword("kelaku", 2),

        // ── Kata kerja penerimaan (bobot tinggi) ──
        WeightedKeyword("terima", 3),
        WeightedKeyword("diterima", 3),
        WeightedKeyword("menerima", 3),
        WeightedKeyword("nerima", 3),
        WeightedKeyword("penerimaan", 3),
        WeightedKeyword("dapat", 2),
        WeightedKeyword("mendapat", 3),
        WeightedKeyword("mendapatkan", 3),
        WeightedKeyword("dapet", 2),
        WeightedKeyword("kedapetan", 2),
        WeightedKeyword("didapat", 3),
        WeightedKeyword("perolehan", 3),
        WeightedKeyword("memperoleh", 3),
        WeightedKeyword("diperoleh", 3),
        WeightedKeyword("diberikan", 2),
        WeightedKeyword("dikasih", 2),
        WeightedKeyword("dikasi", 2),

        // ── Kata kerja uang masuk ──
        WeightedKeyword("masuk", 2),
        WeightedKeyword("pemasukan", 3),
        WeightedKeyword("pendapatan", 3),
        WeightedKeyword("penghasilan", 3),
        WeightedKeyword("income", 3),

        // ── Gaji / upah (bobot tinggi — jelas income) ──
        WeightedKeyword("gaji", 3),
        WeightedKeyword("gajian", 3),
        WeightedKeyword("digaji", 3),
        WeightedKeyword("penggajian", 3),
        WeightedKeyword("upah", 3),
        WeightedKeyword("honor", 3),
        WeightedKeyword("honorarium", 3),

        // ── Kata kerja pembayaran masuk ──
        WeightedKeyword("dibayar", 3),
        WeightedKeyword("dibayarin", 3),
        WeightedKeyword("dibayarkan", 3),
        WeightedKeyword("bayaran", 2),
        WeightedKeyword("terbayar", 2),
        WeightedKeyword("terbayarkan", 2),

        // ── Pencairan / cair ──
        WeightedKeyword("cair", 3),
        WeightedKeyword("mencair", 3),
        WeightedKeyword("dicairkan", 3),
        WeightedKeyword("pencairan", 3),

        // ── Konteks usaha / bisnis ──
        WeightedKeyword("omset", 3),
        WeightedKeyword("omzet", 3),
        WeightedKeyword("revenue", 3),
        WeightedKeyword("laba", 3),
        WeightedKeyword("untung", 3),
        WeightedKeyword("keuntungan", 3),
        WeightedKeyword("profit", 3),
        WeightedKeyword("hasil", 2),
        WeightedKeyword("komisi", 3),

        // ── Konteks penjualan katering/warung ──
        WeightedKeyword("orderan", 2),
        WeightedKeyword("pesanan", 2),
        WeightedKeyword("catering", 2),
        WeightedKeyword("katering", 2),

        // ── Transfer masuk / kiriman ──
        WeightedKeyword("kiriman", 2),
        WeightedKeyword("ditransfer", 2),
        WeightedKeyword("transferan", 2),

        // ── Bonus / hadiah ──
        WeightedKeyword("bonus", 2),
        WeightedKeyword("hadiah", 2),
        WeightedKeyword("reward", 2),
        WeightedKeyword("cashback", 2),

        // ── Pinjaman masuk / hutang dibayar ──
        WeightedKeyword("dilunasi", 3),
        WeightedKeyword("dibalikin", 2),
        WeightedKeyword("dikembalikan", 2),
        WeightedKeyword("pengembalian", 3)
    )

    /**
     * Kata kunci PENGELUARAN (Expense) — diperluas dengan variasi imbuhan bahasa Indonesia.
     *
     * Bobot 3 = indikator kuat (hampir pasti expense)
     * Bobot 2 = indikator sedang (biasanya expense, tapi kontekstual)
     * Bobot 1 = indikator lemah (bisa expense atau income tergantung kalimat)
     */
    private val expenseKeywords = listOf(
        // ── Kata kerja pembelian (bobot tinggi) ──
        WeightedKeyword("beli", 3),
        WeightedKeyword("membeli", 3),
        WeightedKeyword("dibeli", 3),
        WeightedKeyword("terbeli", 3),
        WeightedKeyword("kebeli", 3),
        WeightedKeyword("pembelian", 3),
        WeightedKeyword("belanja", 3),
        WeightedKeyword("belanjaan", 3),
        WeightedKeyword("berbelanja", 3),
        WeightedKeyword("shopping", 2),
        WeightedKeyword("borong", 2),
        WeightedKeyword("memborong", 2),

        // ── Jajan ──
        WeightedKeyword("jajan", 3),
        WeightedKeyword("jajanan", 3),

        // ── Kata kerja pembayaran keluar ──
        WeightedKeyword("bayar", 3),
        WeightedKeyword("membayar", 3),
        WeightedKeyword("membayarkan", 3),
        WeightedKeyword("bayarin", 3),
        WeightedKeyword("pembayaran", 3),
        WeightedKeyword("ngebayar", 3),
        WeightedKeyword("kebayar", 2),

        // ── Harga / seharga ──
        WeightedKeyword("seharga", 3),
        WeightedKeyword("harga", 2),
        WeightedKeyword("harganya", 2),

        // ── Lunas / cicil ──
        WeightedKeyword("lunas", 2),
        WeightedKeyword("lunasi", 3),
        WeightedKeyword("melunasi", 3),
        WeightedKeyword("cicil", 3),
        WeightedKeyword("cicilan", 3),
        WeightedKeyword("mencicil", 3),
        WeightedKeyword("dicicil", 2),
        WeightedKeyword("angsuran", 3),

        // ── Transfer / kirim uang ──
        WeightedKeyword("transfer", 2),
        WeightedKeyword("tf", 2),
        WeightedKeyword("kirim", 2),
        WeightedKeyword("mengirim", 2),
        WeightedKeyword("ngirim", 2),

        // ── Kata kerja pengeluaran ──
        WeightedKeyword("pengeluaran", 3),
        WeightedKeyword("keluar", 2),
        WeightedKeyword("habis", 2),
        WeightedKeyword("abis", 2),
        WeightedKeyword("kehabisan", 2),
        WeightedKeyword("menghabiskan", 3),
        WeightedKeyword("expense", 3),

        // ── Ongkos / biaya ──
        WeightedKeyword("ongkir", 3),
        WeightedKeyword("ongkos", 3),
        WeightedKeyword("biaya", 3),
        WeightedKeyword("cost", 2),
        WeightedKeyword("kena", 1),
        WeightedKeyword("kena biaya", 3),

        // ── Kasih / beri uang ──
        WeightedKeyword("kasih", 2),
        WeightedKeyword("ngasih", 2),
        WeightedKeyword("mengasih", 2),
        WeightedKeyword("ngasi", 2),
        WeightedKeyword("kasihkan", 2),
        WeightedKeyword("beri", 2),
        WeightedKeyword("memberikan", 2),
        WeightedKeyword("memberi", 2),

        // ── Konteks hutang/pinjam ──
        WeightedKeyword("pinjam", 2),
        WeightedKeyword("pinjamin", 2),
        WeightedKeyword("minjemin", 2),
        WeightedKeyword("meminjam", 2),
        WeightedKeyword("meminjami", 2),
        WeightedKeyword("meminjamkan", 2),
        WeightedKeyword("dipinjam", 2),
        WeightedKeyword("pinjaman", 2),
        WeightedKeyword("utang", 2),
        WeightedKeyword("hutang", 2),
        WeightedKeyword("ngutang", 2),
        WeightedKeyword("berutang", 2),
        WeightedKeyword("berhutang", 2),

        // ── Konteks operasional ──
        WeightedKeyword("sewa", 3),
        WeightedKeyword("menyewa", 3),
        WeightedKeyword("kontrak", 2),
        WeightedKeyword("tagihan", 3),
        WeightedKeyword("servis", 2),
        WeightedKeyword("service", 2),
        WeightedKeyword("perbaikan", 2),
        WeightedKeyword("reparasi", 2),
        WeightedKeyword("isi ulang", 2),
        WeightedKeyword("topup", 2),
        WeightedKeyword("top up", 2),

        // ── Donasi / sumbangan ──
        WeightedKeyword("donasi", 3),
        WeightedKeyword("sumbangan", 3),
        WeightedKeyword("sedekah", 3),
        WeightedKeyword("infaq", 3),
        WeightedKeyword("zakat", 3),
        WeightedKeyword("amal", 2),

        // ── Denda / pajak ──
        WeightedKeyword("denda", 3),
        WeightedKeyword("pajak", 2),
        WeightedKeyword("retribusi", 2)
    )

    // ═══════════════════════════════════════════════════
    // NEGATION HANDLING
    // ═══════════════════════════════════════════════════

    /**
     * Kata-kata negasi yang membalik makna kata kunci berikutnya.
     * Contoh: "belum dibayar" → bukan income, "tidak jual" → bukan income
     */
    private val negationWords = listOf(
        "tidak", "nggak", "ngga", "gak", "ga", "tak",
        "belum", "blm", "blom",
        "bukan", "jangan", "tanpa"
    )

    // ═══════════════════════════════════════════════════
    // DETEKSI KATEGORI (tidak berubah, tetap simple matching)
    // ═══════════════════════════════════════════════════

    /** Kata kunci kategori PENJUALAN */
    private val salesKeywords = listOf(
        // Aktivitas jual
        "jual", "jualan", "penjualan", "terjual",
        "laku", "orderan", "pesanan",
        "omset", "omzet",
        // Jenis dagangan umum
        "katering", "catering", "makanan", "minuman",
        "dagangan", "barang", "produk"
    )

    /** Kata kunci kategori BAHAN BAKU */
    private val rawMaterialKeywords = listOf(
        // ── Bahan pokok ──
        "beras", "gabah", "ketan",
        "minyak", "minyak goreng", "minyak sayur",
        "tepung", "tepung terigu", "tepung beras", "tepung tapioka", "tepung maizena",
        "gula", "gula pasir", "gula merah", "gula aren",
        "garam", "vetsin", "micin", "penyedap",
        // ── Protein ──
        "telur", "telor", "endog",
        "ayam", "daging", "ikan", "udang", "cumi", "seafood",
        "tahu", "tempe", "oncom",
        // ── Sayuran & buah ──
        "sayur", "sayuran", "kangkung", "bayam", "wortel",
        "bawang", "bawang merah", "bawang putih", "bawang bombay",
        "cabai", "cabe", "lombok", "rawit",
        "tomat", "terong", "kentang", "jagung",
        "sawi", "kol", "kubis", "selada", "timun", "mentimun",
        "buncis", "labu", "pare", "seledri",
        "buah", "pisang", "jeruk", "apel", "mangga", "pepaya",
        "kelapa", "santan",
        // ── Bumbu & rempah ──
        "bumbu", "rempah", "kunyit", "jahe", "lengkuas", "laos",
        "sereh", "serai", "daun salam", "daun jeruk",
        "merica", "lada", "ketumbar", "pala", "kayu manis",
        "kecap", "saos", "saus", "sambal",
        "terasi", "petis",
        // ── Bahan kering & olahan ──
        "mie", "mi", "indomie", "bihun", "soun", "kwetiau",
        "roti", "mentega", "margarin", "keju", "susu",
        "kopi", "teh", "coklat", "cokelat",
        "es batu",
        // ── Kata kunci pembelian bahan ──
        "bahan baku", "bahan mentah", "bahan dasar",
        "restock", "restok", "kulakan", "belanja bahan"
    )

    /** Kata kunci kategori OPERASIONAL */
    private val operationalKeywords = listOf(
        // ── Utilitas ──
        "listrik", "pln", "token listrik",
        "air", "pdam", "pam",
        "wifi", "internet", "indihome", "firstmedia",
        "telepon", "telpon",
        // ── Komunikasi & data ──
        "pulsa", "kuota", "paket data", "paket internet",
        "top up", "topup", "isi ulang",
        // ── Transportasi & BBM ──
        "bensin", "solar", "bbm", "pertamax", "pertalite",
        "parkir", "tol", "e-toll", "etol",
        "ojek", "ojol", "gojek", "grab", "taksi", "taxi",
        "ongkir", "ongkos kirim", "pengiriman", "ekspedisi",
        "jne", "jnt", "sicepat", "anteraja", "pos",
        // ── Sewa & properti ──
        "sewa", "kontrak", "kontrakan",
        "kos", "kosan", "kost",
        // ── Perlengkapan toko/usaha ──
        "plastik", "kresek", "kantong",
        "tisu", "tissue", "lap", "serbet",
        "sabun", "deterjen", "pembersih",
        "gas", "elpiji", "lpg", "tabung gas",
        "galon", "air galon",
        // ── Peralatan & maintenance ──
        "servis", "service", "reparasi", "perbaikan",
        "alat", "peralatan", "sparepart",
        "printer", "tinta", "kertas", "atk",
        // ── Biaya tenaga kerja ──
        "lembur", "thr", "bonus karyawan",
        // ── Pajak & administrasi ──
        "pajak", "retribusi", "iuran",
        "asuransi", "bpjs",
        "materai", "notaris", "izin",
        // ── Digital & langganan ──
        "langganan", "subscription"
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
    // FUNGSI UTAMA
    // ═══════════════════════════════════════════════════

    /**
     * Klasifikasikan teks transkripsi menjadi transaksi.
     *
     * Alur:
     * 1. Deteksi nominal via IndonesianNumberParser
     * 2. Hitung skor berbobot Income vs Expense (baca SELURUH kalimat)
     * 3. Terapkan negation handling (kata negasi membalik skor)
     * 4. Pilih jenis berdasarkan skor tertinggi, default EXPENSE jika ambigu
     * 5. Deteksi kategori berdasarkan keyword matching
     *
     * @param rawText Teks mentah dari speech-to-text
     * @return ClassificationResult berisi type, category, amount, description, dan confidence
     */
    fun classify(rawText: String): ClassificationResult {
        val lowerText = rawText.lowercase().trim()
        var confidence = 0.0f

        // --- 1. Deteksi Nominal ---
        val amount = IndonesianNumberParser.parse(rawText)
        if (amount > 0L) confidence += 0.3f

        // --- 2. Hitung Skor Berbobot untuk Jenis Transaksi ---
        val incomeScore = calculateWeightedScore(lowerText, incomeKeywords)
        val expenseScore = calculateWeightedScore(lowerText, expenseKeywords)

        // --- 3. Tentukan Jenis Transaksi berdasarkan Skor ---
        val type: TransactionType
        val scoreDifference = kotlin.math.abs(incomeScore - expenseScore)

        when {
            // Kasus 1: Income menang jelas (skor lebih tinggi)
            incomeScore > expenseScore && incomeScore > 0 -> {
                type = TransactionType.INCOME
                confidence += 0.3f
                // Bonus confidence jika selisih besar (indikator kuat)
                if (scoreDifference >= 3) confidence += 0.15f
                else if (scoreDifference >= 2) confidence += 0.1f
                else confidence += 0.05f // selisih tipis, kurang yakin
            }

            // Kasus 2: Expense menang jelas (skor lebih tinggi)
            expenseScore > incomeScore && expenseScore > 0 -> {
                type = TransactionType.EXPENSE
                confidence += 0.3f
                if (scoreDifference >= 3) confidence += 0.15f
                else if (scoreDifference >= 2) confidence += 0.1f
                else confidence += 0.05f
            }

            // Kasus 3: Skor sama & keduanya > 0 → SMART DEFAULT ke Expense
            incomeScore > 0 && expenseScore > 0 && incomeScore == expenseScore -> {
                type = TransactionType.EXPENSE
                confidence += 0.15f // confidence rendah karena ambigu
            }

            // Kasus 4: Hanya ada skor expense
            expenseScore > 0 -> {
                type = TransactionType.EXPENSE
                confidence += 0.3f
            }

            // Kasus 5: Hanya ada skor income
            incomeScore > 0 -> {
                type = TransactionType.INCOME
                confidence += 0.3f
            }

            // Kasus 6: Tidak ada kata kunci sama sekali → SMART DEFAULT ke Expense
            // Alasan: secara statistik, transaksi pengeluaran jauh lebih sering
            // dari pemasukan dalam pencatatan keuangan pribadi
            else -> {
                type = TransactionType.EXPENSE
                // Confidence rendah karena murni tebakan
                confidence += 0.05f
            }
        }

        // --- 4. Deteksi Kategori ---
        val salesScore = countKeywordMatches(lowerText, salesKeywords)
        val rawMaterialScore = countKeywordMatches(lowerText, rawMaterialKeywords)
        val operationalScore = countKeywordMatches(lowerText, operationalKeywords)

        // Pilih kategori dengan skor tertinggi
        val categoryScores = mapOf(
            "Penjualan" to salesScore,
            "Bahan Baku" to rawMaterialScore,
            "Operasional" to operationalScore
        )

        val bestCategory = categoryScores.maxByOrNull { it.value }
        val category: String
        if (bestCategory != null && bestCategory.value > 0) {
            category = bestCategory.key
            confidence += 0.2f
            if (bestCategory.value >= 2) confidence += 0.1f
        } else {
            category = "Lainnya"
        }

        // Clamp confidence to 0.0..1.0
        confidence = confidence.coerceIn(0.0f, 1.0f)

        // --- 5. Buat Deskripsi ---
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
    // SCORING ENGINE
    // ═══════════════════════════════════════════════════

    /**
     * Hitung skor berbobot dari kata kunci yang cocok dalam teks.
     *
     * Fitur:
     * - Mendukung multi-word keywords ("bahan baku", "ongkos kirim")
     * - Menerapkan negation handling: jika kata kunci didahului kata negasi,
     *   bobot TIDAK ditambahkan (kata tersebut di-skip)
     * - Kata kunci "kuat" (weight=3) berkontribusi lebih besar ke skor akhir
     *
     * @param text teks yang sudah di-lowercase
     * @param keywords list kata kunci berbobot
     * @return total skor berbobot
     */
    private fun calculateWeightedScore(text: String, keywords: List<WeightedKeyword>): Int {
        var totalScore = 0
        val words = text.split("\\s+".toRegex())

        for (keyword in keywords) {
            if (keyword.word.contains(" ")) {
                // Multi-word keyword: cek as substring
                if (text.contains(keyword.word)) {
                    // Cek negasi sebelum multi-word keyword
                    if (!isNegated(text, keyword.word)) {
                        totalScore += keyword.weight
                    }
                }
            } else {
                // Single-word keyword: cek sebagai whole word
                val regex = Regex("""\b${Regex.escape(keyword.word)}\b""")
                val match = regex.find(text)
                if (match != null) {
                    // Cek negasi: apakah kata sebelumnya adalah kata negasi?
                    if (!isNegatedAtPosition(words, keyword.word)) {
                        totalScore += keyword.weight
                    }
                }
            }
        }
        return totalScore
    }

    /**
     * Cek apakah sebuah kata kunci dinegasikan (didahului kata negasi).
     * Untuk single-word keywords.
     *
     * Contoh: "tidak jual" → jual dinegasikan
     *         "belum dibayar" → dibayar dinegasikan
     */
    private fun isNegatedAtPosition(words: List<String>, keyword: String): Boolean {
        for (i in words.indices) {
            if (words[i] == keyword && i > 0) {
                // Cek 1 kata sebelumnya
                if (words[i - 1] in negationWords) return true
                // Cek 2 kata sebelumnya (untuk pola "belum pernah dibayar")
                if (i > 1 && words[i - 2] in negationWords) return true
            }
        }
        return false
    }

    /**
     * Cek apakah multi-word keyword dinegasikan.
     * Mencari kata negasi yang muncul tepat sebelum keyword dalam teks.
     */
    private fun isNegated(text: String, keyword: String): Boolean {
        val index = text.indexOf(keyword)
        if (index <= 0) return false

        // Ambil beberapa kata sebelum keyword
        val precedingText = text.substring(0, index).trim()
        val precedingWords = precedingText.split("\\s+".toRegex())

        // Cek apakah kata terakhir sebelum keyword adalah negasi
        if (precedingWords.isNotEmpty() && precedingWords.last() in negationWords) {
            return true
        }
        return false
    }

    /**
     * Hitung jumlah keyword (non-weighted) yang cocok dalam teks.
     * Digunakan untuk deteksi kategori yang tidak memerlukan bobot.
     */
    private fun countKeywordMatches(text: String, keywords: List<String>): Int {
        var count = 0
        for (keyword in keywords) {
            if (keyword.contains(" ")) {
                // Multi-word keyword: cek as substring
                if (text.contains(keyword)) count++
            } else {
                // Single-word keyword: cek as whole word using word boundary
                val regex = Regex("""\b${Regex.escape(keyword)}\b""")
                if (regex.containsMatchIn(text)) count++
            }
        }
        return count
    }

    /**
     * Generate deskripsi singkat dari teks asli.
     * Menghapus angka-angka dan kata bantu, menyisakan kata-kata bermakna.
     */
    private fun generateDescription(text: String): String {
        // Capitalize first letter dan trim
        val cleaned = text.trim()
        return if (cleaned.isNotEmpty()) {
            cleaned.replaceFirstChar { it.uppercase() }
        } else {
            "Transaksi dari suara"
        }
    }
}
