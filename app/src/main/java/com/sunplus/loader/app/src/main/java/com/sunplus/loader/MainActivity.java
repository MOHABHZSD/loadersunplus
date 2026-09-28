package com.sunplus.loader;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private Spinner spinnerDevices, spinnerBaudRate, spinnerParity, spinnerChipType, spinnerDdrType, spinnerOperation, spinnerSection, spinnerStorage;
    private EditText etLength, etStartAddress;
    private Button btnSelectFile, btnDumpPath, btnStart, btnStop;
    private TextView tvFileInfo, tvStatus, tvConsoleLog;
    private ScrollView logScrollView;

    private static final int MAX_LOG_LENGTH = 50000;

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

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startFlashingProcess();
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopFlashingProcess();
            }
        });

        ignoreBatteryOptimization();
    }

    public void appendLog(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (tvConsoleLog == null || logScrollView == null) return;

                if (tvConsoleLog.getText().length() > MAX_LOG_LENGTH) {
                    String currentText = tvConsoleLog.getText().toString();
                    tvConsoleLog.setText(currentText.substring(currentText.length() - (MAX_LOG_LENGTH / 2)));
                }

                tvConsoleLog.append(message + "\n");

                logScrollView.post(new Runnable() {
                    @Override
                    public void run() {
                        logScrollView.fullScroll(View.FOCUS_DOWN);
                    }
                });
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

    public void startFlashingProcess() {
        Intent serviceIntent = new Intent(this, FlashingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        appendLog("تم تفعيل حماية عدم إغلاق التطبيق أثناء العمليات.");
    }

    public void stopFlashingProcess() {
        Intent serviceIntent = new Intent(this, FlashingService.class);
        stopService(serviceIntent);
        appendLog("تم إيقاف الخدمة وحماية البطارية.");
    }
            }
