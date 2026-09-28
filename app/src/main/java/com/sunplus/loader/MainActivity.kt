package com.sunplus.loader

import android.os.Bundle
import android.widget.*
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

        btnStart.setOnClickListener {
            appendLog("🟢 تم النقر على بدء العملية...")
        }

        btnStop.setOnClickListener {
            appendLog("🔴 تم إيقاف العملية.")
        }

        appendLog("🟢 تم تهيئة الواجهة الاحترافية بنجاح.")
    }

    private fun appendLog(message: String) {
        val currentText = tvConsoleLog.text.toString()
        tvConsoleLog.text = "$currentText\n$message"
    }
}
