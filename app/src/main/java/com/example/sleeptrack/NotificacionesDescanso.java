package com.example.sleeptrack;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public final class NotificacionesDescanso {
    private static final String CANAL = "sleeptrack_descanso_activo_2026";
    private static final int ID = 421;

    private NotificacionesDescanso() { }

    public static void mostrar(Context context) {
        SharedPreferences pref = context.getSharedPreferences(AlarmaProgramador.PREF, Context.MODE_PRIVATE);
        if (!pref.getBoolean("sesionDescansoActiva", false)) return;
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel canal = new NotificationChannel(CANAL, "Descanso en curso", NotificationManager.IMPORTANCE_LOW);
            canal.setSound(null, null);
            canal.enableVibration(false);
            manager.createNotificationChannel(canal);
        }
        Intent abrir = new Intent(context, DescansoActivity.class);
        PendingIntent pending = PendingIntent.getActivity(context, 701, abrir,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long proxima = pref.getLong("proximaAlarma", 0L);
        boolean libre = "libre".equals(pref.getString("modoDescanso", "alarma"));
        String detalle = libre ? "Sin alarma · Toca para ver o finalizar" :
                proxima > System.currentTimeMillis() ? "Alarma programada · Toca para abrir" :
                        "Toca para ver tu descanso";
        NotificationCompat.Builder n = new NotificationCompat.Builder(context, CANAL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("SleepTrack · Descanso en curso")
                .setContentText(detalle)
                .setContentIntent(pending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE);
        if (!libre && proxima > System.currentTimeMillis()) {
            n.setWhen(proxima);
            if (Build.VERSION.SDK_INT >= 24) {
                n.setUsesChronometer(true);
                n.setChronometerCountDown(true);
            }
        } else {
            n.setWhen(pref.getLong("inicioDescanso", System.currentTimeMillis()));
            n.setUsesChronometer(true);
        }
        try { NotificationManagerCompat.from(context).notify(ID, n.build()); }
        catch (SecurityException ignored) { }
    }

    public static void ocultar(Context context) {
        NotificationManagerCompat.from(context).cancel(ID);
    }
}
