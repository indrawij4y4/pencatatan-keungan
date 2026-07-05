# Checklist Transaksi & Laporan Redesign & CRUD

## Bagian Transaksi
- [x] Membuat Vector Drawables (`ic_search.xml`, `ic_filter.xml`, `ic_delete.xml`)
- [x] Memperbarui `Transaction.kt` (menambahkan enum `TransactionSource` dan properti `source`)
- [x] Memperbarui `item_transaction.xml` (visual baru: catatan di atas, kategori + sumber di bawah)
- [x] Membuat `item_transaction_header.xml` (divider grup tanggal & total pengeluaran)
- [x] Membuat layout dialog:
  - [x] `dialog_filter.xml` (opsi filter)
  - [x] `dialog_transaction_detail.xml` (detail aksi bottom sheet)
  - [x] `dialog_add_transaction.xml` (formulir tambah/edit manual)
- [x] Membuat `GroupedTransactionAdapter.kt` (adapter list terkelompok)
- [x] Memperbarui `TransactionAdapter.kt` (menyesuaikan list ringkas Beranda)
- [x] Memperbarui `activity_main.xml` (kontainer layout Beranda vs Transaksi, header pencarian)
- [x] Memperbarui `MainActivity.kt` (menghubungkan event, pencarian, filter, bottom sheet dialog, state CRUD lokal)

## Bagian Laporan
- [x] Membuat Vector Drawables (`ic_arrow_back.xml`, `ic_arrow_forward.xml`, `ic_calendar.xml`)
- [x] Membuat Progress Bar Style (`bg_progress_teal.xml`)
- [x] Membuat custom views grafik:
  - [x] `BarChartView.kt` (grafik batang Arus Kas bersandingan mingguan dengan warna teal `#1f6f5f`)
  - [x] `DonutChartView.kt` (grafik donat Kategori Pengeluaran dengan tulisan Total Pengeluaran di tengah)
- [x] Membuat layout list peringkat (`item_top_expense.xml` dengan progress bar tipis)
- [x] Memperbarui `activity_main.xml` (menambahkan `headerReportsContainer` dan `layoutReportsContent` dengan grid summary, toggle tab, chart frame, ranking, dan ekspor)
- [x] Memperbarui `MainActivity.kt` (mengimplementasikan navigasi bulan, filter range custom kalender, tab toggle grafik, kalkulasi statistik, populasi list peringkat secara dinamis, dan toast simulasi ekspor)
