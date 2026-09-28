package com.sunplus.loader;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private Spinner spinnerDevices, spinnerBaudRate, spinnerParity, spinnerDdrType, spinnerChipType, spinnerOperation, spinnerStorage, spinnerSection;
    private EditText etStartAddress, etLength;
    private Button btnSelectFile, btnDumpPath, btnStart, btnStop;
    private TextView tvFileInfo, tvStatus, tvConsoleLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ربط العناصر بالواجهة
        spinnerDevices = findViewById(R.id.spinnerDevices);
        spinnerBaudRate = findViewById(R.id.spinnerBaudRate);
        spinnerParity = findViewById(R.id.spinnerParity);
        spinnerDdrType = findViewById(R.id.spinnerDdrType);
        spinnerChipType = findViewById(R.id.spinnerChipType);
        spinnerOperation = findViewById(R.id.spinnerOperation);
        spinnerStorage = findViewById(R.id.spinnerStorage);
        spinnerSection = findViewById(R.id.spinnerSection);
        
        etStartAddress = findViewById(R.id.etStartAddress);
        etLength = findViewById(R.id.etLength);
        
        btnSelectFile = findViewById(R.id.btnSelectFile);
        btnDumpPath = findViewById(R.id.btnDumpPath);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        
        tvFileInfo = findViewById(R.id.tvFileInfo);
        tvStatus = findViewById(R.id.tvStatus);
        tvConsoleLog = findViewById(R.id.tvConsoleLog);

        // تعبئة القوائم (Spinners)
        setupSpinners();

        // زر بدء العملية لإحياء الريسيفر
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecoveryProcess();
            }
        });
    }

    private void setupSpinners() {
        // نوع العملية
        String[] operations = {"كتابة (Write)", "قراءة (Read / Dump)", "مسح (Erase)"};
        ArrayAdapter<String> opAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, operations);
        spinnerOperation.setAdapter(opAdapter);

        // معدل السرعة لمعالجات صن بلس
        String[] baudRates = {"115200", "57600", "38400"};
        ArrayAdapter<String> baudAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, baudRates);
        spinnerBaudRate.setAdapter(baudAdapter);

        // نوع المعالج
        String[] chips = {"1506TV / 1506F", "1503", "1512"};
        ArrayAdapter<String> chipAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, chips);
        spinnerChipType.setAdapter(chipAdapter);
    }

    private void startRecoveryProcess() {
        tvStatus.setText("الحالة: جاري الاتصال بالريسيفر لإصلاح اللمبة الحمراء...");
        tvConsoleLog.append("\n⚡ يرجى توصيل كابل التحديث وتغذية الريسيفر بالكهرباء الآن...");
        
        // هنا يتم حقن أوامر البوت لمدخلات السيريال (UART) لمعالج Sunplus 1506TV
        // لعلاج اللمبة الحمراء عن طريق إرسال ملف دامب أصلي مسحوب بنفس المواصفات
    }
}
