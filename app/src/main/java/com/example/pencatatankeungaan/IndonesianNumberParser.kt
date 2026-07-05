package com.example.pencatatankeungaan

/**
 * IndonesianNumberParser — Utilitas konversi teks bahasa Indonesia menjadi angka nominal.
 *
 * Pipeline:
 *   Raw Text → Lowercase & Trim → Hapus Stopwords → Tokenize
 *   → Konversi kata angka ke digit → Proses pengali → Regex fallback
 *   → Return nominal terbesar
 *
 * Contoh:
 *   "lima puluh ribu" → 50000
 *   "dua ratus lima puluh ribu" → 250000
 *   "sejuta setengah" → 1500000
 *   "50rb" → 50000
 *   "ceban" → 10000
 *   "25000" → 25000
 */
object IndonesianNumberParser {

    // ═══════════════════════════════════════════════════
    // BAGIAN A: STOPWORDS — Kata yang dibuang sebelum parsing
    // ═══════════════════════════════════════════════════
    private val stopwords = setOf(
        // Satuan mata uang
        "rupiah", "rp", "perak", "idr",
        // Kata hubung/preposisi umum
        "buat", "untuk", "dari", "ke", "di", "yang", "dan", "dengan",
        "sama", "ama", "nya", "nih", "itu", "ini", "ya", "dong", "sih",
        "lah", "kah", "tuh", "deh", "kok", "kan",
        // Kata deskriptif nominal
        "sebesar", "senilai", "sejumlah", "seharga", "total",
        "kurang", "lebih", "sekitar", "hampir",
        "per",
        // Kata waktu non-informatif
        "hari", "kemarin", "tadi", "barusan", "baru"
    )

    // ═══════════════════════════════════════════════════
    // BAGIAN B: DICTIONARY ANGKA DASAR
    // ═══════════════════════════════════════════════════
    private val numberWords = mapOf(
        // Angka dasar 0-9
        "nol" to 0L, "kosong" to 0L,
        "satu" to 1L,
        "dua" to 2L,
        "tiga" to 3L,
        "empat" to 4L,
        "lima" to 5L,
        "enam" to 6L,
        "tujuh" to 7L,
        "delapan" to 8L,
        "sembilan" to 9L,
        // Belasan (11-19)
        "sebelas" to 11L,
        "seblas" to 11L,
        // Angka 10 sebagai unit
        "sepuluh" to 10L,
        // Prefix "se" khusus di-handle terpisah di parsing logic
        // Slang & singkatan umum (standalone value)
        "cepek" to 100L,
        "gopek" to 500L,
        "seceng" to 1000L,
        "goceng" to 5000L,
        "ceban" to 10000L,
        "noban" to 50000L
    )

    // ═══════════════════════════════════════════════════
    // BAGIAN C: PENGALI (Multiplier)
    // ═══════════════════════════════════════════════════
    private val multiplierWords = mapOf(
        "puluh" to 10L,
        "plh" to 10L,
        "belas" to -1L, // sentinel: handled specially for belasan
        "ratus" to 100L,
        "atus" to 100L,
        "ribu" to 1_000L,
        "rb" to 1_000L,
        "rebu" to 1_000L,
        "rbu" to 1_000L,
        "juta" to 1_000_000L,
        "jt" to 1_000_000L,
        "jet" to 1_000_000L,
        "miliar" to 1_000_000_000L,
        "milyar" to 1_000_000_000L
    )

    // Regex untuk menangkap angka numerik dan shorthand
    private val shorthandRegex = Regex("""(\d[\d.,]*)\s*(rb|ribu|rebu|rbu|jt|juta|jet|k|m|miliar|milyar)\b""", RegexOption.IGNORE_CASE)
    private val pureNumberRegex = Regex("""\d[\d.,]*""")

    /**
     * Fungsi utama: Ekstrak nominal uang terbesar dari teks bahasa Indonesia.
     * @param text Teks mentah dari speech-to-text
     * @return Nominal terbesar yang ditemukan, atau 0 jika tidak ditemukan
     */
    fun parse(text: String): Long {
        if (text.isBlank()) return 0L

        val cleanedText = text.lowercase().trim()

        // Tahap 1: Coba shorthand regex dulu ("50rb", "2jt", "100k")
        val shorthandResults = mutableListOf<Long>()
        shorthandRegex.findAll(cleanedText).forEach { match ->
            val numberPart = match.groupValues[1].replace(".", "").replace(",", "")
            val multiplierPart = match.groupValues[2].lowercase()
            val baseNumber = numberPart.toLongOrNull() ?: 0L
            val multiplier = when (multiplierPart) {
                "rb", "ribu", "rebu", "rbu" -> 1_000L
                "jt", "juta", "jet" -> 1_000_000L
                "k" -> 1_000L
                "m" -> 1_000_000L
                "miliar", "milyar" -> 1_000_000_000L
                else -> 1L
            }
            shorthandResults.add(baseNumber * multiplier)
        }

        // Tahap 2: Coba word-based parsing
        val wordResult = parseWords(cleanedText)

        // Tahap 3: Coba pure number regex ("25000", "1.500.000")
        val pureNumberResults = mutableListOf<Long>()
        pureNumberRegex.findAll(cleanedText).forEach { match ->
            val numStr = match.value.replace(".", "").replace(",", "")
            numStr.toLongOrNull()?.let { pureNumberResults.add(it) }
        }

        // Kumpulkan semua kandidat dan ambil yang terbesar
        val allCandidates = mutableListOf<Long>()
        allCandidates.addAll(shorthandResults)
        if (wordResult > 0L) allCandidates.add(wordResult)
        allCandidates.addAll(pureNumberResults)

        return allCandidates.maxOrNull() ?: 0L
    }

    /**
     * Parsing kata-kata angka Indonesia menjadi nilai numerik.
     *
     * Logika:
     * - Angka dasar (satu-sembilan) di-buffer sebagai currentValue
     * - Saat bertemu multiplier (puluh/ratus), kalikan currentValue dengan multiplier
     * - Saat bertemu multiplier besar (ribu/juta/miliar), kalikan accumulated section lalu
     *   tambahkan ke grandTotal
     * - "setengah" setelah multiplier besar = 0.5 × multiplier besar terakhir
     * - Prefix "se" di awal sebelum multiplier = 1 × multiplier
     *
     * Contoh:
     *   "dua ratus lima puluh ribu" → (2×100 + 5×10) × 1000 = 250000
     *   "sejuta setengah" → 1×1000000 + 0.5×1000000 = 1500000
     *   "tiga belas ribu" → 13 × 1000 = 13000
     */
    private fun parseWords(text: String): Long {
        // Hapus stopwords
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return 0L

        var grandTotal = 0L     // Akumulator utama
        var sectionTotal = 0L   // Akumulator untuk section saat ini (sebelum ribu/juta/miliar)
        var currentValue = 0L   // Nilai angka yang sedang diproses
        var lastBigMultiplier = 0L // Multiplier besar terakhir (untuk "setengah")
        var hasWordNumber = false

        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]

            // Cek apakah token adalah angka numerik murni
            val numericValue = token.replace(".", "").replace(",", "").toLongOrNull()
            if (numericValue != null) {
                currentValue = numericValue
                hasWordNumber = true
                i++
                continue
            }

            // Cek slang standalone (cepek, gopek, seceng, goceng, ceban, noban)
            if (token in numberWords && numberWords[token]!! >= 100L) {
                // Slang terms are standalone values, not composable
                sectionTotal += numberWords[token]!!
                currentValue = 0L
                hasWordNumber = true
                i++
                continue
            }

            // Cek angka dasar kata (satu-sembilan, sepuluh, sebelas)
            if (token in numberWords) {
                currentValue = numberWords[token]!!
                hasWordNumber = true
                i++
                continue
            }

            // Handle prefix "se" yang berdiri sendiri ("se ribu" -> 1000)
            if (token == "se") {
                currentValue = 1L
                hasWordNumber = true
                i++
                continue
            }

            // Handle "setengah" / "stengah"
            if (token == "setengah" || token == "stengah" || token == "tengah") {
                if (lastBigMultiplier > 0L) {
                    // "sejuta setengah" → tambahkan 0.5 × multiplier besar terakhir
                    grandTotal += lastBigMultiplier / 2
                } else {
                    // Standalone "setengah" tanpa konteks → abaikan
                }
                hasWordNumber = true
                i++
                continue
            }

            // Cek multiplier
            if (token in multiplierWords) {
                val multiplier = multiplierWords[token]!!
                hasWordNumber = true

                if (token == "belas") {
                    // "X belas" → X + 10 (misal: "tiga belas" → 13)
                    currentValue += 10L
                    i++
                    continue
                }

                if (multiplier >= 1_000L) {
                    // Multiplier besar (ribu, juta, miliar)
                    // Akumulasi section sebelumnya
                    if (currentValue == 0L && sectionTotal == 0L) {
                        // "ribu" tanpa angka di depan → dianggap 1 × multiplier
                        currentValue = 1L
                    }
                    val sectionValue = if (sectionTotal > 0L) {
                        (sectionTotal + currentValue) * multiplier
                    } else {
                        currentValue * multiplier
                    }
                    grandTotal += sectionValue
                    sectionTotal = 0L
                    currentValue = 0L
                    lastBigMultiplier = multiplier
                } else {
                    // Multiplier kecil (puluh, ratus)
                    if (currentValue == 0L) currentValue = 1L
                    currentValue *= multiplier
                    sectionTotal += currentValue
                    currentValue = 0L
                }
                i++
                continue
            }

            // Token tidak dikenali → skip
            i++
        }

        // Sisa nilai yang belum di-flush
        grandTotal += sectionTotal + currentValue

        return if (hasWordNumber) grandTotal else 0L
    }

    /**
     * Tokenize teks dan buang stopwords.
     * Juga menangani prefix "se" yang menempel: "seribu" → ["se", "ribu"],
     * "sejuta" → ["se", "juta"], "seratus" → ["se", "ratus"]
     */
    private fun tokenize(text: String): List<String> {
        // Split by whitespace and non-alphanumeric (keep numbers intact)
        val rawTokens = text.split(Regex("""[\s,;:!?()\[\]{}'\"-]+"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val result = mutableListOf<String>()
        for (token in rawTokens) {
            // Skip stopwords
            if (token in stopwords) continue

            // Handle "se-" prefix glued to multiplier: seribu, sejuta, seratus, sepuluh
            if (token.startsWith("se") && token.length > 2) {
                val suffix = token.substring(2)
                if (suffix in multiplierWords) {
                    result.add("se")
                    result.add(suffix)
                    continue
                }
                // "sebelas" is in numberWords, handle as single token
                if (token in numberWords) {
                    result.add(token)
                    continue
                }
                // "sehari", "setengah", etc.
                if (token == "setengah" || token == "stengah") {
                    result.add(token)
                    continue
                }
            }

            // Handle "kira-kira" type words
            if (token == "kira-kira") continue

            result.add(token)
        }
        return result
    }
}
