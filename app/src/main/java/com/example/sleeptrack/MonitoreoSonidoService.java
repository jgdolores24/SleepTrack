package com.example.sleeptrack;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MonitoreoSonidoService extends Service {

    public static final String INICIAR = "SLEEPTRACK_SONIDO_INICIAR";
    public static final String DETENER = "SLEEPTRACK_SONIDO_DETENER";

    private static final String TAG = "SleepTrackSonido";
    private static final String CANAL = "sleeptrack_sonidos";

    private static final int ID_NOTIFICACION = 410;
    private static final int FRECUENCIA = 16000;
    private static final int MAX_EVENTOS = 100;

    private static final long MAXIMO = 12L * 60 * 60 * 1000;
    private static final long PAUSA_EVENTOS = 12000L;

    private volatile boolean activo = false;
    private volatile long horaFin;
    private volatile AudioRecord grabadora;

    private String uid;
    private String fecha;
    private int totalEventos;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null && DETENER.equals(intent.getAction())) {

            Log.i(TAG, "Monitoreo detenido");
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent == null || !INICIAR.equals(intent.getAction())) {

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
        long finMonitoreo = descansoLibre ? Long.MAX_VALUE : proxima;

        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();

        if (usuario == null
                || !usuario.getUid().equals(pref.getString("sesionUid", usuario.getUid()))) {
            Log.e(TAG, "Usuario no autenticado");
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!pref.getBoolean("sesionDescansoActiva", false)
                || !pref.getBoolean("grabacionSonido", false)) {
            Log.w(TAG, "Grabación desactivada");
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!descansoLibre && (restante <= 0 || restante > MAXIMO)) {
            Log.w(TAG, "Alarma fuera del intervalo permitido");
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            Log.e(TAG, "Permiso de micrófono no concedido");
            stopSelf();
            return START_NOT_STICKY;
        }

        if (activo) {
            horaFin = finMonitoreo;
            Log.i(TAG, "Monitoreo activo. Horario actualizado");
            return START_NOT_STICKY;
        }

        try {
            crearCanal();

            Notification notificacion = crearNotificacion();

            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                        ID_NOTIFICACION,
                        notificacion,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(ID_NOTIFICACION, notificacion);
            }

        } catch (RuntimeException e) {

            Log.e(TAG, "No se pudo iniciar el servicio", e);
            stopSelf();
            return START_NOT_STICKY;
        }

        uid = usuario.getUid();
        sesionId = pref.getString("sesionId", "");
        fecha =
                pref.getBoolean("sesionDescansoActiva", false)
                        ? pref.getString("fechaDescanso", formatear(ahora, "yyyy-MM-dd"))
                        : formatear(proxima, "yyyy-MM-dd");
        if (fecha == null || fecha.isEmpty()) {
            fecha = formatear(ahora, "yyyy-MM-dd");
        }
        horaFin = finMonitoreo;
        totalEventos = 0;
        activo = true;

        Log.i(TAG, "Servicio de sonidos iniciado");
        Log.i(TAG, "Fecha de registro: " + fecha);

        new Thread(this::capturarSonidos, "SleepTrackAudio").start();

        return START_NOT_STICKY;
    }

    private String sesionId = "";

    @SuppressLint("MissingPermission")
    private void capturarSonidos() {

        ClasificadorSonidos clasificador = null;
        AudioRecord audio = null;

        ByteArrayOutputStream clip = null;

        String tipoClip = null;
        float confianzaClip = 0;
        long momentoClip = 0;

        int ventanasFaltantes = 0;
        int ventanasAnalizadas = 0;
        int consecutivos = 0;

        long ultimoGuardado = 0;
        String tipoAnterior = null;

        ArrayDeque<short[]> historial = new ArrayDeque<>();

        try {

            Log.i(TAG, "Cargando modelo YAMNet");

            clasificador = new ClasificadorSonidos(this);

            Log.i(TAG, "Modelo YAMNet cargado correctamente");

            int minimo =
                    AudioRecord.getMinBufferSize(
                            FRECUENCIA,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT);

            if (minimo <= 0) {
                throw new IllegalStateException("Configuración de micrófono no compatible");
            }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {

                throw new SecurityException("No hay permiso para usar el micrófono");
            }

            audio =
                    new AudioRecord(
                            MediaRecorder.AudioSource.MIC,
                            FRECUENCIA,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            Math.max(minimo, FRECUENCIA * 8));

            if (audio.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new IllegalStateException("No se pudo inicializar AudioRecord");
            }

            grabadora = audio;
            audio.startRecording();

            if (audio.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {

                throw new IllegalStateException("El micrófono no comenzó a grabar");
            }

            RepositorioEstadoAlarma.servicio(this, true, true, sesionId);
            long ultimoLatido = System.currentTimeMillis();
            Log.i(TAG, "Micrófono iniciado correctamente");
            Log.i(TAG, "Analizando sonidos nocturnos");

            while (activo && System.currentTimeMillis() < horaFin && totalEventos < MAX_EVENTOS) {
                if (System.currentTimeMillis() - ultimoLatido >= 60000L) {
                    RepositorioEstadoAlarma.latido(this, true);
                    ultimoLatido = System.currentTimeMillis();
                }
                short[] muestras = new short[ClasificadorSonidos.MUESTRAS];

                int leidas = 0;

                while (activo && leidas < muestras.length) {

                    int n =
                            audio.read(
                                    muestras,
                                    leidas,
                                    muestras.length - leidas,
                                    AudioRecord.READ_BLOCKING);

                    if (n <= 0) {
                        if (!activo) break;

                        throw new IOException("Error al leer el micrófono: " + n);
                    }

                    leidas += n;
                }

                if (!activo || leidas < muestras.length) {
                    break;
                }

                ventanasAnalizadas++;

                if (clip != null) {

                    escribirPCM(clip, muestras);
                    ventanasFaltantes--;

                    if (ventanasFaltantes <= 0) {

                        guardarClip(clip.toByteArray(), tipoClip, confianzaClip, momentoClip);

                        totalEventos++;
                        ultimoGuardado = System.currentTimeMillis();

                        Log.i(TAG, "Total de audios guardados: " + totalEventos);

                        clip = null;
                        tipoAnterior = null;
                        consecutivos = 0;
                    }

                } else {

                    ClasificadorSonidos.Resultado resultado = clasificador.analizar(muestras);

                    if (ventanasAnalizadas % 5 == 0) {

                        double suma = 0;

                        for (short muestra : muestras) {
                            double valor = muestra / 32768.0;
                            suma += valor * valor;
                        }

                        double nivel = Math.sqrt(suma / muestras.length);

                        Log.i(
                                TAG,
                                "Nivel audio: "
                                        + String.format(Locale.US, "%.4f", nivel)
                                        + " | YAMNet: "
                                        + (resultado == null
                                                ? "Sin sonido relevante"
                                                : resultado.tipo
                                                        + " ("
                                                        + resultado.confianza
                                                        + ")"));
                    }

                    String tipo = resultado == null ? null : resultado.tipo;

                    if (tipo != null && tipo.equals(tipoAnterior)) {
                        consecutivos++;
                    } else {
                        consecutivos = tipo == null ? 0 : 1;
                    }

                    tipoAnterior = tipo;

                    boolean detectado =
                            resultado != null
                                    && ("ruido_fuerte".equals(tipo) || consecutivos >= 2)
                                    && System.currentTimeMillis() - ultimoGuardado >= PAUSA_EVENTOS;

                    if (detectado) {

                        Log.i(
                                TAG,
                                "Evento detectado: "
                                        + tipo
                                        + " | Confianza: "
                                        + resultado.confianza);

                        clip = new ByteArrayOutputStream();

                        for (short[] anteriores : historial) {
                            escribirPCM(clip, anteriores);
                        }

                        escribirPCM(clip, muestras);

                        tipoClip = tipo;
                        confianzaClip = resultado.confianza;
                        momentoClip = System.currentTimeMillis();

                        ventanasFaltantes = 5;
                        consecutivos = 0;
                    }
                }

                historial.addLast(muestras);

                if (historial.size() > 2) {
                    historial.removeFirst();
                }

                if (ventanasAnalizadas % 10 == 0) {
                    Log.i(TAG, "Micrófono funcionando. Ventanas procesadas: " + ventanasAnalizadas);
                }
            }

        } catch (Exception | LinkageError e) {

            Log.e(TAG, "Error al analizar sonidos nocturnos", e);

        } finally {

            if (clip != null && clip.size() >= FRECUENCIA * 2) {
                try {

                    guardarClip(clip.toByteArray(), tipoClip, confianzaClip, momentoClip);

                    totalEventos++;

                } catch (IOException e) {
                    Log.e(TAG, "Error al guardar último audio", e);
                }
            }

            activo = false;
            grabadora = null;

            if (audio != null) {

                try {
                    if (audio.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        audio.stop();
                    }
                } catch (RuntimeException ignored) {
                }

                audio.release();
            }

            if (clasificador != null) {
                try {
                    clasificador.close();
                } catch (RuntimeException e) {
                    Log.w(TAG, "Error al cerrar el clasificador", e);
                }
            }

            Log.i(TAG, "Monitoreo finalizado. Audios guardados: " + totalEventos);
            RepositorioEstadoAlarma.servicio(this, true, false, sesionId);
            stopSelf();
        }
    }

    private void escribirPCM(ByteArrayOutputStream destino, short[] muestras) {

        for (short valor : muestras) {
            destino.write(valor & 0xff);
            destino.write((valor >>> 8) & 0xff);
        }
    }

    private void guardarClip(byte[] pcm, String tipo, float confianza, long momento)
            throws IOException {

        File carpeta = new File(getFilesDir(), "audios_sueno/" + fecha);

        if (!carpeta.exists() && !carpeta.mkdirs()) {
            throw new IOException("No se pudo crear la carpeta de audios");
        }

        String nombre = "sonido_" + momento + "_" + tipo + ".wav";

        File archivo = new File(carpeta, nombre);

        ByteBuffer cabecera = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);

        cabecera.put(new byte[] {'R', 'I', 'F', 'F'});
        cabecera.putInt(36 + pcm.length);

        cabecera.put(new byte[] {'W', 'A', 'V', 'E'});
        cabecera.put(new byte[] {'f', 'm', 't', ' '});

        cabecera.putInt(16);
        cabecera.putShort((short) 1);
        cabecera.putShort((short) 1);

        cabecera.putInt(FRECUENCIA);
        cabecera.putInt(FRECUENCIA * 2);

        cabecera.putShort((short) 2);
        cabecera.putShort((short) 16);

        cabecera.put(new byte[] {'d', 'a', 't', 'a'});
        cabecera.putInt(pcm.length);

        try (FileOutputStream salida = new FileOutputStream(archivo)) {

            salida.write(cabecera.array());
            salida.write(pcm);
        }

        float duracion = Math.round(pcm.length * 10f / (FRECUENCIA * 2)) / 10f;

        Map<String, Object> evento = new HashMap<>();

        evento.put("sesionId", sesionId);
        evento.put("tipo", tipo);
        evento.put("hora", formatear(momento, "HH:mm:ss"));
        evento.put("timestamp", momento);
        evento.put("confianza", confianza);
        evento.put("duracionSegundos", duracion);
        evento.put("archivoLocal", "audios_sueno/" + fecha + "/" + nombre);

        Map<String, Object> datos = new HashMap<>();
        datos.put("audios", FieldValue.arrayUnion(evento));

        SharedPreferences pref = getSharedPreferences(AlarmaProgramador.PREF, MODE_PRIVATE);
        String fechaDestino = fecha;
        if (!pref.getBoolean("sesionDescansoActiva", false)
                && sesionId.equals(pref.getString("sesionId", ""))) {
            fechaDestino = pref.getString("fechaDescanso", fecha);
        }
        RepositorioDescanso.guardarEventos(uid, fechaDestino, sesionId, datos)
                .addOnSuccessListener(v -> {
                    Log.i(TAG, "Metadatos guardados en Firestore");
                    RepositorioEstadoAlarma.eventoConfirmado(this, true, momento, sesionId);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error al guardar en Firestore", e));

        Log.i(TAG, "Audio guardado: " + archivo.getAbsolutePath());
    }

    private String formatear(long tiempo, String patron) {
        return new SimpleDateFormat(patron, Locale.US).format(new Date(tiempo));
    }

    private void crearCanal() {

        if (Build.VERSION.SDK_INT < 26) return;

        NotificationChannel canal =
                new NotificationChannel(
                        CANAL,
                        "Grabación selectiva de sonidos",
                        NotificationManager.IMPORTANCE_LOW);

        canal.setSound(null, null);

        NotificationManager manager = getSystemService(NotificationManager.class);

        if (manager != null) {
            manager.createNotificationChannel(canal);
        }
    }

    private Notification crearNotificacion() {

        Intent abrir = new Intent(this, alarma.class);

        PendingIntent abrirAlarma =
                PendingIntent.getActivity(
                        this,
                        411,
                        abrir,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent detener = new Intent(this, MonitoreoSonidoService.class);

        detener.setAction(DETENER);

        PendingIntent detenerAccion =
                PendingIntent.getService(
                        this,
                        412,
                        detener,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CANAL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("SleepTrack · Sonidos activos")
                .setContentText("Solo se guardan sonidos relevantes")
                .setContentIntent(abrirAlarma)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(android.R.drawable.ic_delete, "Detener sonido", detenerAccion)
                .build();
    }

    @Override
    public void onDestroy() {

        activo = false;
        if (uid != null) RepositorioEstadoAlarma.servicio(this, true, false, sesionId);

        AudioRecord actual = grabadora;

        if (actual != null) {
            try {
                actual.stop();
            } catch (RuntimeException ignored) {
            }
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
