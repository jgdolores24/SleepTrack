package com.example.sleeptrack;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FeedbackDescansoActivity extends AppCompatActivity {

    private static final int MORADO = Color.rgb(112, 83, 176);

    private TextView txtPorcentaje, txtNivel, txtObjetivo, txtHoras, txtExplicacion;
    private TextView botonMalo, botonRegular, botonBueno;
    private static final int OBJETIVO_MINUTOS = 480;
    private Integer indiceOrientativo = null;
    private Integer cumplimientoObjetivo = null;
    private Integer duracionRegistrada = null;
    private boolean descansoBreve = false;
    private String valoracionElegida = null;
    private Button btnGuardar;
    private LinearLayout contenedorMolestias;
    private int porcentaje = 0;
    private boolean guardando = false;
    private boolean consultando = true;
    private boolean consultaExitosa = false;

    private final LinkedHashMap<String, Molestia> molestias = new LinkedHashMap<>();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private static class Molestia {
        String nombre;
        String fechaActividad;
        String hora;
        String tipo;
        Integer intensidadAntes;
        Integer intensidadDespues;
        long instante;

        Molestia(
                String nombre,
                String fechaActividad,
                String hora,
                Integer intensidadAntes,
                long instante) {
            this.nombre = nombre;
            this.fechaActividad = fechaActividad;
            this.hora = hora;
            this.intensidadAntes = intensidadAntes;
            this.instante = instante;
            this.tipo = esEstres(nombre) ? "estres" : "molestia";
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback_descanso);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        androidx.core.view.WindowInsetsControllerCompat controlador =
                androidx.core.view.WindowCompat.getInsetsController(
                        getWindow(), getWindow().getDecorView());
        controlador.setAppearanceLightStatusBars(true);
        controlador.setAppearanceLightNavigationBars(true);
        View raiz = findViewById(R.id.main);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
                raiz,
                (vista, insets) -> {
                    androidx.core.graphics.Insets barras =
                            insets.getInsets(
                                    androidx.core.view.WindowInsetsCompat.Type.systemBars()
                                            | androidx.core.view.WindowInsetsCompat.Type
                                                    .displayCutout());
                    vista.setPadding(barras.left, barras.top, barras.right, barras.bottom);
                    return insets;
                });
        androidx.core.view.ViewCompat.requestApplyInsets(raiz);

        txtPorcentaje = findViewById(R.id.txtPorcentajeDescanso);
        txtNivel = findViewById(R.id.txtNivelDescanso);
        txtObjetivo = findViewById(R.id.txtObjetivoPorcentaje);
        txtHoras = findViewById(R.id.txtHorasFeedback);
        txtExplicacion = findViewById(R.id.txtExplicacionIndice);
        botonMalo = findViewById(R.id.btnCalidadMala);
        botonRegular = findViewById(R.id.btnCalidadRegular);
        botonBueno = findViewById(R.id.btnCalidadBuena);
        btnGuardar = findViewById(R.id.btnGuardarEvaluacion);
        contenedorMolestias = findViewById(R.id.contenedorMolestiasDespertar);

        botonMalo.setOnClickListener(v -> seleccionarCalidad("Malo", 25));
        botonRegular.setOnClickListener(v -> seleccionarCalidad("Regular", 60));
        botonBueno.setOnClickListener(v -> seleccionarCalidad("Bueno", 90));
        mostrarHoras();
        actualizarValor();
        consultarMolestiasPrevias();

        btnGuardar.setOnClickListener(v -> guardarEvaluacion());
        findViewById(R.id.btnOmitirEvaluacion).setOnClickListener(v -> finalizar());
        getOnBackPressedDispatcher()
                .addCallback(
                        this,
                        new OnBackPressedCallback(true) {
                            @Override
                            public void handleOnBackPressed() {
                                if (!guardando) finalizar();
                            }
                        });
    }

    private void seleccionarCalidad(String nivel, int puntos) {
        valoracionElegida = nivel;
        porcentaje = puntos;
        actualizarValor();
    }

    private void actualizarValor() {
        actualizarBoton(botonMalo, "Malo".equals(valoracionElegida));
        actualizarBoton(botonRegular, "Regular".equals(valoracionElegida));
        actualizarBoton(botonBueno, "Bueno".equals(valoracionElegida));
        txtNivel.setText(
                valoracionElegida == null
                        ? "Selecciona una opción"
                        : "Valoración: " + valoracionElegida);
        if (valoracionElegida == null || cumplimientoObjetivo == null) {
            indiceOrientativo = null;
            txtPorcentaje.setText("—");
            if (descansoBreve) {
                txtExplicacion.setText(
                        "Descanso breve: no se compara con el objetivo nocturno de 8 h.");
            } else {
                txtExplicacion.setText(
                        "Se mostrará un índice cuando registres una valoración y una sesión"
                            + " nocturna válida.");
            }
            return;
        }
        indiceOrientativo = Math.round(0.6f * porcentaje + 0.4f * cumplimientoObjetivo);
        txtPorcentaje.setText(indiceOrientativo + "%");
        txtExplicacion.setText(
                "Índice propio: 60% valoración personal y 40% cumplimiento de 8 h. No mide sueño"
                    + " confirmado.");
    }

    private void actualizarBoton(TextView vista, boolean seleccionado) {
        vista.setBackgroundResource(R.drawable.st_ff_choice);
        vista.setTextColor(0xFF211A2D);
        vista.setTypeface(null, seleccionado ? Typeface.BOLD : Typeface.NORMAL);
        vista.setSelected(seleccionado);
    }

    private void mostrarHoras() {
        SharedPreferences pref = getSharedPreferences(AlarmaProgramador.PREF, MODE_PRIVATE);
        SimpleDateFormat formato = new SimpleDateFormat("HH:mm", Locale.getDefault());
        long inicio = pref.getLong("inicioDescanso", 0L);
        long fin = pref.getLong("finDescanso", 0L);
        ((TextView) findViewById(R.id.txtInicioEvaluacion))
                .setText(inicio > 0 ? formato.format(new Date(inicio)) : "--:--");
        ((TextView) findViewById(R.id.txtFinEvaluacion))
                .setText(fin > 0 ? formato.format(new Date(fin)) : "--:--");

        duracionRegistrada = null;
        cumplimientoObjetivo = null;
        descansoBreve = false;
        if (inicio > 0 && fin > inicio && fin - inicio <= 18L * 60 * 60000) {
            duracionRegistrada = (int) Math.round((fin - inicio) / 60000.0);
            txtHoras.setText(
                    (duracionRegistrada / 60) + " h " + (duracionRegistrada % 60) + " min");
            descansoBreve = duracionRegistrada < 180;
            if (!descansoBreve) {
                cumplimientoObjetivo =
                        Math.max(
                                0,
                                Math.min(
                                        100,
                                        Math.round(duracionRegistrada * 100f / OBJETIVO_MINUTOS)));
            }
        } else {
            txtHoras.setText("--");
        }
        txtObjetivo.setText(
                cumplimientoObjetivo == null
                        ? (descansoBreve ? "No aplica (breve)" : "Sin datos")
                        : cumplimientoObjetivo + "%");
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private static String clave(String s) {
        String sinTildes =
                Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                        .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return sinTildes.trim();
    }

    private static boolean esEstres(String nombre) {
        String s = clave(nombre);
        return s.contains("estres") || s.contains("ansiedad");
    }

    private static String fecha(Calendar calendario) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendario.getTime());
    }

    private void consultarMolestiasPrevias() {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        SharedPreferences pref = getSharedPreferences(AlarmaProgramador.PREF, MODE_PRIVATE);
        String fechaSesion = pref.getString(
                "fechaInicioDescanso", pref.getString("fechaDescanso", ""));
        molestias.clear();
        consultaExitosa = false;
        consultando = true;
        contenedorMolestias.removeAllViews();
        contenedorMolestias.setVisibility(View.GONE);
        if (usuario == null
                || fechaSesion == null
                || !fechaSesion.matches("\\d{4}-\\d{2}-\\d{2}")) {
            consultando = false;
            mostrarEstado("No se pudo identificar la fecha de esta evaluación.");
            return;
        }
        // Las actividades del sueño nocturno corresponden a la fecha de inicio,
        // aunque el registro de sueño se contabilice el día del despertar.
        db.collection("usuarios")
                .document(usuario.getUid())
                .collection("actividades")
                .document(fechaSesion)
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener(
                        documento -> {
                            if (isFinishing() || isDestroyed()) return;
                            molestias.clear();
                            procesarMolestias(documento, fechaSesion);
                            consultaExitosa = true;
                            consultando = false;
                            mostrarPreguntas();
                        })
                .addOnFailureListener(
                        error -> {
                            if (isFinishing() || isDestroyed()) return;
                            molestias.clear();
                            consultando = false;
                            consultaExitosa = false;
                            mostrarEstado(
                                    "No se pudieron consultar las molestias de ese día. Puedes"
                                        + " guardar tu valoración.");
                        });
    }

    private void procesarMolestias(DocumentSnapshot doc, String fechaSesion) {
        if (!fechaSesion.equals(doc.getId())) return;
        Object lista = doc.get("Molestias actuales");
        if (!(lista instanceof List)) return;
        for (Object item : (List<?>) lista) {
            if (!(item instanceof Map)) continue;
            Map<?, ?> evento = (Map<?, ?>) item;
            Object nombreValor = evento.get("nombre");
            if (!(nombreValor instanceof String)) continue;
            String nombre = ((String) nombreValor).trim();
            if (nombre.isEmpty()) continue;
            Object horaValor = evento.get("hora");
            String hora = horaValor instanceof String ? (String) horaValor : "";
            long orden = -1;
            if (hora.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")) {
                orden =
                        Integer.parseInt(hora.substring(0, 2)) * 60L
                                + Integer.parseInt(hora.substring(3, 5));
            } else {
                hora = "";
            }
            Object previo = evento.get("intensidad");
            Integer antes =
                    previo instanceof Number
                            ? Math.max(0, Math.min(10, ((Number) previo).intValue()))
                            : null;
            String id = clave(nombre);
            Molestia actual = molestias.get(id);
            if (actual == null || orden >= actual.instante) {
                molestias.put(id, new Molestia(nombre, doc.getId(), hora, antes, orden));
            }
        }
    }

    private void mostrarEstado(String texto) {
        contenedorMolestias.removeAllViews();
        contenedorMolestias.setVisibility(View.VISIBLE);
        TextView aviso = new TextView(this);
        aviso.setText(texto);
        aviso.setTextColor(0xFF756B80);
        aviso.setGravity(android.view.Gravity.CENTER);
        aviso.setTextSize(12);
        aviso.setPadding(dp(8), dp(12), dp(8), dp(12));
        contenedorMolestias.addView(aviso);
    }

    private void mostrarPreguntas() {
        contenedorMolestias.removeAllViews();
        if (molestias.isEmpty()) {
            contenedorMolestias.setVisibility(View.GONE);
            return;
        }
        contenedorMolestias.setVisibility(View.VISIBLE);
        TextView titulo = new TextView(this);
        titulo.setText("¿Cómo te sientes al despertar?");
        titulo.setTextColor(0xFF1C1823);
        titulo.setGravity(android.view.Gravity.CENTER);
        titulo.setTextSize(17);
        titulo.setTypeface(null, Typeface.BOLD);
        titulo.setPadding(0, dp(8), 0, dp(12));
        contenedorMolestias.addView(titulo);

        for (Molestia molestia : molestias.values()) {
            LinearLayout tarjeta = new LinearLayout(this);
            tarjeta.setOrientation(LinearLayout.VERTICAL);
            tarjeta.setPadding(dp(16), dp(14), dp(16), dp(12));
            tarjeta.setBackgroundResource(R.drawable.st_ff_molestia);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = dp(12);
            contenedorMolestias.addView(tarjeta, params);

            TextView nombre = new TextView(this);
            nombre.setText(molestia.nombre);
            nombre.setTextSize(16);
            nombre.setTextColor(0xFF1C1823);
            nombre.setTypeface(null, Typeface.BOLD);
            tarjeta.addView(nombre);

            TextView antes = new TextView(this);
            antes.setText(
                    molestia.intensidadAntes == null
                            ? "Intensidad anterior: sin registrar"
                            : "Registrado ese día: " + molestia.intensidadAntes + "/10");
            antes.setTextSize(12);
            antes.setTextColor(0xFF756B80);
            antes.setPadding(0, dp(7), 0, dp(8));
            tarjeta.addView(antes);

            TextView actual = new TextView(this);
            actual.setText("Nivel al despertar: sin responder");
            actual.setTextColor(MORADO);
            actual.setTextSize(13);
            actual.setPadding(0, 0, 0, dp(4));
            tarjeta.addView(actual);

            SeekBar barra = new SeekBar(this);
            barra.setMax(10);
            barra.setProgress(0);
            barra.setProgressTintList(android.content.res.ColorStateList.valueOf(MORADO));
            barra.setThumbTintList(android.content.res.ColorStateList.valueOf(MORADO));
            barra.setProgressBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFFE4DCEE));
            barra.setContentDescription("Intensidad al despertar: " + molestia.nombre);
            tarjeta.addView(barra, new LinearLayout.LayoutParams(-1, dp(48)));

            CheckBox sinMolestia = new CheckBox(this);
            sinMolestia.setText(
                    molestia.tipo.equals("estres")
                            ? "Ya no siento estrés o ansiedad"
                            : "Ya no tengo esta molestia");
            sinMolestia.setTextColor(0xFF40364D);
            sinMolestia.setButtonTintList(android.content.res.ColorStateList.valueOf(MORADO));
            sinMolestia.setMinHeight(dp(48));
            sinMolestia.setTextSize(13);
            tarjeta.addView(sinMolestia);

            barra.setOnSeekBarChangeListener(
                    new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar sb, int valor, boolean delUsuario) {
                            if (delUsuario) {
                                if (sinMolestia.isChecked()) sinMolestia.setChecked(false);
                                molestia.intensidadDespues = valor;
                                actual.setText("Nivel al despertar: " + valor + "/10");
                            }
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar s) {}

                        @Override
                        public void onStopTrackingTouch(SeekBar s) {}
                    });
            sinMolestia.setOnCheckedChangeListener(
                    (button, marcado) -> {
                        barra.setEnabled(!marcado);
                        if (marcado) {
                            molestia.intensidadDespues = 0;
                            actual.setText("Nivel al despertar: 0/10");
                        } else {
                            molestia.intensidadDespues = null;
                            actual.setText("Nivel al despertar: sin responder");
                        }
                    });
        }
    }

    private void guardarEvaluacion() {
        if (valoracionElegida == null) {
            Toast.makeText(this, "Elige Malo, Regular o Bueno", Toast.LENGTH_SHORT).show();
            return;
        }
        if (guardando || consultando) {
            if (consultando)
                Toast.makeText(this, "Cargando actividades...", Toast.LENGTH_SHORT).show();
            return;
        }
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        SharedPreferences pref = getSharedPreferences(AlarmaProgramador.PREF, MODE_PRIVATE);
        String fecha = pref.getString("fechaDescanso", "");
        if (usuario == null
                || fecha.isEmpty()
                || !usuario.getUid().equals(pref.getString("sesionUid", usuario.getUid()))) {
            Toast.makeText(this, "No se encontró la sesión", Toast.LENGTH_SHORT).show();
            return;
        }

        if (consultaExitosa) {
            for (Molestia m : molestias.values()) {
                if (m.intensidadDespues == null) {
                    Toast.makeText(this, "Indica cómo está: " + m.nombre, Toast.LENGTH_SHORT)
                            .show();
                    return;
                }
            }
        }

        guardando = true;
        btnGuardar.setEnabled(false);

        Map<String, Object> datos = FirestoreOrden.evaluacion(porcentaje, valoracionElegida);
        @SuppressWarnings("unchecked")
        Map<String, Object> evaluacion = (Map<String, Object>) datos.get("evaluacion");
        evaluacion.put("tipo", "percepcion_tres_niveles");
        evaluacion.put("indiceOrientativo", indiceOrientativo);
        evaluacion.put("cumplimientoObjetivo", cumplimientoObjetivo);
        evaluacion.put("objetivoMinutos", OBJETIVO_MINUTOS);
        evaluacion.put("duracionRegistradaMinutos", duracionRegistrada);
        evaluacion.put("algoritmoIndice", "60% valoracion / 40% duracion, v1; no validado");
        if (consultaExitosa) {
            List<Map<String, Object>> respuestas = new ArrayList<>();
            for (Molestia m : molestias.values()) {
                Map<String, Object> respuesta = new HashMap<>();
                respuesta.put("nombre", m.nombre);
                respuesta.put("tipo", m.tipo);
                respuesta.put("fechaActividad", m.fechaActividad);
                respuesta.put("horaActividad", m.hora);
                respuesta.put("intensidadAntes", m.intensidadAntes);
                respuesta.put("intensidadDespues", m.intensidadDespues);
                respuesta.put(
                        "cambio",
                        m.intensidadAntes == null
                                ? "sin_referencia"
                                : m.intensidadDespues < m.intensidadAntes
                                        ? "mejoro"
                                        : m.intensidadDespues > m.intensidadAntes
                                                ? "empeoro"
                                                : "igual");
                respuestas.add(respuesta);
            }
            evaluacion.put("molestiasAlDespertar", respuestas);
        }

        RepositorioDescanso.guardarEvaluacion(
                        usuario.getUid(), fecha, pref.getString("sesionId", ""), datos)
                .addOnSuccessListener(
                        v -> {
                            Toast.makeText(this, "Evaluación guardada", Toast.LENGTH_SHORT).show();
                            finalizar();
                        })
                .addOnFailureListener(
                        error -> {
                            guardando = false;
                            btnGuardar.setEnabled(true);
                            Toast.makeText(
                                            this,
                                            "No se pudo guardar: " + error.getMessage(),
                                            Toast.LENGTH_LONG)
                                    .show();
                        });
    }

    private void finalizar() {
        getSharedPreferences(AlarmaProgramador.PREF, MODE_PRIVATE)
                .edit()
                .putBoolean("evaluacionPendiente", false)
                .apply();
        Intent ir = new Intent(this, Registros.class);
        ir.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(ir);
        finish();
    }
}
