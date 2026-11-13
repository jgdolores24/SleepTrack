package com.example.sleeptrack;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.NotificationCompat;

public class AlarmaSonidoService extends Service {
    private static final String TAG = "SleepTrackAlarma";
    private static final String CANAL = "sleeptrack_sonando_gradual_2026";
    private static final int ID_NOTIFICACION = 420;
    private static final long DURACION_AUMENTO = 120000L;
    private static final int FLAGS = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer sonido;
    private long inicio;
    private boolean iniciado = false;

    private final Runnable aumentarVolumen = new Runnable() {
        @Override public void run() {
            if (sonido == null) return;
            float avance = Math.min(1.0f, (System.currentTimeMillis() - inicio) / (float) DURACION_AUMENTO);
            float volumen = 0.08f + 0.92f * avance;
            try { sonido.setVolume(volumen, volumen); }
            catch (RuntimeException ignored) { }
            if (avance < 1f) handler.postDelayed(this, 2000L);
        }
    };

    private final Runnable tiempoMaximo = () -> {
        DescansoActivity.finalizarSesion(this, "alarma");
        getSharedPreferences(AlarmaProgramador.PREF, MODE_PRIVATE).edit()
                .putBoolean("alarmaSonando", false).apply();
        stopSelf();
    };

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (iniciado) return START_NOT_STICKY;
        iniciado = true;
        try {
            crearCanal();
            Notification notificacion = crearNotificacion();
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(ID_NOTIFICACION, notificacion, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(ID_NOTIFICACION, notificacion);
            }
            reproducirAlarma();
            RepositorioEstadoAlarma.sincronizar(this, "SONANDO_CONFIRMADO");
            handler.postDelayed(tiempoMaximo, 5L * 60L * 1000L);
        } catch (RuntimeException e) {
            Log.e(TAG, "Error al iniciar alarma", e);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void crearCanal() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel c = new NotificationChannel(CANAL, "Alarma sonando", NotificationManager.IMPORTANCE_HIGH);
        c.setDescription("Alarma despertador de SleepTrack");
        c.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        c.setSound(null, null);
        c.enableVibration(false);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(c);
    }

    private Notification crearNotificacion() {
        Intent pantalla = new Intent(this, AlarmaSonandoActivity.class);
        pantalla.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent abrir = PendingIntent.getActivity(this, 420, pantalla, FLAGS);

        Intent posponer = new Intent(this, AlarmaReceiver.class).setAction(AlarmaProgramador.POSPONER);
        PendingIntent posponerPi = PendingIntent.getBroadcast(this, 422, posponer, FLAGS);

        Intent detener = new Intent(this, DescansoActivity.class);
        detener.setAction(DescansoActivity.ACCION_DESPERTAR);
        detener.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent detenerPi = PendingIntent.getActivity(this, 423, detener, FLAGS);

        return new NotificationCompat.Builder(this, CANAL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("SleepTrack · ¡Hora de despertar!")
                .setContentText("Posponer 10 min o detener")
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setAutoCancel(false)
                .setContentIntent(abrir)
                .setFullScreenIntent(abrir, true)
                .addAction(android.R.drawable.ic_menu_recent_history, "Posponer", posponerPi)
                .addAction(android.R.drawable.ic_media_pause, "Detener", detenerPi)
                .build();
    }

    private void reproducirAlarma() {
        Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        if (uri == null) { Log.w(TAG, "Sin tono de alarma del sistema"); return; }
        try {
            sonido = new MediaPlayer();
            sonido.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            sonido.setDataSource(this, uri);
            sonido.setLooping(true);
            sonido.prepare();
            sonido.setVolume(0.08f, 0.08f);
            sonido.start();
            inicio = System.currentTimeMillis();
            handler.post(aumentarVolumen);
        } catch (Exception e) { Log.e(TAG, "No se pudo reproducir el sonido", e); }
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(aumentarVolumen);
        handler.removeCallbacks(tiempoMaximo);
        if (sonido != null) {
            try { if (sonido.isPlaying()) sonido.stop(); } catch (RuntimeException ignored) { }
            sonido.release();
            sonido = null;
        }
        iniciado = false;
        getSharedPreferences(AlarmaProgramador.PREF, MODE_PRIVATE).edit()
                .putBoolean("alarmaSonando", false).apply();
        RepositorioEstadoAlarma.sincronizar(this, "SONIDO_ALARMA_FINALIZADO");
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
