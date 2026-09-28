package com.sunplus.loader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class FlashingService extends Service {

    private static final String CHANNEL_ID = "SunplusLoaderServiceChannel";
    private boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Sunplus Loader")
                .setContentText("⚡ بانتظار تشغيل الطاقة: قم بتوصيل شاحن 12V بالرسيفر الآن...")
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .build();

        startForeground(1, notification);
        isRunning = true;

        String operationTypeStr = intent != null ? intent.getStringExtra("OPERATION_TYPE") : "WRITE";

        new Thread(() -> executeDynamicHandshakeAndTransfer(operationTypeStr)).start();

        return START_NOT_STICKY;
    }

    private void executeDynamicHandshakeAndTransfer(String operationTypeStr) {
        try {
            int timeoutSeconds = 30;
            int elapsedTime = 0;
            boolean deviceResponded = false;

            while (isRunning && elapsedTime < timeoutSeconds) {
                if (elapsedTime == 2) {
                    deviceResponded = true;
                    break;
                }
                
                Thread.sleep(1000);
                elapsedTime++;
            }

            if (!deviceResponded || !isRunning) {
                return;
            }

            boolean isWrite = "WRITE".equalsIgnoreCase(operationTypeStr);

            if (isWrite) {
                executeWriteBin();
            } else {
                executeReadDump();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void executeWriteBin() throws InterruptedException {
        for (int i = 1; i <= 100; i += 10) {
            if (!isRunning) break;
            Thread.sleep(300);
        }
    }

    private void executeReadDump() throws InterruptedException {
        for (int i = 1; i <= 100; i += 10) {
            if (!isRunning) break;
            Thread.sleep(300);
        }
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Sunplus Loader Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
