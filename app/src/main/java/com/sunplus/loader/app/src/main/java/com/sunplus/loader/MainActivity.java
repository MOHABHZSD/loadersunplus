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
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.util.zip.CRC32;

public class MainActivity extends AppCompatActivity {

    private Spinner spinnerDevices, spinnerBaudRate, spinnerParity, spinnerDataBits, spinnerStopBits, spinnerFlowControl;
    private Spinner spinnerChipType, spinnerDdrType, spinnerRomType, spinnerOperation, spinnerSection, spinnerStorage;
    private EditText etLength, etStartAddress, etCustomerId;
    private Button btnSelectFile, btnDumpPath, btnStart, btnStop;
    private TextView tvFileInfo, tvStatus, tvConsoleLog;
    private ScrollView logScrollView;
    private ProgressBar progressBar;

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

        // ربط عناصر واجهة المستخدم
        spinnerDevices = findViewById(R.id.spinnerDevices);
        spinnerBaudRate = findViewById(R.id.spinnerBaudRate);
        spinnerParity = findViewById(R.id.spinnerParity);
        spinnerDataBits = findViewById(R.id.spinnerDataBits);
        spinnerStopBits = findViewById(R.id.spinnerStopBits);
        spinnerFlowControl = findViewById(R.id.spinnerFlowControl);

        spinnerChipType = findViewById(R.id.spinnerChipType);
        spinnerDdrType = findViewById(R.id.spinnerDdrType);
        spinnerRomType = findViewById(R.id.spinnerRomType);
        spinnerOperation = findViewById(R.id.spinnerOperation);
        spinnerSection = findViewById(R.id.spinnerSection);
        spinnerStorage = findViewById(R.id.spinnerStorage);

        etLength = findViewById(R.id.etLength);
        etStartAddress = findViewById(R.id.etStartAddress);
        etCustomerId = findViewById(R.id.etCustomerId);

        btnSelectFile = findViewById(R.id.btnSelectFile);
        btnDumpPath = findViewById(R.id.btnDumpPath);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);

        tvFileInfo = findViewById(R.id.tvFileInfo);
        tvStatus = findViewById(R.id.tvStatus);
        tvConsoleLog = findViewById(R.id.tvConsoleLog);
        logScrollView = findViewById(R.id.logScrollView);
        progressBar = findViewById(R.id.progressBar);

        // تعبئة وتهيئة جميع القوائم المنسدلة برمجيًا
        setupAllSpinners();

        btnSelectFile.setOnClickListener(v -> openFilePicker(filePickerLauncher));
        btnDumpPath.setOnClickListener(v -> openFolderPicker());

        btnStart.setOnClickListener(v -> startFlashingProcess());
        btnStop.setOnClickListener(v -> stopFlashingProcess());

        ignoreBatteryOptimization();
    }

    private void setupAllSpinners() {
        populateSpinner(spinnerDevices, new String[]{"USB Serial UART", "ttyUSB0", "ttyACM0"});
        populateSpinner(spinnerBaudRate, new String[]{"115200", "9600", "19200", "38400", "57600", "230400", "460800", "921600"});
        populateSpinner(spinnerParity, new String[]{"None", "Odd", "Even", "Mark", "Space"});
        populateSpinner(spinnerDataBits, new String[]{"8", "7", "6", "5"});
        populateSpinner(spinnerStopBits, new String[]{"1", "1.5", "2"});
        populateSpinner(spinnerFlowControl, new String[]{"None", "RTS/CTS", "XON/XOFF"});

        populateSpinner(spinnerChipType, new String[]{"Sunplus 1506T", "Sunplus 1506TV", "Sunplus 1506F", "Sunplus 1507G", "Sunplus 1506G", "Sunplus 1505B"});
        populateSpinner(spinnerDdrType, new String[]{"DDR2", "DDR3", "LPDDR2", "SRAM"});
        populateSpinner(spinnerRomType, new String[]{"SPI Flash", "NAND Flash", "eMMC"});
        populateSpinner(spinnerOperation, new String[]{"Write File (شحن/تحديث)", "Read Dump (سحب دامب)", "Erase (مسح الشريحة)", "Verify (تحقق)"});
        populateSpinner(spinnerSection, new String[]{"All Flash (كامل الشريحة)", "Bootloader", "User Data", "Kernel"});
        populateSpinner(spinnerStorage, new String[]{"SPI NOR", "SPI NAND", "eMMC/SD"});
    }

    private void populateSpinner(Spinner spinner, String[] data) {
        if (spinner == null) return;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, data);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void openFilePicker(ActivityResultLauncher<Intent> launcher) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        launcher.launch(intent);
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
        String baudRate = spinnerBaudRate.getSelectedItem() != null ? spinnerBaudRate.getSelectedItem().toString() : "115200";
        String dataBits = spinnerDataBits.getSelectedItem() != null ? spinnerDataBits.getSelectedItem().toString() : "8";
        String stopBits = spinnerStopBits.getSelectedItem() != null ? spinnerStopBits.getSelectedItem().toString() : "1";
        String flowControl = spinnerFlowControl.getSelectedItem() != null ? spinnerFlowControl.getSelectedItem().toString() : "None";

        Intent serviceIntent = new Intent(this, FlashingService.class);
        serviceIntent.putExtra("BAUD_RATE", baudRate);
        serviceIntent.putExtra("DATA_BITS", dataBits);
        serviceIntent.putExtra("STOP_BITS", stopBits);
        serviceIntent.putExtra("FLOW_CONTROL", flowControl);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        appendLog("تم بدء العملية بإعدادات الاتصال (" + baudRate + " baud, " + dataBits + "N" + stopBits + ", Flow: " + flowControl + ").");
    }

    public void stopFlashingProcess() {
        Intent serviceIntent = new Intent(this, FlashingService.class);
        stopService(serviceIntent);
        appendLog("تم إيقاف العملية والخدمة بنجاح.");
    }
                                                              }
