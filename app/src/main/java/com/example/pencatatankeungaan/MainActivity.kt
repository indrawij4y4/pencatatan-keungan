package com.example.pencatatankeungaan

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.AnimatorSet
import android.app.DatePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import android.view.WindowManager
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.core.content.FileProvider
import android.provider.MediaStore
import android.graphics.pdf.PdfDocument
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import android.text.Layout
import android.view.Gravity
import android.widget.ProgressBar
import kotlinx.coroutines.withContext

import java.io.File
import java.io.FileWriter
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import com.example.pencatatankeungaan.data.AppDatabase
import com.example.pencatatankeungaan.data.TransactionRepository
import com.example.pencatatankeungaan.data.AppSettings

class MainActivity : AppCompatActivity() {

    private var onImagePickedCallback: ((String?) -> Unit)? = null
    private var tempCameraFile: File? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val copiedPath = copyUriToCache(uri)
            onImagePickedCallback?.invoke(copiedPath)
        }
    }

    private val cameraCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val file = tempCameraFile
            if (file != null && file.exists()) {
                val compressedPath = compressImageFile(file.absolutePath)
                if (file.exists()) {
                    file.delete()
                }
                onImagePickedCallback?.invoke(compressedPath)
            }
        }
    }

    private val createPdfLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null) {
            exportPdfToUri(uri)
        }
    }

    private val createCsvLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            exportCsvToUri(uri)
        }
    }

    private fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): android.graphics.Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, options)
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            BitmapFactory.decodeFile(path, options)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun compressImageFile(sourcePath: String): String? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(sourcePath, options)
            
            val maxDimension = 1600
            val srcWidth = options.outWidth
            val srcHeight = options.outHeight
            var inSampleSize = 1
            if (srcWidth > maxDimension || srcHeight > maxDimension) {
                val halfWidth = srcWidth / 2
                val halfHeight = srcHeight / 2
                while (halfWidth / inSampleSize >= maxDimension && halfHeight / inSampleSize >= maxDimension) {
                    inSampleSize *= 2
                }
            }
            
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            val bitmap = BitmapFactory.decodeFile(sourcePath, decodeOptions) ?: return null
            
            val scaledBitmap = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val (newWidth, newHeight) = if (ratio > 1) {
                    maxDimension to (maxDimension / ratio).toInt()
                } else {
                    (maxDimension * ratio).toInt() to maxDimension
                }
                android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            } else {
                bitmap
            }
            
            val file = File(cacheDir, "proof_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { out ->
                scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
            }
            
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }
            bitmap.recycle()
            
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun copyUriToCache(uri: Uri): String? {
        return try {
            val contentResolver = contentResolver
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(cacheDir, "proof_temp_${System.currentTimeMillis()}.jpg")
            tempFile.outputStream().use { outputStream ->
                inputStream.use { it.copyTo(outputStream) }
            }
            val finalPath = compressImageFile(tempFile.absolutePath)
            if (tempFile.exists()) {
                tempFile.delete()
            }
            finalPath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun showPhotoPickerDialog(onImageSelected: (String?) -> Unit) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_photo_picker, null)
        dialog.setContentView(view)

        view.findViewById<View>(R.id.optionGallery).setOnClickListener {
            onImagePickedCallback = onImageSelected
            pickImageLauncher.launch("image/*")
            dialog.dismiss()
        }

        view.findViewById<View>(R.id.optionCamera).setOnClickListener {
            onImagePickedCallback = onImageSelected
            checkCameraPermissionAndStartCapture()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun checkCameraPermissionAndStartCapture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCameraCapture()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_CAPTURE_PERMISSION
            )
        }
    }

    private fun startCameraCapture() {
        try {
            val photoFile = File(cacheDir, "proof_cam_${System.currentTimeMillis()}.jpg")
            tempCameraFile = photoFile
            
            val authority = "${packageName}.fileprovider"
            val photoURI = FileProvider.getUriForFile(this, authority, photoFile)
            
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            }
            cameraCaptureLauncher.launch(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Gagal membuka kamera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private lateinit var tvCurrentPeriod: TextView
    private lateinit var tvTotalBalance: TextView
    private lateinit var tvIncomeAmount: TextView
    private lateinit var tvExpenseAmount: TextView
    private lateinit var rvRecentTransactions: RecyclerView
    private lateinit var layoutEmptyState: View

    // Bottom navigation views to toggle active states
    private lateinit var ivNavHome: ImageView
    private lateinit var tvNavHome: TextView
    private lateinit var ivNavTransactions: ImageView
    private lateinit var tvNavTransactions: TextView
    private lateinit var ivNavReports: ImageView
    private lateinit var tvNavReports: TextView
    private lateinit var ivNavSettings: ImageView
    private lateinit var tvNavSettings: TextView

    // Tab Containers
    private lateinit var headerPeriodContainer: View
    private lateinit var headerSearchFilterContainer: View
    private lateinit var headerReportsContainer: View
    private lateinit var layoutHomeContent: View
    private lateinit var layoutTransactionsContent: View
    private lateinit var layoutReportsContent: View

    // Grouped Transactions RecyclerView
    private lateinit var rvGroupedTransactions: RecyclerView
    private lateinit var layoutTransactionsEmptyState: View
    private lateinit var groupedAdapter: GroupedTransactionAdapter
    private lateinit var recentAdapter: TransactionAdapter

    // Reports Widgets
    private lateinit var tvReportsPeriod: TextView
    private lateinit var tvReportsIncome: TextView
    private lateinit var tvReportsExpense: TextView
    private lateinit var tvReportsBalance: TextView
    private lateinit var btnReportsTabCashFlow: TextView
    private lateinit var btnReportsTabCategory: TextView
    private lateinit var barChartView: BarChartView
    private lateinit var donutChartView: DonutChartView

    // State Variables
    private val transactionsList = mutableListOf<Transaction>()
    private var currentPeriodName: String = ""
    private var isOnboardingCompleted: Boolean = false
    private var currentSearchQuery: String = ""
    private var filterType: TransactionType? = null // null = All
    private var filterCategory: String = "Semua" // "Semua" = All
    private var filterStartDate: Long? = null
    private var filterEndDate: Long? = null

    // Reports State
    private var monthsList: List<String> = emptyList()
    private var reportsPeriodIndex = 0
    private var reportsPeriodName = ""
    private var reportsTabActive = "CASHFLOW" // "CASHFLOW" or "CATEGORY"

    // Settings Views
    private lateinit var headerSettingsContainer: View
    private lateinit var layoutSettingsContent: View
    private lateinit var tvSettingBusinessNameVal: TextView

    // Settings Sub-pages
    private lateinit var layoutManageCategoriesContent: View
    private lateinit var layoutUserGuideContent: View

    // Settings State
    private val categoriesIncome = mutableListOf<String>()
    private val categoriesExpense = mutableListOf<String>()

    // Room & DataStore
    private lateinit var repository: TransactionRepository
    private lateinit var appSettings: AppSettings

    // Voice Input
    private lateinit var voiceInputManager: VoiceInputManager
    private var voiceRecordingDialog: BottomSheetDialog? = null
    private var pulseAnimatorSet: AnimatorSet? = null

    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
        private const val REQUEST_CAMERA_CAPTURE_PERMISSION = 202
    }

    enum class Tab {
        HOME, TRANSACTIONS, REPORTS, SETTINGS
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Adjust window insets for edge-to-edge design
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize UI Widgets
        tvCurrentPeriod = findViewById(R.id.tvCurrentPeriod)
        tvTotalBalance = findViewById(R.id.tvTotalBalance)
        tvIncomeAmount = findViewById(R.id.tvIncomeAmount)
        tvExpenseAmount = findViewById(R.id.tvExpenseAmount)
        rvRecentTransactions = findViewById(R.id.rvRecentTransactions)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)

        ivNavHome = findViewById(R.id.ivNavHome)
        tvNavHome = findViewById(R.id.tvNavHome)
        ivNavTransactions = findViewById(R.id.ivNavTransactions)
        tvNavTransactions = findViewById(R.id.tvNavTransactions)
        ivNavReports = findViewById(R.id.ivNavReports)
        tvNavReports = findViewById(R.id.tvNavReports)
        ivNavSettings = findViewById(R.id.ivNavSettings)
        tvNavSettings = findViewById(R.id.tvNavSettings)

        // Initialize Container Views
        headerPeriodContainer = findViewById(R.id.headerPeriodContainer)
        headerSearchFilterContainer = findViewById(R.id.headerSearchFilterContainer)
        headerReportsContainer = findViewById(R.id.headerReportsContainer)
        layoutHomeContent = findViewById(R.id.layoutHomeContent)
        layoutTransactionsContent = findViewById(R.id.layoutTransactionsContent)
        layoutReportsContent = findViewById(R.id.layoutReportsContent)
        rvGroupedTransactions = findViewById(R.id.rvGroupedTransactions)
        layoutTransactionsEmptyState = findViewById(R.id.layoutTransactionsEmptyState)

        // Initialize Reports Widgets
        tvReportsPeriod = findViewById(R.id.tvReportsPeriod)
        tvReportsIncome = findViewById(R.id.tvReportsIncome)
        tvReportsExpense = findViewById(R.id.tvReportsExpense)
        tvReportsBalance = findViewById(R.id.tvReportsBalance)
        btnReportsTabCashFlow = findViewById(R.id.btnReportsTabCashFlow)
        btnReportsTabCategory = findViewById(R.id.btnReportsTabCategory)
        barChartView = findViewById(R.id.barChartView)
        donutChartView = findViewById(R.id.donutChartView)

        // Initialize Settings Views
        headerSettingsContainer = findViewById(R.id.headerSettingsContainer)
        layoutSettingsContent = findViewById(R.id.layoutSettingsContent)
        tvSettingBusinessNameVal = findViewById(R.id.tvSettingBusinessNameVal)

        // Initialize Settings Sub-pages Overlays
        layoutManageCategoriesContent = findViewById(R.id.layoutManageCategoriesContent)
        layoutUserGuideContent = findViewById(R.id.layoutUserGuideContent)

        val btnSelectPeriod = findViewById<View>(R.id.btnSelectPeriod)
        val ivProfileIcon = findViewById<View>(R.id.ivProfileIcon)
        val fabAdd = findViewById<View>(R.id.fabAdd)

        // Set up recycler views layout managers
        rvRecentTransactions.layoutManager = LinearLayoutManager(this)
        rvGroupedTransactions.layoutManager = LinearLayoutManager(this)

        recentAdapter = TransactionAdapter { transaction ->
            showTransactionDetailDialog(transaction)
        }
        rvRecentTransactions.adapter = recentAdapter

        groupedAdapter = GroupedTransactionAdapter { transaction ->
            showTransactionDetailDialog(transaction)
        }
        rvGroupedTransactions.adapter = groupedAdapter

        // Initialize Room Database & AppSettings
        val database = AppDatabase.getInstance(this)
        repository = TransactionRepository(database.transactionDao())
        appSettings = AppSettings(this)

        // Observe Data Flows
        observeSettings()
        observeTransactions()

        // Load Default Month Dynamically
        val defaultPeriodSdf = SimpleDateFormat("MMMM yyyy", Locale.forLanguageTag("id-ID"))
        currentPeriodName = defaultPeriodSdf.format(Date())
        tvCurrentPeriod.text = currentPeriodName
        reportsPeriodName = currentPeriodName
        tvReportsPeriod.text = reportsPeriodName

        // Period Selection Dialog (Bottom Sheet)
        btnSelectPeriod.setOnClickListener {
            showMonthPickerDialog()
        }

        // Add Transaction Dialog (Bottom Sheet via FAB)
        fabAdd.setOnClickListener {
            showQuickInputDialog()
        }

        // Profile / settings shortcut
        ivProfileIcon.setOnClickListener {
            setActiveNav(ivNavSettings, tvNavSettings)
            switchToTab(Tab.SETTINGS)
            Toast.makeText(this, getString(R.string.toast_settings_open), Toast.LENGTH_SHORT).show()
        }

        // Setup Bottom Nav item click actions
        findViewById<View>(R.id.navHome).setOnClickListener {
            setActiveNav(ivNavHome, tvNavHome)
            switchToTab(Tab.HOME)
        }

        findViewById<View>(R.id.navTransactions).setOnClickListener {
            setActiveNav(ivNavTransactions, tvNavTransactions)
            switchToTab(Tab.TRANSACTIONS)
        }

        findViewById<View>(R.id.navReports).setOnClickListener {
            setActiveNav(ivNavReports, tvNavReports)
            switchToTab(Tab.REPORTS)
        }

        findViewById<View>(R.id.navSettings).setOnClickListener {
            setActiveNav(ivNavSettings, tvNavSettings)
            switchToTab(Tab.SETTINGS)
        }

        // Setup Settings Event Listeners
        setupSettingsListeners()

        // Setup real-time search field
        val etSearch = findViewById<EditText>(R.id.etSearch)
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString() ?: ""
                updateUI()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Setup unified filter icon button
        findViewById<View>(R.id.btnFilter).setOnClickListener {
            showFilterDialog()
        }

        // --- Setup Reports Nav & Header Clicks ---
        findViewById<View>(R.id.btnReportsPrevMonth).setOnClickListener {
            if (reportsPeriodIndex > 0) {
                reportsPeriodIndex--
                reportsPeriodName = monthsList[reportsPeriodIndex]
                tvReportsPeriod.text = reportsPeriodName
                updateReportsUI()
            }
        }

        findViewById<View>(R.id.btnReportsNextMonth).setOnClickListener {
            if (reportsPeriodIndex < monthsList.size - 1) {
                reportsPeriodIndex++
                reportsPeriodName = monthsList[reportsPeriodIndex]
                tvReportsPeriod.text = reportsPeriodName
                updateReportsUI()
            }
        }

        // Reports Tab toggle clicks
        btnReportsTabCashFlow.setOnClickListener {
            setReportsTabActive("CASHFLOW")
        }

        btnReportsTabCategory.setOnClickListener {
            setReportsTabActive("CATEGORY")
        }

        // Export call to actions
        findViewById<View>(R.id.btnExportCsv).setOnClickListener {
            val periodName = if (filterStartDate != null || filterEndDate != null) {
                "Kustom"
            } else {
                reportsPeriodName
            }
            val sanitizedPeriod = periodName.replace(" ", "_").replace("-", "to").replace("/", "_")
            val defaultFileName = "Mutasi_Keuangan_${sanitizedPeriod}.csv"
            createCsvLauncher.launch(defaultFileName)
        }

        findViewById<View>(R.id.btnDownloadPdf).setOnClickListener {
            val periodName = if (filterStartDate != null || filterEndDate != null) {
                "Kustom"
            } else {
                reportsPeriodName
            }
            val sanitizedPeriod = periodName.replace(" ", "_").replace("-", "to").replace("/", "_")
            val defaultFileName = "Laporan_Keuangan_${sanitizedPeriod}.pdf"
            createPdfLauncher.launch(defaultFileName)
        }
    }

    private fun switchToTab(tab: Tab) {
        headerSettingsContainer.visibility = View.GONE
        layoutSettingsContent.visibility = View.GONE

        when (tab) {
            Tab.HOME -> {
                headerPeriodContainer.visibility = View.VISIBLE
                layoutHomeContent.visibility = View.VISIBLE
                headerSearchFilterContainer.visibility = View.GONE
                layoutTransactionsContent.visibility = View.GONE
                headerReportsContainer.visibility = View.GONE
                layoutReportsContent.visibility = View.GONE
            }
            Tab.TRANSACTIONS -> {
                headerPeriodContainer.visibility = View.GONE
                layoutHomeContent.visibility = View.GONE
                headerSearchFilterContainer.visibility = View.VISIBLE
                layoutTransactionsContent.visibility = View.VISIBLE
                headerReportsContainer.visibility = View.GONE
                layoutReportsContent.visibility = View.GONE
                updateUI()
            }
            Tab.REPORTS -> {
                headerPeriodContainer.visibility = View.GONE
                layoutHomeContent.visibility = View.GONE
                headerSearchFilterContainer.visibility = View.GONE
                layoutTransactionsContent.visibility = View.GONE
                headerReportsContainer.visibility = View.VISIBLE
                layoutReportsContent.visibility = View.VISIBLE
                
                // Initialize default reports period representation
                tvReportsPeriod.text = reportsPeriodName
                updateReportsUI()
            }
            Tab.SETTINGS -> {
                headerPeriodContainer.visibility = View.GONE
                layoutHomeContent.visibility = View.GONE
                headerSearchFilterContainer.visibility = View.GONE
                layoutTransactionsContent.visibility = View.GONE
                headerReportsContainer.visibility = View.GONE
                layoutReportsContent.visibility = View.GONE
                
                headerSettingsContainer.visibility = View.VISIBLE
                layoutSettingsContent.visibility = View.VISIBLE
            }
        }
    }

    private fun setReportsTabActive(tab: String) {
        reportsTabActive = tab
        if (tab == "CASHFLOW") {
            btnReportsTabCashFlow.setBackgroundResource(R.drawable.bg_brutal_chip_filter)
            btnReportsTabCashFlow.isSelected = true
            btnReportsTabCashFlow.setTypeface(null, android.graphics.Typeface.BOLD)

            btnReportsTabCategory.setBackgroundResource(android.R.color.transparent)
            btnReportsTabCategory.isSelected = false
            btnReportsTabCategory.setTextColor(ContextCompat.getColor(this, R.color.primary))
            btnReportsTabCategory.setTypeface(null, android.graphics.Typeface.NORMAL)

            barChartView.visibility = View.VISIBLE
            donutChartView.visibility = View.GONE
        } else {
            btnReportsTabCategory.setBackgroundResource(R.drawable.bg_brutal_chip_filter)
            btnReportsTabCategory.isSelected = true
            btnReportsTabCategory.setTypeface(null, android.graphics.Typeface.BOLD)

            btnReportsTabCashFlow.setBackgroundResource(android.R.color.transparent)
            btnReportsTabCashFlow.isSelected = false
            btnReportsTabCashFlow.setTextColor(ContextCompat.getColor(this, R.color.primary))
            btnReportsTabCashFlow.setTypeface(null, android.graphics.Typeface.NORMAL)

            barChartView.visibility = View.GONE
            donutChartView.visibility = View.VISIBLE
        }
        updateReportsUI()
    }

    private fun showMonthPickerDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_month_picker, null)
        dialog.setContentView(view)

        val rvPeriods = view.findViewById<RecyclerView>(R.id.rvPeriods)

        // Get dynamic periods sorted in reverse-chronological order (newer on top) for the picker
        val rawPeriods = getAvailablePeriods()
        val displayPeriods = rawPeriods.reversed()

        // Calculate empty periods dynamically based on existing transactions
        val emptyPeriods = mutableSetOf<String>()
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.forLanguageTag("id-ID"))
        for (period in displayPeriods) {
            val transactionsInPeriod = transactionsList.filter { tx ->
                sdf.format(Date(tx.timestamp)) == period
            }
            if (transactionsInPeriod.isEmpty()) {
                emptyPeriods.add(period)
            }
        }

        val adapter = PeriodAdapter(
            periods = displayPeriods,
            activePeriod = currentPeriodName,
            emptyPeriods = emptyPeriods
        ) { selectedPeriod ->
            currentPeriodName = selectedPeriod
            loadPeriodData(currentPeriodName)
            dialog.dismiss()
        }

        rvPeriods.layoutManager = LinearLayoutManager(this)
        rvPeriods.adapter = adapter

        dialog.show()
    }

    private fun showQuickInputDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_quick_input, null)
        dialog.setContentView(view)

        view.findViewById<View>(R.id.btnQuickVoice).setOnClickListener {
            dialog.dismiss()
            checkAudioPermissionAndStartVoice()
        }



        view.findViewById<View>(R.id.btnQuickManual).setOnClickListener {
            dialog.dismiss()
            showAddOrEditTransactionDialog()
        }

        dialog.show()
    }

    private fun getMockTimestamp(year: Int, monthIndex: Int, day: Int, hour: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, monthIndex)
        cal.set(Calendar.DAY_OF_MONTH, day)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun filterTransactionsByPeriod(list: List<Transaction>, periodName: String): List<Transaction> {
        if (periodName == "Semua Periode") return list
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.forLanguageTag("id-ID"))
        return list.filter { tx ->
            sdf.format(Date(tx.timestamp)) == periodName
        }
    }


    private fun loadPeriodData(period: String) {
        tvCurrentPeriod.text = period
        updateUI()
    }


    private fun updateUI() {
        val periodTransactions = if (filterStartDate != null || filterEndDate != null) {
            transactionsList.filter { tx ->
                val matchesStartDate = filterStartDate == null || tx.timestamp >= filterStartDate!!
                val matchesEndDate = filterEndDate == null || tx.timestamp <= filterEndDate!! + 86399000L
                matchesStartDate && matchesEndDate
            }
        } else {
            filterTransactionsByPeriod(transactionsList, currentPeriodName)
        }

        // Calculate dynamic income & expense
        var totalIncome = 0L
        var totalExpense = 0L
        for (tx in periodTransactions) {
            if (tx.type == TransactionType.INCOME) {
                totalIncome += tx.amount
            } else {
                totalExpense += tx.amount
            }
        }
        updateBalanceWidgets(totalIncome, totalExpense)

        // 1. Update Home Content UI
        val recentTransactions = periodTransactions.sortedByDescending { it.timestamp }.take(5)
        if (recentTransactions.isEmpty()) {
            rvRecentTransactions.visibility = View.GONE
            layoutEmptyState.visibility = View.VISIBLE
        } else {
            rvRecentTransactions.visibility = View.VISIBLE
            layoutEmptyState.visibility = View.GONE
            recentAdapter.submitList(recentTransactions)
        }

        // Update current period name display if custom date filter is active
        if (filterStartDate != null || filterEndDate != null) {
            val displayDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.forLanguageTag("id-ID"))
            val startStr = filterStartDate?.let { displayDateFormat.format(Date(it)) } ?: "Awal"
            val endStr = filterEndDate?.let { displayDateFormat.format(Date(it)) } ?: "Akhir"
            tvCurrentPeriod.text = "$startStr - $endStr"
        } else {
            tvCurrentPeriod.text = currentPeriodName
        }

        // 2. Update Transactions Content UI
        val baseList = periodTransactions

        val filteredList = baseList.filter { tx ->
            val matchesSearch = currentSearchQuery.isEmpty() ||
                    tx.description.contains(currentSearchQuery, ignoreCase = true) ||
                    tx.category.contains(currentSearchQuery, ignoreCase = true)

            val matchesType = filterType == null || tx.type == filterType
            val matchesCategory = filterCategory == "Semua" || tx.category == filterCategory

            matchesSearch && matchesType && matchesCategory
        }

        if (filteredList.isEmpty()) {
            rvGroupedTransactions.visibility = View.GONE
            layoutTransactionsEmptyState.visibility = View.VISIBLE
        } else {
            rvGroupedTransactions.visibility = View.VISIBLE
            layoutTransactionsEmptyState.visibility = View.GONE

            groupedAdapter.submitTransactions(filteredList)
        }
    }

    // --- Reports UI Calculation and Binding ---
    private fun updateReportsUI() {
        // Query master list dynamically
        val reportsTransactions = if (filterStartDate != null || filterEndDate != null) {
            transactionsList.filter { tx ->
                val matchesStartDate = filterStartDate == null || tx.timestamp >= filterStartDate!!
                val matchesEndDate = filterEndDate == null || tx.timestamp <= filterEndDate!! + 86399000L
                matchesStartDate && matchesEndDate
            }
        } else {
            filterTransactionsByPeriod(transactionsList, reportsPeriodName)
        }

        if (filterStartDate != null || filterEndDate != null) {
            val displayDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.forLanguageTag("id-ID"))
            val startStr = filterStartDate?.let { displayDateFormat.format(Date(it)) } ?: "Awal"
            val endStr = filterEndDate?.let { displayDateFormat.format(Date(it)) } ?: "Akhir"
            tvReportsPeriod.text = "$startStr - $endStr"
        } else {
            tvReportsPeriod.text = reportsPeriodName
        }

        // Disable export/download if there is no data
        val btnDownloadPdf = findViewById<View>(R.id.btnDownloadPdf)
        val btnExportCsv = findViewById<View>(R.id.btnExportCsv)
        val hasData = reportsTransactions.isNotEmpty()
        btnDownloadPdf.isEnabled = hasData
        btnDownloadPdf.alpha = if (hasData) 1.0f else 0.5f
        btnExportCsv.isEnabled = hasData
        btnExportCsv.alpha = if (hasData) 1.0f else 0.5f

        // Calculate summary values
        var reportsIncome = 0L
        var reportsExpense = 0L
        for (tx in reportsTransactions) {
            if (tx.type == TransactionType.INCOME) {
                reportsIncome += tx.amount
            } else {
                reportsExpense += tx.amount
            }
        }
        val reportsBalance = reportsIncome - reportsExpense

        // Bind text elements
        val numberFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID"))
        numberFormat.maximumFractionDigits = 0

        tvReportsIncome.text = numberFormat.format(reportsIncome).replace("Rp", "Rp ")
        tvReportsExpense.text = numberFormat.format(reportsExpense).replace("Rp", "Rp ")
        tvReportsBalance.text = numberFormat.format(reportsBalance).replace("Rp", "Rp ")

        if (reportsBalance < 0) {
            tvReportsBalance.setTextColor(ContextCompat.getColor(this, R.color.expense_red))
        } else {
            tvReportsBalance.setTextColor(ContextCompat.getColor(this, R.color.income_green))
        }

        // --- Bind Chart Data ---
        if (reportsTabActive == "CASHFLOW") {
            // Group transactions into 4 weekly bars based on day of month
            val weeklyIncomes = floatArrayOf(0f, 0f, 0f, 0f)
            val weeklyExpenses = floatArrayOf(0f, 0f, 0f, 0f)
            val cal = Calendar.getInstance()

            for (tx in reportsTransactions) {
                cal.timeInMillis = tx.timestamp
                val day = cal.get(Calendar.DAY_OF_MONTH)
                val weekIdx = when (day) {
                    in 1..7 -> 0
                    in 8..14 -> 1
                    in 15..21 -> 2
                    else -> 3
                }
                if (tx.type == TransactionType.INCOME) {
                    weeklyIncomes[weekIdx] += tx.amount.toFloat()
                } else {
                    weeklyExpenses[weekIdx] += tx.amount.toFloat()
                }
            }
            barChartView.setData(weeklyIncomes.toList(), weeklyExpenses.toList())
        } else {
            // Group transactions by type (Pemasukan vs Pengeluaran)
            val totalIncome = reportsTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
            val totalExpense = reportsTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            val totalSum = totalIncome + totalExpense

            val percentages = mutableListOf<Float>()
            val categoryNames = mutableListOf<String>()

            if (totalIncome > 0) {
                categoryNames.add("Pemasukan")
                percentages.add(totalIncome.toFloat() / totalSum.toFloat() * 100f)
            }
            if (totalExpense > 0) {
                categoryNames.add("Pengeluaran")
                percentages.add(totalExpense.toFloat() / totalSum.toFloat() * 100f)
            }

            donutChartView.setData(percentages, categoryNames, totalSum)
        }
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    private fun updateBalanceWidgets(income: Long, expense: Long) {
        val balance = income - expense
        val numberFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID"))
        numberFormat.maximumFractionDigits = 0

        tvTotalBalance.text = numberFormat.format(balance).replace("Rp", "Rp ")
        tvIncomeAmount.text = numberFormat.format(income).replace("Rp", "Rp ")
        tvExpenseAmount.text = numberFormat.format(expense).replace("Rp", "Rp ")
    }



    private fun wouldBeNegativeBalanceAfterAdd(newTx: Transaction): Boolean {
        val tempTransactions = transactionsList.toMutableList()
        tempTransactions.add(newTx)
        val totalIncome = tempTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val totalExpense = tempTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        return (totalIncome - totalExpense) < 0
    }

    private fun wouldBeNegativeBalanceAfterEdit(oldTxId: Long, updatedTx: Transaction): Boolean {
        val tempTransactions = transactionsList.toMutableList()
        val index = tempTransactions.indexOfFirst { it.id == oldTxId }
        if (index != -1) {
            tempTransactions[index] = updatedTx
        }
        val totalIncome = tempTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val totalExpense = tempTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        return (totalIncome - totalExpense) < 0
    }

    private fun wouldBeNegativeBalanceAfterDelete(txToDelete: Transaction): Boolean {
        val tempTransactions = transactionsList.toMutableList()
        tempTransactions.remove(txToDelete)
        val totalIncome = tempTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val totalExpense = tempTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        return (totalIncome - totalExpense) < 0
    }

    private fun setActiveNav(activeImage: ImageView, activeText: TextView) {
        val colorActive = ContextCompat.getColor(this, R.color.primary)
        val colorInactive = ContextCompat.getColor(this, R.color.text_muted)

        val images = listOf(ivNavHome, ivNavTransactions, ivNavReports, ivNavSettings)
        val texts = listOf(tvNavHome, tvNavTransactions, tvNavReports, tvNavSettings)

        for (img in images) {
            img.imageTintList = ColorStateList.valueOf(colorInactive)
        }
        for (txt in texts) {
            txt.setTextColor(colorInactive)
            txt.setTypeface(null, android.graphics.Typeface.NORMAL)
        }

        activeImage.imageTintList = ColorStateList.valueOf(colorActive)
        activeText.setTextColor(colorActive)
        activeText.setTypeface(null, android.graphics.Typeface.BOLD)
    }

    // --- Search & Filter sheet controllers ---
    private fun showFilterDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_filter, null)
        dialog.setContentView(view)

        val rgTypeFilter = view.findViewById<RadioGroup>(R.id.rgTypeFilter)
        val btnStartDate = view.findViewById<TextView>(R.id.btnStartDate)
        val btnEndDate = view.findViewById<TextView>(R.id.btnEndDate)

        when (filterType) {
            null -> rgTypeFilter.check(R.id.rbTypeAll)
            TransactionType.INCOME -> rgTypeFilter.check(R.id.rbTypeIncome)
            TransactionType.EXPENSE -> rgTypeFilter.check(R.id.rbTypeExpense)
        }

        val displayDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.forLanguageTag("id-ID"))
        var tempStartDate = filterStartDate
        var tempEndDate = filterEndDate

        if (tempStartDate != null) {
            btnStartDate.text = displayDateFormat.format(Date(tempStartDate))
        }
        if (tempEndDate != null) {
            btnEndDate.text = displayDateFormat.format(Date(tempEndDate))
        }

        btnStartDate.setOnClickListener {
            showDatePicker { timestamp ->
                tempStartDate = timestamp
                btnStartDate.text = displayDateFormat.format(Date(timestamp))
            }
        }

        btnEndDate.setOnClickListener {
            showDatePicker { timestamp ->
                tempEndDate = timestamp
                btnEndDate.text = displayDateFormat.format(Date(timestamp))
            }
        }

        view.findViewById<View>(R.id.btnResetFilter).setOnClickListener {
            filterType = null
            filterCategory = "Semua"
            filterStartDate = null
            filterEndDate = null
            updateUI()
            Toast.makeText(this, "Filter berhasil direset", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        view.findViewById<Button>(R.id.btnApplyFilter).setOnClickListener {
            filterType = when (rgTypeFilter.checkedRadioButtonId) {
                R.id.rbTypeIncome -> TransactionType.INCOME
                R.id.rbTypeExpense -> TransactionType.EXPENSE
                else -> null
            }

            filterCategory = "Semua"

            filterStartDate = tempStartDate
            filterEndDate = tempEndDate

            updateUI()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDatePicker(onDateSelected: (Long) -> Unit) {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth, 0, 0, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                onDateSelected(calendar.timeInMillis)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    // --- Bottom Sheet Transaction Detail view ---
    private fun showTransactionDetailDialog(transaction: Transaction) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_transaction_detail, null)
        dialog.setContentView(view)

        val tvDetailAmount = view.findViewById<TextView>(R.id.tvDetailAmount)
        val tvDetailTypeBadge = view.findViewById<TextView>(R.id.tvDetailTypeBadge)
        val tvDetailDescription = view.findViewById<TextView>(R.id.tvDetailDescription)
        val tvDetailDate = view.findViewById<TextView>(R.id.tvDetailDate)
        val tvDetailCategory = view.findViewById<TextView>(R.id.tvDetailCategory)
        val tvDetailSource = view.findViewById<TextView>(R.id.tvDetailSource)
        val detailCategoryIconContainer = view.findViewById<View>(R.id.detailCategoryIconContainer)
        val ivDetailCategoryIcon = view.findViewById<ImageView>(R.id.ivDetailCategoryIcon)
        val ivDetailSourceIcon = view.findViewById<ImageView>(R.id.ivDetailSourceIcon)

        val numberFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID"))
        numberFormat.maximumFractionDigits = 0
        val formattedAmount = numberFormat.format(transaction.amount).replace("Rp", "Rp ")

        if (transaction.type == TransactionType.INCOME) {
            tvDetailAmount.text = "+$formattedAmount"
            tvDetailAmount.setTextColor(ContextCompat.getColor(this, R.color.income_green))
            tvDetailTypeBadge.text = "Pemasukan"
            tvDetailTypeBadge.setTextColor(ContextCompat.getColor(this, R.color.income_green))
            tvDetailTypeBadge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.income_green_light))
            ivDetailCategoryIcon.setImageResource(R.drawable.ic_trending_up)
            ivDetailCategoryIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.income_green))
            detailCategoryIconContainer.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.income_green_light))
        } else {
            tvDetailAmount.text = "-$formattedAmount"
            tvDetailAmount.setTextColor(ContextCompat.getColor(this, R.color.expense_red))
            tvDetailTypeBadge.text = "Pengeluaran"
            tvDetailTypeBadge.setTextColor(ContextCompat.getColor(this, R.color.expense_red))
            tvDetailTypeBadge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.expense_red_light))
            ivDetailCategoryIcon.setImageResource(R.drawable.ic_trending_down)
            ivDetailCategoryIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.expense_red))
            detailCategoryIconContainer.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.expense_red_light))
        }

        tvDetailDescription.text = transaction.description

        val detailDateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy HH:mm", Locale.forLanguageTag("id-ID"))
        tvDetailDate.text = detailDateFormat.format(Date(transaction.timestamp))

        tvDetailCategory.text = transaction.category

        when (transaction.source) {
            TransactionSource.VOICE -> {
                tvDetailSource.text = "Catatan Suara (Voice)"
                ivDetailSourceIcon.setImageResource(R.drawable.ic_mic)
            }
            TransactionSource.MANUAL -> {
                tvDetailSource.text = "Input Manual"
                ivDetailSourceIcon.setImageResource(R.drawable.ic_edit)
            }
            TransactionSource.OCR -> {
                tvDetailSource.text = "Pindai Foto Struk"
                ivDetailSourceIcon.setImageResource(R.drawable.ic_camera)
            }
        }

        val layoutDetailProof = view.findViewById<View>(R.id.layoutDetailProof)
        val ivDetailProof = view.findViewById<ImageView>(R.id.ivDetailProof)
        val cardDetailProof = view.findViewById<View>(R.id.cardDetailProof)
        if (transaction.imagePath != null) {
            val bitmap = decodeSampledBitmap(transaction.imagePath, 360, 200)
            if (bitmap != null) {
                layoutDetailProof.visibility = View.VISIBLE
                ivDetailProof.setImageBitmap(bitmap)
                cardDetailProof.setOnClickListener {
                    showFullscreenProof(transaction)
                }
            } else {
                layoutDetailProof.visibility = View.GONE
            }
        } else {
            layoutDetailProof.visibility = View.GONE
        }

        view.findViewById<View>(R.id.btnDetailDelete).setOnClickListener {
            if (wouldBeNegativeBalanceAfterDelete(transaction)) {
                Toast.makeText(this, "Transaksi tidak dapat dihapus karena akan menyebabkan total saldo minus", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle("Hapus Transaksi")
                .setMessage("Apakah Anda yakin ingin menghapus transaksi ini?")
                .setPositiveButton("Hapus") { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        transaction.imagePath?.let { path ->
                            val file = File(path)
                            if (file.exists()) {
                                file.delete()
                            }
                        }
                        repository.deleteTransaction(transaction)
                    }
                    Toast.makeText(this, "Transaksi berhasil dihapus", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        view.findViewById<View>(R.id.btnDetailEdit).setOnClickListener {
            dialog.dismiss()
            showAddOrEditTransactionDialog(transaction)
        }

        dialog.show()
    }

    private fun showFullscreenProof(transaction: Transaction) {
        val path = transaction.imagePath ?: return
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val view = layoutInflater.inflate(R.layout.dialog_fullscreen_proof, null)
        dialog.setContentView(view)

        val ivFullscreenProof = view.findViewById<ImageView>(R.id.ivFullscreenProof)
        val btnFullscreenClose = view.findViewById<View>(R.id.btnFullscreenClose)
        val tvFullscreenAmount = view.findViewById<TextView>(R.id.tvFullscreenAmount)
        val tvFullscreenDate = view.findViewById<TextView>(R.id.tvFullscreenDate)

        val displayMetrics = resources.displayMetrics
        val bitmap = decodeSampledBitmap(path, displayMetrics.widthPixels, displayMetrics.heightPixels)
        if (bitmap != null) {
            ivFullscreenProof.setImageBitmap(bitmap)
        } else {
            Toast.makeText(this, "Gagal memuat gambar bukti", Toast.LENGTH_SHORT).show()
            return
        }

        // Format Info
        val numberFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID"))
        numberFormat.maximumFractionDigits = 0
        val formattedAmount = numberFormat.format(transaction.amount).replace("Rp", "Rp ")
        val sign = if (transaction.type == TransactionType.INCOME) "+" else "-"
        tvFullscreenAmount.text = "$sign$formattedAmount"
        
        val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy HH:mm", Locale.forLanguageTag("id-ID"))
        tvFullscreenDate.text = dateFormat.format(Date(transaction.timestamp))

        btnFullscreenClose.setOnClickListener {
            dialog.dismiss()
        }

        view.setOnClickListener {
            dialog.dismiss()
        }
        ivFullscreenProof.setOnClickListener {
            // Prevent dismiss when tapping image itself
        }

        // Pinch to zoom and pan gestures implementation
        var scaleFactor = 1.0f
        val scaleGestureDetector = android.view.ScaleGestureDetector(this, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = Math.max(0.8f, Math.min(scaleFactor, 5.0f))
                ivFullscreenProof.scaleX = scaleFactor
                ivFullscreenProof.scaleY = scaleFactor
                return true
            }
        })

        var startX = 0f
        var startY = 0f
        var lastX = 0f
        var lastY = 0f

        ivFullscreenProof.setOnTouchListener { v, event ->
            scaleGestureDetector.onTouchEvent(event)
            
            if (scaleFactor > 1.0f) {
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        startX = event.x
                        startY = event.y
                        lastX = ivFullscreenProof.translationX
                        lastY = ivFullscreenProof.translationY
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx = event.x - startX
                        val dy = event.y - startY
                        ivFullscreenProof.translationX = lastX + dx
                        ivFullscreenProof.translationY = lastY + dy
                    }
                }
            } else {
                ivFullscreenProof.translationX = 0f
                ivFullscreenProof.translationY = 0f
            }
            true
        }

        dialog.show()
    }

    // --- Add / Edit Transaction Manual Form ---
    @Suppress("DEPRECATION")
    private fun showAddOrEditTransactionDialog(transactionToEdit: Transaction? = null) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_add_transaction, null)
        dialog.setContentView(view)

        // Configure BottomSheet Behavior and soft input mode for better UX
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val tvAddTxTitle = view.findViewById<TextView>(R.id.tvAddTxTitle)
        val rgAddTxType = view.findViewById<RadioGroup>(R.id.rgAddTxType)
        val etAddTxAmount = view.findViewById<EditText>(R.id.etAddTxAmount)
        val etAddTxDescription = view.findViewById<EditText>(R.id.etAddTxDescription)
        val btnSaveTx = view.findViewById<Button>(R.id.btnSaveTx)

        val layoutUploadZone = view.findViewById<View>(R.id.layoutUploadZone)
        val layoutProofPreview = view.findViewById<View>(R.id.layoutProofPreview)
        val ivProofPreview = view.findViewById<ImageView>(R.id.ivProofPreview)
        val btnReplaceProof = view.findViewById<View>(R.id.btnReplaceProof)
        val btnRemoveProof = view.findViewById<View>(R.id.btnRemoveProof)
        val tvProofFileInfo = view.findViewById<TextView>(R.id.tvProofFileInfo)

        var currentProofPath: String? = transactionToEdit?.imagePath

        fun updateProofUI() {
            if (currentProofPath != null) {
                val bitmap = decodeSampledBitmap(currentProofPath!!, 360, 200)
                if (bitmap != null) {
                    layoutUploadZone.visibility = View.GONE
                    layoutProofPreview.visibility = View.VISIBLE
                    ivProofPreview.setImageBitmap(bitmap)
                    
                    val file = File(currentProofPath!!)
                    val sizeKb = file.length() / 1024
                    tvProofFileInfo.text = "Bukti Terunggah (${sizeKb} KB)"
                } else {
                    layoutProofPreview.visibility = View.GONE
                    layoutUploadZone.visibility = View.VISIBLE
                }
            } else {
                layoutProofPreview.visibility = View.GONE
                layoutUploadZone.visibility = View.VISIBLE
            }
        }

        updateProofUI()

        layoutUploadZone.setOnClickListener {
            showPhotoPickerDialog { path ->
                currentProofPath = path
                updateProofUI()
            }
        }

        btnReplaceProof.setOnClickListener {
            showPhotoPickerDialog { path ->
                currentProofPath = path
                updateProofUI()
            }
        }

        btnRemoveProof.setOnClickListener {
            currentProofPath = null
            updateProofUI()
        }

        val selectedTypeInitial = transactionToEdit?.type ?: TransactionType.EXPENSE
        if (transactionToEdit != null) {
            tvAddTxTitle.text = "Ubah Rincian Transaksi"
            etAddTxAmount.setText(transactionToEdit.amount.toString())
            etAddTxDescription.setText(transactionToEdit.description)
        }

        // Set initial type selection
        if (selectedTypeInitial == TransactionType.INCOME) {
            rgAddTxType.check(R.id.rbAddTxIncome)
        } else {
            rgAddTxType.check(R.id.rbAddTxExpense)
        }

        btnSaveTx.setOnClickListener {
            val amountStr = etAddTxAmount.text.toString().trim()
            val descriptionStr = etAddTxDescription.text.toString().trim()

            if (amountStr.isEmpty()) {
                etAddTxAmount.error = "Nominal harus diisi"
                return@setOnClickListener
            }
            val amountVal = amountStr.toLongOrNull() ?: 0L
            if (amountVal <= 0L) {
                etAddTxAmount.error = "Nominal harus lebih dari 0"
                return@setOnClickListener
            }

            if (descriptionStr.isEmpty()) {
                etAddTxDescription.error = "Catatan harus diisi"
                return@setOnClickListener
            }

            val typeVal = if (rgAddTxType.checkedRadioButtonId == R.id.rbAddTxIncome) {
                TransactionType.INCOME
            } else {
                TransactionType.EXPENSE
            }

            val categoryVal = VoiceTransactionClassifier.classify(descriptionStr).category

            val tempTx = if (transactionToEdit == null) {
                Transaction(
                    id = 0L,
                    type = typeVal,
                    amount = amountVal,
                    category = categoryVal,
                    description = descriptionStr,
                    timestamp = System.currentTimeMillis(),
                    source = TransactionSource.MANUAL,
                    imagePath = currentProofPath
                )
            } else {
                transactionToEdit.copy(
                    type = typeVal,
                    amount = amountVal,
                    category = categoryVal,
                    description = descriptionStr,
                    imagePath = currentProofPath
                )
            }

            if (transactionToEdit == null) {
                if (wouldBeNegativeBalanceAfterAdd(tempTx)) {
                    etAddTxAmount.error = "Transaksi ditolak. Nominal pengeluaran melebihi total saldo saat ini."
                    etAddTxAmount.requestFocus()
                    return@setOnClickListener
                }
                lifecycleScope.launch(Dispatchers.IO) {
                    repository.insertTransaction(tempTx)
                }
                Toast.makeText(this, "Transaksi berhasil disimpan", Toast.LENGTH_SHORT).show()
            } else {
                if (wouldBeNegativeBalanceAfterEdit(transactionToEdit.id, tempTx)) {
                    etAddTxAmount.error = "Transaksi ditolak. Perubahan ini akan menyebabkan total saldo menjadi minus."
                    etAddTxAmount.requestFocus()
                    return@setOnClickListener
                }
                lifecycleScope.launch(Dispatchers.IO) {
                    val oldPath = transactionToEdit.imagePath
                    if (oldPath != null && oldPath != currentProofPath) {
                        val file = File(oldPath)
                        if (file.exists()) {
                            file.delete()
                        }
                    }
                    repository.updateTransaction(tempTx)
                }
                Toast.makeText(this, "Transaksi berhasil diperbarui", Toast.LENGTH_SHORT).show()
            }

            dialog.dismiss()
        }

        dialog.show()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (layoutManageCategoriesContent.visibility == View.VISIBLE) {
            slideOutView(layoutManageCategoriesContent)
        } else if (layoutUserGuideContent.visibility == View.VISIBLE) {
            slideOutView(layoutUserGuideContent)
        } else if (layoutSettingsContent.visibility == View.VISIBLE) {
            // Return to Home tab
            setActiveNav(ivNavHome, tvNavHome)
            switchToTab(Tab.HOME)
        } else {
            super.onBackPressed()
        }
    }

    private fun slideInView(view: View) {
        view.visibility = View.VISIBLE
        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        view.translationX = screenWidth
        view.animate()
            .translationX(0f)
            .setDuration(300)
            .setListener(null)
            .start()
    }

    private fun slideOutView(view: View) {
        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        view.animate()
            .translationX(screenWidth)
            .setDuration(300)
            .withEndAction {
                view.visibility = View.GONE
            }
            .start()
    }

    private fun observeSettings() {
        lifecycleScope.launch {
            appSettings.businessNameFlow.collect { businessName ->
                tvSettingBusinessNameVal.text = businessName
            }
        }

        lifecycleScope.launch {
            appSettings.onboardingCompletedFlow.collect { completed ->
                isOnboardingCompleted = completed
            }
        }

        lifecycleScope.launch {
            appSettings.categoriesIncomeFlow.collect { list ->
                categoriesIncome.clear()
                categoriesIncome.addAll(list)
                if (layoutManageCategoriesContent.visibility == View.VISIBLE && isCategoryIncomeTabSelected) {
                    setupCategoriesList(true)
                }
            }
        }

        lifecycleScope.launch {
            appSettings.categoriesExpenseFlow.collect { list ->
                categoriesExpense.clear()
                categoriesExpense.addAll(list)
                if (layoutManageCategoriesContent.visibility == View.VISIBLE && !isCategoryIncomeTabSelected) {
                    setupCategoriesList(false)
                }
            }
        }
    }

    private fun observeTransactions() {
        lifecycleScope.launch {
            repository.allTransactionsFlow.collect { list ->
                if (list.isEmpty()) {
                    if (!isOnboardingCompleted) {
                        populateDefaultTransactions()
                    } else {
                        transactionsList.clear()
                        updateDynamicPeriods()
                        updateUI()
                        updateReportsUI()
                    }
                } else {
                    transactionsList.clear()
                    transactionsList.addAll(list)
                    updateDynamicPeriods()
                    updateUI()
                    updateReportsUI()
                }
            }
        }
    }

    private fun populateDefaultTransactions() {
        lifecycleScope.launch(Dispatchers.IO) {
            // Mark onboarding as completed to prevent double-populating
            appSettings.saveOnboardingCompleted(true)
            
            val list = listOf(
                Transaction(0, TransactionType.INCOME, 1500000, "Penjualan", "Penjualan katering siang", getMockTimestamp(2026, Calendar.JUNE, 15, 12), TransactionSource.MANUAL),
                Transaction(0, TransactionType.EXPENSE, 320000, "Bahan Baku", "Beli beras & minyak goreng", getMockTimestamp(2026, Calendar.JUNE, 16, 10), TransactionSource.MANUAL),
                Transaction(0, TransactionType.EXPENSE, 450000, "Operasional", "Bayar listrik ruko", getMockTimestamp(2026, Calendar.JUNE, 20, 14), TransactionSource.MANUAL),
                Transaction(0, TransactionType.EXPENSE, 850000, "Operasional", "Servis RAM & SSD laptop kasir", getMockTimestamp(2026, Calendar.JUNE, 25, 11), TransactionSource.MANUAL),
                Transaction(0, TransactionType.INCOME, 5000000, "Penjualan", "Gaji bulanan kantor", getMockTimestamp(2026, Calendar.MAY, 25, 9), TransactionSource.MANUAL),
                Transaction(0, TransactionType.EXPENSE, 2000000, "Operasional", "Bayar sewa kosan", getMockTimestamp(2026, Calendar.MAY, 1, 10), TransactionSource.MANUAL),
                Transaction(0, TransactionType.INCOME, 6000000, "Penjualan", "Penjualan borongan toko", getMockTimestamp(2026, Calendar.MARCH, 10, 15), TransactionSource.MANUAL),
                Transaction(0, TransactionType.INCOME, 2000000, "Penjualan", "Bonus proyek sampingan", getMockTimestamp(2026, Calendar.MARCH, 15, 17), TransactionSource.MANUAL),
                Transaction(0, TransactionType.EXPENSE, 2500000, "Bahan Baku", "Restock supplies bulanan", getMockTimestamp(2026, Calendar.MARCH, 5, 11), TransactionSource.MANUAL)
            )
            for (tx in list) {
                repository.insertTransaction(tx)
            }
        }
    }

    private fun updateDynamicPeriods() {
        val periods = getAvailablePeriods()
        monthsList = periods

        if (monthsList.isNotEmpty()) {
            if (!monthsList.contains(reportsPeriodName)) {
                reportsPeriodIndex = monthsList.size - 1
                reportsPeriodName = monthsList[reportsPeriodIndex]
            } else {
                reportsPeriodIndex = monthsList.indexOf(reportsPeriodName)
            }
            tvReportsPeriod.text = reportsPeriodName
            
            if (!monthsList.contains(currentPeriodName)) {
                currentPeriodName = monthsList[monthsList.size - 1]
                tvCurrentPeriod.text = currentPeriodName
            }
        } else {
            val sdf = SimpleDateFormat("MMMM yyyy", Locale.forLanguageTag("id-ID"))
            val thisMonth = sdf.format(Date())
            currentPeriodName = thisMonth
            tvCurrentPeriod.text = thisMonth
            reportsPeriodName = thisMonth
            tvReportsPeriod.text = thisMonth
            reportsPeriodIndex = 0
        }
    }

    private fun getAvailablePeriods(): List<String> {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.forLanguageTag("id-ID"))
        val periods = mutableSetOf<String>()

        val now = Date()
        val currentMonthStr = sdf.format(now)
        periods.add(currentMonthStr)

        if (transactionsList.isNotEmpty()) {
            val minTimestamp = transactionsList.minOf { it.timestamp }
            
            val cal = Calendar.getInstance()
            cal.timeInMillis = minTimestamp
            cal.set(Calendar.DAY_OF_MONTH, 1)
            
            val currentCal = Calendar.getInstance()
            currentCal.time = now
            currentCal.set(Calendar.DAY_OF_MONTH, 1)
            
            while (cal.before(currentCal)) {
                periods.add(sdf.format(cal.time))
                cal.add(Calendar.MONTH, 1)
            }
        }

        val sortedPeriods = periods.map { periodStr ->
            try {
                sdf.parse(periodStr) to periodStr
            } catch (e: Exception) {
                Date(0L) to periodStr
            }
        }.sortedBy { it.first }.map { it.second }.toMutableList()

        sortedPeriods.add("Semua Periode")
        return sortedPeriods
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density + 0.5f).toInt()
    }

    private fun saveBusinessName(name: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            appSettings.saveBusinessName(name)
        }
    }

    private fun saveCategories() {
        lifecycleScope.launch(Dispatchers.IO) {
            appSettings.saveCategories(categoriesIncome, categoriesExpense)
        }
    }

    private var isCategoryIncomeTabSelected = true

    private fun setupSettingsListeners() {
        // Edit Business Name
        findViewById<View>(R.id.btnSettingBusinessName).setOnClickListener {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Nama Usaha / Pengguna")
            
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val h = dpToPx(24f).toInt()
                val v = dpToPx(16f).toInt()
                setPadding(h, v, h, v)
            }
            val input = EditText(this).apply {
                setText(tvSettingBusinessNameVal.text.toString())
                setSelection(text.length)
                hint = "Masukkan Nama Usaha"
            }
            layout.addView(input)
            builder.setView(layout)

            builder.setPositiveButton("Simpan") { d, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    saveBusinessName(name)
                    Toast.makeText(this, "Nama usaha berhasil diperbarui", Toast.LENGTH_SHORT).show()
                }
                d.dismiss()
            }
            builder.setNegativeButton("Batal") { d, _ -> d.dismiss() }
            builder.show()
        }


        findViewById<View>(R.id.btnBackCategories).setOnClickListener {
            slideOutView(layoutManageCategoriesContent)
        }

        findViewById<View>(R.id.btnTabCategoriesIncome).setOnClickListener {
            isCategoryIncomeTabSelected = true
            updateCategoryTabsUI()
            setupCategoriesList(true)
        }

        findViewById<View>(R.id.btnTabCategoriesExpense).setOnClickListener {
            isCategoryIncomeTabSelected = false
            updateCategoryTabsUI()
            setupCategoriesList(false)
        }

        findViewById<View>(R.id.btnAddNewCategory).setOnClickListener {
            showAddCategoryDialog(isCategoryIncomeTabSelected)
        }



        // Backup Local/Export Data
        findViewById<View>(R.id.btnSettingBackup).setOnClickListener {
            try {
                // Generate simple JSON with all transactions, settings, and categories
                val rootJson = JSONObject().apply {
                    put("business_name", tvSettingBusinessNameVal.text.toString())
                    
                    val txArray = JSONArray()
                    for (tx in transactionsList) {
                        val txObj = JSONObject().apply {
                            put("id", tx.id)
                            put("type", tx.type.name)
                            put("amount", tx.amount)
                            put("category", tx.category)
                            put("description", tx.description)
                            put("timestamp", tx.timestamp)
                            put("source", tx.source.name)
                            put("imagePath", tx.imagePath ?: JSONObject.NULL)
                        }
                        txArray.put(txObj)
                    }
                    put("transactions", txArray)

                    put("categories_income", JSONArray(categoriesIncome))
                    put("categories_expense", JSONArray(categoriesExpense))


                }

                // Save to app external storage
                val backupFile = File(getExternalFilesDir(null), "PencatatanKeuangan_Backup.json")
                FileWriter(backupFile).use { writer ->
                    writer.write(rootJson.toString(4))
                }

                AlertDialog.Builder(this)
                    .setTitle("Cadangkan Berhasil")
                    .setMessage("Data Anda berhasil dicadangkan ke penyimpanan internal:\n\n${backupFile.absolutePath}\n\nAnda dapat menggunakan file ini untuk memulihkan data Anda kapan saja.")
                    .setPositiveButton("OK", null)
                    .show()

            } catch (e: Exception) {
                Toast.makeText(this, "Gagal mencadangkan data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // Reset Semua Data
        findViewById<View>(R.id.btnSettingResetData).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset Semua Data")
                .setMessage("Apakah Anda yakin ingin menghapus semua data transaksi dan pengaturan secara permanen? Tindakan ini tidak dapat dibatalkan.")
                .setPositiveButton("Reset") { _, _ ->
                    lifecycleScope.launch {
                        // Clear Room Database
                        repository.clearAll()
                        // Clear Cache files
                        withContext(Dispatchers.IO) {
                            cacheDir.listFiles()?.forEach { file ->
                                if (file.name.startsWith("proof")) {
                                    file.delete()
                                }
                            }
                        }
                        // Clear DataStore Preferences
                        appSettings.clearSettings()
                        // Keep onboarding completed as true so mock data is not re-created
                        appSettings.saveOnboardingCompleted(true)
                        
                        Toast.makeText(this@MainActivity, "Semua data berhasil di-reset", Toast.LENGTH_SHORT).show()
                        
                        // Switch back to Home Tab
                        setActiveNav(ivNavHome, tvNavHome)
                        switchToTab(Tab.HOME)
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        // Panduan Penggunaan
        findViewById<View>(R.id.btnSettingGuide).setOnClickListener {
            slideInView(layoutUserGuideContent)
        }

        findViewById<View>(R.id.btnBackGuide).setOnClickListener {
            slideOutView(layoutUserGuideContent)
        }
    }

    private fun updateCategoryTabsUI() {
        val btnTabIncome = findViewById<TextView>(R.id.btnTabCategoriesIncome)
        val btnTabExpense = findViewById<TextView>(R.id.btnTabCategoriesExpense)
        if (isCategoryIncomeTabSelected) {
            btnTabIncome.setBackgroundResource(R.drawable.bg_brutal_chip_filter)
            btnTabIncome.isSelected = true
            btnTabIncome.setTypeface(null, android.graphics.Typeface.BOLD)
            
            btnTabExpense.setBackgroundResource(android.R.color.transparent)
            btnTabExpense.isSelected = false
            btnTabExpense.setTextColor(ContextCompat.getColor(this, R.color.primary))
            btnTabExpense.setTypeface(null, android.graphics.Typeface.NORMAL)
        } else {
            btnTabExpense.setBackgroundResource(R.drawable.bg_brutal_chip_filter)
            btnTabExpense.isSelected = true
            btnTabExpense.setTypeface(null, android.graphics.Typeface.BOLD)
            
            btnTabIncome.setBackgroundResource(android.R.color.transparent)
            btnTabIncome.isSelected = false
            btnTabIncome.setTextColor(ContextCompat.getColor(this, R.color.primary))
            btnTabIncome.setTypeface(null, android.graphics.Typeface.NORMAL)
        }
    }

    private fun setupCategoriesList(isIncome: Boolean) {
        val list = if (isIncome) categoriesIncome else categoriesExpense
        val rv = findViewById<RecyclerView>(R.id.rvCategories)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = CategoriesAdapter(
            list,
            isIncome,
            onEdit = { position -> showEditCategoryDialog(position, isIncome) },
            onDelete = { position -> showDeleteCategoryDialog(position, isIncome) }
        )
    }



    private fun showAddCategoryDialog(isIncome: Boolean) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Tambah Kategori Baru")
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val h = dpToPx(24f).toInt()
            val v = dpToPx(16f).toInt()
            setPadding(h, v, h, v)
        }
        val input = EditText(this).apply {
            hint = "Nama Kategori (misal: Transportasi)"
        }
        layout.addView(input)
        builder.setView(layout)

        builder.setPositiveButton("Simpan") { dialog, _ ->
            val name = input.text.toString().trim()
            if (name.isNotEmpty()) {
                val list = if (isIncome) categoriesIncome else categoriesExpense
                if (list.contains(name)) {
                    Toast.makeText(this, "Kategori sudah ada", Toast.LENGTH_SHORT).show()
                } else {
                    list.add(name)
                    saveCategories()
                    setupCategoriesList(isIncome)
                    Toast.makeText(this, "Kategori \"$name\" berhasil ditambahkan", Toast.LENGTH_SHORT).show()
                }
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Batal") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    private fun showEditCategoryDialog(position: Int, isIncome: Boolean) {
        val list = if (isIncome) categoriesIncome else categoriesExpense
        val currentName = list[position]
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Ubah Nama Kategori")
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val h = dpToPx(24f).toInt()
            val v = dpToPx(16f).toInt()
            setPadding(h, v, h, v)
        }
        val input = EditText(this).apply {
            setText(currentName)
            setSelection(currentName.length)
            hint = "Nama Kategori"
        }
        layout.addView(input)
        builder.setView(layout)

        builder.setPositiveButton("Simpan") { dialog, _ ->
            val newName = input.text.toString().trim()
            if (newName.isNotEmpty() && newName != currentName) {
                if (list.contains(newName)) {
                    Toast.makeText(this, "Kategori sudah ada", Toast.LENGTH_SHORT).show()
                } else {
                    list[position] = newName
                    saveCategories()
                    setupCategoriesList(isIncome)
                    Toast.makeText(this, "Kategori berhasil diperbarui", Toast.LENGTH_SHORT).show()
                }
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Batal") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    private fun showDeleteCategoryDialog(position: Int, isIncome: Boolean) {
        val list = if (isIncome) categoriesIncome else categoriesExpense
        val name = list[position]
        AlertDialog.Builder(this)
            .setTitle("Hapus Kategori")
            .setMessage("Apakah Anda yakin ingin menghapus kategori \"$name\"?")
            .setPositiveButton("Hapus") { dialog, _ ->
                list.removeAt(position)
                saveCategories()
                setupCategoriesList(isIncome)
                Toast.makeText(this, "Kategori berhasil dihapus", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Batal", null)
            .show()
    }





    // --- Inner Class Adapters for Settings ---
    inner class CategoriesAdapter(
        private val list: MutableList<String>,
        private val isIncome: Boolean,
        private val onEdit: (Int) -> Unit,
        private val onDelete: (Int) -> Unit
    ) : RecyclerView.Adapter<CategoriesAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvCategoryName: TextView = view.findViewById(R.id.tvCategoryName)
            val btnEditCategory: View = view.findViewById(R.id.btnEditCategory)
            val btnDeleteCategory: View = view.findViewById(R.id.btnDeleteCategory)
            val categoryIconBg: View = view.findViewById(R.id.categoryIconBg)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = layoutInflater.inflate(R.layout.item_settings_category, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val name = list[position]
            holder.tvCategoryName.text = name
            holder.btnEditCategory.setOnClickListener { onEdit(position) }
            holder.btnDeleteCategory.setOnClickListener { onDelete(position) }

            val tintColor = if (isIncome) {
                ContextCompat.getColor(this@MainActivity, R.color.income_green_light)
            } else {
                ContextCompat.getColor(this@MainActivity, R.color.expense_red_light)
            }
            holder.categoryIconBg.backgroundTintList = ColorStateList.valueOf(tintColor)
        }

        override fun getItemCount(): Int = list.size
    }

    // ═══════════════════════════════════════════════════
    // VOICE INPUT FEATURE
    // ═══════════════════════════════════════════════════

    /**
     * Cek izin RECORD_AUDIO. Jika sudah diberikan, langsung mulai rekam.
     * Jika belum, minta izin runtime.
     */
    private fun checkAudioPermissionAndStartVoice() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startVoiceRecording()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceRecording()
            } else {
                Toast.makeText(
                    this,
                    "Izin mikrofon diperlukan untuk fitur input suara.",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else if (requestCode == REQUEST_CAMERA_CAPTURE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCameraCapture()
            } else {
                Toast.makeText(
                    this,
                    "Izin kamera diperlukan untuk mengambil foto bukti.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Tampilkan dialog recording dan mulai SpeechRecognizer.
     */
    private fun startVoiceRecording() {
        voiceInputManager = VoiceInputManager(this)

        voiceRecordingDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_voice_recording, null)
        voiceRecordingDialog?.setContentView(view)

        val tvVoiceStatus = view.findViewById<TextView>(R.id.tvVoiceStatus)
        val tvPartialResult = view.findViewById<TextView>(R.id.tvPartialResult)
        val viewPulseOuter = view.findViewById<View>(R.id.viewPulseOuter)
        val viewPulseInner = view.findViewById<View>(R.id.viewPulseInner)
        val btnCancelRecording = view.findViewById<Button>(R.id.btnCancelRecording)
        val originalStatusColor = tvVoiceStatus.textColors

        // Start pulse animation
        startPulseAnimation(viewPulseOuter, viewPulseInner)

        var isInErrorState = false
        lateinit var recognitionCallback: VoiceInputManager.Callback

        recognitionCallback = object : VoiceInputManager.Callback {
            override fun onListening() {
                stopPulseAnimation()
                tvVoiceStatus.setTextColor(originalStatusColor)
                tvVoiceStatus.text = "Mendengarkan..."
                tvPartialResult.text = "Silakan ucapkan transaksi Anda..."
                btnCancelRecording.text = "Batal"
                isInErrorState = false
            }

            override fun onPartialResult(partialText: String) {
                tvPartialResult.text = "\"$partialText\""
            }

            override fun onResult(fullText: String) {
                stopPulseAnimation()
                voiceRecordingDialog?.dismiss()

                // Proses hasil speech-to-text
                val result = VoiceTransactionClassifier.classify(fullText)
                showVoiceConfirmationDialog(result)
            }

            override fun onError(errorMessage: String) {
                stopPulseAnimation()
                isInErrorState = true

                // Reset scales of circles to base
                viewPulseOuter.animate().scaleX(1.0f).scaleY(1.0f).alpha(0.3f).setDuration(150).start()
                viewPulseInner.animate().scaleX(1.0f).scaleY(1.0f).alpha(0.6f).setDuration(150).start()

                tvVoiceStatus.text = "Gagal Mendengar"
                tvVoiceStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.expense_red))
                tvPartialResult.text = errorMessage
                btnCancelRecording.text = "Coba Lagi"
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (!isInErrorState && pulseAnimatorSet == null) {
                    val db = rmsdB.coerceIn(0f, 12f)
                    val scaleInner = 1.0f + (db / 12f) * 0.25f
                    val scaleOuter = 1.0f + (db / 12f) * 0.55f

                    viewPulseInner.animate()
                        .scaleX(scaleInner)
                        .scaleY(scaleInner)
                        .alpha(0.6f + (db / 12f) * 0.3f)
                        .setDuration(60)
                        .start()

                    viewPulseOuter.animate()
                        .scaleX(scaleOuter)
                        .scaleY(scaleOuter)
                        .alpha(0.3f + (db / 12f) * 0.4f)
                        .setDuration(60)
                        .start()
                }
            }
        }

        btnCancelRecording.setOnClickListener {
            if (isInErrorState) {
                isInErrorState = false
                tvVoiceStatus.setTextColor(originalStatusColor)
                tvVoiceStatus.text = "Menghubungkan..."
                tvPartialResult.text = "Silakan ucapkan transaksi Anda..."
                btnCancelRecording.text = "Batal"
                startPulseAnimation(viewPulseOuter, viewPulseInner)

                voiceInputManager.startListening(recognitionCallback)
            } else {
                voiceInputManager.cancel()
                stopPulseAnimation()
                voiceRecordingDialog?.dismiss()
            }
        }

        voiceRecordingDialog?.setOnDismissListener {
            voiceInputManager.cancel()
            stopPulseAnimation()
        }

        voiceRecordingDialog?.show()

        // Mulai speech recognition
        voiceInputManager.startListening(recognitionCallback)
    }

    /**
     * Animasi pulse pada lingkaran recording.
     */
    private fun startPulseAnimation(outer: View, inner: View) {
        val outerScaleX = ObjectAnimator.ofFloat(outer, "scaleX", 1.0f, 1.3f, 1.0f)
        val outerScaleY = ObjectAnimator.ofFloat(outer, "scaleY", 1.0f, 1.3f, 1.0f)
        val outerAlpha = ObjectAnimator.ofFloat(outer, "alpha", 0.3f, 0.1f, 0.3f)

        val innerScaleX = ObjectAnimator.ofFloat(inner, "scaleX", 1.0f, 1.15f, 1.0f)
        val innerScaleY = ObjectAnimator.ofFloat(inner, "scaleY", 1.0f, 1.15f, 1.0f)
        val innerAlpha = ObjectAnimator.ofFloat(inner, "alpha", 0.6f, 0.3f, 0.6f)

        pulseAnimatorSet = AnimatorSet().apply {
            playTogether(outerScaleX, outerScaleY, outerAlpha, innerScaleX, innerScaleY, innerAlpha)
            duration = 1200
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (pulseAnimatorSet != null) {
                        animation.start() // Loop
                    }
                }
            })
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimatorSet?.cancel()
        pulseAnimatorSet = null
    }

    /**
     * Tampilkan dialog konfirmasi hasil voice recognition.
     * Pengguna bisa mengedit semua field sebelum menyimpan.
     */
    @Suppress("DEPRECATION")
    private fun showVoiceConfirmationDialog(result: VoiceTransactionClassifier.ClassificationResult) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_voice_confirmation, null)
        dialog.setContentView(view)

        // Configure BottomSheet Behavior and soft input mode for better UX
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val tvRawTranscript = view.findViewById<TextView>(R.id.tvRawTranscript)
        val viewConfidenceDot = view.findViewById<View>(R.id.viewConfidenceDot)
        val tvConfidenceLabel = view.findViewById<TextView>(R.id.tvConfidenceLabel)
        val rgVoiceType = view.findViewById<RadioGroup>(R.id.rgVoiceType)
        val etVoiceAmount = view.findViewById<EditText>(R.id.etVoiceAmount)
        val etVoiceDescription = view.findViewById<EditText>(R.id.etVoiceDescription)
        val btnSaveVoiceTx = view.findViewById<Button>(R.id.btnSaveVoiceTx)
        val btnRetryVoice = view.findViewById<Button>(R.id.btnRetryVoice)

        val layoutVoiceUploadZone = view.findViewById<View>(R.id.layoutVoiceUploadZone)
        val layoutVoiceProofPreview = view.findViewById<View>(R.id.layoutVoiceProofPreview)
        val ivVoiceProofPreview = view.findViewById<ImageView>(R.id.ivVoiceProofPreview)
        val btnVoiceReplaceProof = view.findViewById<View>(R.id.btnVoiceReplaceProof)
        val btnVoiceRemoveProof = view.findViewById<View>(R.id.btnVoiceRemoveProof)
        val tvVoiceProofFileInfo = view.findViewById<TextView>(R.id.tvVoiceProofFileInfo)

        var voiceProofPath: String? = null

        fun updateVoiceProofUI() {
            if (voiceProofPath != null) {
                val bitmap = decodeSampledBitmap(voiceProofPath!!, 360, 200)
                if (bitmap != null) {
                    layoutVoiceUploadZone.visibility = View.GONE
                    layoutVoiceProofPreview.visibility = View.VISIBLE
                    ivVoiceProofPreview.setImageBitmap(bitmap)
                    
                    val file = File(voiceProofPath!!)
                    val sizeKb = file.length() / 1024
                    tvVoiceProofFileInfo.text = "Bukti Terunggah (${sizeKb} KB)"
                } else {
                    layoutVoiceProofPreview.visibility = View.GONE
                    layoutVoiceUploadZone.visibility = View.VISIBLE
                }
            } else {
                layoutVoiceProofPreview.visibility = View.GONE
                layoutVoiceUploadZone.visibility = View.VISIBLE
            }
        }

        updateVoiceProofUI()

        layoutVoiceUploadZone.setOnClickListener {
            showPhotoPickerDialog { path ->
                voiceProofPath = path
                updateVoiceProofUI()
            }
        }

        btnVoiceReplaceProof.setOnClickListener {
            showPhotoPickerDialog { path ->
                voiceProofPath = path
                updateVoiceProofUI()
            }
        }

        btnVoiceRemoveProof.setOnClickListener {
            voiceProofPath = null
            updateVoiceProofUI()
        }

        // Populate raw transcript
        tvRawTranscript.text = "\"${result.rawText}\""

        // Confidence indicator
        when {
            result.confidence >= 0.7f -> {
                viewConfidenceDot.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.income_green)
                )
                tvConfidenceLabel.text = "Keyakinan Tinggi (${(result.confidence * 100).toInt()}%)"
            }
            result.confidence >= 0.4f -> {
                viewConfidenceDot.backgroundTintList = ColorStateList.valueOf(
                    Color.parseColor("#F59E0B") // amber
                )
                tvConfidenceLabel.text = "Keyakinan Sedang (${(result.confidence * 100).toInt()}%)"
            }
            else -> {
                viewConfidenceDot.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.expense_red)
                )
                tvConfidenceLabel.text = "Keyakinan Rendah (${(result.confidence * 100).toInt()}%) — Periksa ulang"
            }
        }

        // Set transaction type
        if (result.type == TransactionType.INCOME) {
            rgVoiceType.check(R.id.rbVoiceIncome)
        } else {
            rgVoiceType.check(R.id.rbVoiceExpense)
        }

        // Set amount
        if (result.amount > 0) {
            etVoiceAmount.setText(result.amount.toString())
        }

        // Set description
        etVoiceDescription.setText(result.description)

        // Retry button → dismiss and restart recording
        btnRetryVoice.setOnClickListener {
            dialog.dismiss()
            startVoiceRecording()
        }

        // Save button
        btnSaveVoiceTx.setOnClickListener {
            val amountStr = etVoiceAmount.text.toString().trim()
            if (amountStr.isEmpty()) {
                etVoiceAmount.error = "Nominal harus diisi"
                return@setOnClickListener
            }
            val amountVal = amountStr.toLongOrNull() ?: 0L
            if (amountVal <= 0L) {
                etVoiceAmount.error = "Nominal harus lebih dari 0"
                return@setOnClickListener
            }

            val descriptionStr = etVoiceDescription.text.toString().trim()
            if (descriptionStr.isEmpty()) {
                etVoiceDescription.error = "Catatan harus diisi"
                return@setOnClickListener
            }

            val typeVal = if (rgVoiceType.checkedRadioButtonId == R.id.rbVoiceIncome) {
                TransactionType.INCOME
            } else {
                TransactionType.EXPENSE
            }

            val categoryVal = VoiceTransactionClassifier.classify(descriptionStr).category

            val newTx = Transaction(
                id = 0L,
                type = typeVal,
                amount = amountVal,
                category = categoryVal,
                description = descriptionStr,
                timestamp = System.currentTimeMillis(),
                source = TransactionSource.VOICE,
                imagePath = voiceProofPath
            )
            if (wouldBeNegativeBalanceAfterAdd(newTx)) {
                etVoiceAmount.error = "Transaksi ditolak. Nominal pengeluaran melebihi total saldo saat ini."
                etVoiceAmount.requestFocus()
                return@setOnClickListener
            }
            lifecycleScope.launch(Dispatchers.IO) {
                repository.insertTransaction(newTx)
            }

            Toast.makeText(
                this,
                "Transaksi suara berhasil disimpan!",
                Toast.LENGTH_SHORT
            ).show()
            dialog.dismiss()
        }

        dialog.show()
    }


    private var pdfProgressDialog: AlertDialog? = null

    private fun showPdfLoadingDialog() {
        val padding = 50
        val linearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(padding, padding, padding, padding)
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val progressBar = ProgressBar(this).apply {
            isIndeterminate = true
            setPadding(0, 0, padding, 0)
        }
        val textView = TextView(this).apply {
            text = "Menyusun Laporan PDF..."
            textSize = 16f
            setTextColor(Color.parseColor("#1E1E1E"))
        }
        linearLayout.addView(progressBar)
        linearLayout.addView(textView)

        pdfProgressDialog = AlertDialog.Builder(this)
            .setView(linearLayout)
            .setCancelable(false)
            .create()
        pdfProgressDialog?.show()
    }

    private fun dismissPdfLoadingDialog() {
        pdfProgressDialog?.dismiss()
        pdfProgressDialog = null
    }

    private fun getFilteredReportsTransactions(): List<Transaction> {
        return if (filterStartDate != null || filterEndDate != null) {
            transactionsList.filter { tx ->
                val matchesStartDate = filterStartDate == null || tx.timestamp >= filterStartDate!!
                val matchesEndDate = filterEndDate == null || tx.timestamp <= filterEndDate!! + 86399000L
                matchesStartDate && matchesEndDate
            }
        } else {
            filterTransactionsByPeriod(transactionsList, reportsPeriodName)
        }
    }

    private fun formatCurrencyAccounting(amount: Long): String {
        val isNegative = amount < 0
        val absVal = if (isNegative) -amount else amount
        val numberFormat = NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID"))
        val formattedNum = numberFormat.format(absVal)
        return if (isNegative) {
            "(Rp $formattedNum)"
        } else {
            "Rp $formattedNum"
        }
    }

    private fun exportPdfToUri(uri: Uri) {
        showPdfLoadingDialog()
        
        lifecycleScope.launch(Dispatchers.IO) {
            var pdfDocument: PdfDocument? = null
            var success = false
            var errorMsg: String? = null
            
            try {
                val transactions = getFilteredReportsTransactions()
                val businessName = appSettings.businessNameFlow.first()
                
                pdfDocument = PdfDocument()
                
                val pageWidth = 595
                val pageHeight = 842
                val marginLeft = 40f
                val marginRight = 40f
                val marginTop = 40f
                val marginBottom = 40f
                val contentWidth = 515f
                val maxYPerPage = 780f
                
                var totalIncome = 0L
                var totalExpense = 0L
                for (tx in transactions) {
                    if (tx.type == TransactionType.INCOME) {
                        totalIncome += tx.amount
                    } else {
                        totalExpense += tx.amount
                    }
                }
                
                var pageNumber = 1
                var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                var page = pdfDocument.startPage(pageInfo)
                var canvas = page.canvas
                
                val paintText = Paint().apply {
                    isAntiAlias = true
                    color = Color.parseColor("#1E1E1E")
                    textSize = 8.5f
                }
                val paintBold = Paint().apply {
                    isAntiAlias = true
                    color = Color.parseColor("#1E1E1E")
                    textSize = 8.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val paintLine = Paint().apply {
                    color = Color.parseColor("#E2E8F0")
                    strokeWidth = 0.8f
                    style = Paint.Style.STROKE
                }
                val paintBrutalLine = Paint().apply {
                    color = Color.parseColor("#1E1E1E")
                    strokeWidth = 1.2f
                    style = Paint.Style.STROKE
                }
                val paintFillHeader = Paint().apply {
                    color = Color.parseColor("#F3F4F6")
                    style = Paint.Style.FILL
                }
                val paintFillZebra = Paint().apply {
                    color = Color.parseColor("#F9FAFB")
                    style = Paint.Style.FILL
                }
                val paintWhite = Paint().apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                }
                
                fun drawFooter(c: android.graphics.Canvas, pageNum: Int) {
                    val footerPaint = Paint().apply {
                        color = Color.parseColor("#6B7280")
                        textSize = 7.5f
                        isAntiAlias = true
                    }
                    val dateStr = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.forLanguageTag("id-ID")).format(Date())
                    c.drawText("Pencatatan Keuangan - Diekspor pada $dateStr", marginLeft, 810f, footerPaint)
                    
                    footerPaint.textAlign = Paint.Align.RIGHT
                    c.drawText("Halaman $pageNum", 555f, 810f, footerPaint)
                }
                
                fun drawTableHeader(c: android.graphics.Canvas, y: Float) {
                    c.drawRect(marginLeft, y, 555f, y + 20f, paintFillHeader)
                    c.drawLine(marginLeft, y, 555f, y, paintBrutalLine)
                    c.drawLine(marginLeft, y + 20f, 555f, y + 20f, paintBrutalLine)
                    c.drawLine(marginLeft, y, marginLeft, y + 20f, paintBrutalLine)
                    c.drawLine(555f, y, 555f, y + 20f, paintBrutalLine)
                    
                    val headerPaint = Paint().apply {
                        color = Color.parseColor("#1E1E1E")
                        textSize = 8f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        isAntiAlias = true
                    }
                    
                    headerPaint.textAlign = Paint.Align.CENTER
                    c.drawText("No", 55f, y + 13f, headerPaint)
                    
                    headerPaint.textAlign = Paint.Align.LEFT
                    c.drawText("Tanggal", 74f, y + 13f, headerPaint)
                    c.drawText("Kategori", 149f, y + 13f, headerPaint)
                    c.drawText("Keterangan", 239f, y + 13f, headerPaint)
                    
                    headerPaint.textAlign = Paint.Align.RIGHT
                    c.drawText("Debit (Pemasukan)", 456f, y + 13f, headerPaint)
                    c.drawText("Kredit (Pengeluaran)", 551f, y + 13f, headerPaint)
                }
                
                // --- DRAW PAGE 1 HEADER ---
                val logoRect = RectF(40f, 40f, 72f, 72f)
                val logoBgPaint = Paint().apply {
                    color = Color.parseColor("#8B5CF6")
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                canvas.drawRoundRect(logoRect, 6f, 6f, logoBgPaint)
                
                val walletDrawable = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_wallet)
                if (walletDrawable != null) {
                    val wrapped = androidx.core.graphics.drawable.DrawableCompat.wrap(walletDrawable).mutate()
                    androidx.core.graphics.drawable.DrawableCompat.setTint(wrapped, Color.WHITE)
                    wrapped.setBounds(44, 44, 68, 68)
                    wrapped.draw(canvas)
                }
                
                val paintBusName = Paint().apply {
                    color = Color.parseColor("#1E1E1E")
                    textSize = 13f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
                canvas.drawText(businessName.uppercase(Locale.forLanguageTag("id-ID")), 297.5f, 54f, paintBusName)
                
                val paintSubtitle = Paint().apply {
                    color = Color.parseColor("#1E1E1E")
                    textSize = 9.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
                canvas.drawText("LAPORAN BUKU KAS UMUM", 297.5f, 68f, paintSubtitle)
                
                val paintPeriod = Paint().apply {
                    color = Color.parseColor("#6B7280")
                    textSize = 8f
                    textAlign = Paint.Align.RIGHT
                    isAntiAlias = true
                }
                val periodDisplay = if (filterStartDate != null || filterEndDate != null) {
                    val displayDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.forLanguageTag("id-ID"))
                    val startStr = filterStartDate?.let { displayDateFormat.format(Date(it)) } ?: "Awal"
                    val endStr = filterEndDate?.let { displayDateFormat.format(Date(it)) } ?: "Akhir"
                    "$startStr - $endStr"
                } else {
                    reportsPeriodName
                }
                canvas.drawText("Periode: $periodDisplay", 555f, 60f, paintPeriod)
                canvas.drawLine(marginLeft, 80f, 555f, 80f, paintBrutalLine)
                
                // --- DRAW SUMMARY CARD ---
                val cardRect = RectF(40f, 90f, 555f, 132f)
                canvas.drawRoundRect(cardRect, 4f, 4f, paintFillZebra)
                canvas.drawRoundRect(cardRect, 4f, 4f, paintBrutalLine)
                
                canvas.drawLine(211.6f, 90f, 211.6f, 132f, paintBrutalLine)
                canvas.drawLine(383.2f, 90f, 383.2f, 132f, paintBrutalLine)
                
                val paintCardLabel = Paint().apply {
                    color = Color.parseColor("#6B7280")
                    textSize = 7f
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
                val paintCardVal = Paint().apply {
                    textSize = 9.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
                
                canvas.drawText("TOTAL PEMASUKAN", 125.8f, 104f, paintCardLabel)
                paintCardVal.color = Color.parseColor("#10B981")
                canvas.drawText(formatCurrencyAccounting(totalIncome), 125.8f, 122f, paintCardVal)
                
                canvas.drawText("TOTAL PENGELUARAN", 297.4f, 104f, paintCardLabel)
                paintCardVal.color = Color.parseColor("#F43F5E")
                canvas.drawText(formatCurrencyAccounting(totalExpense), 297.4f, 122f, paintCardVal)
                
                canvas.drawText("LABA / RUGI BERSIH", 469.1f, 104f, paintCardLabel)
                val netVal = totalIncome - totalExpense
                paintCardVal.color = if (netVal >= 0) Color.parseColor("#10B981") else Color.parseColor("#F43F5E")
                canvas.drawText(formatCurrencyAccounting(netVal), 469.1f, 122f, paintCardVal)
                
                var yPosition = 145f
                drawTableHeader(canvas, yPosition)
                yPosition += 20f
                
                val textPaint = TextPaint().apply {
                    textSize = 7.5f
                    color = Color.parseColor("#1E1E1E")
                    isAntiAlias = true
                }
                
                val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.forLanguageTag("id-ID"))
                
                for (i in transactions.indices) {
                    val tx = transactions[i]
                    
                    val descTextWidth = 122
                    val staticLayout = StaticLayout.Builder.obtain(tx.description, 0, tx.description.length, textPaint, descTextWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0f, 1.0f)
                        .setIncludePad(false)
                        .build()
                    val descHeight = staticLayout.height
                    val rowHeight = maxOf(18f, descHeight + 6f)
                    
                    if (yPosition + rowHeight > maxYPerPage) {
                        canvas.drawLine(marginLeft, yPosition, 555f, yPosition, paintBrutalLine)
                        drawFooter(canvas, pageNumber)
                        pdfDocument.finishPage(page)
                        
                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        
                        yPosition = 40f
                        drawTableHeader(canvas, yPosition)
                        yPosition += 20f
                    }
                    
                    if (i % 2 == 1) {
                        canvas.drawRect(marginLeft, yPosition, 555f, yPosition + rowHeight, paintFillZebra)
                    } else {
                        canvas.drawRect(marginLeft, yPosition, 555f, yPosition + rowHeight, paintWhite)
                    }
                    
                    canvas.drawLine(marginLeft, yPosition, marginLeft, yPosition + rowHeight, paintBrutalLine)
                    canvas.drawLine(555f, yPosition, 555f, yPosition + rowHeight, paintBrutalLine)
                    
                    val yText = yPosition + 5f
                    
                    paintText.textAlign = Paint.Align.CENTER
                    canvas.drawText((i + 1).toString(), 55f, yText + 7f, paintText)
                    
                    paintText.textAlign = Paint.Align.LEFT
                    val dateText = dateFormat.format(Date(tx.timestamp))
                    canvas.drawText(dateText, 74f, yText + 7f, paintText)
                    
                    val catText = if (tx.category.length > 18) tx.category.substring(0, 15) + "..." else tx.category
                    canvas.drawText(catText, 149f, yText + 7f, paintText)
                    
                    canvas.save()
                    canvas.translate(239f, yPosition + 3f)
                    staticLayout.draw(canvas)
                    canvas.restore()
                    
                    paintText.textAlign = Paint.Align.RIGHT
                    if (tx.type == TransactionType.INCOME) {
                        canvas.drawText(formatCurrencyAccounting(tx.amount), 456f, yText + 7f, paintText)
                        canvas.drawText("-", 551f, yText + 7f, paintText)
                    } else {
                        canvas.drawText("-", 456f, yText + 7f, paintText)
                        canvas.drawText(formatCurrencyAccounting(tx.amount), 551f, yText + 7f, paintText)
                    }
                    
                    canvas.drawLine(marginLeft, yPosition + rowHeight, 555f, yPosition + rowHeight, paintLine)
                    yPosition += rowHeight
                }
                
                if (yPosition + 36f > maxYPerPage) {
                    canvas.drawLine(marginLeft, yPosition, 555f, yPosition, paintBrutalLine)
                    drawFooter(canvas, pageNumber)
                    pdfDocument.finishPage(page)
                    
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    yPosition = 40f
                    drawTableHeader(canvas, yPosition)
                    yPosition += 20f
                }
                
                canvas.drawRect(marginLeft, yPosition, 555f, yPosition + 18f, paintFillHeader)
                canvas.drawLine(marginLeft, yPosition, 555f, yPosition, paintBrutalLine)
                canvas.drawLine(marginLeft, yPosition + 18f, 555f, yPosition + 18f, paintLine)
                canvas.drawLine(marginLeft, yPosition, marginLeft, yPosition + 18f, paintBrutalLine)
                canvas.drawLine(555f, yPosition, 555f, yPosition + 18f, paintBrutalLine)
                
                paintBold.textAlign = Paint.Align.LEFT
                canvas.drawText("TOTAL", 239f, yPosition + 12f, paintBold)
                
                paintBold.textAlign = Paint.Align.RIGHT
                canvas.drawText(formatCurrencyAccounting(totalIncome), 456f, yPosition + 12f, paintBold)
                canvas.drawText(formatCurrencyAccounting(totalExpense), 551f, yPosition + 12f, paintBold)
                
                yPosition += 18f
                
                canvas.drawRect(marginLeft, yPosition, 555f, yPosition + 18f, paintFillHeader)
                canvas.drawLine(marginLeft, yPosition, marginLeft, yPosition + 18f, paintBrutalLine)
                canvas.drawLine(555f, yPosition, 555f, yPosition + 18f, paintBrutalLine)
                canvas.drawLine(marginLeft, yPosition + 18f, 555f, yPosition + 18f, paintBrutalLine)
                
                val netLabel = if (netVal >= 0) "LABA BERSIH" else "RUGI BERSIH"
                
                paintBold.textAlign = Paint.Align.LEFT
                canvas.drawText(netLabel, 239f, yPosition + 12f, paintBold)
                
                paintBold.textAlign = Paint.Align.RIGHT
                val formattedNet = formatCurrencyAccounting(netVal)
                canvas.drawText(formattedNet, 551f, yPosition + 12f, paintBold)
                
                val underlineY1 = yPosition + 14f
                val underlineY2 = yPosition + 16f
                canvas.drawLine(460f, underlineY1, 555f, underlineY1, paintBold)
                canvas.drawLine(460f, underlineY2, 555f, underlineY2, paintBold)
                
                yPosition += 18f
                
                drawFooter(canvas, pageNumber)
                pdfDocument.finishPage(page)
                
                contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                    java.io.FileOutputStream(pfd.fileDescriptor).use { fileOutputStream ->
                        pdfDocument.writeTo(fileOutputStream)
                    }
                }
                
                success = true
            } catch (e: Exception) {
                e.printStackTrace()
                errorMsg = e.localizedMessage ?: "Terjadi kesalahan tidak dikenal"
            } finally {
                pdfDocument?.close()
            }
            
            withContext(Dispatchers.Main) {
                dismissPdfLoadingDialog()
                if (success) {
                    Toast.makeText(this@MainActivity, "Laporan PDF berhasil disimpan!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MainActivity, "Gagal mengekspor PDF: $errorMsg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private var csvProgressDialog: AlertDialog? = null

    private fun showCsvLoadingDialog() {
        val padding = 50
        val linearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(padding, padding, padding, padding)
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val progressBar = ProgressBar(this).apply {
            isIndeterminate = true
            setPadding(0, 0, padding, 0)
        }
        val textView = TextView(this).apply {
            text = "Mengekspor Laporan CSV..."
            textSize = 16f
            setTextColor(Color.parseColor("#1E1E1E"))
        }
        linearLayout.addView(progressBar)
        linearLayout.addView(textView)

        csvProgressDialog = AlertDialog.Builder(this)
            .setView(linearLayout)
            .setCancelable(false)
            .create()
        csvProgressDialog?.show()
    }

    private fun dismissCsvLoadingDialog() {
        csvProgressDialog?.dismiss()
        csvProgressDialog = null
    }

    private fun escapeCsvField(value: String): String {
        val delimiter = ';'
        val needsQuotes = value.contains(delimiter) || value.contains('"') || value.contains('\n') || value.contains('\r')
        if (!needsQuotes) {
            return value
        }
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun getReportsTimestampRange(): Pair<Long, Long>? {
        if (filterStartDate != null || filterEndDate != null) {
            val min = filterStartDate ?: (transactionsList.minOfOrNull { it.timestamp } ?: return null)
            val max = filterEndDate?.let { it + 86399000L } ?: (transactionsList.maxOfOrNull { it.timestamp } ?: return null)
            return Pair(min, max)
        }

        val parsedDate = try {
            SimpleDateFormat("MMMM yyyy", Locale.forLanguageTag("id-ID")).parse(reportsPeriodName)
        } catch (e: Exception) {
            null
        }
        if (parsedDate != null) {
            val cal = Calendar.getInstance().apply {
                time = parsedDate
                add(Calendar.MONTH, 1)
                add(Calendar.MILLISECOND, -1)
            }
            return Pair(parsedDate.time, cal.timeInMillis)
        }
        
        // Fallback: return range of all transactions in transactionsList
        val minTimestamp = transactionsList.minOfOrNull { it.timestamp } ?: return null
        val maxTimestamp = transactionsList.maxOfOrNull { it.timestamp } ?: return null
        return Pair(minTimestamp, maxTimestamp)
    }

    private fun exportCsvToUri(uri: Uri) {
        showCsvLoadingDialog()
        
        lifecycleScope.launch(Dispatchers.IO) {
            var success = false
            var errorMsg: String? = null
            
            try {
                val range = getReportsTimestampRange()
                if (range == null) {
                    errorMsg = "Tidak ada data transaksi untuk diekspor"
                } else {
                    val startDate = range.first
                    val endDate = range.second
                    
                    val initialBalance = repository.getBalanceBeforeTimestamp(startDate)
                    
                    contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                        java.io.FileOutputStream(pfd.fileDescriptor).use { fileOutputStream ->
                            java.io.BufferedWriter(java.io.OutputStreamWriter(fileOutputStream, "UTF-8")).use { writer ->
                                // Write Excel delimiter directive
                                writer.write("sep=;\r\n")
                                // Write header
                                writer.write("Tanggal;Keterangan;Kategori;Tipe Transaksi;Pemasukan;Pengeluaran;Saldo Berjalan;Sumber Input\r\n")
                                
                                var runningBalance = initialBalance
                                var offset = 0
                                val limit = 500
                                var hasMore = true
                                val csvDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                                
                                while (hasMore) {
                                    val batch = repository.getTransactionsPagedInRange(startDate, endDate, limit, offset)
                                    if (batch.isEmpty()) {
                                        hasMore = false
                                    } else {
                                        for (tx in batch) {
                                            val formattedDate = csvDateFormat.format(Date(tx.timestamp))
                                            val escapedDescription = escapeCsvField(tx.description)
                                            val escapedCategory = escapeCsvField(tx.category)
                                            val typeText = if (tx.type == TransactionType.INCOME) "Pemasukan" else "Pengeluaran"
                                            
                                            val incomeStr: String
                                            val expenseStr: String
                                            if (tx.type == TransactionType.INCOME) {
                                                incomeStr = tx.amount.toString()
                                                expenseStr = ""
                                                runningBalance += tx.amount
                                            } else {
                                                incomeStr = ""
                                                expenseStr = tx.amount.toString()
                                                runningBalance -= tx.amount
                                            }
                                            
                                            val sourceText = when (tx.source) {
                                                TransactionSource.VOICE -> "Suara"
                                                TransactionSource.MANUAL -> "Manual"
                                                TransactionSource.OCR -> "Struk"
                                            }
                                            val escapedSource = escapeCsvField(sourceText)
                                            
                                            writer.write("$formattedDate;$escapedDescription;$escapedCategory;$typeText;$incomeStr;$expenseStr;$runningBalance;$escapedSource\r\n")
                                        }
                                        offset += limit
                                        if (batch.size < limit) {
                                            hasMore = false
                                        }
                                    }
                                }
                                writer.flush()
                            }
                        }
                    }
                    success = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMsg = e.localizedMessage ?: "Terjadi kesalahan tidak dikenal"
            }
            
            withContext(Dispatchers.Main) {
                dismissCsvLoadingDialog()
                if (success) {
                    Toast.makeText(this@MainActivity, "Laporan CSV berhasil disimpan!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MainActivity, "Gagal mengekspor CSV: $errorMsg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::voiceInputManager.isInitialized) {
            voiceInputManager.destroy()
        }
        stopPulseAnimation()
    }
}