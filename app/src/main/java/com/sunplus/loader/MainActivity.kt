val spinnerChipType = findViewById<Spinner>(R.id.spinnerChipType)
val btnSaveDumpPath = findViewById<Button>(R.id.btnSaveDumpPath)
val btnStopProcess = findViewById<Button>(R.id.btnStopProcess)
val progressBar = findViewById<ProgressBar>(R.id.progressBar)
val etConsoleLog = findViewById<EditText>(R.id.etConsoleLog)

// تعبئة خيارات قائمة المعالجات
val chips = arrayOf("1506TV / 1506F", "1506G / 1507G", "1506T")
spinnerChipType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, chips)

// برمجة زر الإلغاء لتفريغ مؤشر التقدم وكتابة تنبيه في السجل
btnStopProcess.setOnClickListener {
    etConsoleLog.append("⚠️ تم إيقاف العملية بواسطة المستخدم.\n")
    progressBar.progress = 0
}
