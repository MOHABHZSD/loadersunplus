package com.sunplus.loader

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var spinnerComPort: Spinner
    private lateinit var spinnerBaudRate: Spinner
    private lateinit var spinnerDdrType: Spinner
    private lateinit var spinnerOperateType: Spinner
    private lateinit var spinnerStorage: Spinner
    private lateinit var spinnerSection: Spinner

    private lateinit var btnSelectFile: Button
    private lateinit var btnStartFlashing: Button
    private lateinit var txtFileName: TextView
    private lateinit var txtStatus: TextView
    private lateinit var progressBar: ProgressBar

    private var selectedFileUri: Uri? = null
    private var usbSerialPort: UsbSerialPort? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedFileUri = it
            val fileName = getFileName(it)
            txtFileName.text = "الملف المحدد: $fileName"
            logStatus("تم اختيار الملف: $fileName")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupSpinners()
        refreshUsbDevices()

        btnSelectFile.setOnClickListener {
            filePickerLauncher.launch("*/*")
        }

        btnStartFlashing.setOnClickListener {
            startProcess()
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
        txtFileName = findViewById(R.id.txtFileName)
        txtStatus = findViewById(R.id.txtStatus)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupSpinners() {
        val baudRates = arrayOf("115200", "57600", "38400", "19200", "9600")
        val ddrTypes = arrayOf("DDR2", "DDR3", "Auto")
        val operateTypes = arrayOf("تحديث (Flash)", "قراءة (Dump)", "مسح (Erase)")
        val storageTypes = arrayOf("SPI Flash", "NAND Flash", "eMMC")
        val sectionTypes = arrayOf("الكل (Full Flash)", "Bootloader", "Main Code", "User Data")

        setSpinnerAdapter(spinnerBaudRate, baudRates)
        setSpinnerAdapter(spinnerDdrType, ddrTypes)
        setSpinnerAdapter(spinnerOperateType, operateTypes)
        setSpinnerAdapter(spinnerStorage, storageTypes)
        setSpinnerAdapter(spinnerSection, sectionTypes)
    }

    private fun setSpinnerAdapter(spinner: Spinner, items: Array<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private fun refreshUsbDevices() {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

        val deviceList = mutableListOf<String>()
        if (availableDrivers.isEmpty()) {
            deviceList.add("لا يوجد جهاز USB Serial متصل")
        } else {
            for (driver in availableDrivers) {
                val device: UsbDevice = driver.device
                deviceList.add("${device.productName ?: "USB Serial"} (${device.deviceId})")
            }
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, deviceList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerComPort.adapter = adapter
    }

    private fun startProcess() {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

        if (availableDrivers.isEmpty()) {
            Toast.makeText(this, "يرجى توصيل وصلة RS232 USB أولاً", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedFileUri == null && spinnerOperateType.selectedItemPosition == 0) {
            Toast.makeText(this, "يرجى اختيار ملف السوفت وير أولاً", Toast.LENGTH_SHORT).show()
            return
        }

        val driver: UsbSerialDriver = availableDrivers[0]
        val connection = usbManager.openDevice(driver.device)
        if (connection == null) {
            Toast.makeText(this, "تعذر الحصول على إذن الوصول للـ USB", Toast.LENGTH_SHORT).show()
            return
        }

        val port = driver.ports[0]
        try {
            port.open(connection)
            val baudRate = spinnerBaudRate.selectedItem.toString().toIntOrNull() ?: 115200
            port.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            usbSerialPort = port
            logStatus("تم فتح المنفذ بنجاح بكتلة $baudRate")

            performTransfer(port)

        } catch (e: Exception) {
            logStatus("خطأ بالاتصال: ${e.message}")
            try { port.close() } catch (_: Exception) {}
        }
    }

    private fun performTransfer(port: UsbSerialPort) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    progressBar.progress = 0
                    logStatus("جاري نقل البيانات... قم بتوصيل الرسيفر بالكهرباء الآن")
                }

                val uri = selectedFileUri
                if (uri != null) {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val buffer = ByteArray(1024)
                        var bytesRead: Int
                        var totalRead = 0
                        val fileSize = inputStream.available()

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            port.write(buffer.copyOf(bytesRead), 2000)
                            totalRead += bytesRead

                            val progress = if (fileSize > 0) (totalRead * 100 / fileSize) else 0
                            withContext(Dispatchers.Main) {
                                progressBar.progress = progress
                                txtStatus.text = "جاري الإرسال: $progress%"
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    progressBar.progress = 100
                    logStatus("اكتمل النقل بنجاح!")
                    Toast.makeText(this@MainActivity, "تمت العملية بنجاح!", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    logStatus("حدث خطأ أثناء النقل: ${e.message}")
                }
            } finally {
                try { port.close() } catch (_: Exception) {}
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex)
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
        return result ?: "firmware.bin"
    }

    private fun logStatus(message: String) {
        val currentText = txtStatus.text.toString()
        txtStatus.text = "$currentText\n$message"
    }
}
