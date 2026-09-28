package com.example.sunplusloader;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private Spinner spinnerParity, spinnerBaudRate, spinnerDdrType, spinnerChipType, spinnerStorage, spinnerSection;
    private EditText editStart, editLength;
    private Button btnSelectFirmware, btnSelectDump, btnStart, btnStop;
    private TextView txtFileInfo, txtStatus, txtConsoleLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Binding UI views
        spinnerParity = findViewById(R.id.spinnerParity);
        spinnerBaudRate = findViewById(R.id.spinnerBaudRate);
        spinnerDdrType = findViewById(R.id.spinnerDdrType);
        spinnerChipType = findViewById(R.id.spinnerChipType);
        spinnerStorage = findViewById(R.id.spinnerStorage);
        spinnerSection = findViewById(R.id.spinnerSection);

        editStart = findViewById(R.id.editStart);
        editLength = findViewById(R.id.editLength);

        btnSelectFirmware = findViewById(R.id.btnSelectFirmware);
        btnSelectDump = findViewById(R.id.btnSelectDump);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);

        txtFileInfo = findViewById(R.id.txtFileInfo);
        txtStatus = findViewById(R.id.txtStatus);
        txtConsoleLog = findViewById(R.id.txtConsoleLog);

        setupSpinners();
    }

    private void setupSpinners() {
        setupSingleItemSpinner(spinnerParity, "None");
        setupSingleItemSpinner(spinnerBaudRate, "115200");
        setupSingleItemSpinner(spinnerDdrType, "DDR2 (512)");
        setupSingleItemSpinner(spinnerChipType, "1506TV / 1506F");
        setupSingleItemSpinner(spinnerStorage, "SPI Flash");
        setupSingleItemSpinner(spinnerSection, "(Full Flash) الكل");
    }

    private void setupSingleItemSpinner(Spinner spinner, String item) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{item});
        spinner.setAdapter(adapter);
    }
            }
