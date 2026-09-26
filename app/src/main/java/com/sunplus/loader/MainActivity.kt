package com.sunplus.loader

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var btnSelectFile: Button
    private lateinit var btnStartFlashing: Button
    private lateinit var tvSelectedFile: TextView
    private lateinit var tvStatusLog: TextView
    private lateinit var spinnerChipset: Spinner

    private var selectedFileUri: Uri? = null

    // فتح متصفح ملفات أندرويد اختيار ملف .bin
    private val openFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            val fileName = getFileName(uri)
            tvSelectedFile.text = "الملف المحدد: $fileName"
            logMessage("تم اختيار الملف: $fileName")
        } else {
            Toast.makeText(this, "لم يتم اختيار أي ملف", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnSelectFile = findViewById(R.id.btnSelectFile)
        btnStartFlashing = findViewById(R.id.btnStartFlashing)
        tvSelectedFile = findViewById(R.id.tvSelectedFile)
        tvStatusLog = findViewById(R.id.tvStatusLog)
        spinnerChipset = findViewById(R.id.spinnerChipset)

        setupSpinner()

        // عند الضغط على زر اختيار الملف
        btnSelectFile.setOnClickListener {
            openFileLauncher.launch("*/*")
        }

        // عند الضغط على زر بدء الشحن
        btnStartFlashing.setOnClickListener {
            if (selectedFileUri == null) {
                Toast.makeText(this, "يرجى اختيار ملف الفلاشة (.bin) أولاً!", Toast.LENGTH_LONG).show()
                logMessage("خطأ: يجب اختيار ملف الفلاشة قبل بدء الشحن.")
            } else {
                logMessage("جاري بدء الاتصال بالريسيفر عبر وصلة OTG وشحن الملف...")
            }
        }
    }

    private fun setupSpinner() {
        val chipsets = arrayOf("Sunplus 1506TV (4M)", "Sunplus 1506HV (4M)", "Sunplus 1506F (4M)", "Sunplus 1507 / 2507 (8M)")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, chipsets)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerChipset.adapter = adapter
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

    private fun logMessage(msg: String) {
        tvStatusLog.append("\n$msg")
    }
}
