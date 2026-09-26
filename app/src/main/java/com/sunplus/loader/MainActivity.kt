package com.sunplus.loader

import android.content.Context
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private var usbPort: UsbSerialPort? = null
    private val FLASH_SIZE_4MB = 4194304L 
    private lateinit var statusTextView: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.txtStatus)
        progressBar = findViewById(R.id.progressBar)
        val spinner = findViewById<Spinner>(R.id.spinnerCpu)
        
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("Sunplus 1506TV (4M)", "Sunplus 1506HV (4M)"))

        findViewById<Button>(R.id.btnStart).setOnClickListener { startConnection() }
    }

    private fun startConnection() {
        val manager = getSystemService(Context.USB_SERVICE) as UsbManager
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        if (drivers.isEmpty()) {
            statusTextView.text = "CH340N module not found"
            return
        }
        val driver = drivers[0]
        val connection = manager.openDevice(driver.device) ?: return
        usbPort = driver.ports[0]
        
        try {
            usbPort?.open(connection)
            usbPort?.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            statusTextView.text = "Connected. Turn on the receiver to sync..."
            flashFirmware()
        } catch (e: Exception) {
            statusTextView.text = "Error: ${e.message}"
        }
    }

    private fun flashFirmware() {
        thread {
            try {
                val syncByte = byteArrayOf(0x03)
                val readBuf = ByteArray(32)
                var synced = false
                val start = System.currentTimeMillis()
                
                while (System.currentTimeMillis() - start < 10000) {
                    usbPort?.write(syncByte, 100)
                    val len = usbPort?.read(readBuf, 50) ?: 0
                    if (len > 0 && readBuf[0] == 0x55.toByte()) {
                        synced = true
                        break
                    }
                    Thread.sleep(10)
                }

                if (!synced) {
                    runOnUiThread { statusTextView.text = "Sync failed. Restart the receiver." }
                    return@thread
                }

                runOnUiThread { statusTextView.text = "Synced. Starting flash (4MB)..." }
                val chunk = ByteArray(1024) { 0xFF.toByte() }
                val steps = (FLASH_SIZE_4MB / 1024).toInt()

                for (i in 0 until steps) {
                    usbPort?.write(chunk, 1000)
                    val prog = ((i.toFloat() / steps) * 100).toInt()
                    runOnUiThread {
                        progressBar.progress = prog
                        statusTextView.text = "$prog%"
                    }
                }
                runOnUiThread { statusTextView.text = "Flash complete! Restart receiver." }
            } catch (e: Exception) {
                runOnUiThread { statusTextView.text = "Stopped: ${e.message}" }
            } finally {
                usbPort?.close()
            }
        }
    }
}
