# 📱 Pencatatan Keuangan Cerdas (Smart Finance Tracker)

Aplikasi pencatatan keuangan pribadi berbasis Android yang didesain secara modern, aman, dan cerdas. Aplikasi ini tidak hanya mencatat transaksi harian biasa, tetapi juga dilengkapi dengan fitur input suara otomatis, pemindaian struk belanja, visualisasi data interaktif, dan enkripsi data tingkat tinggi untuk menjaga privasi keuangan Anda.

---

## 📥 Link Download Aplikasi (APK)

Untuk dosen, penguji, atau rekan yang ingin menguji coba aplikasi secara langsung di perangkat Android tanpa perlu mem-build kode sumber, silakan unduh APK melalui tautan di bawah ini:

| File Rilis | Deskripsi | Link Download |
| :--- | :--- | :--- |
| **🚀 APK Release (v1.0.0-MVP)** | Versi MVP pertama siap instal di semua perangkat Android | [**Download app-debug.apk**](https://github.com/indrawij4y4/pencatatan-keungan/releases/download/v1.0.0-MVP/app-debug.apk) |
| **📦 Halaman Rilis GitHub** | Akses ke rilis versi dan log perubahan di GitHub | [**Lihat Releases**](https://github.com/indrawij4y4/pencatatan-keungan/releases) |

*Catatan: Pastikan Anda telah mengizinkan instalasi dari "Sumber tidak dikenal" (Unknown Sources) pada pengaturan keamanan ponsel Android Anda sebelum menginstal APK.*

---

## ✨ Fitur Utama

### 1. 🎤 Asisten Input Suara Cerdas (*Voice Input Manager*)
Mencatat keuangan kini lebih cepat. Cukup ucapkan nominal dan kategori (contoh: *"Beli bakso dua puluh ribu rupiah"*), aplikasi akan secara otomatis mendeteksi jumlah uang dan mengklasifikasikan kategori transaksi menggunakan algoritma parsing teks lokal (*Indonesian Number & Text Parser*).

### 2. 🧾 Pemindaian Struk Belanja (*Receipt OCR & Parser*)
Unggah foto struk belanja Anda untuk mengekstrak data transaksi (seperti nama toko, daftar belanja, total pengeluaran, dan tanggal transaksi) secara otomatis demi pencatatan yang lebih praktis.

### 3. 🔐 Database Terenkripsi Militer (*SQLCipher & Android KeyStore*)
Keamanan data adalah prioritas utama. Seluruh data transaksi Anda disimpan secara lokal menggunakan database **Room SQLite** yang dienkripsi penuh menggunakan **SQLCipher**. Kunci enkripsi diamankan menggunakan **Android KeyStore** tingkat sistem untuk mencegah kebocoran data dari serangan *rooting* atau penyadapan database lokal.

### 4. 📊 Visualisasi Laporan Premium (*Custom Interactive Charts*)
Menyajikan visualisasi kesehatan keuangan Anda dengan dua jenis chart buatan sendiri (*Custom Views*):
* **BarChartView**: Grafik batang arus kas mingguan interaktif (Pemasukan vs Pengeluaran).
* **DonutChartView**: Grafik donat interaktif untuk melacak persentase kategori pengeluaran Anda.

### 5. ⚙️ Pemeliharaan Latar Belakang Otomatis (*WorkManager Database Vacuum*)
Menggunakan **Android WorkManager** untuk menjalankan tugas pembersihan data (*Database Vacuum*) secara otomatis di latar belakang demi menjaga kinerja aplikasi tetap cepat dan ukuran file database tetap efisien.

---

## 🛠️ Tech Stack & Arsitektur

Aplikasi ini dibangun menggunakan praktik pengembangan Android modern:
* **Bahasa**: Kotlin 100%
* **Arsitektur**: MVVM (Model-View-ViewModel) dengan Repository Pattern
* **Database**: Room Database yang dienkripsi oleh **SQLCipher**
* **Keamanan**: Android KeyStore API
* **Penyimpanan Key-Value**: Jetpack DataStore Preferences (menggantikan SharedPreferences lama)
* **Pekerjaan Latar Belakang**: Android WorkManager (Kotlin Coroutines Worker)
* **Grafik Visual**: Custom Canvas Drawing (Custom Android Views)
* **Kompatibilitas**: Min SDK 23 (Android 6.0) hingga Target SDK 36 (Android 14)

---

## 🧑‍💻 Panduan Pengembangan (Untuk Developer)

Jika Anda ingin menjalankan atau memodifikasi kode sumber aplikasi ini:

### Prasyarat
1. **Android Studio** versi Jellyfish atau yang lebih baru.
2. **JDK 11 atau JDK 17** terpasang di sistem Anda.
3. Perangkat fisik Android atau Android Emulator dengan API Level 23+.

### Langkah Instalasi
1. Clone repositori ini:
   ```bash
   git clone https://github.com/indrawij4y4/pencatatan-keungan.git
   ```
2. Buka proyek melalui Android Studio (`File > Open...`).
3. Biarkan Gradle melakukan sinkronisasi dependensi.
4. Hubungkan perangkat Anda atau jalankan emulator.
5. Klik tombol **Run** (`Shift + F10`) di Android Studio.

---

## 👤 Penulis
* **Indra Wijaya** ([@indrawij4y4](https://github.com/indrawij4y4))
