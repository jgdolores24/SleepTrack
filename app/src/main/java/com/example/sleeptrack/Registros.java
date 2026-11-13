package com.example.sleeptrack;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.DashPathEffect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Registros extends AppCompatActivity {

    private static final int MORADO = Color.rgb(112, 83, 176);
    private static final int GRIS = Color.rgb(138, 130, 153);

    private static final String[] DIAS = {"dom", "lun", "mar", "mié", "jue", "vie", "sáb"};

    private static final String[] DIAS_GRAFICO = {"L", "M", "M", "J", "V", "S", "D"};

    private TextView txtFechaSeleccionada, txtMinutos, txtHoras;
    private TextView txtDuracion, txtCalidad, txtEstadoGrafico;
    private TextView txtObjetivoResumen, txtValoracionPersonal, txtEtiquetaCalidad;
    private TextView txtMonitoreoTelefono, txtRuidosGuardados;
    private TextView txtDespertaresNocturnos, txtCantidadAudios;
    private ProgressBar progresoSueno, progresoDormirse;
    private HorizontalScrollView scrollDias;
    private LinearLayout contenedorDias;
    private GraficoSemanal grafico;

    private Calendar fechaSeleccionada, inicioSemana;
    private FirebaseUser usuario;
    private FirebaseFirestore db;
    private ListenerRegistration escucha;
    private MediaPlayer reproductor;
    private Audio audioActivo;
    private ImageButton botonAudioActivo;

    private final Map<String, RegistroSueno> registros = new HashMap<>();
    private final Map<String, DiaVisual> diasVisuales = new HashMap<>();
    private String diaResaltado;

    private static class Audio {
        final String tipo, hora, url, archivoLocal;
        final Map<String, Object> datos;

        Audio(
                String tipo,
                String hora,
                String url,
                String archivoLocal,
                Map<String, Object> datos) {
            this.tipo = tipo;
            this.hora = hora;
            this.url = url;
            this.archivoLocal = archivoLocal;
            this.datos = datos;
        }
    }

    private static class RegistroSueno {
        Integer minutos, descanso, suenoEstimado, latencia, calidad, indice, cumplimiento;
        Integer despertares, activaciones, posiblesInterrupciones;
        String valoracion;
        final List<Audio> audios = new ArrayList<>();
    }

    private static class DiaVisual {
        LinearLayout tarjeta;
        TextView numero, nombre;

        DiaVisual(LinearLayout tarjeta, TextView numero, TextView nombre) {
            this.tarjeta = tarjeta;
            this.numero = numero;
            this.nombre = nombre;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_registros);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
                (v, insets) -> {
                    Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());

                    v.setPadding(
                            barras.left,
                            barras.top,
                            barras.right,
                            Math.max(0, barras.bottom - dp(12)));
                    return insets;
                });

        usuario = FirebaseAuth.getInstance().getCurrentUser();
        db = FirebaseFirestore.getInstance();

        txtFechaSeleccionada = findViewById(R.id.txtFechaSeleccionada);
        txtMinutos = findViewById(R.id.txtMinutosDormirse);
        txtHoras = findViewById(R.id.txtHorasDormidasCirculo);
        txtDuracion = findViewById(R.id.txtDuracionSueno);
        txtCalidad = findViewById(R.id.txtCalidadSueno);
        txtObjetivoResumen = findViewById(R.id.txtObjetivoResumen);
        txtValoracionPersonal = findViewById(R.id.txtValoracionPersonal);
        txtEtiquetaCalidad = findViewById(R.id.txtEtiquetaCalidad);
        txtMonitoreoTelefono = findViewById(R.id.txtMonitoreoTelefono);
        txtRuidosGuardados = findViewById(R.id.txtRuidosGuardados);
        txtDespertaresNocturnos = findViewById(R.id.txtDespertaresNocturnos);
        txtCantidadAudios = findViewById(R.id.txtCantidadAudios);

        progresoSueno = findViewById(R.id.progresoSueno);
        progresoDormirse = findViewById(R.id.progresoDormirse);
        scrollDias = findViewById(R.id.scrollDias);
        contenedorDias = findViewById(R.id.contenedorDias);

        fechaSeleccionada = Calendar.getInstance();

        if (savedInstanceState != null) {
            long fecha = savedInstanceState.getLong("fechaSeleccionada", -1);

            if (fecha > 0) {
                fechaSeleccionada.setTimeInMillis(fecha);
            }
        }

        inicioSemana = obtenerLunes(fechaSeleccionada);

        configurarNavegacion();
        configurarGrafico();
        configurarCalendario();
        actualizarFecha();
        mostrarRegistro();

        findViewById(R.id.btnVerAudios).setOnClickListener(v -> mostrarAudios());

        scrollDias.post(
                () -> {
                    DiaVisual actual = diasVisuales.get(fechaId(fechaSeleccionada));

                    if (actual != null) {
                        int x =
                                actual.tarjeta.getLeft()
                                        + actual.tarjeta.getWidth() / 2
                                        - scrollDias.getWidth() / 2;

                        scrollDias.scrollTo(Math.max(0, x), 0);
                    }
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (usuario != null) {
            MigracionFirebaseV4.migrarUnaVez(this);
        }
        cargarSemana();
    }

    @Override
    protected void onStop() {
        detenerAudio();
        if (escucha != null) {
            escucha.remove();
            escucha = null;
        }
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle estado) {
        estado.putLong("fechaSeleccionada", fechaSeleccionada.getTimeInMillis());

        super.onSaveInstanceState(estado);
    }

    private int dp(float valor) {
        return Math.round(valor * getResources().getDisplayMetrics().density);
    }

    private String fechaId(Calendar fecha) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(fecha.getTime());
    }

    private Calendar obtenerLunes(Calendar fecha) {
        Calendar lunes = (Calendar) fecha.clone();
        lunes.add(Calendar.DAY_OF_MONTH, -(lunes.get(Calendar.DAY_OF_WEEK) + 5) % 7);
        return lunes;
    }

    private void configurarNavegacion() {

        ImageButton navRegistros = findViewById(R.id.navRegistros);
        navRegistros.setImageResource(R.drawable.st_capsula_registros);
        navRegistros.setBackgroundResource(android.R.color.transparent);

        NavegacionPrincipal.configurar(this, R.id.navRegistros);

        findViewById(R.id.btnRapidoVerTodo).setOnClickListener(v -> abrirActividad(null));
        findViewById(R.id.btnRapidoBebidas).setOnClickListener(v -> abrirActividad("bebidas"));
        findViewById(R.id.btnRapidoAlcohol).setOnClickListener(v -> abrirActividad("alcohol"));
        findViewById(R.id.btnRapidoCigarrillos)
                .setOnClickListener(v -> abrirActividad("cigarrillos"));
        findViewById(R.id.btnRapidoSuplementos)
                .setOnClickListener(v -> abrirActividad("suplementos"));
        findViewById(R.id.btnRapidoMolestias).setOnClickListener(v -> abrirActividad("molestias"));
    }

    private void abrirActividad(String categoria) {
        Intent intent = new Intent(this, Actividad.class);
        intent.putExtra("fechaRegistro", fechaId(fechaSeleccionada));
        if (categoria != null) intent.putExtra("registroRapido", categoria);
        startActivity(intent);
    }


    private TextView crearTexto(String texto, int tamano) {
        TextView vista = new TextView(this);
        vista.setText(texto);
        vista.setTextSize(tamano);
        vista.setGravity(Gravity.CENTER);
        return vista;
    }

    private void configurarCalendario() {

        contenedorDias.removeAllViews();
        diasVisuales.clear();

        Calendar dia = Calendar.getInstance();
        dia.add(Calendar.DAY_OF_MONTH, -365);

        if (fechaSeleccionada.before(dia)) {
            dia = (Calendar) fechaSeleccionada.clone();
        }

        Calendar hoy = Calendar.getInstance();

        while (!dia.after(hoy)) {

            Calendar fecha = (Calendar) dia.clone();

            LinearLayout tarjeta = new LinearLayout(this);
            tarjeta.setOrientation(LinearLayout.VERTICAL);
            tarjeta.setGravity(Gravity.CENTER);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(52), dp(56));

            params.setMargins(dp(3), 0, dp(3), 0);
            contenedorDias.addView(tarjeta, params);

            TextView numero = crearTexto(String.valueOf(dia.get(Calendar.DAY_OF_MONTH)), 16);

            numero.setTypeface(null, Typeface.BOLD);

            TextView nombre = crearTexto(DIAS[dia.get(Calendar.DAY_OF_WEEK) - 1], 11);

            tarjeta.addView(numero);
            tarjeta.addView(nombre);

            diasVisuales.put(fechaId(fecha), new DiaVisual(tarjeta, numero, nombre));

            resaltarDia(fechaId(fecha), false);

            tarjeta.setOnClickListener(v -> seleccionarDia(fecha));

            dia.add(Calendar.DAY_OF_MONTH, 1);
        }
    }

    private void seleccionarDia(Calendar fecha) {

        String lunesAnterior = fechaId(inicioSemana);

        fechaSeleccionada = (Calendar) fecha.clone();
        inicioSemana = obtenerLunes(fechaSeleccionada);

        actualizarFecha();

        if (!lunesAnterior.equals(fechaId(inicioSemana))) {
            cargarSemana();
        } else {
            mostrarRegistro();
            grafico.invalidate();
        }
    }

    private void actualizarFecha() {

        txtFechaSeleccionada.setText(
                new SimpleDateFormat("EEEE, dd 'de' MMMM", new Locale("es", "PE"))
                        .format(fechaSeleccionada.getTime()));

        if (diaResaltado != null) {
            resaltarDia(diaResaltado, false);
        }

        diaResaltado = fechaId(fechaSeleccionada);
        resaltarDia(diaResaltado, true);
    }

    private void resaltarDia(String fecha, boolean seleccionado) {

        DiaVisual dia = diasVisuales.get(fecha);
        if (dia == null) return;

        if (seleccionado) {
            dia.tarjeta.setBackgroundResource(R.drawable.fondo_dia_seleccionado);
        } else {
            GradientDrawable fondo = new GradientDrawable();
            fondo.setColor(Color.parseColor("#F0E9FA"));
            fondo.setCornerRadius(dp(14));
            dia.tarjeta.setBackground(fondo);
        }

        int color = seleccionado ? Color.WHITE : Color.parseColor("#514567");

        dia.numero.setTextColor(color);
        dia.nombre.setTextColor(color);
    }

    private Integer entero(DocumentSnapshot doc, String campo) {
        Object valor = doc.get(campo);
        return valor instanceof Number ? ((Number) valor).intValue() : null;
    }

    private String texto(Object valor) {
        return valor == null ? "" : String.valueOf(valor);
    }

    private RegistroSueno leerRegistro(DocumentSnapshot doc) {

        RegistroSueno r = new RegistroSueno();

        AnalizadorSueno.Resultado calculo =
                AnalizadorSueno.calcular(FirestoreOrden.lecturaRegistro(doc.getData()));
        r.descanso = calculo.minutosDescanso;
        r.suenoEstimado = calculo.minutosSuenoPosible;
        r.minutos = calculo.minutosDescanso;
        r.latencia = calculo.minutosParaDormirEstimados;
        r.calidad = calculo.calidadPercibida;
        r.indice = entero(doc, "evaluacion.indiceOrientativo");
        r.cumplimiento = entero(doc, "evaluacion.cumplimientoObjetivo");
        r.valoracion = doc.getString("evaluacion.nivel");
        if (r.valoracion == null) r.valoracion = doc.getString("calificacionUsuario");
        r.posiblesInterrupciones = calculo.posiblesInterrupciones;
        r.despertares = entero(doc, "despertaresNocturnos");
        r.activaciones = entero(doc, "activacionesTelefono");

        // Compatibilidad con registros antiguos sin inicio/fin de sesión.
        if (r.minutos == null) r.minutos = entero(doc, "minutosDormidos");
        if (r.calidad == null) r.calidad = entero(doc, "calidadSueno");

        Object eventos = doc.get("eventosTelefono");

        if (r.activaciones == null && eventos instanceof List) {

            int total = 0;

            for (Object evento : (List<?>) eventos) {

                if (!(evento instanceof Map)) continue;

                Object tipo = ((Map<?, ?>) evento).get("tipo");

                if ("pantalla_encendida".equals(tipo) || "screen_on".equals(tipo)) {
                    total++;
                }
            }

            r.activaciones = total;
        }

        Object audios = doc.get("audios");

        if (audios instanceof List) {

            for (Object audio : (List<?>) audios) {

                if (!(audio instanceof Map)) continue;

                Map<?, ?> datos = (Map<?, ?>) audio;

                Map<String, Object> datosOriginales = new HashMap<>();
                for (Map.Entry<?, ?> campo : datos.entrySet()) {
                    if (campo.getKey() instanceof String) {
                        datosOriginales.put((String) campo.getKey(), campo.getValue());
                    }
                }

                r.audios.add(
                        new Audio(
                                texto(datos.get("tipo")),
                                texto(datos.get("hora")),
                                texto(datos.get("url")),
                                texto(datos.get("archivoLocal")),
                                datosOriginales));
            }
        }

        return r;
    }

    private void cargarSemana() {

        if (escucha != null) {
            escucha.remove();
            escucha = null;
        }

        registros.clear();
        mostrarRegistro();
        actualizarGrafico();

        if (usuario == null) {
            txtEstadoGrafico.setText("Inicia sesión para ver tus registros");
            return;
        }

        String lunes = fechaId(inicioSemana);

        Calendar fin = (Calendar) inicioSemana.clone();
        fin.add(Calendar.DAY_OF_MONTH, 6);

        txtEstadoGrafico.setText("Cargando registros...");

        escucha =
                db.collection("usuarios")
                        .document(usuario.getUid())
                        .collection("registrosSueno")
                        .orderBy(FieldPath.documentId())
                        .startAt(lunes)
                        .endAt(fechaId(fin))
                        .addSnapshotListener(
                                (documentos, error) -> {
                                    if (!lunes.equals(fechaId(inicioSemana))) return;

                                    if (error != null) {
                                        txtEstadoGrafico.setText("Error al cargar registros");

                                        Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG)
                                                .show();
                                        return;
                                    }

                                    if (documentos == null) return;

                                    registros.clear();

                                    for (DocumentSnapshot doc : documentos.getDocuments()) {
                                        registros.put(doc.getId(), leerRegistro(doc));
                                        guardarAnalisisSiCambio(doc);
                                    }

                                    mostrarRegistro();
                                    actualizarGrafico();
                                });
    }

    private void guardarAnalisisSiCambio(DocumentSnapshot doc) {
        if (doc.getMetadata().hasPendingWrites() || doc.contains("sesionesFinalizadas")) return;
        Map<String, Object> vista = FirestoreOrden.lecturaRegistro(doc.getData());
        if (vista.get("finDescanso") == null) return;

        AnalizadorSueno.Resultado resultado = AnalizadorSueno.calcular(vista);
        if (resultado.minutosDescanso == null) return;

        Map<String, Object> campos = FirestoreOrden.analisis(resultado);
        // Se comparan sólo los campos calculados. Firestore puede devolver
        // enteros como Long, mientras Java calcula Integer.
        boolean distinto = !igualesFirestore(doc.get("analisis"), campos.get("analisis"));
        boolean faltaDuracion =
                !igualesFirestore(
                        doc.get("sesion.duracionMinutos"),
                        ((Map<?, ?>) campos.get("sesion")).get("duracionMinutos"));
        Map<String, Object> nuevosMonitoreos = FirestoreOrden.monitoreoDesdePlano(doc.getData());
        boolean monitorDistinto =
                !nuevosMonitoreos.isEmpty()
                        && !igualesFirestore(
                                doc.get("monitoreo"), nuevosMonitoreos.get("monitoreo"));
        if (monitorDistinto) campos.putAll(nuevosMonitoreos);
        if (distinto || faltaDuracion || monitorDistinto) {
            doc.getReference()
                    .set(campos, SetOptions.merge())
                    .addOnFailureListener(
                            e ->
                                    android.util.Log.e(
                                            "SleepTrackAnalisis",
                                            "Error al guardar indicadores",
                                            e));
        }
    }

    private boolean igualesFirestore(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue()) == 0;
        }
        if (a instanceof Map && b instanceof Map) {
            Map<?, ?> existe = (Map<?, ?>) a;
            Map<?, ?> esperado = (Map<?, ?>) b;
            for (Map.Entry<?, ?> item : esperado.entrySet()) {
                if (!existe.containsKey(item.getKey())
                        || !igualesFirestore(existe.get(item.getKey()), item.getValue())) {
                    return false;
                }
            }
            return true;
        }
        return java.util.Objects.equals(a, b);
    }

    private RegistroSueno registroActual() {
        return registros.get(fechaId(fechaSeleccionada));
    }

    private Integer posiblesDespertares(RegistroSueno r) {

        if (r == null) return null;

        return r.posiblesInterrupciones != null
                ? r.posiblesInterrupciones
                : r.despertares != null ? r.despertares : r.activaciones;
    }

    private void mostrarRegistro() {
        RegistroSueno r = registroActual();
        Integer duracion = r == null ? null : (r.descanso != null ? r.descanso : r.minutos);
        Integer latencia = r == null ? null : r.latencia;
        String duracionTexto =
                duracion == null
                        ? "-- h -- min"
                        : (duracion / 60) + " h " + (duracion % 60) + " min";
        txtHoras.setText(duracionTexto);
        TextView etiqueta = findViewById(R.id.txtEtiquetaDuracion);
        etiqueta.setText("Total de descansos finalizados del día");
        if (duracion == null) {
            txtDuracion.setText("Sin registro");
        } else if (r != null && r.suenoEstimado != null) {
            txtDuracion.setText(
                    "Posible sueño: ~"
                            + (r.suenoEstimado / 60)
                            + " h "
                            + (r.suenoEstimado % 60)
                            + " min");
        } else {
            txtDuracion.setText(duracionTexto);
        }
        txtMinutos.setText(latencia == null ? "Sin estimación" : "~" + latencia + " min");
        if (r != null && r.indice != null) {
            txtEtiquetaCalidad.setText("Índice orientativo");
            txtCalidad.setText(r.indice + "%");
        } else if (r != null && r.valoracion != null) {
            txtEtiquetaCalidad.setText("Índice orientativo");
            txtCalidad.setText("No aplica");
        } else if (r != null && r.calidad != null) {
            txtEtiquetaCalidad.setText("Evaluación antigua");
            txtCalidad.setText(r.calidad + "%");
        } else {
            txtEtiquetaCalidad.setText("Índice orientativo");
            txtCalidad.setText("—");
        }
        txtValoracionPersonal.setText(
                r == null || r.valoracion == null ? "Sin evaluación" : r.valoracion);
        int porcentajeObjetivo =
                duracion == null
                        ? 0
                        : Math.min(100, Math.max(0, Math.round(duracion * 100f / 480f)));
        if (duracion == null || duracion < 180) {
            txtObjetivoResumen.setText("Referencia: 8 h");
        } else {
            txtObjetivoResumen.setText(porcentajeObjetivo + "% de 8 h");
        }
        progresoSueno.setMax(480);
        progresoSueno.setProgress(duracion == null ? 0 : Math.min(480, Math.max(0, duracion)));
        progresoDormirse.setMax(60);
        progresoDormirse.setProgress(latencia == null ? 0 : Math.min(60, Math.max(0, latencia)));

        Integer activaciones = r == null ? null : r.activaciones;
        txtMonitoreoTelefono.setText(
                activaciones == null
                        ? "Sin activaciones registradas"
                        : activaciones + " activaciones de pantalla registradas");
        int cantidad = r == null ? 0 : r.audios.size();
        txtRuidosGuardados.setText(
                cantidad == 0 ? "Sin audios registrados" : cantidad + " audios guardados");
        txtCantidadAudios.setVisibility(cantidad == 0 ? View.GONE : View.VISIBLE);
        if (cantidad > 0) {
            txtCantidadAudios.setText(cantidad > 99 ? "99+" : String.valueOf(cantidad));
        }
        if (r != null && r.posiblesInterrupciones != null) {
            txtDespertaresNocturnos.setText(
                    r.posiblesInterrupciones + " posibles interrupciones por pantalla");
        } else if (r != null && r.despertares != null) {
            txtDespertaresNocturnos.setText(r.despertares + " despertares registrados");
        } else if (activaciones != null) {
            txtDespertaresNocturnos.setText(activaciones + " posibles interrupciones por pantalla");
        } else {
            txtDespertaresNocturnos.setText("Sin datos de interrupciones");
        }
    }

    private void configurarGrafico() {
        LinearLayout contenedor = findViewById(R.id.contenedorGrafico);
        contenedor.removeAllViews();
        TextView titulo = crearTexto("Tu semana de descanso", 17);
        titulo.setGravity(Gravity.START);
        titulo.setTypeface(null, Typeface.BOLD);
        titulo.setTextColor(0xFF30264A);
        contenedor.addView(titulo);

        TextView subtitulo = crearTexto("Barras = tiempo registrado · 8 h = referencia", 12);
        subtitulo.setGravity(Gravity.START);
        subtitulo.setTextColor(GRIS);
        LinearLayout.LayoutParams pSub = new LinearLayout.LayoutParams(-1, -2);
        pSub.topMargin = dp(5);
        contenedor.addView(subtitulo, pSub);

        grafico = new GraficoSemanal();
        LinearLayout.LayoutParams pGrafico = new LinearLayout.LayoutParams(-1, dp(230));
        pGrafico.topMargin = dp(14);
        contenedor.addView(grafico, pGrafico);

        txtEstadoGrafico = crearTexto("Sin registros", 12);
        txtEstadoGrafico.setGravity(Gravity.START);
        txtEstadoGrafico.setTextColor(0xFF6E5C82);
        txtEstadoGrafico.setPadding(0, dp(10), 0, dp(5));
        contenedor.addView(txtEstadoGrafico);
    }

    private void actualizarGrafico() {
        int noches = 0;
        int total = 0;
        for (int i = 0; i < 7; i++) {
            Calendar fecha = (Calendar) inicioSemana.clone();
            fecha.add(Calendar.DAY_OF_MONTH, i);
            RegistroSueno registro = registros.get(fechaId(fecha));
            if (registro != null && registro.descanso != null && registro.descanso > 0) {
                noches++;
                total += registro.descanso;
            }
        }
        if (noches == 0) {
            txtEstadoGrafico.setText("Aún no hay sesiones finalizadas esta semana");
        } else {
            int media = Math.round(total / (float) noches);
            txtEstadoGrafico.setText(
                    noches
                            + " de 7 días con registro · Media: "
                            + (media / 60)
                            + " h "
                            + (media % 60)
                            + " min");
        }
        grafico.invalidate();
    }

    private class GraficoSemanal extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        GraficoSemanal() {
            super(Registros.this);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float ancho = getWidth();
            float alto = getHeight();
            if (ancho < 1 || alto < 1) return;
            float izquierda = dp(29);
            float derecha = ancho - dp(5);
            float arriba = dp(26);
            float abajo = alto - dp(37);
            float escala = abajo - arriba;
            int[] duraciones = new int[7];
            boolean[] presentes = new boolean[7];
            int maximo = 600;
            for (int i = 0; i < 7; i++) {
                Calendar dia = (Calendar) inicioSemana.clone();
                dia.add(Calendar.DAY_OF_MONTH, i);
                RegistroSueno registro = registros.get(fechaId(dia));
                if (registro != null && registro.descanso != null && registro.descanso > 0) {
                    presentes[i] = true;
                    duraciones[i] = registro.descanso;
                    maximo = Math.max(maximo, registro.descanso + 60);
                }
            }
            int escalaMax = Math.max(600, ((maximo + 119) / 120) * 120);
            p.setStyle(Paint.Style.FILL);
            p.setPathEffect(null);
            p.setTextSize(dp(10));
            p.setTextAlign(Paint.Align.RIGHT);
            p.setTypeface(Typeface.DEFAULT);
            for (int horas = 0; horas <= escalaMax / 60; horas += 2) {
                float y = abajo - horas * 60f / escalaMax * escala;
                p.setColor(0xFF9F92AE);
                canvas.drawText(horas + " h", izquierda - dp(7), y + dp(3), p);
                p.setColor(0xFFEDE7F2);
                p.setStrokeWidth(dp(1));
                canvas.drawLine(izquierda, y, derecha, y, p);
            }
            float y8 = abajo - 480f / escalaMax * escala;
            p.setColor(0xFFB397D9);
            p.setStrokeWidth(dp(1.5f));
            p.setPathEffect(new DashPathEffect(new float[] {dp(5), dp(4)}, 0));
            canvas.drawLine(izquierda, y8, derecha, y8, p);
            p.setPathEffect(null);

            float paso = (derecha - izquierda) / 7f;
            for (int i = 0; i < 7; i++) {
                float x = izquierda + paso * (i + 0.5f);
                float mitad = Math.min(dp(15), paso * 0.28f);
                Calendar dia = (Calendar) inicioSemana.clone();
                dia.add(Calendar.DAY_OF_MONTH, i);
                boolean seleccionado = fechaId(dia).equals(fechaId(fechaSeleccionada));

                p.setColor(0xFFF2EEF7);
                canvas.drawRoundRect(
                        new RectF(x - mitad, arriba, x + mitad, abajo), dp(7), dp(7), p);
                if (presentes[i]) {
                    float altura = Math.min(escala, duraciones[i] / (float) escalaMax * escala);
                    p.setColor(seleccionado ? MORADO : 0xFFBEABE2);
                    canvas.drawRoundRect(
                            new RectF(x - mitad, abajo - altura, x + mitad, abajo),
                            dp(7),
                            dp(7),
                            p);
                    p.setTypeface(Typeface.DEFAULT_BOLD);
                    p.setColor(0xFF4B3666);
                    p.setTextAlign(Paint.Align.CENTER);
                    p.setTextSize(dp(10));
                    String etiqueta =
                            duraciones[i] >= 60 ? (duraciones[i] / 60) + "h" : duraciones[i] + "m";
                    canvas.drawText(etiqueta, x, Math.max(dp(13), abajo - altura - dp(7)), p);
                }
                p.setTextAlign(Paint.Align.CENTER);
                p.setTypeface(seleccionado ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                p.setColor(seleccionado ? MORADO : 0xFF9184A1);
                p.setTextSize(dp(11));
                canvas.drawText(DIAS_GRAFICO[i], x, alto - dp(10), p);
            }
        }
    }

    private String nombreAudio(String tipo) {
        switch (tipo) {
            case "ronquido":
                return "Posible ronquido";
            case "voz_detectada":
                return "Voz detectada";
            case "ruido_fuerte":
                return "Ruido fuerte";
            default:
                return tipo.isEmpty() ? "Audio nocturno" : tipo;
        }
    }

    private GradientDrawable fondoAudios(int color, int radio) {
        GradientDrawable fondo = new GradientDrawable();
        fondo.setColor(color);
        fondo.setCornerRadius(dp(radio));
        return fondo;
    }

    private void mostrarAudios() {
        RegistroSueno r = registroActual();
        if (r == null || r.audios.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Sonidos nocturnos")
                    .setMessage("No hay grabaciones para este día.")
                    .setPositiveButton("Aceptar", null)
                    .show();
            return;
        }

        LinearLayout principal = new LinearLayout(this);
        principal.setOrientation(LinearLayout.VERTICAL);
        principal.setPadding(dp(16), dp(18), dp(16), dp(16));
        principal.setBackground(fondoAudios(0xFFFFF9FF, 22));
        AlertDialog dialogo = new AlertDialog.Builder(this).setView(principal).create();

        LinearLayout cabecera = new LinearLayout(this);
        cabecera.setGravity(Gravity.CENTER_VERTICAL);
        TextView titulo = crearTexto("Sonidos nocturnos", 20);
        titulo.setTextColor(0xFF30264A);
        titulo.setTypeface(null, Typeface.BOLD);
        cabecera.addView(titulo, new LinearLayout.LayoutParams(0, dp(44), 1f));
        TextView cerrar = crearTexto("×", 27);
        cerrar.setTextColor(MORADO);
        cerrar.setContentDescription("Cerrar grabaciones");
        cabecera.addView(cerrar, new LinearLayout.LayoutParams(dp(38), dp(40)));
        cerrar.setOnClickListener(v -> dialogo.dismiss());
        principal.addView(cabecera);

        TextView contador = crearTexto(r.audios.size() + " sonidos guardados", 12);
        contador.setGravity(Gravity.START);
        contador.setTextColor(MORADO);
        contador.setPadding(dp(8), dp(7), dp(8), dp(12));
        principal.addView(contador);

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(lista);
        principal.addView(
                scroll,
                new LinearLayout.LayoutParams(-1, Math.min(dp(390), r.audios.size() * dp(83))));

        String fecha = fechaId(fechaSeleccionada);
        for (Audio audio : r.audios) {
            LinearLayout fila = new LinearLayout(this);
            fila.setGravity(Gravity.CENTER_VERTICAL);
            fila.setPadding(dp(9), dp(9), dp(9), dp(9));
            fila.setBackground(fondoAudios(0xFFF3EEFA, 15));
            LinearLayout.LayoutParams pFila = new LinearLayout.LayoutParams(-1, dp(74));
            pFila.bottomMargin = dp(8);
            lista.addView(fila, pFila);

            TextView icono = crearTexto("♫", 22);
            icono.setTextColor(MORADO);
            icono.setBackground(fondoAudios(0xFFE8DDF8, 12));
            fila.addView(icono, new LinearLayout.LayoutParams(dp(38), dp(38)));

            LinearLayout informacion = new LinearLayout(this);
            informacion.setGravity(Gravity.CENTER_VERTICAL);
            informacion.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams pInfo = new LinearLayout.LayoutParams(0, -1, 1f);
            pInfo.leftMargin = dp(9);
            fila.addView(informacion, pInfo);

            TextView nombre = crearTexto(nombreAudio(audio.tipo), 13);
            nombre.setGravity(Gravity.START);
            nombre.setTextColor(0xFF30264A);
            nombre.setTypeface(null, Typeface.BOLD);
            informacion.addView(nombre);

            String detalle = audio.hora;
            Object duracion = audio.datos.get("duracionSegundos");
            if (duracion instanceof Number) {
                detalle +=
                        " · "
                                + String.format(
                                        Locale.US, "%.1f s", ((Number) duracion).doubleValue());
            }
            TextView hora = crearTexto(detalle, 11);
            hora.setGravity(Gravity.START);
            hora.setTextColor(GRIS);
            informacion.addView(hora);

            ImageButton reproducir = new ImageButton(this);
            reproducir.setImageResource(android.R.drawable.ic_media_play);
            reproducir.setColorFilter(Color.WHITE);
            reproducir.setBackground(fondoAudios(MORADO, 11));
            reproducir.setPadding(dp(9), dp(9), dp(9), dp(9));
            reproducir.setContentDescription("Reproducir audio");
            fila.addView(reproducir, new LinearLayout.LayoutParams(dp(38), dp(38)));

            ImageButton eliminar = new ImageButton(this);
            eliminar.setImageResource(android.R.drawable.ic_menu_delete);
            eliminar.setColorFilter(MORADO);
            eliminar.setBackground(fondoAudios(0xFFE8DDF8, 11));
            eliminar.setPadding(dp(9), dp(9), dp(9), dp(9));
            LinearLayout.LayoutParams pEliminar = new LinearLayout.LayoutParams(dp(38), dp(38));
            pEliminar.leftMargin = dp(5);
            fila.addView(eliminar, pEliminar);

            reproducir.setOnClickListener(
                    v -> {
                        if (audioActivo == audio && reproductor != null) {
                            detenerAudio();
                        } else {
                            reproducirAudio(audio);
                            if (reproductor != null) {
                                audioActivo = audio;
                                botonAudioActivo = reproducir;
                                reproducir.setImageResource(android.R.drawable.ic_media_pause);
                            }
                        }
                    });
            eliminar.setOnClickListener(v -> confirmarEliminarAudio(audio, fecha, dialogo));
        }
        dialogo.setOnDismissListener(d -> detenerAudio());
        dialogo.show();
        if (dialogo.getWindow() != null) {
            dialogo.getWindow()
                    .setBackgroundDrawable(
                            new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            int ancho =
                    Math.min(
                            dp(430),
                            (int) (getResources().getDisplayMetrics().widthPixels * 0.94f));
            dialogo.getWindow().setLayout(ancho, -2);
        }
    }

    private File archivoLocal(Audio audio) {

        if (audio.archivoLocal.isEmpty()
                || !audio.archivoLocal.startsWith("audios_sueno/")
                || !audio.archivoLocal.endsWith(".wav")) {
            return null;
        }

        try {
            File carpeta = new File(getFilesDir(), "audios_sueno").getCanonicalFile();
            File archivo = new File(getFilesDir(), audio.archivoLocal).getCanonicalFile();

            if (archivo.getPath().startsWith(carpeta.getPath() + File.separator)) {
                return archivo;
            }
        } catch (IOException e) {
            android.util.Log.e("SleepTrackAudio", "Ruta local inválida", e);
        }
        return null;
    }

    private void reproducirAudio(Audio audio) {

        detenerAudio();
        File archivo = archivoLocal(audio);

        if (archivo != null && archivo.isFile()) {
            try {
                MediaPlayer player = new MediaPlayer();
                reproductor = player;
                player.setAudioAttributes(
                        new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build());
                player.setDataSource(archivo.getAbsolutePath());

                player.setOnPreparedListener(
                        mp -> {
                            if (reproductor == mp) mp.start();
                        });
                player.setOnCompletionListener(mp -> detenerAudio());
                player.setOnErrorListener(
                        (mp, what, extra) -> {
                            detenerAudio();
                            Toast.makeText(
                                            this,
                                            "No se pudo reproducir el audio",
                                            Toast.LENGTH_SHORT)
                                    .show();
                            return true;
                        });
                player.prepareAsync();
                return;
            } catch (IOException | RuntimeException e) {
                detenerAudio();
                android.util.Log.e("SleepTrackAudio", "Error al abrir WAV", e);
                Toast.makeText(this, "No se pudo abrir el archivo WAV", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (audio.url.startsWith("https://")) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(audio.url)));
            } catch (ActivityNotFoundException | SecurityException e) {
                Toast.makeText(this, "No se pudo abrir el enlace del audio", Toast.LENGTH_SHORT)
                        .show();
            }
        } else {
            Toast.makeText(this, "Archivo no disponible en este dispositivo", Toast.LENGTH_LONG)
                    .show();
        }
    }

    private void detenerAudio() {
        if (reproductor != null) {
            reproductor.release();
            reproductor = null;
        }
        if (botonAudioActivo != null) {
            botonAudioActivo.setImageResource(android.R.drawable.ic_media_play);
        }
        botonAudioActivo = null;
        audioActivo = null;
    }

    private void confirmarEliminarAudio(Audio audio, String fecha, AlertDialog dialogoAudios) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar grabación")
                .setMessage("¿Eliminar este audio de SleepTrack y del dispositivo?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton(
                        "Eliminar",
                        (d, which) -> {
                            if (usuario == null) return;

                            detenerAudio();
                            RepositorioDescanso.eliminarAudio(usuario.getUid(), fecha, audio.datos)
                                    .addOnSuccessListener(
                                            v -> {
                                                File archivo = archivoLocal(audio);
                                                if (archivo != null
                                                        && archivo.isFile()
                                                        && !archivo.delete()) {
                                                    Toast.makeText(
                                                                    this,
                                                                    "Registro eliminado; no se pudo"
                                                                        + " borrar el WAV",
                                                                    Toast.LENGTH_LONG)
                                                            .show();
                                                } else {
                                                    Toast.makeText(
                                                                    this,
                                                                    "Audio eliminado",
                                                                    Toast.LENGTH_SHORT)
                                                            .show();
                                                }
                                                dialogoAudios.dismiss();
                                            })
                                    .addOnFailureListener(
                                            e ->
                                                    Toast.makeText(
                                                                    this,
                                                                    "No se pudo eliminar el audio"
                                                                        + " de Firestore",
                                                                    Toast.LENGTH_LONG)
                                                            .show());
                        })
                .show();
    }
}
