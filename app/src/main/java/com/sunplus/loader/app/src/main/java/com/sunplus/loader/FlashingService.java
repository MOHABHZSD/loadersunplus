package com.sunplus.loader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import androidx.core.app.NotificationCompat;

public class FlashingService extends Service {

    private static final String CHANNEL_ID = "sunplus_flashing_channel";
    private static final int NOTIFICATION_ID = 1001;
    private PowerManager.WakeLock wakeLock;
    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        public FlashingService getService() {
            return FlashingService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification("جاري تنفيذ العملية... يرجى عدم إغلاق التطبيق"));
        acquireWakeLock();
        return START_STICKY;
    }

    public void acquireWakeLock() {
        if (wakeLock == null || !wakeLock.isHeld()) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SunplusLoader::FlashingWakeLock");
                wakeLock.acquire(120 * 60 * 1000L);
            }
        }
    }

    public void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
        stopForeground(true);
        stopSelf();
    }

    private Notification buildNotification(String contentText) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("لودر صن بلس يعمل في الخلفية")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "خدمة تفليش صن بلس",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        releaseWakeLock();
        super.onDestroy();
    }
          }
