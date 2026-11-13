package com.example.sleeptrack;

import android.content.Intent;
import com.example.sleeptrack.modelo.DiaDescansoBean;
import com.example.sleeptrack.modelo.HallazgoPatronBean;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.LinearLayout;
import android.view.Gravity;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Patrones extends AppCompatActivity {

    private static final int MORADO = Color.rgb(112, 83, 176);
    private static final int TEXTO = Color.rgb(48, 38, 74);
    private static final int SECUNDARIO = Color.rgb(132, 115, 143);

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private FirebaseUser usuario;
    private TextView btn7, btn30, btn90, resumen, estado;
    private LinearLayout contenedorPatrones;
    private View pantallaVacia, pantallaDatos, pantallaCarga;
    private View scrollPatrones;
    private ProgressBar progresoCarga;
    private TextView txtEstadoCarga, btnReintentar;
    private TextView txtVacio, txtPromedio, txtNoches, txtIndicador;
    private GraficoDuracion grafico;
    private int periodo = 7;
    private int consultaActual = 0;
    private String fechaInicio;
    private String fechaFin;
    private final List<Noche> noches = new ArrayList<>();

    private static class Noche {
        String fecha;
        Integer calidad;
        Integer minutosDescanso;
        Long comienzo;
        Long finPrincipal;
        Integer minutosPrincipales;
        final List<Map<?, ?>> molestiasAlDespertar = new ArrayList<>();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_patrones);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
                (view, insets) -> {
                    Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    view.setPadding(
                            barras.left,
                            barras.top,
                            barras.right,
                            Math.max(0, barras.bottom - dp(12)));
                    return insets;
                });

        usuario = FirebaseAuth.getInstance().getCurrentUser();
        btn7 = findViewById(R.id.btn7Dias);
        btn30 = findViewById(R.id.btn30Dias);
        btn90 = findViewById(R.id.btn90Dias);
        resumen = findViewById(R.id.txtResumenPeriodo);
        estado = findViewById(R.id.txtEstadoGraficoPatrones);
        contenedorPatrones = findViewById(R.id.contenedorPatrones);
        pantallaVacia = findViewById(R.id.estadoSinRegistros);
        pantallaDatos = findViewById(R.id.contenidoConRegistros);
        pantallaCarga = findViewById(R.id.estadoCargando);
        scrollPatrones = findViewById(R.id.scrollPatrones);
        progresoCarga = findViewById(R.id.progresoCargaPatrones);
        txtEstadoCarga = findViewById(R.id.txtEstadoCargaPatrones);
        btnReintentar = findViewById(R.id.btnReintentarPatrones);
        btnReintentar.setOnClickListener(v -> cargarDatos());
        txtVacio = findViewById(R.id.txtMensajeVacio);
        txtPromedio = findViewById(R.id.txtPromedioDuracion);
        txtNoches = findViewById(R.id.txtNumeroNoches);
        txtIndicador = findViewById(R.id.txtIndicadorResumen);
        findViewById(R.id.btnIrRegistros).setOnClickListener(v -> abrir(alarma.class));

        FrameLayout marco = findViewById(R.id.contenedorGraficoPatrones);
        grafico = new GraficoDuracion();
        marco.addView(grafico, new FrameLayout.LayoutParams(-1, -1));

        btn7.setOnClickListener(v -> cambiarPeriodo(7));
        btn30.setOnClickListener(v -> cambiarPeriodo(30));
        btn90.setOnClickListener(v -> cambiarPeriodo(90));
        configurarNavegacion();
        seleccionarPeriodo();
        mostrarCargando("Cargando tus registros...");
        scrollPatrones.post(this::ajustarAlturaEstados);
    }

    @Override
    protected void onStart() {
        super.onStart();
        cargarDatos();
    }

    @Override
    protected void onStop() {
        consultaActual++;
        super.onStop();
    }

    private void configurarNavegacion() {
        NavegacionPrincipal.configurar(this, R.id.navPatrones);
    }

    private void abrir(Class<?> destino) {
        startActivity(new Intent(this, destino));
        finish();
    }

    private void cambiarPeriodo(int dias) {
        if (periodo == dias) return;
        periodo = dias;
        seleccionarPeriodo();
        cargarDatos();
    }

    private void seleccionarPeriodo() {
        seleccionar(btn7, periodo == 7);
        seleccionar(btn30, periodo == 30);
        seleccionar(btn90, periodo == 90);
    }

    private void seleccionar(TextView boton, boolean activo) {
        boton.setBackgroundResource(
                activo ? R.drawable.st_pt_periodo_activo : android.R.color.transparent);
        boton.setTextColor(activo ? MORADO : SECUNDARIO);
        boton.setTypeface(null, activo ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void mostrarCargando(String mensaje) {
        pantallaCarga.setVisibility(View.VISIBLE);
        pantallaVacia.setVisibility(View.GONE);
        pantallaDatos.setVisibility(View.GONE);
        progresoCarga.setVisibility(View.VISIBLE);
        btnReintentar.setVisibility(View.GONE);
        txtEstadoCarga.setText(mensaje);
    }

    private void mostrarError(String mensaje) {
        pantallaCarga.setVisibility(View.VISIBLE);
        pantallaVacia.setVisibility(View.GONE);
        pantallaDatos.setVisibility(View.GONE);
        progresoCarga.setVisibility(View.GONE);
        btnReintentar.setVisibility(View.VISIBLE);
        txtEstadoCarga.setText(mensaje);
    }

    private void mostrarErrorConsulta(Exception error) {
        String mensaje = "No pudimos consultar tus registros. Inténtalo de nuevo.";
        if (error instanceof com.google.firebase.firestore.FirebaseFirestoreException) {
            com.google.firebase.firestore.FirebaseFirestoreException fallo =
                    (com.google.firebase.firestore.FirebaseFirestoreException) error;
            switch (fallo.getCode()) {
                case PERMISSION_DENIED:
                    mensaje =
                            "No pudimos acceder a tus registros. Revisa los permisos de la cuenta"
                                + " en Firebase.";
                    break;
                case UNAUTHENTICATED:
                    mensaje = "Tu sesión necesita renovarse. Vuelve a iniciar sesión.";
                    break;
                case UNAVAILABLE:
                case DEADLINE_EXCEEDED:
                    mensaje =
                            "El servicio no está disponible en este momento. Revisa la conexión o"
                                + " inténtalo después.";
                    break;
                default:
                    break;
            }
        }
        mostrarError(mensaje);
    }

    private void mostrarContenido() {
        pantallaCarga.setVisibility(View.GONE);
        pantallaVacia.setVisibility(View.GONE);
        pantallaDatos.setVisibility(View.VISIBLE);
    }

    private void mostrarPantallaVacia(String mensaje) {
        pantallaCarga.setVisibility(View.GONE);
        pantallaVacia.setVisibility(View.VISIBLE);
        pantallaDatos.setVisibility(View.GONE);
        txtVacio.setText(mensaje);
        scrollPatrones.post(this::ajustarAlturaEstados);
    }

    private void ajustarAlturaEstados() {
        View titulo = findViewById(R.id.txtTituloPatrones);
        View subtitulo = findViewById(R.id.txtSubtituloPatrones);
        int disponible =
                scrollPatrones.getHeight()
                        - titulo.getHeight()
                        - subtitulo.getHeight()
                        - dp(24 + 4 + 22 + 16);
        int altura = Math.max(dp(450), disponible);
        actualizarAltura(pantallaVacia, altura);
        actualizarAltura(pantallaCarga, altura);
    }

    private void actualizarAltura(View vista, int altura) {
        if (!(vista.getLayoutParams() instanceof LinearLayout.LayoutParams)) return;
        LinearLayout.LayoutParams parametros = (LinearLayout.LayoutParams) vista.getLayoutParams();
        if (parametros.height == altura) return;
        parametros.height = altura;
        vista.setLayoutParams(parametros);
    }

    private void mostrarSinDatosEnPeriodo() {
        mostrarContenido();
        pintarGrafico();
        mostrarPatrones(Collections.emptyMap(), true);
    }

    private void cargarDatos() {
        final int token = ++consultaActual;
        usuario = FirebaseAuth.getInstance().getCurrentUser();
        noches.clear();
        mostrarCargando("Revisando tus sesiones de descanso...");
        grafico.mostrar(Collections.emptyList(), Collections.emptyList());
        estado.setText("");
        resumen.setText("Cargando registros...");
        txtNoches.setText("—");
        txtPromedio.setText("—");
        txtIndicador.setText("Sin datos todavía");
        contenedorPatrones.removeAllViews();

        if (usuario == null) {
            mostrarError("Inicia sesión para consultar tus registros.");
            return;
        }

        Calendar ahora = Calendar.getInstance();
        fechaFin = fechaId(ahora);
        ahora.add(Calendar.DAY_OF_YEAR, -(periodo - 1));
        fechaInicio = fechaId(ahora);

        db.collection("usuarios")
                .document(usuario.getUid())
                .collection("registrosSueno")
                .whereGreaterThanOrEqualTo(FieldPath.documentId(), fechaInicio)
                .whereLessThanOrEqualTo(FieldPath.documentId(), fechaFin)
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener(
                        documentos -> {
                            if (token != consultaActual || isFinishing()) return;
                            noches.clear();
                            for (QueryDocumentSnapshot doc : documentos) {
                                Noche noche = leerNoche(doc);
                                if (noche != null) noches.add(noche);
                            }
                            noches.sort(Comparator.comparing(n -> n.fecha));

                            if (noches.isEmpty()) {
                                mostrarSinDatosEnPeriodo();
                                return;
                            }

                            mostrarContenido();
                            pintarGrafico();
                            contenedorPatrones.removeAllViews();
                            agregarNota("Comparando tus actividades con las noches registradas...");
                            cargarActividades(token);
                        })
                .addOnFailureListener(
                        error -> {
                            if (token != consultaActual || isFinishing()) return;
                            mostrarErrorConsulta(error);
                        });
    }

    private void cargarActividades(int token) {
        Calendar inicio = Calendar.getInstance();
        try {
            Date fecha = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(fechaInicio);
            if (fecha == null) return;
            inicio.setTime(fecha);
        } catch (Exception error) {
            return;
        }
        inicio.add(Calendar.DAY_OF_YEAR, -1);
        String desde = fechaId(inicio);

        db.collection("usuarios")
                .document(usuario.getUid())
                .collection("actividades")
                .whereGreaterThanOrEqualTo(FieldPath.documentId(), desde)
                .whereLessThanOrEqualTo(FieldPath.documentId(), fechaFin)
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener(
                        documentos -> {
                            if (token != consultaActual || isFinishing()) return;
                            Map<String, Map<String, Object>> porFecha = new HashMap<>();
                            for (QueryDocumentSnapshot doc : documentos) {
                                porFecha.put(doc.getId(), doc.getData());
                            }
                            mostrarPatrones(porFecha, true);
                        })
                .addOnFailureListener(
                        error -> {
                            if (token != consultaActual || isFinishing()) return;
                            mostrarPatrones(Collections.emptyMap(), false);
                        });
    }

    private void pintarGrafico() {
        List<String> etiquetas = new ArrayList<>();
        List<Integer> valores = new ArrayList<>();
        Map<String, Integer> indices = new HashMap<>();
        int nochesConDuracion = 0;
        int sumaMinutos = 0;

        for (Noche noche : noches) {
            if (noche.minutosDescanso != null) indices.put(noche.fecha, noche.minutosDescanso);
            if (noche.minutosDescanso != null && noche.minutosDescanso > 0) {
                nochesConDuracion++;
                sumaMinutos += noche.minutosDescanso;
            }
        }

        Calendar fecha = Calendar.getInstance();
        try {
            Date inicio = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(fechaInicio);
            if (inicio != null) fecha.setTime(inicio);
        } catch (Exception ignored) {
        }

        for (int i = 0; i < periodo; i++) {
            String fechaDia = fechaId(fecha);
            etiquetas.add(new SimpleDateFormat("dd/MM", Locale.US).format(fecha.getTime()));
            valores.add(indices.get(fechaDia));
            fecha.add(Calendar.DAY_OF_MONTH, 1);
        }
        grafico.mostrar(etiquetas, valores);
        txtNoches.setText(String.valueOf(noches.size()));
        if (nochesConDuracion == 0) {
            txtPromedio.setText("—");
        } else {
            int m = Math.round(sumaMinutos / (float) nochesConDuracion);
            txtPromedio.setText(String.format(Locale.US, "%d h %02d min", m / 60, m % 60));
        }

        int evaluaciones = 0;
        int sumaCalidad = 0;
        for (Noche noche : noches) {
            if (noche.calidad != null) {
                evaluaciones++;
                sumaCalidad += noche.calidad;
            }
        }
        resumen.setText(nochesConDuracion + " días con descanso registrado en " + periodo + " días");
        txtIndicador.setText(
                evaluaciones == 0
                        ? "Aún no hay evaluaciones de descanso"
                        : "Índice orientativo: "
                                + Math.round(sumaCalidad / (float) evaluaciones)
                                + "% · "
                                + evaluaciones
                                + " evaluaciones");
        estado.setText(
                "Cada barra muestra el total del día (incluye siestas) · — Sin registro\n"
                        + "Las asociaciones usan la sesión nocturna principal. "
                        + "Duración de monitoreo, no sueño confirmado.");
    }

    private void mostrarPatrones(
            Map<String, Map<String, Object>> actividades, boolean actividadesConsultadas) {
        contenedorPatrones.removeAllViews();
        List<DiaDescansoBean> dias = new ArrayList<>();
        for (Noche noche : noches) {
            DiaDescansoBean dia = new DiaDescansoBean();
            dia.setFecha(noche.fecha);
            dia.setMinutosTotales(noche.minutosDescanso);
            dia.setMinutosPrincipales(noche.minutosPrincipales);
            dia.setInicioPrincipal(noche.comienzo);
            dia.setFinPrincipal(noche.finPrincipal);
            dia.getMolestiasAlDespertar().addAll(noche.molestiasAlDespertar);
            dias.add(dia);
        }
        List<HallazgoPatronBean> hallazgos =
                AnalisisPatrones.analizar(dias, actividades, periodo, actividadesConsultadas,
                        usuario == null ? 0 : usuario.getUid().hashCode());
        for (HallazgoPatronBean h : hallazgos) {
            agregarPatron(h.getCategoria(), h.getTitulo(), h.getResumen(), h.getDetalle());
        }
        boolean sonConsejosGenerales = hallazgos.size() == 2;
        for (HallazgoPatronBean h : hallazgos) {
            if (!h.getTitulo().contains("consejo general")) {
                sonConsejosGenerales = false;
                break;
            }
        }
        if (!sonConsejosGenerales) {
            agregarNota("Las coincidencias son orientativas: no demuestran causas ni diagnósticos.");
        } else if (!actividadesConsultadas) {
            agregarNota("No se pudieron consultar las actividades. Los consejos mostrados son generales.");
        }
    }

    private void agregarPatron(String categoria, String titulo, String descripcion) {
        agregarPatron(categoria, titulo, "Prueba otro período.", descripcion);
    }

    private void agregarPatron(
            String categoria, String titulo, String descripcion, String evidencia) {
        LinearLayout tarjeta = new LinearLayout(this);
        tarjeta.setOrientation(LinearLayout.HORIZONTAL);
        tarjeta.setGravity(Gravity.CENTER_VERTICAL);
        tarjeta.setPadding(dp(13), dp(15), dp(13), dp(15));
        tarjeta.setBackgroundResource(R.drawable.st_pt_card);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.bottomMargin = dp(11);
        contenedorPatrones.addView(tarjeta, layout);

        ImageView icono = new ImageView(this);
        icono.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icono.setPadding(dp(10), dp(10), dp(10), dp(10));
        icono.setBackgroundResource(R.drawable.st_pt_icon_bg);
        int recurso;
        switch (categoria) {
            case "cafe":
                recurso = R.drawable.st_ac_bebidas;
                break;
            case "alcohol":
                recurso = R.drawable.st_ac_alcohol;
                break;
            case "cigarrillos":
                recurso = R.drawable.st_ac_cigarrillos;
                break;
            case "trabajo":
                recurso = R.drawable.st_ac_trabajo;
                break;
            case "salud":
            case "bienestar":
                recurso = R.drawable.st_ac_molestias;
                break;
            case "suplementos":
                recurso = R.drawable.st_ac_suplementos;
                break;
            case "sueno":
            case "ambiente":
            case "pantallas":
            case "actividades":
            default:
                recurso = R.drawable.st_capsula_patrones;
                break;
        }
        icono.setImageResource(recurso);
        icono.setContentDescription("Categoría: " + categoria);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        iconParams.rightMargin = dp(12);
        tarjeta.addView(icono, iconParams);

        LinearLayout textoTarjeta = new LinearLayout(this);
        textoTarjeta.setOrientation(LinearLayout.VERTICAL);
        tarjeta.addView(textoTarjeta, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView encabezado = texto(titulo, 14, TEXTO, true);
        encabezado.setMaxLines(1);
        encabezado.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textoTarjeta.addView(encabezado);
        TextView detalle = texto(descripcion, 12, SECUNDARIO, false);
        detalle.setMaxLines(1);
        detalle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tarjeta.setClickable(true);
        tarjeta.setFocusable(true);
        tarjeta.setContentDescription(titulo + ". " + descripcion + ". Toca para ver detalles.");
        tarjeta.setOnClickListener(
                v ->
                        new androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle(titulo)
                                .setMessage(evidencia)
                                .setPositiveButton("Entendido", null)
                                .show());
        LinearLayout.LayoutParams d = new LinearLayout.LayoutParams(-1, -2);
        d.topMargin = dp(5);
        textoTarjeta.addView(detalle, d);
    }

    private void agregarNota(String mensaje) {
        TextView aviso = texto(mensaje, 11, SECUNDARIO, false);
        aviso.setGravity(Gravity.CENTER);
        aviso.setPadding(dp(8), dp(9), dp(8), dp(9));
        contenedorPatrones.addView(aviso);
    }

    private TextView texto(String contenido, int size, int color, boolean negrita) {
        TextView text = new TextView(this);
        text.setText(contenido);
        text.setTextSize(size);
        text.setTextColor(color);
        if (negrita) text.setTypeface(null, Typeface.BOLD);
        return text;
    }

    private static Noche leerNoche(DocumentSnapshot doc) {
        Map<String, Object> data = doc.getData();
        if (data == null) return null;
        Noche noche = new Noche();
        noche.fecha = doc.getId();
        Map<String, Object> sesion = mapa(data.get("sesion"));
        Map<String, Object> evaluacion = mapa(data.get("evaluacion"));
        // En el feedback nuevo "porcentaje" representa la elección subjetiva
        // (25/60/90); el índice combinado está en "indiceOrientativo".
        // Las siestas dejan el índice en null y no se grafican como noches.
        Object indice =
                evaluacion.containsKey("indiceOrientativo")
                        ? evaluacion.get("indiceOrientativo")
                        : evaluacion.containsKey("porcentaje")
                                ? evaluacion.get("porcentaje")
                                : data.get("porcentajeDescanso");
        noche.calidad = entero(indice);
        if (noche.calidad != null && (noche.calidad < 0 || noche.calidad > 100)) {
            noche.calidad = null;
        }
        noche.minutosDescanso = AnalizadorSueno.calcular(data).minutosDescanso;
        if (noche.minutosDescanso != null
                && (noche.minutosDescanso <= 0 || noche.minutosDescanso > 1440))
            noche.minutosDescanso = null;
        Object fin = sesion.containsKey("fin") ? sesion.get("fin") : data.get("finDescanso");
        boolean terminada = tiempo(fin) != null || "finalizada".equals(sesion.get("estado"));
        // Los documentos de configuración o las sesiones abiertas no cuentan como noches.
        if (!terminada && noche.minutosDescanso == null) return null;
        if (noche.minutosDescanso == null && noche.calidad == null) return null;
        Object inicio =
                sesion.containsKey("inicio") ? sesion.get("inicio") : data.get("inicioDescanso");
        if (data.containsKey("sesionesFinalizadas")) {
            Map<String, Object> principal =
                    ResumenSesiones.principal(ResumenSesiones.registros(data));
            long[] intervalo = principal == null ? null : ResumenSesiones.intervalo(principal);
            if (intervalo != null) {
                noche.comienzo = intervalo[0];
                noche.finPrincipal = intervalo[1];
                noche.minutosPrincipales =
                        (int) Math.round((intervalo[1] - intervalo[0]) / 60000.0);
            }
        } else {
            noche.comienzo = tiempo(inicio);
            noche.finPrincipal = tiempo(fin);
            if (noche.comienzo != null && noche.finPrincipal != null
                    && noche.finPrincipal > noche.comienzo
                    && noche.finPrincipal - noche.comienzo <= 18L * 60 * 60000) {
                noche.minutosPrincipales =
                        (int) Math.round((noche.finPrincipal - noche.comienzo) / 60000.0);
            }
        }
        Object respuestas = evaluacion.get("molestiasAlDespertar");
        if (respuestas instanceof List) {
            for (Object item : (List<?>) respuestas) {
                if (item instanceof Map) noche.molestiasAlDespertar.add((Map<?, ?>) item);
            }
        }
        return noche;
    }

    private static Map<String, Object> mapa(Object valor) {
        if (!(valor instanceof Map)) return Collections.emptyMap();
        Map<?, ?> entrada = (Map<?, ?>) valor;
        Map<String, Object> salida = new HashMap<>();
        for (Map.Entry<?, ?> item : entrada.entrySet()) {
            if (item.getKey() instanceof String)
                salida.put((String) item.getKey(), item.getValue());
        }
        return salida;
    }

    private static Integer entero(Object valor) {
        return valor instanceof Number ? ((Number) valor).intValue() : null;
    }

    private static Long tiempo(Object valor) {
        if (valor instanceof Timestamp) return ((Timestamp) valor).toDate().getTime();
        if (valor instanceof Date) return ((Date) valor).getTime();
        if (valor instanceof Number) {
            long n = ((Number) valor).longValue();
            if (n > 100000000000L) return n;
            if (n > 1000000000L) return n * 1000;
        }
        return null;
    }

    private static String texto(Object valor) {
        return valor == null ? "" : String.valueOf(valor);
    }

    private static float promedio(List<Integer> numeros) {
        float suma = 0;
        for (Integer n : numeros) suma += n;
        return suma / numeros.size();
    }

    private static String fechaId(Calendar fecha) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(fecha.getTime());
    }

    private int dp(float valor) {
        return Math.round(valor * getResources().getDisplayMetrics().density);
    }

    private class GraficoDuracion extends android.widget.HorizontalScrollView {
        private final LinearLayout barras;

        GraficoDuracion() {
            super(Patrones.this);
            setFillViewport(true);
            setHorizontalScrollBarEnabled(true);
            barras = new LinearLayout(Patrones.this);
            barras.setOrientation(LinearLayout.HORIZONTAL);
            barras.setGravity(Gravity.BOTTOM);
            barras.setPadding(dp(4), dp(8), dp(4), dp(12));
            addView(barras, new android.widget.HorizontalScrollView.LayoutParams(-2, -1));
        }

        void mostrar(List<String> fechas, List<Integer> minutos) {
            barras.removeAllViews();
            int maximo = 480;
            for (Integer valor : minutos) {
                if (valor != null) maximo = Math.max(maximo, valor);
            }
            for (int i = 0; i < fechas.size(); i++) {
                Integer valor = minutos.get(i);
                String duracion =
                        valor == null
                                ? "—"
                                : String.format(Locale.US, "%dh%02d", valor / 60, valor % 60);
                LinearLayout columna = new LinearLayout(Patrones.this);
                columna.setOrientation(LinearLayout.VERTICAL);
                columna.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
                columna.setPadding(dp(3), 0, dp(3), 0);
                columna.setContentDescription(
                        fechas.get(i)
                                + ": "
                                + (valor == null
                                        ? "sin duración registrada"
                                        : valor / 60 + " horas " + valor % 60 + " minutos"));
                columna.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
                barras.addView(columna, new LinearLayout.LayoutParams(dp(58), -1));

                TextView etiqueta = texto(duracion, 11, TEXTO, true);
                etiqueta.setGravity(Gravity.CENTER);
                etiqueta.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                columna.addView(etiqueta, new LinearLayout.LayoutParams(-1, dp(28)));
                FrameLayout pista = new FrameLayout(Patrones.this);
                columna.addView(pista, new LinearLayout.LayoutParams(dp(26), dp(130)));
                View barra = new View(Patrones.this);
                android.graphics.drawable.GradientDrawable fondo =
                        new android.graphics.drawable.GradientDrawable();
                fondo.setColor(valor == null ? Color.rgb(223, 216, 232) : MORADO);
                fondo.setCornerRadius(dp(7));
                barra.setBackground(fondo);
                int altura =
                        valor == null
                                ? dp(3)
                                : Math.max(dp(4), Math.round(dp(130) * valor / (float) maximo));
                pista.addView(barra, new FrameLayout.LayoutParams(-1, altura, Gravity.BOTTOM));
                TextView fecha = texto(fechas.get(i), 11, SECUNDARIO, false);
                fecha.setGravity(Gravity.CENTER);
                fecha.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                columna.addView(fecha, new LinearLayout.LayoutParams(-1, dp(30)));
            }
            post(() -> fullScroll(View.FOCUS_RIGHT));
        }
    }
}
