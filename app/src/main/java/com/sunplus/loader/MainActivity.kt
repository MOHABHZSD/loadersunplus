package com.sunplus.loader

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var spinnerDevices: Spinner
    private lateinit var spinnerBaudRate: Spinner
    private lateinit var spinnerParity: Spinner
    private lateinit var spinnerDdrType: Spinner
    private lateinit var spinnerChipType: Spinner
    private lateinit var spinnerOperation: Spinner
    private lateinit var spinnerStorage: Spinner
    private lateinit var spinnerSection: Spinner
    
    private lateinit var etStartAddress: EditText
    private lateinit var etLength: EditText
    
    private lateinit var btnSelectFile: Button
    private lateinit var btnDumpPath: Button
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    
    private lateinit var tvFileInfo: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvConsoleLog: TextView

    // مُلقط اختيار ملف السوفت وير (.bin)
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                processSelectedFile(uri)
            }
        }
    }

    // مُلقط تحديد مجلد حفظ الدامب (Dump Path)
    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // منح صلاحيات المستمرة للمجلد المحدد
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                appendLog("📁 تم تحديد مسار الحفظ بنجاح: $uri")
                tvStatus.text = "الحالة: تم تحديد مسار الحفظ"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ربط عناصر الواجهة
        spinnerDevices = findViewById(R.id.spinnerDevices)
        spinnerBaudRate = findViewById(R.id.spinnerBaudRate)
        spinnerParity = findViewById(R.id.spinnerParity)
        spinnerDdrType = findViewById(R.id.spinnerDdrType)
        spinnerChipType = findViewById(R.id.spinnerChipType)
        spinnerOperation = findViewById(R.id.spinnerOperation)
        spinnerStorage = findViewById(R.id.spinnerStorage)
        spinnerSection = findViewById(R.id.spinnerSection)
        
        etStartAddress = findViewById(R.id.etStartAddress)
        etLength = findViewById(R.id.etLength)
        
        btnSelectFile = findViewById(R.id.btnSelectFile)
        btnDumpPath = findViewById(R.id.btnDumpPath)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        
        tvFileInfo = findViewById(R.id.tvFileInfo)
        tvStatus = findViewById(R.id.tvStatus)
        tvConsoleLog = findViewById(R.id.tvConsoleLog)

        // إعداد وتعبئة القوائم المنسدلة بالخيارات الاحترافية المطابقة للكمبيوتر
        setupSpinners()

        // أحداث الأزرار
        btnSelectFile.setOnClickListener {
            openFilePicker()
        }

        btnDumpPath.setOnClickListener {
            openFolderPicker()
        }

        btnStart.setOnClickListener {
            appendLog("🚀 بدء تنفيذ العملية المحددة...")
            tvStatus.text = "الحالة: جاري تنفيذ العمليات..."
        }

        btnStop.setOnClickListener {
            appendLog("⏹️ تم إيقاف العملية بواسطة المستخدم.")
            tvStatus.text = "الحالة: متوقف"
        }

        appendLog("🟢 تم تهيئة اللودر والواجهة الاحترافية بنجاح.")
    }

    private fun setupSpinners() {
        // قائمة المنافذ الوهمية أو المتاحة
        val devicesAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("لا يوجد جهاز متصل"))
        spinnerDevices.adapter = devicesAdapter

        // معدلات السرعة (Baud Rate)
        val baudAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("115200", "57600", "38400", "9600"))
        spinnerBaudRate.adapter = baudAdapter

        // التكافؤ (Parity)
        val parityAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("None", "Odd", "Even"))
        spinnerParity.adapter = parityAdapter

        // نوع الرام (DDR Type)
        val ddrAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("DDR3 (2G)", "DDR2", "DDR1"))
        spinnerDdrType.adapter = ddrAdapter

        // نوع المعالج (Chip Type)
        val chipAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("1506TV / 1506F", "1507G", "1503G", "VSاصلية"))
        spinnerChipType.adapter = chipAdapter

        // نوع العملية (Operation)
        val opAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("كتابة (Write)", "قراءة (Read / Dump)", "مسح (Erase)"))
        spinnerOperation.adapter = opAdapter

        // نوع التخزين (Storage)
        val storageAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("SPI Flash", "NAND Flash"))
        spinnerStorage.adapter = storageAdapter

        // القسم (Section)
        val sectionAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("الكل (Full Flash)", "Bootloader", "MainCode", "User DB"))
        spinnerSection.adapter = sectionAdapter
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*" // أو تحديد ملفات .bin
        }
        filePickerLauncher.launch(intent)
    }

    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        folderPickerLauncher.launch(intent)
    }

    private fun processSelectedFile(uri: Uri) {
        val fileName = uri.lastPathSegment ?: "rom.bin"
        // حساب حجم الملف عبر ContentResolver
        val cursor = contentResolver.query(uri, null, null, null, null)
        var fileSize = 0L
        cursor?.use {
            val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (it.moveToFirst() && sizeIndex != -1) {
                fileSize = it.getLong(sizeIndex)
            }
        }

        tvFileInfo.text = "الملف المحدد: $fileName\nحجم الملف: $fileSize بايت"
        appendLog("📂 تم اختيار الملف: $fileName (الحجم: $fileSize بايت)")
    }

    private fun appendLog(message: String) {
        val currentText = tvConsoleLog.text.toString()
        tvConsoleLog.text = "$currentText\n$message"
    }
}
