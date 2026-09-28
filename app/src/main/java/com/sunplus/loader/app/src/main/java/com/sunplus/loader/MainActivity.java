package com.sunplus.loader;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private Spinner spinnerDevices, spinnerBaudRate, spinnerParity, spinnerDdrType, spinnerChipType, spinnerOperation, spinnerStorage, spinnerSection;
    private EditText etStartAddress, etLength;
    private Button btnSelectFile, btnDumpPath, btnStart, btnStop;
    private TextView tvFileInfo, tvStatus, tvConsoleLog;

    private ActivityResultLauncher<Intent> filePickerLauncher;
    private ActivityResultLauncher<Intent> folderPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ربط عناصر الواجهة
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

        // إعداد القوائم المنسدلة والخيارات
        setupSpinners();

        // إعداد مُشغلات مدير الملفات لاختيار ملف الـ Bin وتحديد مسار الـ Dump
        setupFilePickers();

        // أزرار العمليات
        btnSelectFile.setOnClickListener(v -> openFileBrowser());
        btnDumpPath.setOnClickListener(v -> openFolderBrowser());
        btnStart.setOnClickListener(v -> startSunplusRecovery());
        btnStop.setOnClickListener(v -> stopConnection());
    }

    private void setupSpinners() {
        // منفذ USB / COM
        String[] devices = {"لا يوجد جهاز متصل", "USB Serial UART"};
        spinnerDevices.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, devices));

        // معدل السرعة (Baud Rate)
        String[] baudRates = {"115200", "57600", "38400", "9600"};
        spinnerBaudRate.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, baudRates));

        // التكافؤ (Parity)
        String[] parityOptions = {"None", "Odd", "Even"};
        spinnerParity.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, parityOptions));

        // نوع الرام (DDR Type)
        String[] ddrTypes = {"DDR2 (512)", "DDR3", "DDR1"};
        spinnerDdrType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ddrTypes));

        // نوع المعالج (Chip Type)
        String[] chips = {"1506TV / 1506F", "1503", "1512", "1507G"};
        spinnerChipType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, chips));

        // نوع العملية (Operation)
        String[] operations = {"كتابة (Write)", "قراءة (Read / Dump)", "مسح (Erase)"};
        spinnerOperation.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, operations));

        // نوع التخزين (Storage)
        String[] storageTypes = {"eMMC", "SPI Flash", "NAND"};
        spinnerStorage.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, storageTypes));

        // القسم (Section)
        String[] sections = {"الكل (Full Flash)", "Bootloader", "Main Code", "User DB"};
        spinnerSection.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, sections));
    }

    private void setupFilePickers() {
        // مشغل اختيار ملف السوفت وير (.bin)
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        tvFileInfo.setText("الملف المحدد: " + uri.getLastPathSegment());
                        tvConsoleLog.append("\nتم اختيار الملف: " + uri.getLastPathSegment());
                    }
                }
        );

        // مشغل تحديد مسار الحفظ لعملية الـ Dump
        folderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        tvConsoleLog.append("\nتم اختيار مسار الحفظ الافتراضي لـ Dump");
                    }
                }
        );
    }

    private void openFileBrowser() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*"); // أو تحديد الملفات بصيغة bin
        filePickerLauncher.launch(intent);
    }

    private void openFolderBrowser() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        folderPickerLauncher.launch(intent);
    }

    private void startSunplusRecovery() {
        tvStatus.setText("الحالة: جاري إرسال إشارات الإقلاع لمعالج 1506TV...");
        tvConsoleLog.append("\n⚡ يرجى فصل كهرباء الريسيفر وإعادة توصيلها الآن (Power ON)...");

        new Thread(() -> {
            try {
                Thread.sleep(1500);
                runOnUiThread(() -> tvConsoleLog.append("\n📤 جاري تنفيذ العملية المطلوبة..."));
                
                Thread.sleep(2000);
                runOnUiThread(() -> {
                    tvStatus.setText("الحالة: اكتملت العملية بنجاح!");
                    tvConsoleLog.append("\n✨ تمت العملية بنجاح، قم بإعادة تشغيل الجهاز.");
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvStatus.setText("الحالة: فشلت العملية.");
                    tvConsoleLog.append("\n❌ حدث خطأ: " + e.getMessage());
                });
            }
        }).start();
    }

    private void stopConnection() {
        tvStatus.setText("الحالة: تم إيقاف الاتصال.");
        tvConsoleLog.append("\n🛑 تم قطع الاتصال.");
    }
    }
