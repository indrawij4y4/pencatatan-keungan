package com.example.pencatatankeungaan

/**
 * IndonesianNumberParser — Utilitas konversi teks bahasa Indonesia menjadi angka nominal.
 *
 * Pipeline:
 *   Raw Text → Lowercase & Trim → Deteksi & Hapus Angka Non-Moneter (Tahun, No HP, dll.)
 *   → Tokenize → Konversi kata angka ke digit → Proses pengali → Regex fallback
 *   → Return nominal terbesar
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
        val sb = StringBuilder(cleanedText)

        // Langkah 1: Identifikasi dan ganti semua angka non-moneter dengan spasi (agar tidak diolah oleh parser)
        pureNumberRegex.findAll(cleanedText).forEach { match ->
            val numStr = normalizeNumericString(match.value)
            val valVal = numStr.toLongOrNull() ?: 0L
            if (isNonMonetary(cleanedText, match.range.first, match.range.last + 1, valVal)) {
                for (idx in match.range) {
                    sb.setCharAt(idx, ' ')
                }
            }
        }

        shorthandRegex.findAll(cleanedText).forEach { match ->
            val numberPart = match.groupValues[1]
            val normalizedNumber = normalizeNumericString(numberPart)
            val baseNumber = normalizedNumber.toDoubleOrNull() ?: 0.0
            val multiplierPart = match.groupValues[2].lowercase()
            val multiplier = when (multiplierPart) {
                "rb", "ribu", "rebu", "rbu" -> 1_000L
                "jt", "juta", "jet" -> 1_000_000L
                "k" -> 1_000L
                "m" -> 1_000_000L
                "miliar", "milyar" -> 1_000_000_000L
                else -> 1L
            }
            val totalValue = (baseNumber * multiplier).toLong()
            if (isNonMonetary(cleanedText, match.range.first, match.range.last + 1, totalValue)) {
                for (idx in match.range) {
                    sb.setCharAt(idx, ' ')
                }
            }
        }

        val textWithoutNonMonetary = sb.toString()

        // Langkah 2: Jalankan pipeline parsing pada teks yang sudah dibersihkan
        val shorthandResults = mutableListOf<Long>()
        shorthandRegex.findAll(textWithoutNonMonetary).forEach { match ->
            val numberPart = match.groupValues[1]
            val normalizedNumber = normalizeNumericString(numberPart)
            val baseNumber = normalizedNumber.toDoubleOrNull() ?: 0.0
            val multiplierPart = match.groupValues[2].lowercase()
            val multiplier = when (multiplierPart) {
                "rb", "ribu", "rebu", "rbu" -> 1_000L
                "jt", "juta", "jet" -> 1_000_000L
                "k" -> 1_000L
                "m" -> 1_000_000L
                "miliar", "milyar" -> 1_000_000_000L
                else -> 1L
            }
            shorthandResults.add((baseNumber * multiplier).toLong())
        }

        val wordResult = parseWords(textWithoutNonMonetary)

        val pureNumberResults = mutableListOf<Long>()
        pureNumberRegex.findAll(textWithoutNonMonetary).forEach { match ->
            val numStr = normalizeNumericString(match.value)
            numStr.toLongOrNull()?.let { pureNumberResults.add(it) }
        }

        // Kumpulkan semua kandidat dan ambil yang terbesar
        val allCandidates = mutableListOf<Long>()
        allCandidates.addAll(shorthandResults)
        if (wordResult > 0L) {
            allCandidates.add(wordResult)
        }
        allCandidates.addAll(pureNumberResults)

        return allCandidates.maxOrNull() ?: 0L
    }

    /**
     * Menormalisasi string angka yang mungkin berisi pemisah ribuan dan desimal (titik/koma).
     * Aturan:
     * - Jika ada '.' dan ',', pemisah desimal adalah yang terakhir muncul.
     * - Jika hanya ada '.' atau ',', dan diikuti tepat 3 digit di akhir, dianggap sebagai pemisah ribuan (dibuang).
     * - Jika diikuti selain 3 digit di akhir, dianggap sebagai pemisah desimal (diubah menjadi '.').
     */
    private fun normalizeNumericString(str: String): String {
        val clean = str.trim()
        if (clean.isEmpty()) return "0"

        val dotCount = clean.count { it == '.' }
        val commaCount = clean.count { it == ',' }

        // Kasus 1: Memiliki '.' dan ',' sekaligus
        if (dotCount > 0 && commaCount > 0) {
            val lastDot = clean.lastIndexOf('.')
            val lastComma = clean.lastIndexOf(',')
            return if (lastDot > lastComma) {
                // '.' adalah desimal, ',' adalah ribuan
                clean.replace(",", "").replace(".", ".")
            } else {
                // ',' adalah desimal, '.' adalah ribuan
                clean.replace(".", "").replace(",", ".")
            }
        }

        // Kasus 2: Hanya memiliki '.'
        if (dotCount > 0) {
            if (dotCount > 1) {
                return clean.replace(".", "")
            }
            val dotIndex = clean.lastIndexOf('.')
            val afterDotLength = clean.length - dotIndex - 1
            return if (afterDotLength == 3) {
                clean.replace(".", "")
            } else {
                clean
            }
        }

        // Kasus 3: Hanya memiliki ','
        if (commaCount > 0) {
            if (commaCount > 1) {
                return clean.replace(",", "")
            }
            val commaIndex = clean.lastIndexOf(',')
            val afterCommaLength = clean.length - commaIndex - 1
            return if (afterCommaLength == 3) {
                clean.replace(",", "")
            } else {
                clean.replace(",", ".")
            }
        }

        return clean
    }

    /**
     * Memeriksa apakah angka yang diparsing berkemungkinan besar non-moneter (seperti tahun/nomor HP).
     */
    private fun isNonMonetary(text: String, matchStart: Int, matchEnd: Int, value: Long): Boolean {
        // 1. Deteksi nomor HP (biasanya 9-15 digit diawali dengan 0, 62, atau 8)
        val valStr = value.toString()
        if (valStr.length in 9..15 && (valStr.startsWith("0") || valStr.startsWith("62") || valStr.startsWith("8"))) {
            return true
        }

        // Ambil konteks teks di sekitar angka
        val beforeText = text.substring(0, matchStart).trim().lowercase()
        val afterText = text.substring(matchEnd).trim().lowercase()

        // Evaluasi kata-kata sebelum angka
        val wordsBefore = beforeText.split("\\s+".toRegex())
        if (wordsBefore.isNotEmpty()) {
            val lastWord = wordsBefore.last().replace(Regex("""[^a-zA-Z0-9]"""), "")
            val secondLastWord = if (wordsBefore.size >= 2) wordsBefore[wordsBefore.size - 2].replace(Regex("""[^a-zA-Z0-9]"""), "") else ""

            val nonMonetaryPrefixes = setOf(
                "tahun", "thn", "tanggal", "tgl", "jam", "pukul", "no", "nomor",
                "umur", "usia", "rt", "rw", "telepon", "telp", "hp", "wa"
            )

            if (lastWord in nonMonetaryPrefixes || secondLastWord in nonMonetaryPrefixes) {
                return true
            }
        }

        // Evaluasi kata-kata setelah angka
        val wordsAfter = afterText.split("\\s+".toRegex())
        if (wordsAfter.isNotEmpty()) {
            val firstWord = wordsAfter.first().replace(Regex("""[^a-zA-Z0-9]"""), "")
            val secondWord = if (wordsAfter.size >= 2) wordsAfter[1].replace(Regex("""[^a-zA-Z0-9]"""), "") else ""

            val nonMonetarySuffixes = setOf(
                "tahun", "thn", "wib", "wita", "wit", "pcs", "biji", "buah", "lembar", "porsi", "unit"
            )

            if (firstWord in nonMonetarySuffixes || secondWord in nonMonetarySuffixes) {
                return true
            }
        }

        // 2. Deteksi tahun (berkisar antara 1950 - 2100) dan terdapat keyword tanggal/tahun di sekitar angka tersebut
        if (value in 1950L..2100L) {
            val dateKeywords = setOf(
                "tahun", "thn", "tanggal", "tgl",
                "jan", "feb", "mar", "apr", "mei", "jun", "jul", "agu", "sep", "okt", "nov", "des",
                "januari", "februari", "maret", "april", "mei", "juni", "juli", "agustus", "september", "oktober", "november", "desember"
            )
            if (wordsBefore.isNotEmpty()) {
                val lastWord = wordsBefore.last().replace(Regex("""[^a-zA-Z0-9]"""), "")
                val secondLastWord = if (wordsBefore.size >= 2) wordsBefore[wordsBefore.size - 2].replace(Regex("""[^a-zA-Z0-9]"""), "") else ""
                if (lastWord in dateKeywords || secondLastWord in dateKeywords) {
                    return true
                }
            }
            if (wordsAfter.isNotEmpty()) {
                val firstWord = wordsAfter.first().replace(Regex("""[^a-zA-Z0-9]"""), "")
                val secondWord = if (wordsAfter.size >= 2) wordsAfter[1].replace(Regex("""[^a-zA-Z0-9]"""), "") else ""
                if (firstWord in dateKeywords || secondWord in dateKeywords) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Parsing kata-kata angka Indonesia menjadi nilai numerik.
     */
    private fun parseWords(text: String): Long {
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return 0L

        var grandTotal = 0.0     // Akumulator utama
        var sectionTotal = 0.0   // Akumulator untuk section saat ini
        var currentValue = 0.0   // Nilai angka yang sedang diproses
        var lastBigMultiplier = 0.0 // Multiplier besar terakhir
        var hasWordNumber = false

        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]

            // Cek apakah token adalah angka numerik murni
            val normalized = normalizeNumericString(token)
            val numericValue = normalized.toDoubleOrNull()
            if (numericValue != null) {
                currentValue = numericValue
                hasWordNumber = true
                i++
                continue
            }

            // Cek slang standalone (cepek, gopek, seceng, goceng, ceban, noban)
            if (token in numberWords && numberWords[token]!! >= 100L) {
                sectionTotal += numberWords[token]!!.toDouble()
                currentValue = 0.0
                hasWordNumber = true
                i++
                continue
            }

            // Cek angka dasar kata (satu-sembilan, sepuluh, sebelas)
            if (token in numberWords) {
                currentValue = numberWords[token]!!.toDouble()
                hasWordNumber = true
                i++
                continue
            }

            // Handle prefix "se" yang berdiri sendiri
            if (token == "se") {
                currentValue = 1.0
                hasWordNumber = true
                i++
                continue
            }

            // Handle "setengah" / "stengah" / "tengah" dengan look-ahead dan look-behind
            if (token == "setengah" || token == "stengah" || token == "tengah") {
                val nextToken = if (i + 1 < tokens.size) tokens[i + 1] else null
                val nextMultiplier = if (nextToken != null) multiplierWords[nextToken] else null

                if (nextMultiplier != null && nextMultiplier >= 1_000L) {
                    val fraction = 0.5
                    val sectionValue = (sectionTotal + currentValue + fraction) * nextMultiplier.toDouble()
                    grandTotal += sectionValue
                    sectionTotal = 0.0
                    currentValue = 0.0
                    lastBigMultiplier = nextMultiplier.toDouble()
                    hasWordNumber = true
                    i += 2 // Konsumsi "setengah" dan token pengali berikutnya
                    continue
                } else if (lastBigMultiplier > 0.0) {
                    grandTotal += lastBigMultiplier / 2.0
                    hasWordNumber = true
                    i++
                    continue
                } else {
                    i++
                    continue
                }
            }

            // Cek multiplier
            if (token in multiplierWords) {
                val multiplier = multiplierWords[token]!!.toDouble()
                hasWordNumber = true

                if (token == "belas") {
                    currentValue += 10.0
                    i++
                    continue
                }

                if (multiplier >= 1_000.0) {
                    if (currentValue == 0.0 && sectionTotal == 0.0) {
                        currentValue = 1.0
                    }
                    val sectionValue = if (sectionTotal > 0.0) {
                        (sectionTotal + currentValue) * multiplier
                    } else {
                        currentValue * multiplier
                    }
                    grandTotal += sectionValue
                    sectionTotal = 0.0
                    currentValue = 0.0
                    lastBigMultiplier = multiplier
                } else {
                    if (currentValue == 0.0) currentValue = 1.0
                    currentValue *= multiplier
                    sectionTotal += currentValue
                    currentValue = 0.0
                }
                i++
                continue
            }

            i++
        }

        grandTotal += sectionTotal + currentValue

        return if (hasWordNumber) grandTotal.toLong() else 0L
    }

    private fun tokenize(text: String): List<String> {
        // Clean non-numeric commas and dots to avoid splitting decimals while cleaning punctuation
        val cleanedText = text
            .replace(Regex("(\\d),(\\d)"), "$1[COMMA]$2")
            .replace(Regex("(\\d)\\.(\\d)"), "$1[DOT]$2")
            .replace(",", " ")
            .replace(".", " ")
            .replace("[COMMA]", ",")
            .replace("[DOT]", ".")

        // Split by whitespace and other punctuation
        val rawTokens = cleanedText.split(Regex("""[\s;:!?()\[\]{}'\"-]+"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val result = mutableListOf<String>()
        for (token in rawTokens) {
            if (token in stopwords) continue

            if (token.startsWith("se") && token.length > 2) {
                val suffix = token.substring(2)
                if (suffix in multiplierWords) {
                    result.add("se")
                    result.add(suffix)
                    continue
                }
                if (token in numberWords) {
                    result.add(token)
                    continue
                }
                if (token == "setengah" || token == "stengah") {
                    result.add(token)
                    continue
                }
            }

            if (token == "kira-kira") continue

            result.add(token)
        }
        return result
    }
}
