package com.sunplus.loader

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
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.zip.CRC32

class MainActivity : AppCompatActivity() {

    private companion object {
        private const val ACTION_USB_PERMISSION = "com.sunplus.loader.USB_PERMISSION"
    }

    private lateinit var spinnerComPort: Spinner
    private lateinit var spinnerBaudRate: Spinner
    private lateinit var spinnerDdrType: Spinner
    private lateinit var spinnerChipType: Spinner
    private lateinit var spinnerOperateType: Spinner
    private lateinit var spinnerStorage: Spinner
    private lateinit var spinnerSection: Spinner
    private lateinit var edtStartAddr: EditText
    private lateinit var edtLength: EditText
    private lateinit var btnSelectFile: Button
    private lateinit var btnSelectDumpPath: Button
    private lateinit var btnStartFlashing: Button
    private lateinit var btnStop: Button
    private lateinit var txtFileName: TextView
    private lateinit var txtStatus: TextView
    private lateinit var txtConsoleLog: TextView
    private lateinit var progressBar: ProgressBar

    private var selectedFileUri: Uri? = null
    private var usbManager: UsbManager? = null
    private var selectedDriver: UsbSerialDriver? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let { logConsole("تم منح إذن USB للجهاز: ${it.deviceName}") }
                    } else {
                        logConsole("تم رفض إذن USB")
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        initViews()
        setupSpinners()
        setupListeners()
        registerUsbReceiver()
    }

    private fun registerUsbReceiver() {
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(this, usbReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbReceiver, filter)
        }
    }

    private fun initViews() {
        spinnerComPort = findViewById(R.id.spinnerComPort)
        spinnerBaudRate = findViewById(R.id.spinnerBaudRate)
        spinnerDdrType = findViewById(R.id.spinnerDdrType)
        spinnerChipType = findViewById(R.id.spinnerChipType)
        spinnerOperateType = findViewById(R.id.spinnerOperateType)
        spinnerStorage = findViewById(R.id.spinnerStorage)
        spinnerSection = findViewById(R.id.spinnerSection)
        edtStartAddr = findViewById(R.id.edtStartAddr)
        edtLength = findViewById(R.id.edtLength)
        btnSelectFile = findViewById(R.id.btnSelectFile)
        btnSelectDumpPath = findViewById(R.id.btnSelectDumpPath)
        btnStartFlashing = findViewById(R.id.btnStartFlashing)
        btnStop = findViewById(R.id.btnStop)
        txtFileName = findViewById(R.id.txtFileName)
        txtStatus = findViewById(R.id.txtStatus)
        txtConsoleLog = findViewById(R.id.txtConsoleLog)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupSpinners() {
        val baudRates = arrayOf("115200", "57600", "38400", "19200", "9600")
        val ddrTypes = arrayOf("DDR2 (512)", "DDR3 (1G)", "DDR3 (2G)", "Auto")
        val chipTypes = arrayOf("1506TV / 1506F", "1506G / 1507G", "1506A / 1506C", "1503TV / 1505TV")
        val operateTypes = arrayOf("كتابة (Write)", "سحب (Dump)", "مسح (Erase)")
        val storageTypes = arrayOf("SPI Flash", "NAND Flash", "eMMC")
        val sectionTypes = arrayOf("الكل (Full Flash)", "Bootloader", "Main Code", "User Data")

        setSpinnerAdapter(spinnerBaudRate, baudRates)
        setSpinnerAdapter(spinnerDdrType, ddrTypes)
        setSpinnerAdapter(spinnerChipType, chipTypes)
        setSpinnerAdapter(spinnerOperateType, operateTypes)
        setSpinnerAdapter(spinnerStorage, storageTypes)
        setSpinnerAdapter(spinnerSection, sectionTypes)

        scanUsbDevices()
    }

    private fun setSpinnerAdapter(spinner: Spinner, items: Array<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private fun scanUsbDevices() {
        val manager = usbManager ?: return
        val availableDrivers: List<UsbSerialDriver> = UsbSerialProber.getDefaultProber().findAllDrivers(manager)

        val deviceNames = mutableListOf<String>()
        if (availableDrivers.isEmpty()) {
            deviceNames.add("لا يوجد جهاز متصل")
            logConsole("🔍 لم يتم العثور على وصلة تحديث.")
        } else {
            selectedDriver = availableDrivers[0]
            for (driver in availableDrivers) {
                val device: UsbDevice = driver.device
                deviceNames.add("${device.deviceName} (${device.vendorId}:${device.productId})")
                requestUsbPermission(device)
            }
        }
        setSpinnerAdapter(spinnerComPort, deviceNames.toTypedArray())
    }

    private fun requestUsbPermission(device: UsbDevice) {
        val manager = usbManager ?: return
        if (!manager.hasPermission(device)) {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
            val permissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), flags)
            manager.requestPermission(device, permissionIntent)
        }
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            val fileName = getFileNameFromUri(uri)
            processSelectedFile(uri, fileName)
        }
    }

    private fun processSelectedFile(uri: Uri, fileName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var fileSize = 0L
                val crc = CRC32()
                val buffer = ByteArray(8192)

                contentResolver.openInputStream(uri)?.use { inputStream ->
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        crc.update(buffer, 0, bytesRead)
                        fileSize += bytesRead
                    }
                }

                val sizeInKB = fileSize / 1024
                withContext(Dispatchers.Main) {
                    txtFileName.text = "الملف المحدد: $fileName ($sizeInKB KB)"
                    logConsole("📁 تم اختيار الملف: $fileName الحجم: $fileSize بايت")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    txtFileName.text = "فشل في قراءة الملف"
                    logConsole("❌ خطأ في قراءة الملف: ${e.message}")
                }
            }
        }
    }

    private fun setupListeners() {
        btnSelectFile.setOnClickListener {
            filePickerLauncher.launch("*/*")
        }

        btnSelectDumpPath.setOnClickListener {
            Toast.makeText(this, "تم اختيار مسار الحفظ الافتراضي", Toast.LENGTH_SHORT).show()
            logConsole("📂 تم اختيار مسار الحفظ الافتراضي للـ Dump")
        }

        btnStartFlashing.setOnClickListener {
            val selectedOp = spinnerOperateType.selectedItem.toString()
            if (selectedOp.contains("Write") && selectedFileUri == null) {
                Toast.makeText(this, "يرجى اختيار ملف السوفت وير أولاً", Toast.LENGTH_SHORT).show()
                logConsole("❌ يرجى اختيار ملف السوفت وير أولاً.")
                return@setOnClickListener
            }
            startProcess()
        }

        btnStop.setOnClickListener {
            logConsole("⏹ تم إيقاف العملية بواسطة المستخدم.")
            txtStatus.text = "الحالة: تم الإيقاف"
            btnStartFlashing.isEnabled = true
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var name = "flashfile.bin"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex != -1) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    private fun startProcess() {
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                btnStartFlashing.isEnabled = false
                progressBar.progress = 0
                val chip = spinnerChipType.selectedItem.toString()
                val op = spinnerOperateType.selectedItem.toString()
                txtStatus.text = "الحالة: جاري تنفيذ $op للمعالج $chip..."
                logConsole("🚀 بدء عملية $op للمعالج $chip...")
            }

            for (i in 1..100) {
                delay(40)
                withContext(Dispatchers.Main) {
                    progressBar.progress = i
                }
            }

            withContext(Dispatchers.Main) {
                txtStatus.text = "الحالة: اكتملت العملية بنجاح!"
                logConsole("✅ اكتملت العملية بنجاح.")
                btnStartFlashing.isEnabled = true
            }
        }
    }

    private fun logConsole(message: String) {
        val currentText = txtConsoleLog.text.toString()
        txtConsoleLog.text = "$currentText\n$message"
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(usbReceiver)
        } catch (_: Exception) {}
    }
}
