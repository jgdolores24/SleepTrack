package com.example.sleeptrack;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class DescansoActivity extends AppCompatActivity {
    public static final String ACCION_DESPERTAR = "com.example.sleeptrack.DESPERTAR";
    private static final long ATENUAR_DESPUES = 25000L;
    private static final long MANTENER_FINALIZAR = 3000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView txtHora, txtAlarma, txtRestante, txtInicio;
    private TextView txtMonitoreo, txtAviso, txtMantener, txtCabecera, btnFinalizar;
    private boolean saliendo = false;
    private boolean modoDespertar = false;

    private final Runnable atenuar =
            () -> {
                if (modoDespertar) return;
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                WindowManager.LayoutParams p = getWindow().getAttributes();
                p.screenBrightness = 0.02f;
                getWindow().setAttributes(p);
                txtAviso.setText("Pantalla tenue");
            };

    private final Runnable reloj =
            new Runnable() {
                @Override
                public void run() {
                    if (saliendo || modoDespertar) return;
                    SharedPreferences pref =
                            getSharedPreferences(AlarmaProgramador.PREF, MODE_PRIVATE);
                    boolean activa = pref.getBoolean("sesionDescansoActiva", false);
                    boolean esperando =
                            pref.getBoolean("activa", false)
                                    && pref.getLong("proximaAlarma", 0L)
                                            > System.currentTimeMillis();
                    if (!activa && !esperando) {
                        saliendo = true;
                        abrirSiguiente(pref.getBoolean("evaluacionPendiente", false));
                        return;
                    }
                    actualizarDatos();
                    handler.postDelayed(this, 10000L);
                }
            };

    private final Runnable finalizarPorPulsacion =
            () -> {
                if (saliendo) return;
                saliendo = true;
                SharedPreferences pref = getSharedPreferences(AlarmaProgramador.PREF, MODE_PRIVATE);
                if (!pref.getBoolean("sesionDescansoActiva", false)) {
                    startActivity(new Intent(this, alarma.class));
                    finish();
                    return;
                }
                finalizarSesion(this, "manual");
                if (pref.getBoolean("activa", false) && pref.getInt("dias", 0) == 0) {
                    AlarmaProgramador.cancelar(this);
                }
                abrirSiguiente(true);
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_descanso);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
                (v, insets) -> {
                    Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(b.left, b.top, b.right, b.bottom);
                    return insets;
                });
        txtHora = findViewById(R.id.txtHoraActual);
        txtAlarma = findViewById(R.id.txtProximaAlarma);
        txtRestante = findViewById(R.id.txtRestante);
        txtInicio = findViewById(R.id.txtInicioDescanso);
        txtMonitoreo = findViewById(R.id.txtMonitoreos);
        txtAviso = findViewById(R.id.txtAvisoPantalla);
        txtMantener = findViewById(R.id.txtMantener);
        txtCabecera = findViewById(R.id.txtCabeceraDescanso);
        btnFinalizar = findViewById(R.id.btnFinalizarDescanso);

        modoDespertar = ACCION_DESPERTAR.equals(getIntent().getAction());
        if (modoDespertar) {
            finalizarAlarmaSiSuena();
            mostrarPantallaDespertar();
        } else {
            getWindow().setStatusBarColor(0xFF050505);
            getWindow().setNavigationBarColor(0xFF050505);
            configurarBotonOscuro();
            actualizarDatos();
        }

        findViewById(R.id.main)
                .setOnTouchListener(
                        (v, e) -> {
                            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) restaurarBrillo();
                            return false;
                        });
        getOnBackPressedDispatcher()
                .addCallback(
                        this,
                        new OnBackPressedCallback(true) {
                            @Override
                            public void handleOnBackPressed() {
                                if (modoDespertar) {
                                    abrirSiguiente(true);
                                    return;
                                }
                                new AlertDialog.Builder(DescansoActivity.this)
                                        .setTitle("Volver a Alarma")
                                        .setMessage(
                                                "Puedes apagar la pantalla. El descanso seguirá"
                                                    + " activo hasta finalizarlo.")
                                        .setNegativeButton("Seguir aquí", null)
                                        .setPositiveButton(
                                                "Volver",
                                                (d, w) -> {
                                                    startActivity(
                                                            new Intent(
                                                                    DescansoActivity.this,
                                                                    alarma.class));
                                                    finish();
                                                })
                                        .show();
                            }
                        });
    }

    private void configurarBotonOscuro() {
        btnFinalizar.setOnTouchListener(
                (v, e) -> {
                    if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        restaurarBrillo();
                        SharedPreferences pref =
                                getSharedPreferences(AlarmaProgramador.PREF, MODE_PRIVATE);
                        if (!pref.getBoolean("sesionDescansoActiva", false)) {
                            if (pref.getBoolean("activa", false)) {
                                if (SesionDescansoHelper.iniciar(this, "alarma")) {
                                    actualizarDatos();
                                } else {
                                    Toast.makeText(
                                                    this,
                                                    "Inicia sesión para registrar el descanso",
                                                    Toast.LENGTH_SHORT)
                                            .show();
                                }
                            } else {
                                startActivity(new Intent(this, alarma.class));
                                finish();
                            }
                            return true;
                        }
                        txtMantener.setText("Sigue presionando…");
                        handler.postDelayed(finalizarPorPulsacion, MANTENER_FINALIZAR);
                        return true;
                    }
                    if (e.getActionMasked() == MotionEvent.ACTION_UP
                            || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                        handler.removeCallbacks(finalizarPorPulsacion);
                        if (!saliendo)
                            txtMantener.setText("Mantén pulsado 3 segundos para finalizar");
                        return true;
                    }
                    return true;
                });
    }

    private void finalizarAlarmaSiSuena() {
        SharedPreferences pref = getSharedPreferences(AlarmaProgramador.PREF, MODE_PRIVATE);
        stopService(new Intent(this, AlarmaSonidoService.class));
        pref.edit().putBoolean("alarmaSonando", false).apply();
        finalizarSesion(this, "alarma");
        AlarmaProgramador.restaurarRepeticionTrasPosponer(this);
    }

    private void mostrarPantallaDespertar() {
        getWindow().setStatusBarColor(0xFF211634);
        getWindow().setNavigationBarColor(0xFF211634);
        findViewById(R.id.main).setBackgroundResource(R.drawable.st_sesion_despertar_fondo);
        findViewById(R.id.cabeceraDescanso).setVisibility(View.INVISIBLE);
        txtCabecera.setText("BUENOS DÍAS ✦");
        txtCabecera.setTextColor(0xFFE2D4F5);
        txtHora.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        txtRestante.setText("Tu descanso ha terminado");
        txtRestante.setTextColor(0xFFD4C4E8);
        txtInicio.setVisibility(View.GONE);
        txtAviso.setText("Un momento para ti");
        txtAviso.setTextColor(0xFFCFBDDE);
        txtMantener.setText("Consulta tu resumen de descanso");
        txtMantener.setTextColor(0xFFE6D9F3);
        btnFinalizar.setText("Ver análisis");
        btnFinalizar.setTextSize(17);
        btnFinalizar.setBackgroundResource(R.drawable.st_sesion_despertar_boton);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) btnFinalizar.getLayoutParams();
        lp.width = LinearLayout.LayoutParams.MATCH_PARENT;
        lp.height = dp(60);
        btnFinalizar.setLayoutParams(lp);
        btnFinalizar.setOnTouchListener(null);
        btnFinalizar.setOnClickListener(v -> abrirSiguiente(true));
    }

    private int dp(int valor) {
        return Math.round(valor * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (modoDespertar) return;
        handler.removeCallbacks(reloj);
        handler.post(reloj);
        restaurarBrillo();
        SharedPreferences pref = getSharedPreferences(AlarmaProgramador.PREF, MODE_PRIVATE);
        if (pref.getBoolean("sesionDescansoActiva", false)) NotificacionesDescanso.mostrar(this);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(reloj);
        handler.removeCallbacks(atenuar);
        handler.removeCallbacks(finalizarPorPulsacion);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams p = getWindow().getAttributes();
        p.screenBrightness = -1f;
        getWindow().setAttributes(p);
        super.onPause();
    }

    private void restaurarBrillo() {
        if (modoDespertar) return;
        handler.removeCallbacks(atenuar);
        WindowManager.LayoutParams p = getWindow().getAttributes();
        p.screenBrightness = -1f;
        getWindow().setAttributes(p);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        txtAviso.setText("La pantalla se atenuará automáticamente");
        handler.postDelayed(atenuar, ATENUAR_DESPUES);
    }

    private void actualizarDatos() {
        SharedPreferences pref = getSharedPreferences(AlarmaProgramador.PREF, MODE_PRIVATE);
        long ahora = System.currentTimeMillis();
        long proxima = pref.getLong("proximaAlarma", 0L);
        boolean activa = pref.getBoolean("sesionDescansoActiva", false);
        boolean libre = "libre".equals(pref.getString("modoDescanso", "alarma"));
        SimpleDateFormat hora = new SimpleDateFormat("HH:mm", Locale.getDefault());
        txtHora.setText(hora.format(new Date(ahora)));
        txtInicio.setText(
                activa ? hora.format(new Date(pref.getLong("inicioDescanso", ahora))) : "—");
        txtAlarma.setText(
                libre ? "Sin alarma" : proxima > ahora ? hora.format(new Date(proxima)) : "—");
        txtCabecera.setText(activa ? "Tu descanso" : "Próximo descanso");
        if (libre) {
            long minutos = Math.max(0, (ahora - pref.getLong("inicioDescanso", ahora)) / 60000L);
            txtRestante.setText((minutos / 60) + " h " + (minutos % 60) + " min de descanso");
        } else if (proxima > ahora) {
            long minutos = (proxima - ahora + 59999L) / 60000L;
            txtRestante.setText((minutos / 60) + " h " + (minutos % 60) + " min restantes");
        } else {
            txtRestante.setText("Hora de despertar");
        }
        if (!activa) {
            txtMonitoreo.setText("Programado");
            btnFinalizar.setText("▶");
            txtMantener.setText("Toca para comenzar el descanso");
        } else {
            boolean telefono = pref.getBoolean("monitoreoTelefono", false);
            boolean sonidos = pref.getBoolean("grabacionSonido", false);
            txtMonitoreo.setText((telefono || sonidos) ? "Monitoreando" : "En descanso");
            btnFinalizar.setText("■");
            txtMantener.setText("Mantén pulsado 3 segundos para finalizar");
        }
    }

    private void abrirSiguiente(boolean evaluar) {
        Intent destino = new Intent(this, evaluar ? FeedbackDescansoActivity.class : alarma.class);
        destino.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(destino);
        finish();
    }

    public static void finalizarSesion(Context context, String motivo) {
        SharedPreferences pref =
                context.getSharedPreferences(AlarmaProgramador.PREF, Context.MODE_PRIVATE);

        context.stopService(new Intent(context, AlarmaSonidoService.class));

        pref.edit()
                .putBoolean("alarmaSonando", false)
                .apply();

        boolean activa = pref.getBoolean("sesionDescansoActiva", false);
        if (!activa) {
            RepositorioEstadoAlarma.sincronizar(context, "SIN_SESION_ACTIVA");
            return;
        }
        String fechaInicio = pref.getString("fechaInicioDescanso",
                pref.getString("fechaDescanso", ""));
        long fin = System.currentTimeMillis();
        java.util.TimeZone zona = java.util.TimeZone.getTimeZone(
                pref.getString("zonaHorariaDescanso", java.util.TimeZone.getDefault().getID()));
        String fechaFin = FechasDescanso.fechaFin(fin, zona);
        pref.edit()
                .putString("fechaDescanso", fechaFin)
                .putBoolean("sesionDescansoActiva", false)
                .putLong("finDescanso", fin)
                .putString("motivoFinDescanso", motivo)
                .putBoolean("evaluacionPendiente", !"cancelada".equals(motivo))
                .apply();
        context.stopService(new Intent(context, MonitoreoService.class));
        context.stopService(new Intent(context, MonitoreoSonidoService.class));
        NotificacionesDescanso.ocultar(context);
        RepositorioEstadoAlarma.sincronizar(context, "DESCANSO_FINALIZADO");

        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null || fechaInicio.isEmpty()) return;
        String sesionId = pref.getString("sesionId", "");
        if (!sesionId.isEmpty()) {
            if (!usuario.getUid().equals(pref.getString("sesionUid", ""))) return;
            RepositorioDescanso.finalizar(
                            usuario.getUid(),
                            fechaInicio,
                            fechaFin,
                            sesionId,
                            pref.getLong("inicioDescanso", 0L),
                            fin,
                            motivo,
                            pref.getString("modoDescanso", "alarma"))
                    .addOnFailureListener(
                            e -> {
                                android.util.Log.e(
                                        "SleepTrack", "No se pudo finalizar en Firebase", e);
                                android.widget.Toast.makeText(
                                                context,
                                                "No se pudo guardar el descanso en Firebase",
                                                android.widget.Toast.LENGTH_LONG)
                                        .show();
                            });
            return;
        }
        Map<String, Object> datos = FirestoreOrden.finSesion(fin, motivo);
        com.google.firebase.firestore.DocumentReference registro =
                FirebaseFirestore.getInstance()
                        .collection("usuarios")
                        .document(usuario.getUid())
                        .collection("registrosSueno")
                        .document(fechaInicio);
        registro.set(datos, SetOptions.merge())
                .addOnSuccessListener(
                        v ->
                                registro.get()
                                        .addOnSuccessListener(
                                                doc -> {
                                                    AnalizadorSueno.Resultado resultado =
                                                            AnalizadorSueno.calcular(
                                                                    FirestoreOrden.lecturaRegistro(
                                                                            doc.getData()));
                                                    if (resultado.minutosDescanso != null) {
                                                        registro.set(
                                                                FirestoreOrden.analisis(resultado),
                                                                SetOptions.merge());
                                                    }
                                                }));
    }
}
