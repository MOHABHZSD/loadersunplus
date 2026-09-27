package com.sunplus.loader

import android.content.Context
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var btnConnect: Button
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnConnect = findViewById(R.id.btnConnect)
        tvStatus = findViewById(R.id.tvStatus)

        btnConnect.setOnClickListener {
            connectToUsb()
        }
    }

    private fun connectToUsb() {
        val manager = getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)

        if (availableDrivers.isEmpty()) {
            tvStatus.text = "لم يتم العثور على أجهزة USB"
            return
        }

        val driver = availableDrivers[0]
        val connection: UsbDeviceConnection? = manager.openDevice(driver.device)

        if (connection == null) {
            tvStatus.text = "تعذر فتح الاتصال بالجهاز"
            return
        }

        val port: UsbSerialPort = driver.ports[0]

        CoroutineScope(Dispatchers.IO).launch {
            try {
                port.open(connection)
                port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

                withContext(Dispatchers.Main) {
                    tvStatus.text = "تم الاتصال بنجاح!"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "خطأ في الاتصال: ${e.localizedMessage}"
                }
            }
        }
    }
}
