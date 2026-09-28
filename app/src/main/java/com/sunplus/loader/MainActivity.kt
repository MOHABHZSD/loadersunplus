package com.sunplus.loader

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.InputStream
import java.util.zip.CRC32

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

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                processSelectedFile(uri)
            }
        }
    }

    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                appendLog("📁 تم تحديد مسار الحفظ بنجاح")
                tvStatus.text = "الحالة: تم تحديد مسار الحفظ"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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

        setupSpinners()

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
    }

    private fun setupSpinners() {
        spinnerDevices.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("لا يوجد جهاز متصل"))
        spinnerBaudRate.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("115200", "57600", "38400", "9600"))
        spinnerParity.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("None", "Odd", "Even"))
        spinnerDdrType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("DDR3 (2G)", "DDR2", "DDR1", "SDRAM"))
        spinnerChipType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("1506TV / 1506F", "1507G", "1503G", "1512", "1510"))
        spinnerOperation.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("كتابة (Write)", "قراءة (Read / Dump)", "مسح (Erase)"))
        spinnerStorage.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("SPI Flash", "NAND Flash"))
        spinnerSection.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("الكل (Full Flash)", "Bootloader", "MainCode", "User DB"))
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        filePickerLauncher.launch(intent)
    }

    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        folderPickerLauncher.launch(intent)
    }

    private fun processSelectedFile(uri: Uri) {
        val fileName = uri.lastPathSegment ?: "rom.bin"
        var fileSize = 0L
        var crcValue = "0x00000000"

        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                val bytes = stream.readBytes()
                fileSize = bytes.size.toLong()
                
                val crc = CRC32()
                crc.update(bytes)
                crcValue = String.format("0x%08X", crc.value)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val sizeInKB = fileSize / 1024
        tvFileInfo.text = "الملف المحدد: $fileName\nحجم الملف: $fileSize بايت ($sizeInKB KB) | CRC32: $crcValue"
        appendLog("📁 تم اختيار الملف: $fileName")
        appendLog("📊 الحجم: $fileSize بايت (KB $sizeInKB) | CRC32: $crcValue")
    }

    private fun appendLog(message: String) {
        val currentText = tvConsoleLog.text.toString()
        if (currentText.isEmpty()) {
            tvConsoleLog.text = message
        } else {
            tvConsoleLog.text = "$currentText\n$message"
        }
    }
}
