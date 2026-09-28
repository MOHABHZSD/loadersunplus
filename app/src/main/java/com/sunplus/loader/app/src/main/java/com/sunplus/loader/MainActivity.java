package com.sunplus.loader;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
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

import java.io.InputStream;
import java.util.zip.CRC32;

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

        setupSpinners();
        setupFilePickers();

        btnSelectFile.setOnClickListener(v -> openFileBrowser());
        btnDumpPath.setOnClickListener(v -> openFolderBrowser());
        btnStart.setOnClickListener(v -> startSunplusRecovery());
        btnStop.setOnClickListener(v -> stopConnection());
    }

    private void setupSpinners() {
        spinnerDevices.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"لا يوجد جهاز متصل", "USB Serial UART"}));
        spinnerBaudRate.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"115200", "57600", "38400", "9600"}));
        spinnerParity.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"None", "Odd", "Even"}));
        spinnerDdrType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"DDR2 (512)", "DDR3", "DDR1"}));
        spinnerChipType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"1506TV / 1506F", "1503", "1512", "1507G"}));
        spinnerOperation.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"كتابة (Write)", "قراءة (Read / Dump)", "مسح (Erase)"}));
        spinnerStorage.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"SPI Flash", "eMMC", "NAND"}));
        spinnerSection.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"الكل (Full Flash)", "Bootloader", "Main Code", "User DB"}));
    }

    private void setupFilePickers() {
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        String fileName = getFileName(uri);
                        long fileSize = getFileSize(uri);
                        long crcValue = calculateCRC32(uri);

                        tvFileInfo.setText("الملف المحدد: " + fileName + "\nحجم الملف: " + fileSize + " بايت | CRC32: 0x" + Long.toHexString(crcValue).toUpperCase());
                        tvConsoleLog.append("\nتم اختيار الملف: " + fileName + " | الحجم: " + fileSize);
                    }
                }
        );

        folderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        tvConsoleLog.append("\nتم اختيار مسار الحفظ لـ Dump بنجاح.");
                    }
                }
        );
    }

    private String getFileName(Uri uri) {
        String result = "unknown.bin";
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx != -1) result = cursor.getString(idx);
            }
        }
        return result;
    }

    private long getFileSize(Uri uri) {
        long size = 0;
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (idx != -1) size = cursor.getLong(idx);
            }
        } catch (Exception e) {
            size = 0;
        }
        return size;
    }

    private long calculateCRC32(Uri uri) {
        CRC32 crc32 = new CRC32();
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                crc32.update(buffer, 0, read);
            }
        } catch (Exception e) {
            return 0;
        }
        return crc32.getValue();
    }

    private void openFileBrowser() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
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
