package com.sunplus.loader

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber

class MainActivity : AppCompatActivity() {

    private val ACTION_USB_PERMISSION = "com.sunplus.loader.USB_PERMISSION"
    private lateinit var usbManager: UsbManager
    private var usbSerialPort: UsbSerialPort? = null
    private val availableDevices = mutableListOf<UsbDevice>()

    // عناصر الواجهة
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        // ربط جميع العناصر
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

        // =========================================================
        // حل مشكلة القوائم الفارغة وتثبيت المواصفات الحصرية (4M - 512 - DDR2)
        // =========================================================
        
        // 1. قائمة المعالجات (كما تظهر في صورتك)
        spinnerChipType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("1506TV / 1506F", "1506G / 1507G", "1506T"))
        
        // 2. قائمة الرام (مثبتة حصرياً على DDR2 512)
        spinnerRam.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("DDR2 (512)"))
        
        // 3. السرعة
        spinnerBaudRate.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("115200"))
        
        // 4. العمليات والتخزين
        spinnerOperation.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("سحب (Dump)", "كتابة (Write)", "مسح (Erase)"))
        spinnerStorage.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("SPI Flash"))
        spinnerSection.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("الكل (Full Flash)"))

        // 5. إجبار طول الملف ليكون 4 ميجا (0x400000) حتى لو كان مكتوباً في التصميم 8 ميجا
        etStartAddress.setText("0x000000")
        etFileLength.setText("0x400000")
        
        // =========================================================

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        registerReceiver(usbReceiver, filter)

        // البحث عن وصلة USB عند فتح التطبيق
        scanForUsbDevices()

        btnStartProcess.setOnClickListener {
            connectToDevice()
        }

        btnStopProcess.setOnClickListener {
            disconnectDevice()
        }
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
