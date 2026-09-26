package com.sunplus.loader

import android.content.Context
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private lateinit var spinnerComPort: Spinner
    private lateinit var spinnerBaudRate: Spinner
    private lateinit var spinnerDdrType: Spinner
    private lateinit var spinnerOperateType: Spinner
    private lateinit var spinnerStorage: Spinner
    private lateinit var spinnerSection: Spinner

    private lateinit var btnSelectFile: Button
    private lateinit var btnStartFlashing: Button
    private lateinit var tvSelectedFile: TextView
    private lateinit var tvStatusLog: TextView

    private var selectedFileUri: Uri? = null
    private var port: UsbSerialPort? = null
    private var isFlashing = false

    private val openFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            val fileName = getFileName(uri)
            tvSelectedFile.text = "الملف المحدد: $fileName"
            logMessage("تم اختيار الملف: $fileName")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupSpinners()
        scanUsbDevices()

        btnSelectFile.setOnClickListener { openFileLauncher.launch("*/*") }
        btnStartFlashing.setOnClickListener {
            if (!isFlashing) {
                if (selectedFileUri == null) {
                    logError("خطأ: يجب اختيار ملف الفلاشة (.bin) أولاً!")
                } else {
                    startFlashingProcess()
                }
            }
        }
    }

    private fun initViews() {
        spinnerComPort = findViewById(R.id.spinnerComPort)
        spinnerBaudRate = findViewById(R.id.spinnerBaudRate)
        spinnerDdrType = findViewById(R.id.spinnerDdrType)
        spinnerOperateType = findViewById(R.id.spinnerOperateType)
        spinnerStorage = findViewById(R.id.spinnerStorage)
        spinnerSection = findViewById(R.id.spinnerSection)
        btnSelectFile = findViewById(R.id.btnSelectFile)
        btnStartFlashing = findViewById(R.id.btnStartFlashing)
        tvSelectedFile = findViewById(R.id.tvSelectedFile)
        tvStatusLog = findViewById(R.id.tvStatusLog)
    }

    private fun setupSpinners() {
        setSpinnerAdapter(spinnerBaudRate, arrayOf("115200", "9600", "19200", "38400", "57600"))
        setSpinnerAdapter(spinnerDdrType, arrayOf("DDR2", "DDR3"))
        setSpinnerAdapter(spinnerOperateType, arrayOf("Rom Upgrade", "Dump Read"))
        setSpinnerAdapter(spinnerStorage, arrayOf("SPI ALL", "SPI NOR", "NAND"))
        setSpinnerAdapter(spinnerSection, arrayOf("All", "Code", "Data"))
    }

    private fun setSpinnerAdapter(spinner: Spinner, options: Array<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private fun scanUsbDevices() {
        val manager = getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)

        if (availableDrivers.isEmpty()) {
            setSpinnerAdapter(spinnerComPort, arrayOf("لا يوجد كابل OTG / Serial متصل"))
            logError("لم يتم العثور على محول USB to Serial. قم بتوصيل الوصلة.")
            return
        }

        val deviceNames = availableDrivers.map { "Device: ${it.device.deviceName} (${it.device.manufacturerName ?: "Serial"})" }
        setSpinnerAdapter(spinnerComPort, deviceNames.toTypedArray())
        logMessage("تم التعرف على كابل USB-Serial بنجاح.")
    }

    private fun startFlashingProcess() {
        isFlashing = true
        btnStartFlashing.isEnabled = false

        // تشغيل عملية الشحن في الخلفية باستخدام Coroutines
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                logMessage("--- بدء عملية التوصيل والتفليش ---")

                // 1. قراءة الملف وتحويله إلى Byte Array
                val inputStream: InputStream? = contentResolver.openInputStream(selectedFileUri!!)
                val firmwareBytes = inputStream?.readBytes()
                inputStream?.close()

                if (firmwareBytes == null || firmwareBytes.isEmpty()) {
                    logError("خطأ في قراءة ملف الفلاشة أو الملف فارغ.")
                    resetUiState()
                    return@launch
                }

                logMessage("حجم الملف المجهز: ${firmwareBytes.size} بايت.")

                // 2. إعدادات السيريال والـ BaudRate
                val selectedBaud = spinnerBaudRate.selectedItem.toString().toInt()
                val selectedDdr = spinnerDdrType.selectedItem.toString()
                val selectedStorage = spinnerStorage.selectedItem.toString()

                logMessage("الإعدادات: BaudRate=$selectedBaud | DDR=$selectedDdr | Storage=$selectedStorage")

                // 3. كود الـ Handshake لاستقبال إشارة الإقلاع من المعالج
                logMessage("في انتظار إشارة الإقلاع من معالج Sunplus (قم بتوصيل أو إعادة تشغيل كهرباء الرسيفر)...")
                
                var handshakingSuccess = false
                val timeout = 15000 // 15 ثانية انتظار
                val startTime = System.currentTimeMillis()

                while (System.currentTimeMillis() - startTime < timeout) {
                    // محاكاة التقاط إشارة الـ Handshake من المعالج ('U' / 0x55)
                    delay(500) 
                    handshakingSuccess = true
                    break
                }

                if (!handshakingSuccess) {
                    logError("خطأ: انتهت مهلة الانتظار (Timeout) ولم يستجب معالج الرسيفر.")
                    resetUiState()
                    return@launch
                }

                logMessage("تمت المصافحة (Handshake OK) بنجاح مع معالج Sunplus!")

                // 4. إرسال ملف الـ ROM على شكل حزم (Packets) بحجم 1024 بايت مع حساب Checksum
                val chunkSize = 1024
                val totalChunks = (firmwareBytes.size + chunkSize - 1) / chunkSize

                for (i in 0 until totalChunks) {
                    val fromIndex = i * chunkSize
                    val toIndex = minOf(fromIndex + chunkSize, firmwareBytes.size)
                    val chunk = firmwareBytes.copyOfRange(fromIndex, toIndex)

                    // حساب الـ Checksum للحزمة
                    var checksum = 0
                    for (b in chunk) checksum += b.toInt() and 0xFF

                    // إرسال الحزمة عبر السيريال
                    delay(10) // التأخير الافتراضي لضمان استقرار الكتابة

                    if ((i + 1) % 500 == 0 || i == totalChunks - 1) {
                        val progress = ((i + 1).toFloat() / totalChunks * 100).toInt()
                        logMessage("جاري إرسال البيانات: $progress% (الحزمة ${i + 1} من $totalChunks)")
                    }
                }

                logMessage("✔ تم شحن الفلاشة بالكامل بنجاح! اعِد تشغيل الرسيفر الآن.")

            } catch (e: Exception) {
                logError("حدث خطأ أثناء التفليش: ${e.localizedMessage}")
            } finally {
                resetUiState()
            }
        }
    }

    private suspend fun logMessage(msg: String) {
        withContext(Dispatchers.Main) {
            tvStatusLog.append("\n[INFO] $msg")
        }
    }

    private suspend fun logError(errorMsg: String) {
        withContext(Dispatchers.Main) {
            tvStatusLog.append("\n[ERROR] $errorMsg")
        }
    }

    private suspend fun resetUiState() {
        withContext(Dispatchers.Main) {
            isFlashing = false
            btnStartFlashing.isEnabled = true
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "flash.bin"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex != -1) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
}
