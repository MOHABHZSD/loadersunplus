package com.sunplus.loader 

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    // المتغيرات
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

        // ربط جميع العناصر بالـ XML
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

        // إعداد خيارات نوع المعالج
        val chipOptions = arrayOf("1506TV / 1506F", "1506G / 1507G", "1506T")
        val chipAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, chipOptions)
        spinnerChipType.adapter = chipAdapter

        // وظيفة زر تحديد مسار الحفظ (Dump)
        btnSaveDumpPath.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_TITLE, "rom.bin")
            }
            appendLog("جاري تحديد مسار الحفظ...")
            // في مشروعك الأصلي ستقوم باستخدام startActivityForResult هنا
        }

        // وظيفة زر الإلغاء
        btnStopProcess.setOnClickListener {
            appendLog("⚠️ تم الإلغاء بواسطة المستخدم.")
            tvStatus.text = "الحالة: متوقف"
            progressBar.progress = 0
        }

        appendLog("التطبيق جاهز للاتصال...")
    }

    // دالة مساعدة لكتابة السجل
    private fun appendLog(message: String) {
        runOnUiThread {
            etConsoleLog.append("$message\n")
            val scrollAmount = etConsoleLog.layout?.let { 
                it.getLineTop(etConsoleLog.lineCount) - etConsoleLog.height 
            } ?: 0
            if (scrollAmount > 0) {
                etConsoleLog.scrollTo(0, scrollAmount)
            }
        }
    }
}
