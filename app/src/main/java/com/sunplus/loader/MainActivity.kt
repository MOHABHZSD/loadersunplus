package com.example.sunplusloader

import android.content.Context
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.InputStream
import java.util.zip.CRC32

class MainActivity : AppCompatActivity() {

    private lateinit var uartManager: SunplusUartManager
    private var isCancelled = false
    private var selectedFileBytes: ByteArray? = null
    private var dumpSaveUri: Uri? = null
    private var availableDrivers: List<UsbSerialDriver> = emptyList()

    // عناصر الواجهة المطبقة في التصميم
    private lateinit var spinnerUsbPort: Spinner
    private lateinit var spinnerBaudRate: Spinner
    private lateinit var spinnerParity: Spinner
    private lateinit var spinnerChipType: Spinner
    private lateinit var spinnerDdrType: Spinner
    private lateinit var spinnerOpType: Spinner
    private lateinit var edtStartAddr: EditText
    private lateinit var edtLength: EditText
    private lateinit var btnSelectFile: Button
    private lateinit var btnDumpPath: Button
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var txtFileInfo: TextView
    private lateinit var txtStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var txtConsoleLog: TextView

    // منتقي ملف السوفت وير للـ WRITE
    private val selectFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            contentResolver.openInputStream(it)?.use { stream ->
                selectedFileBytes = stream.readBytes()
                val size = selectedFileBytes?.size ?: 0
                val crc = calculateCRC32(selectedFileBytes!!)
                txtFileInfo.text = "الملف المحدد: rom.bin\nحجم الملف: $size بايت (${size / 1024} KB) | CRC32: 0x${String.format("%08X", crc)}"
                appendLog("📂 تم اختيار الملف: rom.bin")
                appendLog("📊 الحجم: $size بايت | CRC32: 0x${String.format("%08X", crc)}")
            }
        }
    }

    // تحديد مسار حفظ ملف الـ DUMP
    private val createDumpFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        uri?.let {
            dumpSaveUri = it
            appendLog("📁 تم تحديد مسار حفظ النسخة الاحتياطية.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        uartManager = SunplusUartManager(this)

        scanUsbPorts()

        btnSelectFile.setOnClickListener { selectFileLauncher.launch("*/*") }
        btnDumpPath.setOnClickListener { createDumpFileLauncher.launch("dump_rom.bin") }
        btnStart.setOnClickListener { startOperation() }
        btnStop.setOnClickListener { isCancelled = true }
    }

    private fun initViews() {
        spinnerUsbPort = findViewById(R.id.spinnerUsbPort)
        spinnerBaudRate = findViewById(R.id.spinnerBaudRate)
        spinnerParity = findViewById(R.id.spinnerParity)
        spinnerChipType = findViewById(R.id.spinnerChipType)
        spinnerDdrType = findViewById(R.id.spinnerDdrType)
        spinnerOpType = findViewById(R.id.spinnerOpType)
        edtStartAddr = findViewById(R.id.edtStartAddr)
        edtLength = findViewById(R.id.edtLength)
        btnSelectFile = findViewById(R.id.btnSelectFile)
        btnDumpPath = findViewById(R.id.btnDumpPath)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        txtFileInfo = findViewById(R.id.txtFileInfo)
        txtStatus = findViewById(R.id.txtStatus)
        progressBar = findViewById(R.id.progressBar)
        txtConsoleLog = findViewById(R.id.txtConsoleLog)
    }

    // اكتشاف منافذ الـ USB الموصولة
    private fun scanUsbPorts() {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

        val portNames = mutableListOf<String>()
        if (availableDrivers.isEmpty()) {
            portNames.add("لا يوجد جهاز متصل")
            appendLog("🔍 لم يتم العثور على وصلة تحديث.")
        } else {
            availableDrivers.forEachIndexed { index, driver ->
                portNames.add("USB Serial Device ${index + 1} (${driver.device.deviceName})")
            }
            appendLog("✅ تم العثور على وصلة USB Serial.")
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, portNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerUsbPort.adapter = adapter
    }

    // التنفيذ الحقيقي بناءً على العملية المختارة من الواجهة
    private fun startOperation() {
        if (availableDrivers.isEmpty()) {
            appendLog("❌ لا يوجد جهاز USB متصل بالهاتف!")
            return
        }

        isCancelled = false
        val selectedDriver = availableDrivers[spinnerUsbPort.selectedItemPosition]
        val baudRate = spinnerBaudRate.selectedItem.toString().toIntOrDefault(115200)
        val parity = if (spinnerParity.selectedItem.toString().contains("Even")) UsbSerialPort.PARITY_EVEN else UsbSerialPort.PARITY_NONE
        val selectedOp = spinnerOpType.selectedItem.toString()

        if (!uartManager.connectUsb(selectedDriver, baudRate, parity)) {
            appendLog("❌ فشل فتح منفذ الـ USB.")
            return
        }

        lifecycleScope.launch(Dispatchers.Main) {
            btnStart.isEnabled = false
            btnStop.isEnabled = true

            // مرحلة التزامن الميداني
            if (uartManager.performHandshake { appendLog(it) }) {

                val ddrType = spinnerDdrType.selectedItem.toString()
                val payloadFileName = if (ddrType.contains("DDR3")) "sunplus_1506tv_ddr3.bin" else "sunplus_1506tv_ddr2.bin"
                val dramPayload = loadAssetsFile(payloadFileName)

                if (dramPayload != null && uartManager.initializeDram(dramPayload) { appendLog(it) }) {

                    val startAddr = edtStartAddr.text.toString().removePrefix("0x").toLongOrDefault(16, 0L)
                    val length = edtLength.text.toString().removePrefix("0x").toLongOrDefault(16, 0x400000L)

                    when {
                        // 1. عملية الكتابة (WRITE)
                        selectedOp.contains("كتابة") || selectedOp.contains("Write") -> {
                            if (selectedFileBytes != null) {
                                uartManager.writeFlash(
                                    fileData = selectedFileBytes!!,
                                    startAddress = startAddr,
                                    onProgress = { updateProgress(it) },
                                    onLog = { appendLog(it) },
                                    isCancelled = { isCancelled }
                                )
                            } else {
                                appendLog("⚠️ يرجى اختيار ملف السوفت وير أولاً!")
                            }
                        }

                        // 2. عملية السحب (DUMP)
                        selectedOp.contains("سحب") || selectedOp.contains("Dump") -> {
                            if (dumpSaveUri != null) {
                                contentResolver.openOutputStream(dumpSaveUri!!)?.use { outputStream ->
                                    uartManager.dumpFlash(
                                        outputStream = outputStream,
                                        length = length,
                                        onProgress = { updateProgress(it) },
                                        onLog = { appendLog(it) },
                                        isCancelled = { isCancelled }
                                    )
                                }
                            } else {
                                appendLog("⚠️ يرجى تحديد مسار الحفظ (DUMP PATH) أولاً!")
                            }
                        }

                        // 3. عملية المسح (ERASE)
                        selectedOp.contains("مسح") || selectedOp.contains("Erase") -> {
                            uartManager.eraseFlash(
                                startAddress = startAddr,
                                length = length,
                                onLog = { appendLog(it) }
                            )
                        }
                    }
                }
            }

            btnStart.isEnabled = true
            btnStop.isEnabled = false
            uartManager.disconnect()
        }
    }

    private fun calculateCRC32(data: ByteArray): Long {
        val crc = CRC32()
        crc.update(data)
        return crc.value
    }

    private fun loadAssetsFile(fileName: String): ByteArray? {
        return try {
            val inputStream: InputStream = assets.open(fileName)
            val bytes = inputStream.readBytes()
            inputStream.close()
            bytes
        } catch (e: Exception) {
            appendLog("⚠️ تعذر تحميل ملف التهيئة $fileName، سيتم تخطي مرحلة الحقن.")
            ByteArray(0)
        }
    }

    private fun updateProgress(percent: Int) {
        progressBar.progress = percent
        txtStatus.text = "الحالة: جاري التنفيذ... $percent%"
    }

    private fun appendLog(message: String) {
        runOnUiThread {
            txtConsoleLog.append("\n$message")
        }
    }

    private fun String.toIntOrDefault(default: Int): Int = toIntOrNull() ?: default
    private fun String.toLongOrDefault(radix: Int, default: Long): Long = toLongOrNull(radix) ?: default
}
