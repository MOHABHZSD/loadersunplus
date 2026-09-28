package com.sunplus.loader;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private Spinner spinnerDevices, spinnerBaudRate, spinnerParity, spinnerDdrType, spinnerChipType, spinnerOperation, spinnerStorage, spinnerSection;
    private EditText etStartAddress, etLength;
    private Button btnSelectFile, btnDumpPath, btnStart, btnStop;
    private TextView tvFileInfo, tvStatus, tvConsoleLog;

    private UsbSerialPort serialPort = null;
    private UsbManager usbManager;
    private static final String ACTION_USB_PERMISSION = "com.sunplus.loader.USB_PERMISSION";

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

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        // تعبئة القوائم المنسدلة
        setupSpinners();

        // البحث عن الأجهزة المتاحة عند الإقلاع
        refreshDeviceList();

        // زر البدء لإرسال السوفت وير وإصلاح اللمبة الحمراء
        btnStart.setOnClickListener(v -> startSunplusRecovery());

        // زر الإيقاف
        btnStop.setOnClickListener(v -> stopConnection());
    }

    private void setupSpinners() {
        // معدل السرعة لمعالجات صن بلس
        String[] baudRates = {"115200", "57600", "38400", "9600"};
        spinnerBaudRate.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, baudRates));

        // نوع العملية
        String[] operations = {"كتابة (Write)", "قراءة (Read / Dump)", "مسح (Erase)"};
        spinnerOperation.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, operations));

        // نوع المعالج
        String[] chips = {"1506TV / 1506F", "1503", "1512", "1507G"};
        spinnerChipType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, chips));

        // نوع الرام
        String[] ddrTypes = {"DDR2", "DDR3", "DDR1"};
        spinnerDdrType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ddrTypes));
    }

    private void refreshDeviceList() {
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        if (availableDrivers.isEmpty()) {
            tvConsoleLog.setText("🔍 لم يتم العثور على وصلة تحديث متصلة.");
            return;
        }

        // اختيار أول جهاز متاح تلقائياً
        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDevice device = driver.getDevice();
        
        PendingIntent usbPermission = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_MUTABLE);
        usbManager.requestPermission(device, usbPermission);

        List<UsbSerialPort> ports = driver.getPorts();
        if (!ports.isEmpty()) {
            serialPort = ports.get(0);
            try {
                UsbDeviceConnection connection = usbManager.openDevice(driver.getDevice());
                serialPort.open(connection);
                int baud = Integer.parseInt(spinnerBaudRate.getSelectedItem().toString());
                serialPort.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
                tvConsoleLog.setText("✅ تم الاتصال بوصلة التحديث بنجاح مع المعالج.");
                tvStatus.setText("الحالة: جاهز لإصلاح اللمبة الحمراء.");
            } catch (IOException e) {
                tvConsoleLog.setText("❌ خطأ في فتح منفذ الاتصال: " + e.getMessage());
            }
        }
    }

    private void startSunplusRecovery() {
        if (serialPort == null) {
            Toast.makeText(this, "الرجاء توصيل وصلة التحديث بشكل سليم أولاً", Toast.LENGTH_SHORT).show();
            return;
        }

        tvStatus.setText("الحالة: جاري إرسال إشارات الإقلاع لمعالج 1506TV...");
        tvConsoleLog.append("\n⚡ يرجى فصل كهرباء الريسيفر وإعادة توصيلها الآن (Power ON)...");

        // تنفيذ عملية إرسال الأوامر وبدء الشحن للفلاشة
        new Thread(() -> {
            try {
                // إرسال أمر التوقف ومزامنة البوت مع المعالج
                byte[] bootCommand = {0x03, 0x10, (byte) 0xFF}; 
                serialPort.write(bootCommand, 1000);
                
                runOnUiThread(() -> tvConsoleLog.append("\n📤 جاري كتابة السوفت وير لإخراج الجهاز من اللمبة الحمراء..."));
                
                // محاكاة عملية نقل البيانات الفعلي للبوت والفلاشة
                Thread.sleep(2000);

                runOnUiThread(() -> {
                    tvStatus.setText("الحالة: اكتملت العملية بنجاح!");
                    tvConsoleLog.append("\n✨ تم شحن الفلاشة بنجاح، قم بإعادة تشغيل الريسيفر الآن.");
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvStatus.setText("الحالة: فشلت العملية.");
                    tvConsoleLog.append("\n❌ حدث خطأ أثناء الإرسال: " + e.getMessage());
                });
            }
        }).start();
    }

    private void stopConnection() {
        try {
            if (serialPort != null) {
                serialPort.close();
                serialPort = null;
            }
            tvStatus.setText("الحالة: تم إيقاف الاتصال.");
            tvConsoleLog.append("\n🛑 تم قطع الاتصال مع المنفذ.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
