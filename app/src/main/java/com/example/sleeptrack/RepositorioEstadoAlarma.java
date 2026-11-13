package com.example.sleeptrack;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;
import androidx.core.content.ContextCompat;
import com.example.sleeptrack.modelo.EstadoAlarmaBean;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


public final class RepositorioEstadoAlarma {
    private static final String TAG = "SleepTrackEstado";
    private static final long VIGENCIA_MS = 2 * 60 * 1000L;
    private static final String LLAVE_EQUIPO = "dispositivoEstadoId";

    private RepositorioEstadoAlarma() { }

    private static SharedPreferences pref(Context context) {
        return context.getApplicationContext().getSharedPreferences(
                AlarmaProgramador.PREF, Context.MODE_PRIVATE);
    }

    private static DocumentReference referencia(Context context) {
        FirebaseUser actual = FirebaseAuth.getInstance().getCurrentUser();
        if (actual == null) return null;
        SharedPreferences preferencias = pref(context);
        String uidAlarma = preferencias.getString("alarmaUid", "");
        if (!uidAlarma.isEmpty() && !uidAlarma.equals(actual.getUid())) return null;
        String uidSesion = preferencias.getString("sesionUid", "");
        if (preferencias.getBoolean("sesionDescansoActiva", false)
                && !uidSesion.isEmpty() && !uidSesion.equals(actual.getUid())) return null;
        return FirebaseFirestore.getInstance().collection("usuarios")
                .document(actual.getUid()).collection("estadoActual").document("alarma");
    }

    private static String dispositivo(Context context) {
        SharedPreferences preferencias = pref(context);
        String id = preferencias.getString(LLAVE_EQUIPO, "");
        if (!id.isEmpty()) return id;
        id = UUID.randomUUID().toString();
        preferencias.edit().putString(LLAVE_EQUIPO, id).apply();
        return id;
    }

    private static void escribir(Context context, Map<String, Object> datos) {
        DocumentReference doc = referencia(context);
        if (doc == null) return;
        datos.put("versionEsquema", 4);
        datos.put("dispositivoId", dispositivo(context));
        datos.put("ultimaSincronizacion", FieldValue.serverTimestamp());
        doc.set(datos, SetOptions.merge())
                .addOnFailureListener(e -> Log.w(TAG, "Sin sincronización de estado", e));
    }

    private static Timestamp tiempo(long millis) {
        return millis > 0 ? new Timestamp(new Date(millis)) : null;
    }

    /** Actualiza la programación sin sobrescribir señales ni contadores de los servicios. */
    public static void sincronizar(Context context, String motivo) {
        SharedPreferences preferencias = pref(context);
        if (!preferencias.getBoolean("configurada", false)
                && !preferencias.getBoolean("activa", false)
                && !preferencias.getBoolean("sesionDescansoActiva", false)) return;
        long ahora = System.currentTimeMillis();
        long proxima = preferencias.getLong("proximaAlarma", 0L);
        boolean sonando = preferencias.getBoolean("alarmaSonando", false);
        boolean programada = preferencias.getBoolean("activa", false)
                && proxima > ahora && AlarmaProgramador.tienePermiso(context);
        boolean fallo = preferencias.getBoolean("errorProgramacionAlarma", false)
                || (preferencias.getBoolean("activa", false)
                && !AlarmaProgramador.tienePermiso(context));
        EstadoAlarmaBean estado = new EstadoAlarmaBean();
        estado.setAlarmaProgramada(programada);
        estado.setAlarmaSonando(sonando);
        estado.setProximaAlarmaMs(programada ? proxima : 0L);
        estado.setProximoInicioMonitoreoMs(
                preferencias.getLong("proximoInicioMonitoreo", 0L));
        estado.setSesionId(preferencias.getString("sesionId", ""));
        estado.setMonitoreoActivo(preferencias.getBoolean("telefonoServicioActivo", false)
                || preferencias.getBoolean("sonidoServicioActivo", false));
        estado.setEstado(AlarmaDatos.estado(preferencias.getBoolean("activa", false),
                sonando, preferencias.getBoolean("posponiendo", false), programada, fallo));

        Map<String, Object> datos = new HashMap<>();
        datos.put("estado", estado.getEstado());
        datos.put("alarmaProgramada", estado.isAlarmaProgramada());
        datos.put("alarmaSonando", estado.isAlarmaSonando());
        datos.put("proximaAlarma", tiempo(estado.getProximaAlarmaMs()));
        long siguienteInicio = estado.getProximoInicioMonitoreoMs();
        boolean inicioConfirmado = siguienteInicio > ahora;
        boolean inicioRequerido = preferencias.getInt("dias", 0) != 0;
        datos.put("proximoInicioMonitoreo", tiempo(inicioConfirmado ? siguienteInicio : 0L));
        datos.put("inicioMonitoreoProgramado", inicioConfirmado);
        datos.put("estadoInicioMonitoreo", !inicioRequerido ? "NO_APLICA"
                : inicioConfirmado ? "PROGRAMADO" : "NO_PROGRAMADO");
        datos.put("sesionDescansoActiva", preferencias.getBoolean("sesionDescansoActiva", false));
        datos.put("sesionId", estado.getSesionId());
        datos.put("monitoreoActivo", estado.isMonitoreoActivo());
        datos.put("permisoAlarmasExactas", AlarmaProgramador.tienePermiso(context));
        datos.put("motivoUltimoCambio", motivo);
        Map<String, Object> telefono = new HashMap<>();
        telefono.put("habilitado", preferencias.getBoolean("monitoreoTelefono", false));
        Map<String, Object> sonidos = new HashMap<>();
        sonidos.put("habilitado", preferencias.getBoolean("grabacionSonido", false));
        sonidos.put("permisoConcedido", ContextCompat.checkSelfPermission(context,
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
        datos.put("actividadTelefono", telefono);
        datos.put("deteccionSonidos", sonidos);
        escribir(context, datos);
    }

    /** Recuento por sesión y estado inicial. Se invoca solo al crear una sesión nueva. */
    public static void iniciarSesion(Context context) {
        SharedPreferences preferencias = pref(context);
        Map<String, Object> datos = new HashMap<>();
        datos.put("sesionDescansoActiva", true);
        datos.put("sesionId", preferencias.getString("sesionId", ""));
        datos.put("monitoreoActivo", false);
        Map<String, Object> tel = new HashMap<>();
        tel.put("habilitado", preferencias.getBoolean("monitoreoTelefono", false));
        tel.put("servicioActivo", false);
        tel.put("activacionesDetectadas", 0);
        datos.put("actividadTelefono", tel);
        Map<String, Object> audio = new HashMap<>();
        audio.put("habilitado", preferencias.getBoolean("grabacionSonido", false));
        audio.put("servicioActivo", false);
        audio.put("audiosRegistrados", 0);
        audio.put("permisoConcedido", ContextCompat.checkSelfPermission(context,
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
        datos.put("deteccionSonidos", audio);
        datos.put("motivoUltimoCambio", "INICIO_SESION");
        escribir(context, datos);
    }

    /**
     * Confirmación desde el propio servicio, con vigencia de 2 min renovable.
     * Un campo true antiguo no confirma ejecución actual si vigenteHasta ha pasado.
     */
    public static void servicio(Context context, boolean esAudio, boolean activo) {
        servicio(context, esAudio, activo, null);
    }

    public static void servicio(Context context, boolean esAudio, boolean activo,
                                String sesionEsperada) {
        SharedPreferences preferencias = pref(context);
        if (sesionEsperada != null && !sesionEsperada.isEmpty()
                && !sesionEsperada.equals(preferencias.getString("sesionId", ""))) return;
        preferencias.edit().putBoolean(esAudio ? "sonidoServicioActivo" : "telefonoServicioActivo", activo).apply();
        long ahora = System.currentTimeMillis();
        Map<String, Object> datos = new HashMap<>();
        Map<String, Object> detalle = new HashMap<>();
        detalle.put("servicioActivo", activo);
        detalle.put("ultimaSenal", tiempo(ahora));
        detalle.put("vigenteHasta", tiempo(activo ? ahora + VIGENCIA_MS : ahora));
        detalle.put("habilitado", preferencias.getBoolean(
                esAudio ? "grabacionSonido" : "monitoreoTelefono", false));
        if (esAudio) {
            detalle.put("permisoConcedido", ContextCompat.checkSelfPermission(context,
                    Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
        }
        datos.put(esAudio ? "deteccionSonidos" : "actividadTelefono", detalle);
        datos.put("monitoreoActivo", preferencias.getBoolean("telefonoServicioActivo", false)
                || preferencias.getBoolean("sonidoServicioActivo", false));
        datos.put("motivoUltimoCambio", activo ? "SERVICIO_INICIADO" : "SERVICIO_DETENIDO");
        escribir(context, datos);
    }

    /** Se ejecuta únicamente mientras el servicio continúa activo. */
    public static void latido(Context context, boolean esAudio) {
        long ahora = System.currentTimeMillis();
        SharedPreferences preferencias = pref(context);
        if (!preferencias.getBoolean(esAudio ? "sonidoServicioActivo" : "telefonoServicioActivo", false)) return;
        Map<String, Object> detalle = new HashMap<>();
        detalle.put("ultimaSenal", tiempo(ahora));
        detalle.put("vigenteHasta", tiempo(ahora + VIGENCIA_MS));
        Map<String, Object> datos = new HashMap<>();
        datos.put(esAudio ? "deteccionSonidos" : "actividadTelefono", detalle);
        escribir(context, datos);
    }

    /** El contador solo crece después de que Firestore aceptó el registro del evento. */
    public static void eventoConfirmado(Context context, boolean esAudio, long cuando,
                                        String sesionEsperada) {
        if (sesionEsperada == null || sesionEsperada.isEmpty()
                || !sesionEsperada.equals(pref(context).getString("sesionId", ""))) return;
        Map<String, Object> detalle = new HashMap<>();
        detalle.put(esAudio ? "audiosRegistrados" : "activacionesDetectadas", FieldValue.increment(1));
        detalle.put("ultimaActividad", tiempo(cuando));
        Map<String, Object> datos = new HashMap<>();
        datos.put(esAudio ? "deteccionSonidos" : "actividadTelefono", detalle);
        escribir(context, datos);
    }
}
