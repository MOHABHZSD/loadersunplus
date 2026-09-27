package com.example.sunplusloader

import android.content.Context
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

class SunplusUartManager(private val context: Context) {

    private var serialPort: UsbSerialPort? = null
    private var usbConnection: UsbDeviceConnection? = null

    companion object {
        const val SYNC_BYTE: Byte = 0x00.toByte()
        const val CMD_ACK: Byte = 0x55.toByte()
        const val CHUNK_SIZE = 1024
        const val TIMEOUT_MS = 2000
    }

    // 1. الاتصال بمنفذ الـ USB
    fun connectUsb(driver: UsbSerialDriver, baudRate: Int, parity: Int): Boolean {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val connection = manager.openDevice(driver.device) ?: return false

        val port = driver.ports[0]
        port.open(connection)
        
        val stopBits = UsbSerialPort.STOPBITS_1
        val dataBits = 8
        port.setParameters(baudRate, dataBits, stopBits, parity)

        this.usbConnection = connection
        this.serialPort = port
        return true
    }

    // 2. التزامن الحقيقي للتحقق من وجود الرسيفر (منع الاستجابة الوهمية)
    suspend fun performHandshake(onLog: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val port = serialPort ?: return@withContext false
        val rxBuffer = ByteArray(2)
        var attempts = 0
        val maxAttempts = 200

        onLog("🔄 جاري إرسال إشارات التزامن... قم بتوصيل كهرباء الرسيفر الآن!")

        while (attempts < maxAttempts) {
            try {
                // إرسال بايت التزامن باستمرار
                port.write(byteArrayOf(SYNC_BYTE), 50)
                val bytesRead = port.read(rxBuffer, 50)

                // التثبت الحقيقي: التأكد أن الرد هو بايت التأكيد الصريح 0x55 من المعالج
                if (bytesRead >= 1 && rxBuffer[0] == CMD_ACK) {
                    onLog("✅ تم استلام الرد الحقيقي من معالج Sunplus بنجاح!")
                    return@withContext true
                }
            } catch (e: Exception) {
                // انتظار الاستجابة أثناء محاولات الإقلاع
            }
            attempts++
            kotlinx.coroutines.delay(50)
        }

        onLog("❌ لم يستجب الرسيفر! تحقق من توصيل الكهرباء وخطوط TX/RX.")
        return@withContext false
    }

    // 3. حقن ملف تهيئة ذاكرة الرام (DRAM Init)
    suspend fun initializeDram(dramPayload: ByteArray, onLog: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val port = serialPort ?: return@withContext false
        onLog("⚡ جاري حقن ملف تهيئة ذاكرة الرام...")

        try {
            port.write(dramPayload, TIMEOUT_MS)
            val ackBuffer = ByteArray(1)
            val read = port.read(ackBuffer, TIMEOUT_MS)

            if (read > 0 && ackBuffer[0] == CMD_ACK) {
                onLog("✅ تم تهيئة ذاكرة الرام بنجاح!")
                return@withContext true
            }
        } catch (e: Exception) {
            onLog("❌ خطأ أثناء تهيئة الرام: ${e.localizedMessage}")
        }
        return@withContext false
    }

    // 4. كتابة وشحن السوفت وير الحقيقي (WRITE)
    suspend fun writeFlash(
        fileData: ByteArray,
        startAddress: Long,
        onProgress: (Int) -> Unit,
        onLog: (String) -> Unit,
        isCancelled: () -> Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val port = serialPort ?: return@withContext false
        val totalSize = fileData.size
        var offset = 0
        val ackBuffer = ByteArray(1)

        onLog("🚀 بدء كتابة السوفت وير من العنوان: ${String.format("0x%06X", startAddress)}...")

        while (offset < totalSize) {
            if (isCancelled()) {
                onLog("⚠️ تم إيقاف العملية بطلب من المستخدم.")
                return@withContext false
            }

            val chunkSize = Math.min(CHUNK_SIZE, totalSize - offset)
            val chunk = fileData.copyOfRange(offset, offset + chunkSize)

            var success = false
            var retries = 0

            while (!success && retries < 3) {
                try {
                    port.write(chunk, TIMEOUT_MS)
                    val read = port.read(ackBuffer, TIMEOUT_MS)
                    if (read > 0 && ackBuffer[0] == CMD_ACK) {
                        success = true
                    } else {
                        retries++
                    }
                } catch (e: Exception) {
                    retries++
                }
            }

            if (!success) {
                onLog("❌ فشل إرسال الحزمة عند العنوان: ${String.format("0x%06X", startAddress + offset)}")
                return@withContext false
            }

            offset += chunkSize
            val progressPercent = ((offset.toDouble() / totalSize) * 100).toInt()
            onProgress(progressPercent)
        }

        onLog("🎉 اكتملت عملية الشحن بنجاح 100%!")
        return@withContext true
    }

    // 5. قراءة وسحب الفلاشة (DUMP)
    suspend fun dumpFlash(
        outputStream: OutputStream,
        length: Long,
        onProgress: (Int) -> Unit,
        onLog: (String) -> Unit,
        isCancelled: () -> Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val port = serialPort ?: return@withContext false
        var readBytes = 0L
        val buffer = ByteArray(CHUNK_SIZE)

        onLog("📥 بدء سحب النسخة الاحتياطية (DUMP) بحجم $length بايت...")

        try {
            while (readBytes < length) {
                if (isCancelled()) {
                    onLog("⚠️ تم إلغاء عملية السحب.")
                    return@withContext false
                }

                val bytesRead = port.read(buffer, TIMEOUT_MS)

                if (bytesRead > 0) {
                    outputStream.write(buffer, 0, bytesRead)
                    readBytes += bytesRead
                    val progressPercent = ((readBytes.toDouble() / length) * 100).toInt()
                    onProgress(progressPercent)
                }
            }
            outputStream.flush()
            outputStream.close()
            onLog("🎉 تم سحب الفلاشة وحفظ الملف بنجاح!")
            return@withContext true
        } catch (e: Exception) {
            onLog("❌ خطأ أثناء سحب الفلاشة: ${e.localizedMessage}")
            return@withContext false
        }
    }

    fun disconnect() {
        try {
            serialPort?.close()
            usbConnection?.close()
        } catch (e: Exception) {
            // إغلاق آمن
        } finally {
            serialPort = null
            usbConnection = null
        }
    }
}
