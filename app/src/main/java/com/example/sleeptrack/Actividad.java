package com.example.sleeptrack;

import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.widget.SwitchCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.WindowManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;


public class Actividad extends AppCompatActivity {
    private Button btnSuplementos, btnBebidas;
    private Button btnCigarrillos, btnMolestias;
    private Button btnHoraAlcohol, btnHorarioTrabajo;
    private Button btnGuardarActividad;
    private SwitchCompat switchAlcohol, switchTrabajoNocturno;
    private TextView txtFechaActividad;
    private TextView txtResumenSuplementos, txtResumenBebidas;
    private TextView txtResumenCigarrillos, txtResumenMolestias;
    private TextView txtHoraAlcohol, txtHorarioTrabajo;
    private FirebaseFirestore db;
    private FirebaseUser usuario;
    private Calendar fechaSeleccionada;
    private String inicioTrabajo = null;
    private String finTrabajo = null;
    private boolean actualizandoDatos = false;
    private final ArrayList<Evento> suplementos = new ArrayList<>();
    private final ArrayList<Evento> bebidas = new ArrayList<>();
    private final ArrayList<Evento> alcohol = new ArrayList<>();
    private final ArrayList<Evento> cigarrillos = new ArrayList<>();
    private final ArrayList<Evento> molestias = new ArrayList<>();

    private final ArrayList<String> opcionesSuplementos =
            new ArrayList<>(Arrays.asList(
                    "Melatonina",
                    "Magnesio",
                    "Vitamina D",
                    "Vitamina B12",
                    "Multivitamínico",
                    "Omega 3"
            ));

    private final ArrayList<String> opcionesBebidas =
            new ArrayList<>(Arrays.asList(
                    "Café",
                    "Café descafeinado",
                    "Bebida energizante"
            ));

    private final ArrayList<String> opcionesMolestias =
            new ArrayList<>(Arrays.asList(
                    "Migraña",
                    "Dolor de cabeza",
                    "Estrés",
                    "Ansiedad",
                    "Dolor muscular",
                    "Insomnio"
            ));

    private static class Evento {

        String nombre;
        String hora;
        int cantidad;
        Integer intensidad;

        Evento(String nombre, String hora, int cantidad) {
            this(nombre, hora, cantidad, null);
        }

        Evento(String nombre, String hora, int cantidad, Integer intensidad) {
            this.nombre = nombre;
            this.hora = hora;
            this.cantidad = cantidad;
            this.intensidad = intensidad;
        }

        Map<String, Object> convertirMapa() {

            Map<String, Object> datos = new HashMap<>();
            datos.put("nombre", nombre);
            datos.put("hora", hora);
            datos.put("cantidad", cantidad);
            if (intensidad != null) {
                datos.put("intensidad", intensidad);
            }
            return datos;
        }
    }

    private interface AlSeleccionarHora {
        void recibir(String hora);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_actividad);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right,
                    systemBars.bottom);
            return insets;
        });

        db = FirebaseFirestore.getInstance();
        usuario = FirebaseAuth.getInstance().getCurrentUser();

        btnSuplementos = findViewById(R.id.btnSuplementos);
        btnBebidas = findViewById(R.id.btnBebidas);
        btnCigarrillos = findViewById(R.id.btnCigarrillos);
        btnMolestias = findViewById(R.id.btnMolestias);

        btnHoraAlcohol = findViewById(R.id.btnHoraAlcohol);
        btnHorarioTrabajo = findViewById(R.id.btnHorarioTrabajo);
        btnGuardarActividad = findViewById(R.id.btnGuardarActividad);
        btnGuardarActividad.setVisibility(View.GONE);

        switchAlcohol = findViewById(R.id.switchAlcohol);
        switchTrabajoNocturno =
                findViewById(R.id.switchTrabajoNocturno);

        txtFechaActividad = findViewById(R.id.txtFechaActividad);

        txtResumenSuplementos =
                findViewById(R.id.txtResumenSuplementos);

        txtResumenBebidas =
                findViewById(R.id.txtResumenBebidas);

        txtResumenCigarrillos =
                findViewById(R.id.txtResumenCigarrillos);

        txtResumenMolestias =
                findViewById(R.id.txtResumenMolestias);

        txtHoraAlcohol = findViewById(R.id.txtHoraAlcohol);
        txtHorarioTrabajo = findViewById(R.id.txtHorarioTrabajo);

        fechaSeleccionada = Calendar.getInstance();

        String fechaRapida = getIntent().getStringExtra("fechaRegistro");
        if (fechaRapida != null) {
            try {
                SimpleDateFormat formato = new SimpleDateFormat(
                        "yyyy-MM-dd", Locale.US);
                formato.setLenient(false);
                java.util.Date dia = formato.parse(fechaRapida);
                if (dia != null) fechaSeleccionada.setTime(dia);
            } catch (java.text.ParseException ignored) {
            }
        }

        configurarBotones();
        configurarNavegacion();

        actualizarFecha();
        actualizarResumenes();

        if (usuario != null) {
            cargarOpcionesUsuario();
            cargarActividad();
        } else {
            Toast.makeText(this,
                    "Debes iniciar sesión",
                    Toast.LENGTH_SHORT).show();
            habilitarFormulario(false);
        }
    }

    private void configurarBotones() {

        btnSuplementos.setOnClickListener(v ->
                mostrarEventos(
                        "Suplementos",
                        suplementos,
                        () -> seleccionarNombre(
                                opcionesSuplementos,
                                suplementos,
                                "suplemento"
                        ),
                        false
                )
        );

        btnBebidas.setOnClickListener(v ->
                mostrarEventos(
                        "Bebidas",
                        bebidas,
                        () -> seleccionarNombre(
                                opcionesBebidas,
                                bebidas,
                                "bebida"
                        ),
                        false
                )
        );

        btnCigarrillos.setOnClickListener(v ->
                mostrarEventos(
                        "Cigarrillos",
                        cigarrillos,
                        this::seleccionarCantidadCigarrillos,
                        true
                )
        );

        btnMolestias.setOnClickListener(v ->
                mostrarEventos(
                        "Molestias",
                        molestias,
                        () -> seleccionarNombre(
                                opcionesMolestias,
                                molestias,
                                "molestia"
                        ),
                        false
                )
        );

        btnHoraAlcohol.setOnClickListener(v ->
                mostrarEventos(
                        "Consumo de alcohol",
                        alcohol,
                        () -> seleccionarHora(null, hora -> {

                            alcohol.add(
                                    new Evento("Alcohol", hora, 1)
                            );

                            actualizarResumenes();
                            guardarRegistroAutomatico(alcohol);
                        }),
                        false
                )
        );

        btnHorarioTrabajo.setOnClickListener(v ->
                seleccionarHorarioTrabajo()
        );

        switchAlcohol.setOnCheckedChangeListener(
                (buttonView, checked) -> {

                    if (actualizandoDatos) return;

                    if (!checked) {
                        alcohol.clear();
                        guardarRegistroAutomatico(alcohol);
                    }

                    actualizarResumenes();
                }
        );

        switchTrabajoNocturno.setOnCheckedChangeListener(
                (buttonView, checked) -> {

                    if (actualizandoDatos) return;

                    if (!checked) {
                        inicioTrabajo = null;
                        finTrabajo = null;
                        guardarTrabajoAutomatico();
                    }

                    actualizarResumenes();
                }
        );

        txtFechaActividad.setOnClickListener(v ->
                seleccionarFecha()
        );

    }


    private void mostrarEventos(
            String titulo,
            ArrayList<Evento> eventos,
            Runnable agregar,
            boolean esCigarrillo) {

        View vista = getLayoutInflater().inflate(
                R.layout.dialogo_eventos,
                null
        );

        TextView txtTitulo =
                vista.findViewById(R.id.txtTituloDialogo);

        LinearLayout contenedor =
                vista.findViewById(R.id.contenedorListaEventos);

        ScrollView scroll =
                vista.findViewById(R.id.scrollListaEventos);

        Button btnAgregar =
                vista.findViewById(R.id.btnAgregarEvento);

        TextView btnCerrar =
                vista.findViewById(R.id.btnCerrarDialogo);

        txtTitulo.setText(titulo);

        AlertDialog dialogo = new AlertDialog.Builder(this)
                .setView(vista)
                .create();

        if (eventos.isEmpty()) {

            TextView mensaje = new TextView(this);

            mensaje.setText("Todavía no hay registros.");
            mensaje.setTextColor(Color.rgb(128, 115, 141));
            mensaje.setTextSize(14);
            mensaje.setGravity(Gravity.CENTER);
            mensaje.setPadding(0, dp(20), 0, dp(20));

            contenedor.addView(mensaje);

        } else {

            for (int i = 0; i < eventos.size(); i++) {

                Evento evento = eventos.get(i);
                final int posicion = i;

                LinearLayout fila = new LinearLayout(this);

                fila.setOrientation(LinearLayout.HORIZONTAL);
                fila.setGravity(Gravity.CENTER_VERTICAL);
                fila.setPadding(
                        dp(12),
                        dp(12),
                        dp(12),
                        dp(12)
                );

                fila.setMinimumHeight(dp(58));

                fila.setBackgroundResource(
                        R.drawable.fondo_tarjeta_actividad
                );

                LinearLayout.LayoutParams parametrosFila =
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        );

                parametrosFila.bottomMargin = dp(9);

                fila.setLayoutParams(parametrosFila);

                LinearLayout textos = new LinearLayout(this);

                textos.setOrientation(LinearLayout.VERTICAL);

                LinearLayout.LayoutParams parametrosTextos =
                        new LinearLayout.LayoutParams(
                                0,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                1
                        );

                TextView nombre = new TextView(this);

                if (esCigarrillo) {
                    nombre.setText(
                            evento.cantidad + " cigarrillos"
                    );
                } else {
                    nombre.setText(evento.nombre);
                }

                nombre.setTextColor(Color.rgb(41, 32, 53));
                nombre.setTextSize(15);
                nombre.setTypeface(
                        null,
                        android.graphics.Typeface.BOLD
                );

                TextView descripcion = new TextView(this);

                descripcion.setText(eventos == molestias && evento.intensidad != null
                        ? "Intensidad: " + evento.intensidad + "/10 · Toca para editar"
                        : "Toca para editar");
                descripcion.setTextSize(11);
                descripcion.setTextColor(
                        Color.rgb(128, 115, 141)
                );

                textos.addView(nombre);
                textos.addView(descripcion);

                fila.addView(textos, parametrosTextos);

                TextView hora = new TextView(this);

                hora.setText(mostrarHora(evento.hora));
                hora.setTextColor(Color.rgb(112, 83, 176));
                hora.setTextSize(14);
                hora.setTypeface(
                        null,
                        android.graphics.Typeface.BOLD
                );

                hora.setBackgroundResource(
                        R.drawable.fondo_tarjeta_alarma
                );

                hora.setPadding(
                        dp(12),
                        dp(9),
                        dp(12),
                        dp(9)
                );

                fila.addView(hora);

                fila.setOnClickListener(v -> {

                    dialogo.dismiss();

                    editarEvento(
                            eventos,
                            posicion,
                            esCigarrillo
                    );
                });

                contenedor.addView(fila);
            }
        }

        if (eventos.size() > 4) {

            ViewGroup.LayoutParams parametros =
                    scroll.getLayoutParams();

            parametros.height = dp(270);

            scroll.setLayoutParams(parametros);
        }

        btnAgregar.setOnClickListener(v -> {

            dialogo.dismiss();

            agregar.run();
        });

        btnCerrar.setOnClickListener(v ->
                dialogo.dismiss()
        );

        dialogo.show();

        if (dialogo.getWindow() != null) {

            dialogo.getWindow().setBackgroundDrawableResource(
                    android.R.color.transparent
            );

            int ancho = (int) (
                    getResources().getDisplayMetrics().widthPixels
                            * 0.90f
            );

            dialogo.getWindow().setLayout(
                    ancho,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );

        }
    }
    private int dp(int valor) {
        return (int) (
                valor * getResources()
                        .getDisplayMetrics().density + 0.5f
        );
    }
    private void seleccionarNombre(
            ArrayList<String> opcionesBase,
            ArrayList<Evento> destino,
            String categoria) {

        ArrayList<String> opciones =
                new ArrayList<>(opcionesBase);

        opciones.add("Otro...");

        new AlertDialog.Builder(this)
                .setTitle("Seleccionar " + categoria)
                .setItems(
                        opciones.toArray(new String[0]),
                        (dialog, indice) -> {

                            if (indice == opciones.size() - 1) {

                                agregarOtro(
                                        opcionesBase,
                                        destino,
                                        categoria
                                );

                            } else {

                                agregarEvento(
                                        destino,
                                        opciones.get(indice),
                                        1
                                );
                            }
                        })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void agregarOtro(
            ArrayList<String> opcionesBase,
            ArrayList<Evento> destino,
            String categoria) {

        EditText entrada = new EditText(this);

        entrada.setHint("Nombre");
        entrada.setSingleLine(true);
        entrada.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );

        new AlertDialog.Builder(this)
                .setTitle("Agregar " + categoria)
                .setView(entrada)
                .setPositiveButton("Continuar", (dialog, which) -> {

                    String nombre = entrada.getText()
                            .toString().trim();

                    if (nombre.isEmpty()
                            || nombre.equalsIgnoreCase("Ninguno")) {

                        Toast.makeText(this,
                                "Ingresa un nombre válido",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    agregarOpcion(opcionesBase, nombre);

                    agregarEvento(destino, nombre, 1);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void agregarEvento(
            ArrayList<Evento> destino,
            String nombre,
            int cantidad) {

        if (destino == molestias) {
            seleccionarIntensidad(nombre, null, intensidad ->
                    seleccionarHora(null, hora -> {
                        destino.add(new Evento(nombre, hora, cantidad, intensidad));
                        actualizarResumenes();
                        guardarRegistroAutomatico(destino);
                    }));
        } else {
            seleccionarHora(null, hora -> {
                destino.add(new Evento(nombre, hora, cantidad));
                actualizarResumenes();
                guardarRegistroAutomatico(destino);
            });
        }
    }

    private interface AlSeleccionarIntensidad {
        void recibir(int intensidad);
    }

    private void seleccionarIntensidad(String nombre, Integer anterior,
                                       AlSeleccionarIntensidad accion) {
        LinearLayout contenido = new LinearLayout(this);
        contenido.setOrientation(LinearLayout.VERTICAL);
        contenido.setPadding(dp(22), dp(8), dp(22), dp(8));

        TextView descripcion = new TextView(this);
        descripcion.setText("Intensidad al registrar la molestia (0 = nada, 10 = máxima)");
        descripcion.setTextSize(14);
        descripcion.setTextColor(Color.rgb(60, 48, 77));
        contenido.addView(descripcion);

        TextView valor = new TextView(this);
        valor.setTextSize(24);
        valor.setGravity(Gravity.CENTER);
        valor.setTextColor(Color.rgb(112, 83, 176));
        valor.setPadding(0, dp(14), 0, dp(5));
        contenido.addView(valor);

        android.widget.SeekBar barra = new android.widget.SeekBar(this);
        barra.setMax(10);
        barra.setProgress(anterior == null ? 5 : anterior);
        barra.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(112, 83, 176)));
        barra.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.rgb(112, 83, 176)));
        valor.setText(barra.getProgress() + "/10");
        barra.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar s,
                                                     int progreso, boolean delUsuario) {
                valor.setText(progreso + "/10");
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar s) { }
            @Override public void onStopTrackingTouch(android.widget.SeekBar s) { }
        });
        contenido.addView(barra);

        new AlertDialog.Builder(this)
                .setTitle("Intensidad · " + nombre)
                .setView(contenido)
                .setPositiveButton("Continuar", (dialogo, cual) ->
                        accion.recibir(barra.getProgress()))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void editarEvento(
            ArrayList<Evento> eventos,
            int indice,
            boolean esCigarrillo) {

        Evento evento = eventos.get(indice);
        boolean esMolestia = eventos == molestias;

        String[] opciones = esCigarrillo
                ? new String[]{"Cambiar hora", "Cambiar cantidad", "Eliminar registro"}
                : esMolestia
                ? new String[]{"Cambiar hora", "Cambiar intensidad", "Eliminar registro"}
                : new String[]{"Cambiar hora", "Eliminar registro"};

        new AlertDialog.Builder(this)
                .setTitle("Editar registro")
                .setItems(opciones, (dialogo, opcion) -> {
                    if (opcion == 0) {
                        seleccionarHora(evento.hora, hora -> {
                            evento.hora = hora;
                            actualizarResumenes();
                            guardarRegistroAutomatico(eventos);
                        });
                    } else if (esCigarrillo && opcion == 1) {
                        elegirCantidad(evento.cantidad, cantidad -> {
                            evento.cantidad = cantidad;
                            actualizarResumenes();
                            guardarRegistroAutomatico(eventos);
                        });
                    } else if (esMolestia && opcion == 1) {
                        seleccionarIntensidad(evento.nombre, evento.intensidad, intensidad -> {
                            evento.intensidad = intensidad;
                            actualizarResumenes();
                            guardarRegistroAutomatico(eventos);
                        });
                    } else {
                        eventos.remove(indice);
                        actualizarResumenes();
                        if (eventos == alcohol && eventos.isEmpty()) {
                            switchAlcohol.setChecked(false);
                        } else {
                            guardarRegistroAutomatico(eventos);
                        }
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private interface AlSeleccionarCantidad {
        void recibir(int cantidad);
    }

    private void elegirCantidad(
            int valorInicial,
            AlSeleccionarCantidad accion) {

        NumberPicker selector = new NumberPicker(this);

        selector.setMinValue(1);
        selector.setMaxValue(100);
        selector.setValue(Math.max(1, valorInicial));

        new AlertDialog.Builder(this)
                .setTitle("Cantidad de cigarrillos")
                .setView(selector)
                .setPositiveButton("Continuar", (dialog, which) -> {

                    selector.clearFocus();

                    accion.recibir(selector.getValue());
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void seleccionarCantidadCigarrillos() {

        elegirCantidad(1, cantidad -> {

            agregarEvento(
                    cigarrillos,
                    "Cigarrillos",
                    cantidad
            );
        });
    }

    private void seleccionarHora(String horaAnterior, AlSeleccionarHora accion) {

        Calendar ahora = Calendar.getInstance();
        int hora = ahora.get(Calendar.HOUR_OF_DAY);
        int minuto = ahora.get(Calendar.MINUTE);

        if (horaAnterior != null
                && horaAnterior.matches("([01]\\d|2[0-3]):[0-5]\\d")) {
            String[] partes = horaAnterior.split(":");
            hora = Integer.parseInt(partes[0]);
            minuto = Integer.parseInt(partes[1]);
        }

        View vista = getLayoutInflater()
                .inflate(R.layout.dialog_hora_scroll, null);

        NumberPicker npHora = vista.findViewById(R.id.npHora);
        NumberPicker npMinuto = vista.findViewById(R.id.npMinuto);

        configurarRueda(npHora, 23, hora);
        configurarRueda(npMinuto, 59, minuto);

        AlertDialog dialogo = new AlertDialog.Builder(this)
                .setView(vista)
                .create();

        vista.findViewById(R.id.btnCancelarHora)
                .setOnClickListener(v -> dialogo.dismiss());

        vista.findViewById(R.id.btnAceptarHora)
                .setOnClickListener(v -> {
                    npHora.clearFocus();
                    npMinuto.clearFocus();

                    String seleccion = String.format(Locale.US,
                            "%02d:%02d",
                            npHora.getValue(), npMinuto.getValue());

                    accion.recibir(seleccion);
                    dialogo.dismiss();
                });

        dialogo.show();

        if (dialogo.getWindow() != null) {
            dialogo.getWindow().setBackgroundDrawable(
                    new ColorDrawable(Color.TRANSPARENT));
            dialogo.getWindow().setLayout(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private void configurarRueda(NumberPicker selector, int maximo, int valor) {
        selector.setMinValue(0);
        selector.setMaxValue(maximo);
        selector.setWrapSelectorWheel(true);

        String[] valores = new String[maximo + 1];
        for (int i = 0; i <= maximo; i++) {
            valores[i] = String.format(Locale.US, "%02d", i);
        }

        selector.setDisplayedValues(valores);
        selector.setValue(valor);
        selector.setDescendantFocusability(
                NumberPicker.FOCUS_BLOCK_DESCENDANTS);
    }

    private void seleccionarHorarioTrabajo() {

        if (!switchTrabajoNocturno.isChecked()) {
            return;
        }

        seleccionarHora(inicioTrabajo, inicio ->

                seleccionarHora(finTrabajo, fin -> {

                    if (inicio.equals(fin)) {

                        Toast.makeText(this,
                                "El inicio y el fin deben ser diferentes",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    inicioTrabajo = inicio;
                    finTrabajo = fin;

                    actualizarResumenes();
                    guardarTrabajoAutomatico();
                })
        );
    }

    private void seleccionarFecha() {

        int anio = fechaSeleccionada.get(Calendar.YEAR);
        int mes = fechaSeleccionada.get(Calendar.MONTH);
        int dia = fechaSeleccionada.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog selector = new DatePickerDialog(
                this,
                (view, nuevoAnio, nuevoMes, nuevoDia) -> {

                    fechaSeleccionada.set(
                            nuevoAnio,
                            nuevoMes,
                            nuevoDia
                    );

                    actualizarFecha();
                    cargarActividad();
                },
                anio,
                mes,
                dia
        );

        selector.getDatePicker().setMaxDate(
                System.currentTimeMillis()
        );

        selector.show();
    }

    private void actualizarFecha() {

        SimpleDateFormat formato = new SimpleDateFormat(
                "dd 'de' MMMM 'de' yyyy",
                new Locale("es", "PE")
        );

        txtFechaActividad.setText(
                formato.format(fechaSeleccionada.getTime())
        );
    }

    private String obtenerFechaId() {

        SimpleDateFormat formato = new SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
        );

        return formato.format(fechaSeleccionada.getTime());
    }

    private String mostrarHora(String hora) {

        return hora == null || hora.isEmpty()
                ? "Hora pendiente"
                : hora;
    }

    private String crearResumen(
            ArrayList<Evento> eventos,
            String mensajeVacio,
            boolean esCigarrillo) {

        if (eventos.isEmpty()) {
            return mensajeVacio;
        }

        ArrayList<String> partes = new ArrayList<>();

        for (Evento evento : eventos) {

            if (esCigarrillo) {

                partes.add(
                        evento.cantidad + " cig. · "
                                + mostrarHora(evento.hora)
                );

            } else {

                partes.add(
                        evento.nombre + " · " + mostrarHora(evento.hora)
                                + (eventos == molestias && evento.intensidad != null
                                ? " · " + evento.intensidad + "/10" : "")
                );
            }
        }

        return android.text.TextUtils.join("\n", partes);
    }

    private void actualizarResumenes() {

        txtResumenSuplementos.setText(
                crearResumen(
                        suplementos,
                        "Sin consumos registrados",
                        false
                )
        );

        txtResumenBebidas.setText(
                crearResumen(
                        bebidas,
                        "Sin bebidas registradas",
                        false
                )
        );

        txtResumenCigarrillos.setText(
                crearResumen(
                        cigarrillos,
                        "Sin registros",
                        true
                )
        );

        txtResumenMolestias.setText(
                crearResumen(
                        molestias,
                        "Sin molestias registradas",
                        false
                )
        );

        if (switchAlcohol.isChecked()) {

            txtHoraAlcohol.setText(
                    crearResumen(
                            alcohol,
                            "Hora: sin registrar",
                            false
                    )
            );

        } else {

            txtHoraAlcohol.setText("Sin consumo");
        }

        if (switchTrabajoNocturno.isChecked()) {

            if (inicioTrabajo != null && finTrabajo != null) {

                String texto = inicioTrabajo + " - " + finTrabajo;

                if (finTrabajo.compareTo(inicioTrabajo) < 0) {
                    texto += " (día siguiente)";
                }

                txtHorarioTrabajo.setText(texto);

            } else {

                txtHorarioTrabajo.setText("Sin horario registrado");
            }

        } else {

            txtHorarioTrabajo.setText("No trabajó de noche");
        }

        btnHoraAlcohol.setEnabled(switchAlcohol.isChecked());
        btnHorarioTrabajo.setEnabled(
                switchTrabajoNocturno.isChecked()
        );
    }

    private void agregarOpcion(
            ArrayList<String> opciones,
            String nombre) {

        if (nombre == null) return;

        nombre = nombre.trim();

        if (nombre.isEmpty()
                || nombre.equalsIgnoreCase("Ninguno")
                || nombre.equalsIgnoreCase("Otros")) {
            return;
        }

        for (String existente : opciones) {

            if (existente.equalsIgnoreCase(nombre)) {
                return;
            }
        }

        opciones.add(nombre);
    }

    private void cargarOpcionesUsuario() {

        db.collection("usuarios")
                .document(usuario.getUid())
                .get()
                .addOnSuccessListener(documento -> {

                    cargarNombres(
                            documento.get("vitaminasSuplementos"),
                            opcionesSuplementos
                    );

                    cargarNombres(
                            documento.get("molestiasActuales"),
                            opcionesMolestias
                    );
                });
    }

    private void cargarNombres(
            Object datos,
            ArrayList<String> destino) {

        if (!(datos instanceof List)) {
            return;
        }

        for (Object valor : (List<?>) datos) {

            if (valor instanceof String) {
                agregarOpcion(destino, (String) valor);
            }
        }
    }

    private ArrayList<Map<String, Object>> convertirEventos(
            ArrayList<Evento> origen) {

        ArrayList<Map<String, Object>> resultado =
                new ArrayList<>();

        for (Evento evento : origen) {

            resultado.add(evento.convertirMapa());
        }

        return resultado;
    }

    private void leerEventos(
            Object datos,
            ArrayList<Evento> destino) {

        if (!(datos instanceof List)) {
            return;
        }

        for (Object valor : (List<?>) datos) {

            if (!(valor instanceof Map)) {
                continue;
            }

            Map<?, ?> mapa = (Map<?, ?>) valor;

            Object nombre = mapa.get("nombre");
            Object hora = mapa.get("hora");
            Object cantidad = mapa.get("cantidad");
            Object intensidad = mapa.get("intensidad");

            if (!(nombre instanceof String)) {
                continue;
            }

            int numero = cantidad instanceof Number
                    ? ((Number) cantidad).intValue()
                    : 1;

            Integer nivel = intensidad instanceof Number
                    ? Math.max(0, Math.min(10, ((Number) intensidad).intValue()))
                    : null;
            destino.add(new Evento(
                    (String) nombre,
                    hora instanceof String ? (String) hora : "",
                    numero,
                    nivel
            ));
        }
    }

    private void cargarDatosAnteriores(
            DocumentSnapshot documento) {

        if (!documento.contains("Consumo de Suplementos")) {

            Object datos = documento.get("suplementos");

            if (datos instanceof List) {

                for (Object valor : (List<?>) datos) {

                    if (valor instanceof String
                            && !valor.equals("Ninguno")) {

                        suplementos.add(
                                new Evento((String) valor, "", 1)
                        );
                    }
                }
            }
        }

        if (!documento.contains("Molestias actuales")) {

            Object datos = documento.get("molestias");

            if (datos instanceof List) {

                for (Object valor : (List<?>) datos) {

                    if (valor instanceof String
                            && !valor.equals("Ninguno")) {

                        molestias.add(
                                new Evento((String) valor, "", 1)
                        );
                    }
                }
            }
        }

        if (!documento.contains("Numero de Cigarrillos")) {

            Long cantidad = documento.getLong("cigarrillos");

            if (cantidad != null && cantidad > 0) {

                cigarrillos.add(
                        new Evento(
                                "Cigarrillos",
                                "",
                                cantidad.intValue()
                        )
                );
            }
        }
    }

    private void limpiarFormulario() {

        actualizandoDatos = true;

        suplementos.clear();
        bebidas.clear();
        alcohol.clear();
        cigarrillos.clear();
        molestias.clear();

        inicioTrabajo = null;
        finTrabajo = null;

        switchAlcohol.setChecked(false);
        switchTrabajoNocturno.setChecked(false);

        actualizandoDatos = false;

        actualizarResumenes();
    }

    private void habilitarFormulario(boolean habilitado) {

        btnSuplementos.setEnabled(habilitado);
        btnBebidas.setEnabled(habilitado);
        btnCigarrillos.setEnabled(habilitado);
        btnMolestias.setEnabled(habilitado);
        btnGuardarActividad.setEnabled(habilitado);

        switchAlcohol.setEnabled(habilitado);
        switchTrabajoNocturno.setEnabled(habilitado);
        txtFechaActividad.setEnabled(habilitado);

        btnHoraAlcohol.setEnabled(
                habilitado && switchAlcohol.isChecked()
        );

        btnHorarioTrabajo.setEnabled(
                habilitado && switchTrabajoNocturno.isChecked()
        );
    }

    private void cargarActividad() {

        if (usuario == null) {
            return;
        }

        String fechaId = obtenerFechaId();

        habilitarFormulario(false);
        limpiarFormulario();

        db.collection("usuarios")
                .document(usuario.getUid())
                .collection("actividades")
                .document(fechaId)
                .get()
                .addOnSuccessListener(documento -> {

                    if (!fechaId.equals(obtenerFechaId())) {
                        return;
                    }

                    if (documento.exists()) {

                        leerEventos(
                                documento.get("Consumo de Suplementos"),
                                suplementos
                        );

                        leerEventos(
                                documento.get("Consumo de Bebidas"),
                                bebidas
                        );

                        leerEventos(
                                documento.get("Consumo de Alcohol"),
                                alcohol
                        );

                        leerEventos(
                                documento.get("Consumo de Cigarrillos"),
                                cigarrillos
                        );

                        leerEventos(
                                documento.get("Molestias actuales"),
                                molestias
                        );

                        cargarDatosAnteriores(documento);

                        actualizandoDatos = true;

                        switchAlcohol.setChecked(
                                Boolean.TRUE.equals(
                                        documento.getBoolean("alcohol")
                                )
                        );

                        switchTrabajoNocturno.setChecked(
                                Boolean.TRUE.equals(
                                        documento.getBoolean("trabajoNocturno")
                                )
                        );

                        inicioTrabajo =
                                documento.getString("horaInicioTrabajo");

                        finTrabajo =
                                documento.getString("horaFinTrabajo");

                        actualizandoDatos = false;

                        for (Evento evento : suplementos) {
                            agregarOpcion(
                                    opcionesSuplementos,
                                    evento.nombre
                            );
                        }

                        for (Evento evento : bebidas) {
                            agregarOpcion(
                                    opcionesBebidas,
                                    evento.nombre
                            );
                        }

                        for (Evento evento : molestias) {
                            agregarOpcion(
                                    opcionesMolestias,
                                    evento.nombre
                            );
                        }
                    }

                    actualizarResumenes();
                    habilitarFormulario(true);
                    abrirRegistroRapido();
                })
                .addOnFailureListener(e -> {

                    habilitarFormulario(true);

                    Toast.makeText(this,
                            "Error al cargar: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private ArrayList<String> obtenerNombres(
            ArrayList<Evento> eventos) {

        ArrayList<String> nombres = new ArrayList<>();

        for (Evento evento : eventos) {

            if (!nombres.contains(evento.nombre)) {
                nombres.add(evento.nombre);
            }
        }

        return nombres;
    }

    private int totalCigarrillos() {

        int total = 0;

        for (Evento evento : cigarrillos) {
            total += evento.cantidad;
        }

        return total;
    }

    private void guardarRegistroAutomatico(ArrayList<Evento> eventos) {

        if (usuario == null || actualizandoDatos) return;

        String fechaId = obtenerFechaId();
        Map<String, Object> datos = new HashMap<>();
        datos.put("fecha", fechaId);
        datos.put("fechaActualizacion", FieldValue.serverTimestamp());

        if (eventos == suplementos) {
            datos.put("Consumo de Suplementos", convertirEventos(suplementos));
            datos.put("suplementos", obtenerNombres(suplementos));
        } else if (eventos == bebidas) {
            datos.put("Consumo de Bebidas", convertirEventos(bebidas));
        } else if (eventos == alcohol) {
            datos.put("Consumo de Alcohol", convertirEventos(alcohol));
            datos.put("alcohol", switchAlcohol.isChecked() && !alcohol.isEmpty());
        } else if (eventos == cigarrillos) {
            datos.put("Consumo de Cigarrillos", convertirEventos(cigarrillos));
            datos.put("cigarrillos", totalCigarrillos());
        } else if (eventos == molestias) {
            datos.put("Molestias actuales", convertirEventos(molestias));
            datos.put("molestias", obtenerNombres(molestias));
        } else {
            return;
        }

        guardarCambiosAutomaticos(fechaId, datos);
    }

    private void guardarTrabajoAutomatico() {
        if (usuario == null || actualizandoDatos) return;

        String fechaId = obtenerFechaId();
        Map<String, Object> datos = new HashMap<>();
        datos.put("fecha", fechaId);
        datos.put("trabajoNocturno", switchTrabajoNocturno.isChecked());
        datos.put("horaInicioTrabajo", inicioTrabajo);
        datos.put("horaFinTrabajo", finTrabajo);
        datos.put("fechaActualizacion", FieldValue.serverTimestamp());
        guardarCambiosAutomaticos(fechaId, datos);
    }

    private void guardarCambiosAutomaticos(String fechaId, Map<String, Object> datos) {
        // Vista v4 completa, siempre generada desde las mismas listas que usa la pantalla.
        // Las listas heredadas siguen guardándose para mantener compatibilidad con Patrones.
        Map<String, Object> vista = new HashMap<>();
        vista.put("Consumo de Suplementos", convertirEventos(suplementos));
        vista.put("Consumo de Bebidas", convertirEventos(bebidas));
        vista.put("Consumo de Alcohol", convertirEventos(alcohol));
        vista.put("Consumo de Cigarrillos", convertirEventos(cigarrillos));
        vista.put("Molestias actuales", convertirEventos(molestias));
        vista.put("trabajoNocturno", switchTrabajoNocturno.isChecked());
        vista.put("horaInicioTrabajo", inicioTrabajo);
        vista.put("horaFinTrabajo", finTrabajo);
        datos.put("versionEsquema", 4);
        datos.put("fechaLocal", fechaId);
        datos.put("eventosNormalizados", RepositorioActividades.normalizar(fechaId, vista));
        db.collection("usuarios")
                .document(usuario.getUid())
                .collection("actividades")
                .document(fechaId)
                .set(datos, SetOptions.merge())
                .addOnSuccessListener(ignorado ->
                        Toast.makeText(this, "Registro guardado", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(error ->
                        Toast.makeText(this, "No se pudo guardar: " + error.getMessage(),
                                Toast.LENGTH_LONG).show());
    }

    private void abrirRegistroRapido() {

        String categoria = getIntent().getStringExtra("registroRapido");
        if (categoria == null) return;

        getIntent().removeExtra("registroRapido");

        switch (categoria) {
            case "bebidas":
                btnBebidas.performClick();
                break;
            case "alcohol":
                switchAlcohol.setChecked(true);
                btnHoraAlcohol.performClick();
                break;
            case "cigarrillos":
                btnCigarrillos.performClick();
                break;
            case "suplementos":
                btnSuplementos.performClick();
                break;
            case "molestias":
                btnMolestias.performClick();
                break;
        }
    }

    private void configurarNavegacion() {
        configurarDestino(R.id.navAlarma, alarma.class);
        configurarDestino(R.id.navRegistros, Registros.class);
        configurarDestino(R.id.navPatrones, Patrones.class);
        configurarDestino(R.id.navConsejos, consejos.class);
        configurarDestino(R.id.navPerfil, perfil.class);
    }

    private void configurarDestino(int id, Class<?> destino) {
        ImageButton boton = findViewById(id);
        android.view.View.OnClickListener accion = vista -> {
            startActivity(new Intent(Actividad.this, destino));
            finish();
        };
        boton.setOnClickListener(accion);
        ((android.view.View) boton.getParent()).setOnClickListener(accion);
    }
}
