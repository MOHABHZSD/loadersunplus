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
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupSpinners()
        setupListeners()
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

        scanUsbDevices()
    }

    private fun setSpinnerAdapter(spinner: Spinner, items: Array<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private fun scanUsbDevices() {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

        val deviceNames = mutableListOf<String>()
        if (availableDrivers.isEmpty()) {
            deviceNames.add("لا يوجد جهاز USB متصل")
        } else {
            for (driver in availableDrivers) {
                val device: UsbDevice = driver.device
                deviceNames.add("${device.deviceName} (${device.vendorId}:${device.productId})")
            }
        }
        setSpinnerAdapter(spinnerComPort, deviceNames.toTypedArray())
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            val fileName = getFileNameFromUri(uri)
            txtFileName.text = "الملف المحدد: $fileName"
        }
    }

    private fun setupListeners() {
        btnSelectFile.setOnClickListener {
            filePickerLauncher.launch("*/*")
        }

        btnStartFlashing.setOnClickListener {
            if (selectedFileUri == null) {
                Toast.makeText(this, "يرجى اختيار ملف السوفت وير أولاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startProcess()
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var name = "firmware.bin"
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
                logStatus("بدء العملية...")
            }

            for (i in 1..100) {
                delay(50)
                withContext(Dispatchers.Main) {
                    progressBar.progress = i
                }
            }

            withContext(Dispatchers.Main) {
                logStatus("اكتملت العملية بنجاح!")
                btnStartFlashing.isEnabled = true
            }
        }
    }

    private fun logStatus(message: String) {
        val currentText = txtStatus.text.toString()
        txtStatus.text = "$currentText\n$message"
    }
}