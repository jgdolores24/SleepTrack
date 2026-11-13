package com.example.sleeptrack;

import android.Manifest;
import android.app.AlarmManager;
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
import java.util.Calendar;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class AlarmaProgramador {
    public static final String PREF = "sleeptrack_alarma";
    public static final String DISPARAR = "com.example.sleeptrack.DISPARAR_ALARMA";
    public static final String INICIAR_MONITOREO = "com.example.sleeptrack.INICIAR_MONITOREO_PROGRAMADO";
    public static final String CANCELAR = "com.example.sleeptrack.CANCELAR_ALARMA";
    public static final String POSPONER = "com.example.sleeptrack.POSPONER_ALARMA";

    private static final String CANAL = "proxima_alarma";
    private static final int ID_NOTIFICACION = 300;
    private static final int FLAGS = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

    private AlarmaProgramador() { }

    public static boolean tienePermiso(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return manager != null && (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms());
    }

    public static void mostrarNotificacion(Context context) {
        SharedPreferences pref = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        long proxima = pref.getLong("proximaAlarma", 0L);
        if (proxima <= System.currentTimeMillis()) return;
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel canal = new NotificationChannel(CANAL, "Próximas alarmas", NotificationManager.IMPORTANCE_LOW);
            canal.setSound(null, null);
            canal.enableVibration(false);
            manager.createNotificationChannel(canal);
        }

        Intent abrirPantalla = new Intent(context, DescansoActivity.class);
        PendingIntent abrir = PendingIntent.getActivity(context, 301, abrirPantalla, FLAGS);
        Intent cancelar = new Intent(context, AlarmaReceiver.class).setAction(CANCELAR);
        PendingIntent cancelarIntento = PendingIntent.getBroadcast(context, 302, cancelar, FLAGS);
        String hora = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(proxima));
        NotificationCompat.Builder n = new NotificationCompat.Builder(context, CANAL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("SleepTrack · Próxima alarma " + hora)
                .setContentText("Toca para ver tu descanso")
                .setContentIntent(abrir)
                .setWhen(proxima)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancelar alarma", cancelarIntento);
        if (Build.VERSION.SDK_INT >= 24) {
            n.setUsesChronometer(true);
            n.setChronometerCountDown(true);
        }
        try { NotificationManagerCompat.from(context).notify(ID_NOTIFICACION, n.build()); }
        catch (SecurityException ignored) { }
    }

    public static void ocultarNotificacion(Context context) {
        NotificationManagerCompat.from(context).cancel(ID_NOTIFICACION);
    }

    private static PendingIntent despertar(Context context) {
        return PendingIntent.getBroadcast(context, 100,
                new Intent(context, AlarmaReceiver.class).setAction(DISPARAR), FLAGS);
    }

    private static PendingIntent pospuesto(Context context) {
        return PendingIntent.getBroadcast(context, 105,
                new Intent(context, AlarmaReceiver.class).setAction(DISPARAR).putExtra("pospuesta", true), FLAGS);
    }

    private static PendingIntent inicioPendiente(Context context) {
        return PendingIntent.getBroadcast(context, 103,
                new Intent(context, AlarmaReceiver.class).setAction(INICIAR_MONITOREO), FLAGS);
    }

    private static PendingIntent pantalla(Context context) {
        return PendingIntent.getActivity(context, 101, new Intent(context, alarma.class), FLAGS);
    }

    public static boolean programar(Context context) {
        MigracionAlarmaV5.ajustarPreferencias(context);
        SharedPreferences pref = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if (!pref.getBoolean("activa", false) || !tienePermiso(context)) return false;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return false;

        int dias = pref.getInt("dias", 0);
        boolean duracion = pref.getBoolean("modoDuracion", false) && dias == 0;
        long proxima;
        if (dias != 0) {
            HorarioRepeticion.SesionProgramada sesion = HorarioRepeticion.siguiente(
                    dias, pref.getString("horaInicioMonitoreo", "22:00"),
                    pref.getString("hora", "07:00"), System.currentTimeMillis(),
                    pref.getBoolean("sesionDescansoActiva", false));
            if (sesion == null) return false;
            proxima = sesion.getDespertar();
        } else if (duracion) {
            int minutos = pref.getInt("duracionMinutos", 30);
            if (minutos < 1 || minutos > 720) return false;
            proxima = System.currentTimeMillis() + minutos * 60000L;
        } else {
            String horaGuardada = pref.getString("hora", "07:00");
            if (horaGuardada == null || !horaGuardada.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")) {
                return false;
            }
            String[] partes = horaGuardada.split(":");
            int h = Integer.parseInt(partes[0]);
            int m = Integer.parseInt(partes[1]);
            long resultado = 0L;
            for (int i = 0; i <= 7; i++) {
                Calendar fecha = Calendar.getInstance();
                fecha.add(Calendar.DAY_OF_MONTH, i);
                fecha.set(Calendar.HOUR_OF_DAY, h);
                fecha.set(Calendar.MINUTE, m);
                fecha.set(Calendar.SECOND, 0);
                fecha.set(Calendar.MILLISECOND, 0);
                if (fecha.getTimeInMillis() <= System.currentTimeMillis()) continue;
                if (dias != 0 && (dias & (1 << fecha.get(Calendar.DAY_OF_WEEK))) == 0) continue;
                resultado = fecha.getTimeInMillis();
                break;
            }
            if (resultado <= 0) return false;
            proxima = resultado;
        }
        try {
            manager.setAlarmClock(new AlarmManager.AlarmClockInfo(proxima, pantalla(context)), despertar(context));
            pref.edit().putLong("proximaAlarma", proxima).apply();
            if (!programarInicioMonitoreo(context)) {
                manager.cancel(despertar(context));
                pref.edit().putLong("proximaAlarma", 0L).apply();
                return false;
            }
            mostrarNotificacion(context);
            RepositorioEstadoAlarma.sincronizar(context, "PROGRAMACION_CONFIRMADA");
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    public static boolean programarPosponer(Context context, int minutos) {
        if (!tienePermiso(context)) return false;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return false;
        long proxima = System.currentTimeMillis() + minutos * 60000L;
        SharedPreferences pref = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        long siguienteRepeticion = pref.getInt("dias", 0) != 0
                ? pref.getLong("proximaAlarma", 0L) : 0L;
        try {
            manager.setAlarmClock(new AlarmManager.AlarmClockInfo(proxima, pantalla(context)), pospuesto(context));
            pref.edit().putLong("siguienteRepeticion", siguienteRepeticion)
                    .putBoolean("posponiendo", true)
                    .putBoolean("activa", true)
                    .putBoolean("alarmaSonando", false)
                    .putLong("proximaAlarma", proxima).apply();
            mostrarNotificacion(context);
            RepositorioEstadoAlarma.sincronizar(context, "ALARMA_POSPUESTA");
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    public static boolean programarInicioMonitoreo(Context context) {
        SharedPreferences pref = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return false;
        PendingIntent evento = inicioPendiente(context);
        manager.cancel(evento);
        pref.edit().remove("proximoInicioMonitoreo").apply();
        int dias = pref.getInt("dias", 0);
        if (!pref.getBoolean("activa", false) || dias == 0) return true;
        if (!tienePermiso(context)) return false;

        HorarioRepeticion.SesionProgramada sesion = HorarioRepeticion.siguiente(
                dias, pref.getString("horaInicioMonitoreo", "22:00"),
                pref.getString("hora", "07:00"), System.currentTimeMillis(), false);
        if (sesion == null) return false;
        try {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, sesion.getInicio(), evento);
            pref.edit().putLong("proximoInicioMonitoreo", sesion.getInicio()).apply();
            RepositorioEstadoAlarma.sincronizar(context, "INICIO_PROGRAMADO");
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    public static void restaurarRepeticionTrasPosponer(Context context) {
        SharedPreferences pref = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if (!pref.getBoolean("posponiendo", false)) return;
        long siguiente = pref.getLong("siguienteRepeticion", 0L);
        SharedPreferences.Editor editor = pref.edit().putBoolean("posponiendo", false)
                .remove("siguienteRepeticion");
        if (pref.getInt("dias", 0) != 0 && siguiente > System.currentTimeMillis()) {
            editor.putLong("proximaAlarma", siguiente).apply();
            mostrarNotificacion(context);
        } else {
            editor.apply();
        }
    }

    public static boolean restaurarTrasReinicio(Context context) {
        SharedPreferences pref = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if (!pref.getBoolean("activa", false) || !tienePermiso(context)) return false;
        long guardada = pref.getLong("proximaAlarma", 0L);
        if ((pref.getBoolean("modoDuracion", false) || pref.getBoolean("posponiendo", false))
                && guardada > System.currentTimeMillis()) {
            AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (manager == null) return false;
            boolean pospuesta = pref.getBoolean("posponiendo", false);
            try {
                manager.setAlarmClock(new AlarmManager.AlarmClockInfo(guardada, pantalla(context)),
                        pospuesta ? pospuesto(context) : despertar(context));
                mostrarNotificacion(context);
                RepositorioEstadoAlarma.sincronizar(context, "ALARMA_RESTAURADA");
                return true;
            } catch (SecurityException e) { return false; }
        }
        return programar(context);
    }

    public static void cancelar(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) {
            manager.cancel(despertar(context));
            manager.cancel(pospuesto(context));
            manager.cancel(inicioPendiente(context));
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putBoolean("activa", false)
                .putLong("proximaAlarma", 0L)
                .remove("proximoInicioMonitoreo")
                .putBoolean("posponiendo", false)
                .remove("siguienteRepeticion").apply();
        ocultarNotificacion(context);
        RepositorioEstadoAlarma.sincronizar(context, "ALARMA_CANCELADA");
    }
}
