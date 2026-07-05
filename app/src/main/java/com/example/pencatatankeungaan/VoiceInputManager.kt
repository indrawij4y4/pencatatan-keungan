package com.example.pencatatankeungaan

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * VoiceInputManager — Controller untuk lifecycle SpeechRecognizer Android.
 *
 * Mengelola:
 * - Inisialisasi dan destroy SpeechRecognizer
 * - Konfigurasi RecognizerIntent dengan locale id-ID
 * - Callback untuk listening/partial/result/error states
 *
 * Penggunaan:
 * ```
 * val manager = VoiceInputManager(context)
 * manager.startListening(object : VoiceInputManager.Callback {
 *     override fun onListening() { /* show animation */ }
 *     override fun onPartialResult(text: String) { /* update UI */ }
 *     override fun onResult(text: String) { /* process result */ }
 *     override fun onError(message: String) { /* show error */ }
 * })
 * ```
 */
class VoiceInputManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var callback: Callback? = null
    private var isListening = false

    interface Callback {
        fun onListening()
        fun onPartialResult(partialText: String)
        fun onResult(fullText: String)
        fun onError(errorMessage: String)
        fun onRmsChanged(rmsdB: Float) {}
    }

    /**
     * Buat RecognizerIntent dengan konfigurasi bahasa Indonesia.
     * WAJIB: Force locale ke id-ID agar tidak bergantung pada setting device.
     */
    private fun createRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            // Force bahasa Indonesia
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "id-ID")
            // Konfigurasi tambahan
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    /**
     * Mulai mendengarkan suara pengguna.
     * @param cb Callback untuk menerima status dan hasil
     */
    fun startListening(cb: Callback) {
        this.callback = cb

        // Cek apakah SpeechRecognizer tersedia di device
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            cb.onError("Pengenalan suara tidak tersedia di perangkat ini. Pastikan Google App terinstal.")
            return
        }

        // Destroy existing instance jika ada
        destroy()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                callback?.onListening()
            }

            override fun onBeginningOfSpeech() {
                // Pengguna mulai berbicara
            }

            override fun onRmsChanged(rmsdB: Float) {
                callback?.onRmsChanged(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Tidak digunakan
            }

            override fun onEndOfSpeech() {
                isListening = false
            }

            override fun onError(error: Int) {
                isListening = false
                val message = getErrorMessage(error)
                callback?.onError(message)
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val bestResult = matches?.firstOrNull() ?: ""
                if (bestResult.isNotEmpty()) {
                    callback?.onResult(bestResult)
                } else {
                    callback?.onError("Tidak dapat mengenali suara. Silakan coba lagi.")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partialText = matches?.firstOrNull() ?: ""
                if (partialText.isNotEmpty()) {
                    callback?.onPartialResult(partialText)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                // Tidak digunakan
            }
        })

        speechRecognizer?.startListening(createRecognizerIntent())
    }

    /**
     * Hentikan listening aktif.
     */
    fun stopListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
        }
    }

    /**
     * Batalkan listening dan bersihkan resources.
     */
    fun cancel() {
        speechRecognizer?.cancel()
        isListening = false
    }

    /**
     * Destroy SpeechRecognizer instance.
     * WAJIB dipanggil di onDestroy() Activity.
     */
    fun destroy() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
    }

    /**
     * Cek apakah sedang dalam mode listening.
     */
    fun isCurrentlyListening(): Boolean = isListening

    /**
     * Terjemahkan error code SpeechRecognizer ke pesan bahasa Indonesia.
     */
    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Gagal merekam audio. Periksa izin mikrofon."
            SpeechRecognizer.ERROR_CLIENT -> "Terjadi kesalahan pada aplikasi."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Izin mikrofon belum diberikan."
            SpeechRecognizer.ERROR_NETWORK -> "Koneksi internet diperlukan untuk pengenalan suara."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Koneksi internet timeout. Coba lagi."
            SpeechRecognizer.ERROR_NO_MATCH -> "Tidak dapat mengenali ucapan. Silakan coba lagi."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Pengenalan suara sedang sibuk. Tunggu sebentar."
            SpeechRecognizer.ERROR_SERVER -> "Kesalahan server Google. Coba lagi nanti."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Tidak ada suara yang terdeteksi. Silakan bicara."
            else -> "Terjadi kesalahan tidak dikenal (kode: $errorCode)"
        }
    }
}
