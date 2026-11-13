package com.example.sleeptrack;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RecordatorioChatReceiver extends BroadcastReceiver {

    private static final String ARCHIVO = "sleeptrack_recordatorios_chat";
    private static final String CLAVE = "pendientes";
    private static final String ACCION = "com.example.sleeptrack.RECORDATORIO_CHAT.";
    private static final String CANAL = "sleeptrack_recordatorios_chat";

    static class Entrada {
        final String id;
        final String uid;
        final String nombre;
        final long fecha;

        Entrada(String id, String uid, String nombre, long fecha) {
            this.id = id;
            this.uid = uid;
            this.nombre = nombre;
            this.fecha = fecha;
        }
    }

    private static List<Entrada> leer(Context contexto) {
        SharedPreferences prefs = contexto.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE);
        List<Entrada> lista = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(CLAVE, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o != null) {
                    String id = o.optString("id", "");
                    String uid = o.optString("uid", "");
                    String nombre = o.optString("nombre", "");
                    long fecha = o.optLong("fecha", 0L);
                    if (!id.isEmpty() && !uid.isEmpty() && !nombre.isEmpty() && fecha > 0)
                        lista.add(new Entrada(id, uid, nombre, fecha));
                }
            }
        } catch (Exception ignored) { }
        return lista;
    }

    private static boolean escribir(Context contexto, List<Entrada> lista) {
        JSONArray array = new JSONArray();
        try {
            for (Entrada e : lista) {
                JSONObject objeto = new JSONObject();
                objeto.put("id", e.id);
                objeto.put("uid", e.uid);
                objeto.put("nombre", e.nombre);
                objeto.put("fecha", e.fecha);
                array.put(objeto);
            }
            return contexto.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)
                    .edit().putString(CLAVE, array.toString()).commit();
        } catch (Exception error) {
            return false;
        }
    }

    public static List<Entrada> listar(Context contexto, String uid) {
        List<Entrada> resultado = new ArrayList<>();
        long ahora = System.currentTimeMillis();
        for (Entrada e : leer(contexto)) {
            if (uid.equals(e.uid) && e.fecha > ahora) resultado.add(e);
        }
        resultado.sort((a, b) -> Long.compare(a.fecha, b.fecha));
        return resultado;
    }

    public static String crear(Context contexto, String uid, String nombre, long fecha) {
        if (fecha <= System.currentTimeMillis() || uid == null || uid.isEmpty()) return null;
        List<Entrada> lista = leer(contexto);
        if (lista.size() >= 50) return null;
        Entrada nuevo = new Entrada(UUID.randomUUID().toString(), uid, nombre, fecha);
        lista.add(nuevo);
        if (!escribir(contexto, lista)) return null;
        try {
            programar(contexto, nuevo);
            return nuevo.id;
        } catch (Exception error) {
            borrar(contexto, nuevo.id);
            return null;
        }
    }

    private static PendingIntent pendiente(Context contexto, String id) {
        Intent intent = new Intent(contexto, RecordatorioChatReceiver.class);
        intent.setAction(ACCION + id);
        intent.putExtra("recordatorioId", id);
        return PendingIntent.getBroadcast(contexto, id.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void programar(Context contexto, Entrada entrada) {
        AlarmManager manager = (AlarmManager) contexto.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) throw new IllegalStateException("AlarmManager no disponible");
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, entrada.fecha,
                pendiente(contexto, entrada.id));
    }

    private static void borrar(Context contexto, String id) {
        List<Entrada> lista = leer(contexto);
        lista.removeIf(e -> e.id.equals(id));
        escribir(contexto, lista);
    }

    public static void cancelar(Context contexto, String id) {
        AlarmManager manager = (AlarmManager) contexto.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) manager.cancel(pendiente(contexto, id));
        borrar(contexto, id);
    }

    @Override
    public void onReceive(Context contexto, Intent intent) {
        if (intent == null) return;
        String accion = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(accion)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(accion)) {
            for (Entrada e : leer(contexto)) {
                if (e.fecha > System.currentTimeMillis()) {
                    try { programar(contexto, e); } catch (Exception ignored) { }
                } else {
                    borrar(contexto, e.id);
                }
            }
            return;
        }
        if (accion == null || !accion.startsWith(ACCION)) return;
        String id = intent.getStringExtra("recordatorioId");
        if (id == null) return;
        Entrada actual = null;
        for (Entrada e : leer(contexto)) {
            if (e.id.equals(id)) {
                actual = e;
                break;
            }
        }
        if (actual == null) return;
        borrar(contexto, id);
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null || !actual.uid.equals(usuario.getUid())) return;
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) return;
        if (!NotificationManagerCompat.from(contexto).areNotificationsEnabled()) return;
        mostrarNotificacion(contexto, actual);
    }

    private static void mostrarNotificacion(Context contexto, Entrada e) {
        NotificationManager nm = (NotificationManager)
                contexto.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(CANAL, "Recordatorios del chat",
                    NotificationManager.IMPORTANCE_DEFAULT);
            canal.setDescription("Recordatorios personalizados de SleepTrack");
            nm.createNotificationChannel(canal);
        }
        Intent abrir = new Intent(contexto, consejos.class);
        abrir.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent destino = PendingIntent.getActivity(contexto, e.id.hashCode(), abrir,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder notificacion = new NotificationCompat.Builder(contexto, CANAL)
                .setSmallIcon(R.drawable.sleeptrack_logo_s)
                .setContentTitle("SleepTrack · Recordatorio")
                .setContentText(e.nombre)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(e.nombre))
                .setContentIntent(destino)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);
        nm.notify(e.id.hashCode(), notificacion.build());
    }
}
