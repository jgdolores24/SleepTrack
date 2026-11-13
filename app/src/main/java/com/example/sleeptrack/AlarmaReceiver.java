package com.example.sleeptrack;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.core.content.ContextCompat;

public class AlarmaReceiver extends BroadcastReceiver {

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String accion = intent.getAction();
        MigracionAlarmaV5.ajustarPreferencias(context);
        SharedPreferences pref = context.getSharedPreferences(AlarmaProgramador.PREF, Context.MODE_PRIVATE);
        if (AlarmaProgramador.DISPARAR.equals(accion)
                || AlarmaProgramador.INICIAR_MONITOREO.equals(accion)) {
            com.google.firebase.auth.FirebaseUser actual =
                    com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            String uidAlarma = pref.getString("alarmaUid", "");
            if (!uidAlarma.isEmpty()
                    && (actual == null || !uidAlarma.equals(actual.getUid()))) {
                AlarmaProgramador.cancelar(context);
                return;
            }
        }

        if (AlarmaProgramador.CANCELAR.equals(accion)) {
            DescansoActivity.finalizarSesion(context, "cancelada");
            AlarmaProgramador.cancelar(context);
            return;
        }

        if (AlarmaProgramador.POSPONER.equals(accion)) {
            if (!pref.getBoolean("alarmaSonando", false)) return;
            context.stopService(new Intent(context, AlarmaSonidoService.class));
            pref.edit().putBoolean("alarmaSonando", false).apply();
            AlarmaProgramador.programarPosponer(context, 10);
            RepositorioEstadoAlarma.sincronizar(context, "POSPUESTA");
            NotificacionesDescanso.mostrar(context);
            return;
        }

        if (AlarmaProgramador.INICIAR_MONITOREO.equals(accion)) {
            if (!pref.getBoolean("activa", false) || pref.getInt("dias", 0) == 0) return;
            AlarmaProgramador.programarInicioMonitoreo(context);
            if (pref.getBoolean("sesionDescansoActiva", false)) return;
            SesionDescansoHelper.iniciar(context, "programada");
            return;
        }

        if (AlarmaProgramador.DISPARAR.equals(accion)) {
            if (!pref.getBoolean("activa", false)) return;
            boolean esPospuesta = intent.getBooleanExtra("pospuesta", false);
            // La sesión termina al sonar, no al apagar o posponer el despertador.
            if (pref.getBoolean("sesionDescansoActiva", false)) {
                DescansoActivity.finalizarSesion(context, "alarma");
            }
            pref.edit().putBoolean("alarmaSonando", true).apply();
            context.stopService(new Intent(context, MonitoreoService.class));
            context.stopService(new Intent(context, MonitoreoSonidoService.class));
            AlarmaProgramador.ocultarNotificacion(context);
            NotificacionesDescanso.ocultar(context);

            int dias = pref.getInt("dias", 0);
            if (!esPospuesta && dias != 0 && AlarmaProgramador.tienePermiso(context)) {
                AlarmaProgramador.programar(context);
            } else if (dias == 0) {
                pref.edit().putBoolean("activa", false).apply();
            }
            RepositorioEstadoAlarma.sincronizar(context, "ALARMA_SONANDO");
            try {
                ContextCompat.startForegroundService(context, new Intent(context, AlarmaSonidoService.class));
            } catch (RuntimeException e) {
                android.util.Log.e("SleepTrackAlarma", "No se pudo iniciar el sonido", e);
            }
            return;
        }

        if (Intent.ACTION_BOOT_COMPLETED.equals(accion)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(accion)
                || Intent.ACTION_TIME_CHANGED.equals(accion)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(accion)
                || "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED".equals(accion)) {
            if (pref.getBoolean("activa", false) && AlarmaProgramador.tienePermiso(context)) {
                AlarmaProgramador.restaurarTrasReinicio(context);
            }
            if (pref.getBoolean("sesionDescansoActiva", false)) NotificacionesDescanso.mostrar(context);
            RepositorioEstadoAlarma.sincronizar(context, "RESTAURACION_SISTEMA");
        }
    }
}
