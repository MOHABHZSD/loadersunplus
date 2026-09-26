package com.sunplus.loader

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber

class MainActivity : AppCompatActivity() {

    private val ACTION_USB_PERMISSION = "com.sunplus.loader.USB_PERMISSION"
    private lateinit var usbManager: UsbManager
    private var usbSerialPort: UsbSerialPort? = null
    private val availableDevices = mutableListOf<UsbDevice>()

    private var selectedFileUri: Uri? = null
    private var dumpFileUri: Uri? = null

    private lateinit var spinnerUsb: Spinner
    private lateinit var spinnerBaudRate: Spinner
    private lateinit var spinnerRam: Spinner
    private lateinit var spinnerChipType: Spinner
    private lateinit var spinnerOperation: Spinner
    private lateinit var spinnerStorage: Spinner
    private lateinit var spinnerSection: Spinner
    
    private lateinit var etStartAddress: EditText
    private lateinit var etFileLength: EditText
    private lateinit var btnSelectFile: Button
    private lateinit var btnSaveDumpPath: Button
    private lateinit var tvSelectedFile: TextView
    private lateinit var btnStartProcess: Button
    private lateinit var btnStopProcess: Button
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var etConsoleLog: EditText

    private val selectFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedFileUri = uri
                val fileName = getFileName(uri)
                tvSelectedFile.text = "الملف المحدد: $fileName"
                appendLog("📁 تم اختيار ملف السوفت وير: $fileName")
            }
        }
    }

    private val createFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                dumpFileUri = uri
                val fileName = getFileName(uri)
                tvSelectedFile.text = "مسار الحفظ: $fileName"
                appendLog("💾 تم تحديد مسار الحفظ (Dump): $fileName")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. تفعيل الوضع الداكن إجبارياً
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        
        // 2. منع الشاشة من الانطفاء ومنع النظام من تقييد التطبيق أثناء التفليش
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        spinnerUsb = findViewById(R.id.spinnerUsb)
        spinnerBaudRate = findViewById(R.id.spinnerBaudRate)
        spinnerRam = findViewById(R.id.spinnerRam)
        spinnerChipType = findViewById(R.id.spinnerChipType)
        spinnerOperation = findViewById(R.id.spinnerOperation)
        spinnerStorage = findViewById(R.id.spinnerStorage)
        spinnerSection = findViewById(R.id.spinnerSection)
        
        etStartAddress = findViewById(R.id.etStartAddress)
        etFileLength = findViewById(R.id.etFileLength)
        btnSelectFile = findViewById(R.id.btnSelectFile)
        btnSaveDumpPath = findViewById(R.id.btnSaveDumpPath)
        tvSelectedFile = findViewById(R.id.tvSelectedFile)
        btnStartProcess = findViewById(R.id.btnStartProcess)
        btnStopProcess = findViewById(R.id.btnStopProcess)
        tvStatus = findViewById(R.id.tvStatus)
        progressBar = findViewById(R.id.progressBar)
        etConsoleLog = findViewById(R.id.etConsoleLog)

        spinnerChipType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("1506TV / 1506F", "1506G / 1507G", "1506T"))
        spinnerRam.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("DDR2 (512)"))
        spinnerBaudRate.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("115200"))
        spinnerOperation.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("سحب (Dump)", "كتابة (Write)", "مسح (Erase)"))
        spinnerStorage.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("SPI Flash"))
        spinnerSection.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("الكل (Full Flash)"))

        etStartAddress.setText("0x000000")
        etFileLength.setText("0x400000")

        btnSelectFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            selectFileLauncher.launch(intent)
        }

        btnSaveDumpPath.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_TITLE, "dump_4M.bin")
            }
            createFileLauncher.launch(intent)
        }

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        registerReceiver(usbReceiver, filter)

        scanForUsbDevices()

        btnStartProcess.setOnClickListener {
            connectToDevice()
        }

        btnStopProcess.setOnClickListener {
            disconnectDevice()
        }
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = it.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "ملف غير معروف"
    }

    private fun scanForUsbDevices() {
        availableDevices.clear()
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        
        if (availableDrivers.isEmpty()) {
            spinnerUsb.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("لا يوجد جهاز متصل"))
            appendLog("🔍 لم يتم العثور على وصلة تحديث.")
            return
        }

        val deviceNames = mutableListOf<String>()
        for (driver in availableDrivers) {
            val device = driver.device
            availableDevices.add(device)
            deviceNames.add("جهاز متصل (VID:${device.vendorId})")
        }

        spinnerUsb.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, deviceNames)
        appendLog("✅ تم اكتشاف وصلة جاهزة للاتصال.")
    }

    private fun connectToDevice() {
        if (availableDevices.isEmpty()) {
            appendLog("❌ يرجى توصيل وصلة التفليش أولاً.")
            return
        }

        val device = availableDevices[spinnerUsb.selectedItemPosition]
        
        if (usbManager.hasPermission(device)) {
            openSerialPort(device)
        } else {
            appendLog("⚠️ جاري طلب صلاحية الاتصال...")
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val permissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), flags)
            usbManager.requestPermission(device, permissionIntent)
        }
    }

    private fun openSerialPort(device: UsbDevice) {
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
        if (driver == null) {
            appendLog("❌ لا يوجد تعريف متوافق مع هذه الوصلة.")
            return
        }

        val connection = usbManager.openDevice(device)
        if (connection == null) {
            appendLog("❌ فشل فتح المنفذ.")
            return
        }

        usbSerialPort = driver.ports[0]
        try {
            usbSerialPort?.open(connection)
            
            val baudRate = spinnerBaudRate.selectedItem.toString().toInt()
            usbSerialPort?.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            
            appendLog("🚀 تم فتح المنفذ بنجاح بسرعة $baudRate")
            tvStatus.text = "الحالة: متصل"
            
            appendLog("⏳ بانتظار استجابة معالج صن بلص (يرجى تشغيل الرسيفر الآن)...")
            
        } catch (e: Exception) {
            appendLog("❌ خطأ أثناء فتح المنفذ: ${e.message}")
            disconnectDevice()
        }
    }

    private fun disconnectDevice() {
        try {
            usbSerialPort?.close()
            usbSerialPort = null
            appendLog("🔌 تم إغلاق الاتصال.")
            tvStatus.text = "الحالة: متوقف"
            progressBar.progress = 0
        } catch (e: Exception) {
            appendLog("خطأ أثناء الإغلاق: ${e.message}")
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let { openSerialPort(it) }
                    } else {
                        appendLog("❌ تم رفض الصلاحية من قبل المستخدم.")
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
        disconnectDevice()
    }

    private fun appendLog(message: String) {
        runOnUiThread {
            etConsoleLog.append("$message\n")
            val scrollAmount = etConsoleLog.layout?.let { 
                it.getLineTop(etConsoleLog.lineCount) - etConsoleLog.height 
            } ?: 0
            if (scrollAmount > 0) etConsoleLog.scrollTo(0, scrollAmount)
        }
    }
}
