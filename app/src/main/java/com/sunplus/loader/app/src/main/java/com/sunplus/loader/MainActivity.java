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

    public enum LoaderOperation { WRITE, READ_DUMP }

    public enum ExecutionState {
        IDLE,
        WAITING_FOR_POWER,
        CONNECTING,
        TRANSFERRING,
        COMPLETED,
        FAILED
    }

    private Spinner spinnerDevices, spinnerBaudRate, spinnerParity, spinnerDataBits, spinnerStopBits, spinnerFlowControl;
    private Spinner spinnerChipType, spinnerDdrType, spinnerRomType, spinnerOperation, spinnerSection;
    private EditText etLength, etStartAddress, etCustomerId;
    private Button btnSelectFile, btnSelectAssistant, btnDumpPath, btnStart, btnStop;
    private TextView tvFileInfo, tvStatus, tvConsoleLog;
    private ScrollView logScrollView;
    private ProgressBar progressBar;

    private static final int MAX_LOG_LENGTH = 50000;
    private Uri selectedFileUri = null;
    private Uri assistantFileUri = null;
    private Uri dumpPathUri = null;

    private ExecutionState currentState = ExecutionState.IDLE;

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

    private final ActivityResultLauncher<Intent> assistantPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    assistantFileUri = result.getData().getData();
                    if (assistantFileUri != null) {
                        appendLog("تم اختيار ملف المساعد (Assistant File) بنجاح.", false);
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
                        appendLog("تم تحديد مسار حفظ الـ DUMP بنجاح.", false);
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
        spinnerDataBits = findViewById(R.id.spinnerDataBits);
        spinnerStopBits = findViewById(R.id.spinnerStopBits);
        spinnerFlowControl = findViewById(R.id.spinnerFlowControl);

        spinnerChipType = findViewById(R.id.spinnerChipType);
        spinnerDdrType = findViewById(R.id.spinnerDdrType);
        spinnerRomType = findViewById(R.id.spinnerRomType);
        spinnerOperation = findViewById(R.id.spinnerOperation);
        spinnerSection = findViewById(R.id.spinnerSection);

        etLength = findViewById(R.id.etLength);
        etStartAddress = findViewById(R.id.etStartAddress);
        etCustomerId = findViewById(R.id.etCustomerId);

        btnSelectFile = findViewById(R.id.btnSelectFile);
        btnSelectAssistant = findViewById(R.id.btnSelectAssistant);
        btnDumpPath = findViewById(R.id.btnDumpPath);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);

        tvFileInfo = findViewById(R.id.tvFileInfo);
        tvStatus = findViewById(R.id.tvStatus);
        tvConsoleLog = findViewById(R.id.tvConsoleLog);
        logScrollView = findViewById(R.id.logScrollView);
        progressBar = findViewById(R.id.progressBar);

        initDefaultDeviceSpinner();

        btnSelectFile.setOnClickListener(v -> openFilePicker(filePickerLauncher));
        btnSelectAssistant.setOnClickListener(v -> openFilePicker(assistantPickerLauncher));
        btnDumpPath.setOnClickListener(v -> openFolderPicker());

        btnStart.setOnClickListener(v -> startDynamicProcess());
        btnStop.setOnClickListener(v -> stopFlashingProcess());

        ignoreBatteryOptimization();
    }

    private void initDefaultDeviceSpinner() {
        String[] defaultDevices = new String[]{"USB Serial UART (Auto Detection)"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, defaultDevices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDevices.setAdapter(adapter);
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

            appendLog("تم اختيار ملف السوفت وير: " + fileName + " | الحجم: " + fileSize + " بايت", false);
        } catch (Exception e) {
            appendLog("خطأ أثناء قراءة الملف: " + e.getMessage(), true);
        }
    }

    public void appendLog(final String message, final boolean isError) {
        runOnUiThread(() -> {
            if (tvConsoleLog == null || logScrollView == null) return;

            if (tvConsoleLog.getText().length() > MAX_LOG_LENGTH) {
                String currentText = tvConsoleLog.getText().toString();
                tvConsoleLog.setText(currentText.substring(currentText.length() - (MAX_LOG_LENGTH / 2)));
            }

            String prefix = isError ? "❌ [خطأ] " : "ℹ️ ";
            tvConsoleLog.append(prefix + message + "\n");

            logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
        });
    }

    public void updateStatus(final String statusMessage) {
        runOnUiThread(() -> {
            if (tvStatus != null) {
                tvStatus.setText(statusMessage);
            }
        });
    }

    public void updateProgress(final int progress) {
        runOnUiThread(() -> {
            if (progressBar != null) {
                progressBar.setProgress(progress);
            }
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

    public void startDynamicProcess() {
        String selectedPort = spinnerDevices.getSelectedItem() != null ? spinnerDevices.getSelectedItem().toString() : "";
        String selectedOp = spinnerOperation.getSelectedItem() != null ? spinnerOperation.getSelectedItem().toString() : "Write";

        LoaderOperation operationType = selectedOp.contains("Read") || selectedOp.contains("سحب") || selectedOp.contains("قراءة")
                ? LoaderOperation.READ_DUMP 
                : LoaderOperation.WRITE;

        if (selectedPort.isEmpty() || selectedPort.equals("No Device")) {
            appendLog("خطأ: لم يتم اختيار منفذ USB Serial صالح.", true);
            updateStatus("الحالة: خطأ - المنفذ غير صالح");
            return;
        }

        if (operationType == LoaderOperation.WRITE) {
            if (selectedFileUri == null) {
                appendLog("خطأ: يرجى اختيار ملف السوفتوير (BIN) الأصلي أولاً.", true);
                updateStatus("الحالة: خطأ - لم يتم اختيار ملف الـ BIN");
                return;
            }
        } else if (operationType == LoaderOperation.READ_DUMP) {
            if (dumpPathUri == null) {
                appendLog("تحذير: لم يتم تحديد مسار حفظ الـ Dump، سيتم الحفظ بالمسار الافتراضي.", false);
            }
        }

        currentState = ExecutionState.WAITING_FOR_POWER;
        String actionName = (operationType == LoaderOperation.WRITE) ? "شحن ملف السوفتوير" : "سحب ملف الـ Dump";

        String baudRate = spinnerBaudRate.getSelectedItem() != null ? spinnerBaudRate.getSelectedItem().toString() : "115200";
        appendLog("Sunplus Loader - تم فتح المنفذ " + selectedPort + " بسرعة " + baudRate + " bps.", false);
        appendLog("بدء عملية " + actionName + "...", false);

        updateStatus("⚡ بانتظار تشغيل الطاقة: قم بتوصيل شاحن 12V بالرسيفر الآن...");
        appendLog("👉 يرجى تشغيل الكهرباء (12V) في جهاز الرسيفر للبدء في الاتصال مع المعالج.", false);

        startFlashingForegroundService(operationType);
    }

    private void startFlashingForegroundService(LoaderOperation operationType) {
        String baudRate = spinnerBaudRate.getSelectedItem() != null ? spinnerBaudRate.getSelectedItem().toString() : "115200";
        String dataBits = spinnerDataBits.getSelectedItem() != null ? spinnerDataBits.getSelectedItem().toString() : "8";
        String stopBits = spinnerStopBits.getSelectedItem() != null ? spinnerStopBits.getSelectedItem().toString() : "1";
        String flowControl = spinnerFlowControl.getSelectedItem() != null ? spinnerFlowControl.getSelectedItem().toString() : "None";

        Intent serviceIntent = new Intent(this, FlashingService.class);
        serviceIntent.putExtra("BAUD_RATE", baudRate);
        serviceIntent.putExtra("DATA_BITS", dataBits);
        serviceIntent.putExtra("STOP_BITS", stopBits);
        serviceIntent.putExtra("FLOW_CONTROL", flowControl);
        serviceIntent.putExtra("OPERATION_TYPE", operationType.name());

        if (selectedFileUri != null) {
            serviceIntent.putExtra("FILE_URI", selectedFileUri.toString());
        }
        if (dumpPathUri != null) {
            serviceIntent.putExtra("DUMP_URI", dumpPathUri.toString());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    public void stopFlashingProcess() {
        Intent serviceIntent = new Intent(this, FlashingService.class);
        stopService(serviceIntent);
        currentState = ExecutionState.IDLE;
        updateStatus("الحالة: تم إيقاف العملية بطلب من المستخدم");
        appendLog("تم إيقاف العملية وإغلاق المنفذ بنجاح.", false);
        updateProgress(0);
    }
            }
