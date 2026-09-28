package com.sunplus.loader;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.util.zip.CRC32;

public class MainActivity extends AppCompatActivity {

    private Spinner spinnerDevices, spinnerBaudRate, spinnerParity, spinnerChipType, spinnerDdrType, spinnerOperation, spinnerSection, spinnerStorage;
    private EditText etLength, etStartAddress;
    private Button btnSelectFile, btnDumpPath, btnStart, btnStop;
    private TextView tvFileInfo, tvStatus, tvConsoleLog;
    private ScrollView logScrollView;

    private static final int MAX_LOG_LENGTH = 50000;
    private Uri selectedFileUri = null;
    private Uri dumpPathUri = null;

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectedFileUri = result.getData().getData();
                    if (selectedFileUri != null) {
                        processSelectedFile(selectedFileUri);
                    }
                }
            }
    );

    private final ActivityResultLauncher<Intent> dumpPathLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    dumpPathUri = result.getData().getData();
                    if (dumpPathUri != null) {
                        appendLog("تم اختيار مسار الحفظ لـ Dump بنجاح.");
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        spinnerDevices = findViewById(R.id.spinnerDevices);
        spinnerBaudRate = findViewById(R.id.spinnerBaudRate);
        spinnerParity = findViewById(R.id.spinnerParity);
        spinnerChipType = findViewById(R.id.spinnerChipType);
        spinnerDdrType = findViewById(R.id.spinnerDdrType);
        spinnerOperation = findViewById(R.id.spinnerOperation);
        spinnerSection = findViewById(R.id.spinnerSection);
        spinnerStorage = findViewById(R.id.spinnerStorage);

        etLength = findViewById(R.id.etLength);
        etStartAddress = findViewById(R.id.etStartAddress);

        btnSelectFile = findViewById(R.id.btnSelectFile);
        btnDumpPath = findViewById(R.id.btnDumpPath);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);

        tvFileInfo = findViewById(R.id.tvFileInfo);
        tvStatus = findViewById(R.id.tvStatus);
        tvConsoleLog = findViewById(R.id.tvConsoleLog);
        logScrollView = findViewById(R.id.logScrollView);

        initDefaultDeviceSpinner();

        btnSelectFile.setOnClickListener(v -> openFilePicker());
        btnDumpPath.setOnClickListener(v -> openFolderPicker());

        btnStart.setOnClickListener(v -> startFlashingProcess());
        btnStop.setOnClickListener(v -> stopFlashingProcess());

        ignoreBatteryOptimization();
    }

    private void initDefaultDeviceSpinner() {
        String[] defaultDevices = new String[]{"USB Serial UART"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, defaultDevices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDevices.setAdapter(adapter);
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        filePickerLauncher.launch(intent);
    }

    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        dumpPathLauncher.launch(intent);
    }

    private void processSelectedFile(Uri uri) {
        try {
            String fileName = "rom.bin";
            long fileSize = 0;

            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameIndex != -1) fileName = cursor.getString(nameIndex);
                if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex);
                cursor.close();
            }

            CRC32 crc = new CRC32();
            InputStream inputStream = getContentResolver().openInputStream(uri);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                crc.update(buffer, 0, bytesRead);
            }
            inputStream.close();

            String crcString = String.format("0x%08X", crc.getValue());
            String infoText = "الملف المحدد: " + fileName + "\nحجم الملف: " + fileSize + " بايت | CRC32: " + crcString;
            tvFileInfo.setText(infoText);

            appendLog("تم اختيار الملف: " + fileName + " | الحجم: " + fileSize);
        } catch (Exception e) {
            appendLog("خطأ أثناء قراءة الملف: " + e.getMessage());
        }
    }

    public void appendLog(final String message) {
        runOnUiThread(() -> {
            if (tvConsoleLog == null || logScrollView == null) return;

            if (tvConsoleLog.getText().length() > MAX_LOG_LENGTH) {
                String currentText = tvConsoleLog.getText().toString();
                tvConsoleLog.setText(currentText.substring(currentText.length() - (MAX_LOG_LENGTH / 2)));
            }

            tvConsoleLog.append(message + "\n");

            logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void ignoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            String packageName = getPackageName();
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivity(intent);
            }
        }
    }

    public void startFlashingProcess() {
        Intent serviceIntent = new Intent(this, FlashingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        appendLog("تم بدء العملية بنجاح. حماية الخلفية مفعّلة.");
    }

    public void stopFlashingProcess() {
        Intent serviceIntent = new Intent(this, FlashingService.class);
        stopService(serviceIntent);
        appendLog("تم إيقاف العملية والخدمة بنجاح.");
    }
            }
