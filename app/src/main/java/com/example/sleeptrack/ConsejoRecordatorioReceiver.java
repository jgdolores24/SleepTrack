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
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class ConsejoRecordatorioReceiver extends BroadcastReceiver {
    private static final String PREF = "sleeptrack_recordatorios";
    private static final String CANAL = "sleeptrack_habitos";
    private static final String ACCION = "com.example.sleeptrack.RECORDATORIO_HABITO";
    public static final String[] TIPOS = {"rutina", "cafeina", "estres", "registro", "alcohol", "pantallas", "suplementos", "molestias"};
    public static final String[] NOMBRES = {"Preparar descanso", "Evitar cafeína", "Relajación", "Evaluar mi descanso", "Evitar alcohol", "Dejar pantallas", "Registrar suplementos", "Registrar molestias"};
    public static final String[] MENSAJES = {
            "Prepara un ambiente tranquilo para tu descanso.",
            "Recuerda tu propósito de evitar cafeína cerca de dormir.",
            "Dedica unos minutos a relajarte antes de acostarte.",
            "Registra cómo te sentiste al despertar.",
            "Recuerda tu propósito de evitar alcohol hoy antes de dormir.",
            "Es momento de reducir las pantallas antes de descansar.",
            "Anota los suplementos que tomaste y su horario.",
            "Anota si tuviste molestias y su intensidad."
    };

    private static SharedPreferences preferencias(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static String usuario() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        return u == null ? "" : u.getUid();
    }

    private static int codigo(String tipo) {
        for (int i = 0; i < TIPOS.length; i++) if (TIPOS[i].equals(tipo)) return 701 + i;
        return 0;
    }

    private static PendingIntent intent(Context c, String tipo) {
        Intent i = new Intent(c, ConsejoRecordatorioReceiver.class).setAction(ACCION);
        i.putExtra("tipo", tipo);
        return PendingIntent.getBroadcast(c, codigo(tipo), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void crearCanal(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            nm.createNotificationChannel(new NotificationChannel(CANAL,
                    "Recordatorios de descanso", NotificationManager.IMPORTANCE_DEFAULT));
        }
    }

    public static boolean notificacionesPermitidas(Context c) {
        crearCanal(c);
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(c,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        if (!NotificationManagerCompat.from(c).areNotificationsEnabled()) return false;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            NotificationChannel canal = nm.getNotificationChannel(CANAL);
            if (canal != null && canal.getImportance() == NotificationManager.IMPORTANCE_NONE) return false;
        }
        return true;
    }

    public static boolean programar(Context c, String tipo, String mensaje, long cuando, boolean diario) {
        if (codigo(tipo) == 0 || usuario().isEmpty() || cuando <= System.currentTimeMillis()
                || !notificacionesPermitidas(c)) return false;
        Calendar hora = Calendar.getInstance();
        hora.setTimeInMillis(cuando);
        SharedPreferences p = preferencias(c);
        p.edit().putBoolean(tipo + "_activo", false).apply();
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return false;
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando, intent(c, tipo));
            p.edit().putString(tipo + "_uid", usuario()).putString(tipo + "_mensaje", mensaje)
                    .putLong(tipo + "_cuando", cuando).putBoolean(tipo + "_diario", diario)
                    .putInt(tipo + "_hora", hora.get(Calendar.HOUR_OF_DAY))
                    .putInt(tipo + "_minuto", hora.get(Calendar.MINUTE))
                    .putBoolean(tipo + "_activo", true).apply();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean activo(Context c, String tipo) {
        SharedPreferences p = preferencias(c);
        return !usuario().isEmpty() && usuario().equals(p.getString(tipo + "_uid", ""))
                && p.getBoolean(tipo + "_activo", false);
    }

    public static String descripcion(Context c, String tipo) {
        if (!activo(c, tipo)) return "Sin programar";
        SharedPreferences p = preferencias(c);
        String formato = p.getBoolean(tipo + "_diario", false) ? "HH:mm" : "dd/MM HH:mm";
        return (p.getBoolean(tipo + "_diario", false) ? "Diario · " : "Una vez · ")
                + new SimpleDateFormat(formato, Locale.getDefault())
                .format(new java.util.Date(p.getLong(tipo + "_cuando", 0)));
    }

    public static void cancelar(Context c, String tipo) {
        if (codigo(tipo) == 0) return;
        preferencias(c).edit().putBoolean(tipo + "_activo", false).apply();
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = intent(c, tipo);
        if (am != null) am.cancel(pi);
        pi.cancel();
        NotificationManagerCompat.from(c).cancel(codigo(tipo));
    }

    private static long proximaHora(SharedPreferences p, String tipo) {
        return HorarioRecordatorio.siguiente(System.currentTimeMillis(),
                p.getInt(tipo + "_hora", 21), p.getInt(tipo + "_minuto", 0),
                java.util.TimeZone.getDefault());
    }

    public static void restaurar(Context c) {
        SharedPreferences p = preferencias(c);
        for (String tipo : TIPOS) {
            if (!activo(c, tipo)) continue;
            boolean diario = p.getBoolean(tipo + "_diario", false);
            long cuando = diario ? proximaHora(p, tipo) : p.getLong(tipo + "_cuando", 0);
            if (cuando <= System.currentTimeMillis()) {
                // An already delivered/missed one-off is never silently repeated tomorrow.
                cancelar(c, tipo);
            } else {
                programar(c, tipo, p.getString(tipo + "_mensaje", "Cuida tu descanso"), cuando, diario);
            }
        }
    }

    @Override
    public void onReceive(Context c, Intent i) {
        String accion = i.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(accion) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(accion)
                || Intent.ACTION_TIME_CHANGED.equals(accion) || Intent.ACTION_TIMEZONE_CHANGED.equals(accion)) {
            restaurar(c);
            return;
        }
        if (!ACCION.equals(accion)) return;
        String tipo = i.getStringExtra("tipo");
        if (tipo == null || codigo(tipo) == 0 || !activo(c, tipo)) return;
        SharedPreferences p = preferencias(c);
        long esperado = p.getLong(tipo + "_cuando", 0);
        if (System.currentTimeMillis() < esperado) return;
        String mensaje = p.getString(tipo + "_mensaje", "Cuida tu descanso");
        boolean diario = p.getBoolean(tipo + "_diario", false);
        p.edit().putBoolean(tipo + "_activo", false).apply();
        if (notificacionesPermitidas(c) && System.currentTimeMillis() - esperado < 6 * 60 * 60 * 1000L) {
            Intent abrir = new Intent(c, consejos.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent contenido = PendingIntent.getActivity(c, codigo(tipo), abrir,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            NotificationManagerCompat.from(c).notify(codigo(tipo), new NotificationCompat.Builder(c, CANAL)
                    .setSmallIcon(R.drawable.st_co_notification)
                    .setContentTitle("SleepTrack · Recordatorio")
                    .setContentText(mensaje)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(mensaje))
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setContentIntent(contenido).setAutoCancel(true).build());
        }
        if (diario) programar(c, tipo, mensaje, proximaHora(p, tipo), true);
    }
}
