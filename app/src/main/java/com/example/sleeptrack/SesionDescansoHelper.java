package com.example.sleeptrack;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;
import androidx.core.content.ContextCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import java.util.Map;

public final class SesionDescansoHelper {
    private SesionDescansoHelper() {}

    public static boolean iniciar(Context context, String modo) {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) return false;
        SharedPreferences pref =
                context.getSharedPreferences(AlarmaProgramador.PREF, Context.MODE_PRIVATE);
        String uidAlarma = pref.getString("alarmaUid", "");
        if (!uidAlarma.isEmpty() && !uidAlarma.equals(usuario.getUid())) return false;
        if (pref.getBoolean("sesionDescansoActiva", false)
                && usuario.getUid().equals(pref.getString("sesionUid", usuario.getUid()))) {
            NotificacionesDescanso.mostrar(context);
            return true;
        }
        String sesionId = java.util.UUID.randomUUID().toString();
        long ahora = System.currentTimeMillis();
        String fecha = FechasDescanso.fechaInicio(ahora, java.util.TimeZone.getDefault());
        pref.edit()
                .putBoolean("sesionDescansoActiva", true)
                .putBoolean("evaluacionPendiente", false)
                .putString("sesionId", sesionId)
                .putString("sesionUid", usuario.getUid())
                .putLong("inicioDescanso", ahora)
                .putString("fechaDescanso", fecha)
                .putString("fechaInicioDescanso", fecha)
                .putString("zonaHorariaDescanso", java.util.TimeZone.getDefault().getID())
                .putString("modoDescanso", modo)
                .putBoolean("telefonoServicioActivo", false)
                .putBoolean("sonidoServicioActivo", false)
                .apply();
        Map<String, Object> datos = FirestoreOrden.inicioSesion(ahora);
        datos.put("versionEsquema", 4);
        datos.put("fecha", fecha);
        datos.put("fechaInicio", fecha);
        datos.put("modo", modo);
        datos.put("zonaHoraria", java.util.TimeZone.getDefault().getID());
        FirebaseFirestore.getInstance()
                .collection("usuarios")
                .document(usuario.getUid())
                .collection("registrosSueno")
                .document(fecha)
                .collection("sesiones")
                .document(sesionId)
                .set(datos, SetOptions.merge())
                .addOnFailureListener(
                        e -> {
                            Log.e("SleepTrack", "No se pudo guardar la sesión", e);
                            android.widget.Toast.makeText(
                                            context,
                                            "No se pudo guardar el descanso. Revisa la conexión y"
                                                + " las reglas de Firebase.",
                                            android.widget.Toast.LENGTH_LONG)
                                    .show();
                        });
        RepositorioEstadoAlarma.iniciarSesion(context);
        RepositorioEstadoAlarma.sincronizar(context, "INICIO_DESCANSO");
        iniciarServicios(context, pref);
        NotificacionesDescanso.mostrar(context);
        return true;
    }

    private static void iniciarServicios(Context context, SharedPreferences pref) {
        if (pref.getBoolean("monitoreoTelefono", false)) {
            try {
                Intent i = new Intent(context, MonitoreoService.class);
                i.setAction(MonitoreoService.INICIAR);
                ContextCompat.startForegroundService(context, i);
            } catch (RuntimeException e) {
                Log.e("SleepTrack", "No se pudo iniciar el monitoreo del teléfono", e);
                RepositorioEstadoAlarma.servicio(context, false, false);
            }
        }
        if (pref.getBoolean("grabacionSonido", false)
                && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED) {
            try {
                Intent i = new Intent(context, MonitoreoSonidoService.class);
                i.setAction(MonitoreoSonidoService.INICIAR);
                ContextCompat.startForegroundService(context, i);
            } catch (RuntimeException e) {
                Log.e("SleepTrack", "No se pudo iniciar la detección de sonidos", e);
                RepositorioEstadoAlarma.servicio(context, true, false);
            }
        }
    }
}
