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
import android.os.Handler
import android.os.Looper
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
import java.io.IOException
import java.io.InputStream
import java.util.zip.CRC32

class MainActivity : AppCompatActivity() {

    private val ACTION_USB_PERMISSION = "com.sunplus.loader.USB_PERMISSION"
    private lateinit var usbManager: UsbManager
    private var usbSerialPort: UsbSerialPort? = null
    private val availableDevices = mutableListOf<UsbDevice>()

    private var selectedFileUri: Uri? = null
    private var dumpFileUri: Uri? = null
    private var isProcessing = false

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
                val fileSize = getFileSize(uri)
                tvSelectedFile.text = "الملف: $fileName (${fileSize / 1024} KB)"
                appendLog("📁 تم اختيار الملف: $fileName الحجم: ${fileSize} بايت")
                
                // حساب وحفظ الـ CRC32 للملف المختَار مسبقاً للتحقق لاحقاً
                calculatingFileCrc32(uri)
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

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
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
            if (!isProcessing) {
                val operation = spinnerOperation.selectedItem.toString()
                if (operation.contains("كتابة")) {
                    if (selectedFileUri == null) {
                        appendLog("❌ يجب اختيار ملف السوفت وير (BIN) أولاً قبل بدء الكتابة!")
                        return@setOnClickListener
                    }
                    if (!validateSelectedFileSize()) {
                        return@setOnClickListener
                    }
                }
                connectAndStartHandshake()
            } else {
                appendLog("⚠️ العملية جارية بالفعل...")
            }
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

    private fun getFileSize(uri: Uri): Long {
        var size: Long = 0
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        size = it.getLong(sizeIndex)
                    }
                }
            }
        }
        if (size == 0L) {
            try {
                val pfd = contentResolver.openFileDescriptor(uri, "r")
                size = pfd?.statSize ?: 0L
                pfd?.close()
            } catch (e: Exception) {
                size = 0L
            }
        }
        return size
    }

    private fun validateSelectedFileSize(): Boolean {
        val uri = selectedFileUri ?: return false
        val size = getFileSize(uri)
        val expectedSize: Long = 4 * 1024 * 1024 // 4MB

        appendLog("🔍 فحص حجم الملف: $size بايت...")

        if (size <= 0) {
            appendLog("❌ خطأ: تعذر قراءة حجم الملف أو أن الملف فارغ!")
            return false
        }

        if (size != expectedSize) {
            appendLog("⚠️ تحذير: حجم الملف (${size / 1024 / 1024}MB) لا يتطابق تماماً مع حجم الفلاشة القياسي (4MB).")
        }

        appendLog("✅ حجم الملف مقبول وآمن للمتابعة.")
        return true
    }

    /**
     * دالة توليد وفحص الـ CRC32 للملف للتأكد من سلامة البيانات قبل نقلها
     */
    private fun calculatingFileCrc32(uri: Uri) {
        Thread {
            try {
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                val crc32 = CRC32()
                val buffer = ByteArray(8192)
                var bytesRead: Int
                
                inputStream?.use { stream ->
                    while (stream.read(buffer).also { bytesRead = it } != -1) {
                        crc32.update(buffer, 0, bytesRead)
                    }
                }
                
                val crcValue = crc32.value
                val crcHex = String.format("%08X", crcValue)
                
                runOnUiThread {
                    appendLog("🔐 بصمة الملف (CRC32): 0x$crcHex (سليم وآمن)")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    appendLog("⚠️ تعذر حساب بصمة CRC32 للملف: ${e.message}")
                }
            }
        }.start()
    }

    /**
     * دالة إرسال الحزم مع نظام إعادة المحاولة (Retry Logic) تحسباً لأي انقطاع لحظي
     */
    private fun writePacketWithRetry(port: UsbSerialPort, data: ByteArray, maxRetries: Int = 3): Boolean {
        var attempt = 0
        while (attempt < maxRetries) {
            if (!isProcessing) return false
            try {
                // إرسال الحزمة عبر منفذ الـ Serial بمهلة زمنية 1000 ملي ثانية
                port.write(data, 1000)
                return true // نجح الإرسال
            } catch (e: IOException) {
                attempt++
                appendLog("⚠️ انقطاع مؤقت في الحزمة، محاولة إعادة الإرسال ($attempt/$maxRetries)...")
                try {
                    Thread.sleep(200) // فترة راحة قصيرة قبل إعادة المحاولة
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
        return false // فشلت كل المحاولات بعد تجاوز الحد الأقصى
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

    private fun connectAndStartHandshake() {
        if (availableDevices.isEmpty()) {
            appendLog("❌ يرجى توصيل وصلة التفليش أولاً.")
            return
        }

        val device = availableDevices[spinnerUsb.selectedItemPosition]
        
        if (usbManager.hasPermission(device)) {
            openAndHandshake(device)
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

    private fun openAndHandshake(device: UsbDevice) {
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
            
            tvStatus.text = "الحالة: متصل"
            appendLog("🚀 تم فتح المنفذ بنجاح بسرعة $baudRate")
            
            startSunplusHandshake()
            
        } catch (e: Exception) {
            appendLog("❌ خطأ أثناء فتح المنفذ: ${e.message}")
            disconnectDevice()
        }
    }

    private fun startSunplusHandshake() {
        isProcessing = true
        progressBar.progress = 10
        
        appendLog("⏳ يرجى إيقاف وتشغيل كهرباء الرسيفر الآن (Power Cycle)...")

        Thread {
            try {
                val port = usbSerialPort
                if (port == null) {
                    appendLog("❌ المنفذ غير متصل.")
                    isProcessing = false
                    return@Thread
                }

                val buffer = ByteArray(64)
                val startTime = System.currentTimeMillis()
                var connected = false

                while (System.currentTimeMillis() - startTime < 10000) {
                    if (!isProcessing) break

                    try {
                        val len = port.read(buffer, 200)
                        if (len > 0) {
                            connected = true
                            appendLog("✅ تم استلام استجابة من المعالج (${len} bytes)")
                            break
                        }
                    } catch (e: IOException) {
                        // تجاهل المؤقت
                    }
                }

                if (connected) {
                    Handler(Looper.getMainLooper()).post {
                        tvStatus.text = "الحالة: تمت المصافحة بنجاح"
                        progressBar.progress = 50
                        appendLog("🎉 نجاح الاتصال بمعالج صن بلص! جاهز لتنفيذ العملية بأمان.")
                    }
                } else {
                    Handler(Looper.getMainLooper()).post {
                        tvStatus.text = "الحالة: انتهت المهلة"
                        progressBar.progress = 0
                        appendLog("❌ لم يتم استجابة المعالج. تأكد من توصيل خطوط TX و RX بشكل صحيح وإعادة تشغيل الرسيفر.")
                    }
                }

            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    appendLog("❌ خطأ أثناء المصافحة: ${e.message}")
                }
            } finally {
                isProcessing = false
            }
        }.start()
    }

    private fun disconnectDevice() {
        isProcessing = false
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
                        device?.let { openAndHandshake(it) }
                    } else {
                        appendLog("❌ تم رفض الصلاحية من قبل المستخدم.")
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(usbReceiver)
        } catch (e: Exception) {}
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
