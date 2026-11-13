package com.example.sleeptrack;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MonitoreoService extends Service {

    public static final String INICIAR = "MONITOREO_INICIAR";
    public static final String DETENER = "MONITOREO_DETENER";

    private static final String CANAL = "sleeptrack_monitoreo";
    private static final int ID_NOTIFICACION = 400;
    private static final long MAXIMO = 12L * 60 * 60 * 1000;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private boolean receptorRegistrado = false;
    private boolean sesionActiva = false;
    private final Handler latidos = new Handler(Looper.getMainLooper());
    private final Runnable renovarEstado = new Runnable() {
        @Override public void run() {
            if (!sesionActiva) return;
            RepositorioEstadoAlarma.latido(MonitoreoService.this, false);
            latidos.postDelayed(this, 60000L);
        }
    };

    private String uid, fechaRegistro;
    private long horaFinal;

    private final BroadcastReceiver receptorPantalla =
            new BroadcastReceiver() {

                @Override
                public void onReceive(Context context, Intent intent) {

                    String accion = intent.getAction();

                    if (Intent.ACTION_SCREEN_ON.equals(accion)) {
                        guardarEvento("pantalla_encendida");

                    } else if (Intent.ACTION_SCREEN_OFF.equals(accion)) {
                        guardarEvento("pantalla_apagada");

                    } else if (Intent.ACTION_USER_PRESENT.equals(accion)) {
                        guardarEvento("desbloqueo");
                    }
                }
            };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null && DETENER.equals(intent.getAction())) {

            stopSelf();
            return START_NOT_STICKY;
        }

        SharedPreferences pref = getSharedPreferences(AlarmaProgramador.PREF, MODE_PRIVATE);

        long ahora = System.currentTimeMillis();
        long proxima = pref.getLong("proximaAlarma", 0L);
        boolean descansoLibre =
                "libre".equals(pref.getString("modoDescanso", "alarma"))
                        && pref.getBoolean("sesionDescansoActiva", false);
        long restante = proxima - ahora;

        if (!pref.getBoolean("sesionDescansoActiva", false)
                || !pref.getBoolean("monitoreoTelefono", false)
                || (!descansoLibre && (restante <= 0 || restante > MAXIMO))) {

            stopSelf();
            return START_NOT_STICKY;
        }

        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();

        if (usuario == null
                || !usuario.getUid().equals(pref.getString("sesionUid", usuario.getUid()))) {
            stopSelf();
            return START_NOT_STICKY;
        }

        crearCanal();

        try {
            Notification notificacion = crearNotificacion();

            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                        ID_NOTIFICACION,
                        notificacion,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(ID_NOTIFICACION, notificacion);
            }

        } catch (RuntimeException e) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String nuevaFecha =
                pref.getBoolean("sesionDescansoActiva", false)
                        ? pref.getString("fechaDescanso", formatear(ahora, "yyyy-MM-dd"))
                        : formatear(proxima, "yyyy-MM-dd");
        if (nuevaFecha == null || nuevaFecha.isEmpty()) {
            nuevaFecha = formatear(ahora, "yyyy-MM-dd");
        }
        long finMonitoreo = descansoLibre ? Long.MAX_VALUE : proxima;

        if (!sesionActiva
                || !nuevaFecha.equals(fechaRegistro)
                || !pref.getString("sesionId", "").equals(sesionId)
                || !usuario.getUid().equals(uid)) {

            finalizarSesion();

            uid = usuario.getUid();
            sesionId = pref.getString("sesionId", "");
            fechaRegistro = nuevaFecha;
            horaFinal = finMonitoreo;
            sesionActiva = true;

            guardarInicio();

        } else {
            horaFinal = finMonitoreo;
        }

        if (!receptorRegistrado) {

            IntentFilter filtro = new IntentFilter();
            filtro.addAction(Intent.ACTION_SCREEN_ON);
            filtro.addAction(Intent.ACTION_SCREEN_OFF);
            filtro.addAction(Intent.ACTION_USER_PRESENT);

            registerReceiver(receptorPantalla, filtro);
            receptorRegistrado = true;
            RepositorioEstadoAlarma.servicio(this, false, true, sesionId);
            latidos.removeCallbacks(renovarEstado);
            latidos.postDelayed(renovarEstado, 60000L);
        }

        return START_STICKY;
    }

    private String formatear(long tiempo, String formato) {
        return new SimpleDateFormat(formato, Locale.US).format(new Date(tiempo));
    }

    private String sesionId = "";

    private void guardarInicio() {

        Map<String, Object> datos = new HashMap<>();
        datos.put("fecha", fechaRegistro);
        datos.put("inicioMonitoreo", System.currentTimeMillis());
        datos.put("origenMonitoreo", "telefono");

        referencia().set(datos, SetOptions.merge());
    }

    private void guardarEvento(String tipo) {

        if (!sesionActiva) return;

        SharedPreferences pref = getSharedPreferences(AlarmaProgramador.PREF, MODE_PRIVATE);
        if (!pref.getBoolean("sesionDescansoActiva", false)
                || !sesionId.equals(pref.getString("sesionId", ""))) return;

        long ahora = System.currentTimeMillis();

        if (ahora >= horaFinal) return;

        Map<String, Object> evento = new HashMap<>();
        evento.put("sesionId", sesionId);
        evento.put("tipo", tipo);
        evento.put("hora", formatear(ahora, "HH:mm:ss"));
        evento.put("timestamp", ahora);

        Map<String, Object> datos = new HashMap<>();

        datos.put("eventosTelefono", FieldValue.arrayUnion(evento));

        RepositorioDescanso.guardarEventos(uid, fechaRegistro, sesionId, datos)
                .addOnSuccessListener(ignorado -> {
                    if ("pantalla_encendida".equals(tipo)) {
                        RepositorioEstadoAlarma.eventoConfirmado(this, false, ahora, sesionId);
                    }
                });
    }

    private com.google.firebase.firestore.DocumentReference referencia() {

        com.google.firebase.firestore.DocumentReference dia =
                db.collection("usuarios")
                        .document(uid)
                        .collection("registrosSueno")
                        .document(fechaRegistro);
        return sesionId.isEmpty() ? dia : dia.collection("sesiones").document(sesionId);
    }

    private void finalizarSesion() {

        if (!sesionActiva || uid == null) return;

        sesionActiva = false;

        Map<String, Object> datos = new HashMap<>();
        datos.put("finMonitoreo", System.currentTimeMillis());

        SharedPreferences pref = getSharedPreferences(AlarmaProgramador.PREF, MODE_PRIVATE);
        if (!pref.getBoolean("sesionDescansoActiva", false)
                && sesionId.equals(pref.getString("sesionId", ""))) {
            String fechaFinal = pref.getString("fechaDescanso", fechaRegistro);
            if (!fechaFinal.equals(fechaRegistro)) {
                db.collection("usuarios")
                        .document(uid)
                        .collection("registrosSueno")
                        .document(fechaFinal)
                        .collection("sesiones")
                        .document(sesionId)
                        .set(datos, SetOptions.merge());
                return;
            }
        }
        referencia().set(datos, SetOptions.merge());
    }

    private void crearCanal() {

        if (Build.VERSION.SDK_INT < 26) return;

        NotificationChannel canal =
                new NotificationChannel(
                        CANAL, "Monitoreo nocturno", NotificationManager.IMPORTANCE_LOW);

        canal.setDescription("Registro de actividad nocturna del teléfono");

        canal.setSound(null, null);

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (manager != null) {
            manager.createNotificationChannel(canal);
        }
    }

    private Notification crearNotificacion() {

        Intent abrir = new Intent(this, alarma.class);

        PendingIntent abrirAlarma =
                PendingIntent.getActivity(
                        this,
                        401,
                        abrir,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent detener = new Intent(this, MonitoreoService.class);
        detener.setAction(DETENER);

        PendingIntent detenerMonitoreo =
                PendingIntent.getService(
                        this,
                        402,
                        detener,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CANAL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("SleepTrack · Monitoreo activo")
                .setContentText("Registrando actividad nocturna del teléfono")
                .setContentIntent(abrirAlarma)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(android.R.drawable.ic_delete, "Detener monitoreo", detenerMonitoreo)
                .build();
    }

    @Override
    public void onDestroy() {
        latidos.removeCallbacks(renovarEstado);
        if (uid != null) RepositorioEstadoAlarma.servicio(this, false, false, sesionId);

        if (receptorRegistrado) {
            unregisterReceiver(receptorPantalla);
            receptorRegistrado = false;
        }

        finalizarSesion();

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
