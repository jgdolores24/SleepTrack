package com.example.sleeptrack;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.view.Gravity;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

public class alarma extends AppCompatActivity {

    private static final int PERMISO_MICROFONO = 102;
    private static final int PERMISO_NOTIFICACIONES = 101;
    private static final long MAXIMO = 12L * 60 * 60 * 1000;

    private String nombreAlarma = "Mi descanso";
    private TextView txtHoraAlarma;
    private SwitchCompat switchMonitoreo, switchSonido;
    private int mascaraDias = 0;
    private FirebaseUser usuario;
    private FirebaseFirestore db;

    private String horaAlarma = "07:00";
    private String horaInicioMonitoreo = "22:00";
    private TextView txtHoraInicioMonitoreo, txtHoraFinMonitoreo;
    private TextView btnTerminado, btnComenzarAhora;
    private View contenedorHorarioMonitoreo, contenedorMonitoreoNocturno;
    private TextView txtModoAlarma;
    private TextView txtDescripcionAlarma, txtDuracionElegida, btnDuracion;
    private boolean modoPorDuracion = false;
    private int duracionMinutos = 30;
    private boolean accionPendienteDescanso = false;
    private boolean esperandoPermisoExacto = false;
    private boolean microfonoSolicitado = false;
    private boolean notificacionesSolicitadas = false;

    private final int[] diasCalendario = {
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY,
            Calendar.SUNDAY
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_alarma);


        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main), (v, insets) -> {
                    Insets barras = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars());

                    v.setPadding(
                            barras.left,
                            barras.top,
                            barras.right,
                            Math.max(0, barras.bottom - dp(12))
                    );

                    return insets;
                });

        db = FirebaseFirestore.getInstance();
        usuario = FirebaseAuth.getInstance().getCurrentUser();
        txtHoraAlarma = findViewById(R.id.txtHoraAlarma);
        switchMonitoreo = findViewById(R.id.switchMonitoreo);
        switchSonido = findViewById(R.id.switchSonido);
        txtHoraInicioMonitoreo = findViewById(R.id.txtHoraInicioMonitoreo);
        txtHoraFinMonitoreo = findViewById(R.id.txtHoraFinMonitoreo);
        txtModoAlarma = findViewById(R.id.txtModoAlarma);
        txtDescripcionAlarma = findViewById(R.id.txtDescripcionAlarma);
        txtDuracionElegida = findViewById(R.id.txtDuracionElegida);
        btnDuracion = findViewById(R.id.btnDuracion);
        btnDuracion.setOnClickListener(v -> seleccionarDuracion());
        btnTerminado = findViewById(R.id.btnTerminado);
        btnComenzarAhora = findViewById(R.id.btnComenzarAhora);
        contenedorHorarioMonitoreo = findViewById(R.id.contenedorHorarioMonitoreo);
        contenedorMonitoreoNocturno = findViewById(R.id.contenedorMonitoreoNocturno);
        findViewById(R.id.btnElegirInicioMonitoreo).setOnClickListener(v -> seleccionarHora(true));
        txtHoraFinMonitoreo.setOnClickListener(v -> seleccionarHora(false));
        configurarRutina();
        MigracionAlarmaV5.ajustarPreferencias(this);
        aplicarDias(0);
        configurarAvisos();
        configurarNavegacion();
        cargarAlarma();

        findViewById(R.id.btnSeleccionarHora)
                .setOnClickListener(v -> seleccionarHora(false));
        txtHoraAlarma.setOnClickListener(v -> seleccionarHora(false));
        btnTerminado.setOnClickListener(v -> comenzarAccion(false));
        btnComenzarAhora.setOnClickListener(v -> iniciarLibreConConfirmacion());
    }

    private int dp(int valor) {
        return Math.round(valor * getResources().getDisplayMetrics().density);
    }

    private void configurarAvisos() {
        View avisoMonitoreo = findViewById(R.id.avisoMonitoreo);
        View avisoSonido = findViewById(R.id.avisoSonido);
        switchMonitoreo.setOnCheckedChangeListener((b, activo) ->
                avisoMonitoreo.setVisibility(activo ? View.VISIBLE : View.GONE));
        switchSonido.setOnCheckedChangeListener((b, activo) ->
                avisoSonido.setVisibility(activo ? View.VISIBLE : View.GONE));
        avisoMonitoreo.setVisibility(switchMonitoreo.isChecked()
                ? View.VISIBLE : View.GONE);
        avisoSonido.setVisibility(switchSonido.isChecked()
                ? View.VISIBLE : View.GONE);
    }

    private void seleccionarHora(boolean inicioMonitoreo) {
        View vista = getLayoutInflater().inflate(R.layout.dialog_hora_scroll, null);
        NumberPicker npHora = vista.findViewById(R.id.npHora);
        NumberPicker npMinuto = vista.findViewById(R.id.npMinuto);
        configurarSelector(npHora, 23);
        configurarSelector(npMinuto, 59);

        String elegida = inicioMonitoreo ? horaInicioMonitoreo : horaAlarma;
        String[] partes = elegida.split(":");
        npHora.setValue(Integer.parseInt(partes[0]));
        npMinuto.setValue(Integer.parseInt(partes[1]));

        AlertDialog dialogo = new AlertDialog.Builder(this).setView(vista).create();
        vista.findViewById(R.id.btnCancelarHora).setOnClickListener(v -> dialogo.dismiss());
        vista.findViewById(R.id.btnAceptarHora).setOnClickListener(v -> {
            String nuevaHora = String.format(Locale.US, "%02d:%02d",
                    npHora.getValue(), npMinuto.getValue());
            if (inicioMonitoreo) {
                horaInicioMonitoreo = nuevaHora;
                txtHoraInicioMonitoreo.setText(nuevaHora);
            } else {
                horaAlarma = nuevaHora;
                txtHoraAlarma.setText(nuevaHora);
                txtHoraFinMonitoreo.setText(nuevaHora);
                modoPorDuracion = false;
                actualizarTarjetaDuracion();
            }
            actualizarRutina();
            dialogo.dismiss();
        });
        dialogo.show();
        if (dialogo.getWindow() != null) {
            dialogo.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialogo.getWindow().setLayout(WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private void configurarSelector(NumberPicker selector, int maximo) {
        selector.setMinValue(0);
        selector.setMaxValue(maximo);
        selector.setWrapSelectorWheel(true);
        String[] valores = new String[maximo + 1];
        for (int i = 0; i <= maximo; i++) {
            valores[i] = String.format(Locale.US, "%02d", i);
        }
        selector.setDisplayedValues(valores);
        selector.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
    }

    private void seleccionarDuracion() {
        LinearLayout contenido = new LinearLayout(this);
        contenido.setPadding(dp(22), dp(14), dp(22), dp(8));
        contenido.setGravity(Gravity.CENTER);
        contenido.setOrientation(LinearLayout.HORIZONTAL);
        NumberPicker horas = new NumberPicker(this);
        horas.setMinValue(0);
        horas.setMaxValue(12);
        horas.setValue(Math.min(12, duracionMinutos / 60));
        NumberPicker minutos = new NumberPicker(this);
        minutos.setMinValue(0);
        minutos.setMaxValue(59);
        minutos.setValue(duracionMinutos % 60);
        contenido.addView(horas, new LinearLayout.LayoutParams(0, dp(150), 1));
        contenido.addView(minutos, new LinearLayout.LayoutParams(0, dp(150), 1));
        new AlertDialog.Builder(this)
                .setTitle("¿Cuánto quieres dormir?")
                .setMessage("Horas          Minutos")
                .setView(contenido)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Aplicar", (d, w) -> {
                    int total = horas.getValue() * 60 + minutos.getValue();
                    if (total < 1 || total > 720) {
                        mensaje("Elige entre 1 minuto y 12 horas");
                        return;
                    }
                    duracionMinutos = total;
                    modoPorDuracion = true;
                    if (mascaraDias != 0) {
                        aplicarDias(0);
                        mensaje("La duración se configura como una sola alarma");
                    }
                    actualizarTarjetaDuracion();
                }).show();
    }

    private void actualizarTarjetaDuracion() {
        if (modoPorDuracion && mascaraDias == 0) {
            Calendar despertar = Calendar.getInstance();
            despertar.add(Calendar.MINUTE, duracionMinutos);
            txtHoraAlarma.setText(new SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(despertar.getTime()));
            txtDescripcionAlarma.setText("Alarma después de " + textoDuracion());
            txtDuracionElegida.setText("Duración elegida: " + textoDuracion());
            txtDuracionElegida.setVisibility(View.VISIBLE);
            btnDuracion.setText("✓ Duración");
        } else {
            txtHoraAlarma.setText(horaAlarma);
            txtDescripcionAlarma.setText("Tu próximo despertar");
            txtDuracionElegida.setVisibility(View.GONE);
            btnDuracion.setText("Duración");
        }
    }

    private String textoDuracion() {
        int h = duracionMinutos / 60;
        int m = duracionMinutos % 60;
        if (h == 0) return m + " min";
        return m == 0 ? h + " h" : h + " h " + m + " min";
    }

    private void iniciarLibreConConfirmacion() {
        SharedPreferences pref = getSharedPreferences(AlarmaProgramador.PREF, MODE_PRIVATE);
        if (pref.getBoolean("activa", false)) {
            new AlertDialog.Builder(this)
                    .setTitle("Descanso sin alarma")
                    .setMessage("Tienes una alarma programada. Para descansar sin alarma, primero se cancelará esa alarma. ¿Continuar?")
                    .setNegativeButton("Conservar alarma", null)
                    .setPositiveButton("Descansar sin alarma", (d, w) -> comenzarAccion(true))
                    .show();
        } else {
            comenzarAccion(true);
        }
    }

    private static final String[] NOMBRES_DIAS = {
            "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"
    };

    private static final String[] CORTOS_DIAS = {
            "lun", "mar", "mié", "jue", "vie", "sáb", "dom"
    };

    private int mascaraLaboral() {
        int valor = 0;
        for (int i = 0; i < 5; i++) valor |= 1 << diasCalendario[i];
        return valor;
    }

    private void configurarRutina() {
        findViewById(R.id.btnEditarRutina).setOnClickListener(v -> editarRutina());
    }

    private int obtenerDias() {
        return mascaraDias;
    }

    private void aplicarDias(int mascara) {
        mascaraDias = mascara;
        if (mascara != 0) modoPorDuracion = false;
        actualizarRutina();
        if (txtDescripcionAlarma != null) actualizarTarjetaDuracion();
    }

    private void actualizarRutina() {
        String resumen;
        if (mascaraDias == 0) {
            resumen = "Una vez";
        } else if (mascaraDias == mascaraLaboral()) {
            resumen = "Lun a Vie";
        } else {
            StringBuilder dias = new StringBuilder();
            for (int i = 0; i < 7; i++) {
                if ((mascaraDias & (1 << diasCalendario[i])) != 0) {
                    if (dias.length() > 0) dias.append(", ");
                    dias.append(CORTOS_DIAS[i]);
                }
            }
            resumen = dias.length() == 0 ? "Una vez" : dias.toString();
        }
        ((TextView) findViewById(R.id.txtRutinaResumen)).setText(resumen);
        boolean repetida = mascaraDias != 0;
        contenedorHorarioMonitoreo.setVisibility(repetida ? View.VISIBLE : View.GONE);
        contenedorMonitoreoNocturno.setVisibility(View.VISIBLE);
        btnComenzarAhora.setVisibility(View.VISIBLE);
        btnTerminado.setText(repetida ? "Guardar rutina" : "Guardar alarma");
        btnComenzarAhora.setText("Descanso sin alarma");
        txtModoAlarma.setText(repetida
                ? HorarioRepeticion.descripcion(mascaraDias, horaInicioMonitoreo, horaAlarma)
                : "Una alarma · con monitoreo opcional desde ahora");
        txtHoraInicioMonitoreo.setText(horaInicioMonitoreo);
        txtHoraFinMonitoreo.setText(horaAlarma);
    }

    private void editarRutina() {
        String[] opciones = {"Una vez", "Lunes a viernes (despertar)", "Elegir días de despertar"};
        new AlertDialog.Builder(this)
                .setTitle("¿Qué días quieres despertar?")
                .setItems(opciones, (dialogo, seleccion) -> {
                    if (seleccion == 0) aplicarDias(0);
                    else if (seleccion == 1) aplicarDias(mascaraLaboral());
                    else elegirDiasPersonalizados();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void elegirDiasPersonalizados() {
        boolean[] elegidos = new boolean[7];
        for (int i = 0; i < 7; i++) {
            elegidos[i] = (mascaraDias & (1 << diasCalendario[i])) != 0;
        }
        new AlertDialog.Builder(this)
                .setTitle("Días de despertar (elige al menos uno)")
                .setMultiChoiceItems(NOMBRES_DIAS, elegidos,
                        (dialogo, indice, marcado) -> elegidos[indice] = marcado)
                .setPositiveButton("Aplicar", (dialogo, which) -> {
                    int seleccion = 0;
                    for (int i = 0; i < 7; i++) {
                        if (elegidos[i]) seleccion |= 1 << diasCalendario[i];
                    }
                    if (seleccion == 0) {
                        mensaje("Selecciona al menos un día o utiliza la opción Una vez");
                        return;
                    }
                    aplicarDias(seleccion);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void comenzarAccion(boolean comenzarDescanso) {
        accionPendienteDescanso = comenzarDescanso;
        microfonoSolicitado = false;
        notificacionesSolicitadas = false;
        continuarAccion();
    }

    private void continuarAccion() {
        if (!accionPendienteDescanso && !AlarmaProgramador.tienePermiso(this)) {
            solicitarPermisoAlarma();
            return;
        }
        if (switchSonido.isChecked()
                && !microfonoSolicitado
                && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            microfonoSolicitado = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISO_MICROFONO);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && !notificacionesSolicitadas
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificacionesSolicitadas = true;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    PERMISO_NOTIFICACIONES);
            return;
        }
        guardarYProgramar();
    }

    private void guardarYProgramar() {
        if (accionPendienteDescanso) {
            comenzarDescansoLibre();
            return;
        }
        int repeticion = obtenerDias();
        boolean repetida = repeticion != 0;
        boolean monitoreo = switchMonitoreo.isChecked();
        boolean sonido = switchSonido.isChecked();
        if (repetida && horaInicioMonitoreo.equals(horaAlarma)) {
            mensaje("La hora de inicio debe ser diferente de la hora de despertar");
            return;
        }
        if (getSharedPreferences(AlarmaProgramador.PREF, MODE_PRIVATE)
                .getBoolean("sesionDescansoActiva", false)) {
            mensaje("Finaliza primero tu descanso actual");
            return;
        }
        AlarmaProgramador.cancelar(this);
        String horaGuardar = horaAlarma;
        if (modoPorDuracion && !repetida) {
            Calendar fecha = Calendar.getInstance();
            fecha.add(Calendar.MINUTE, duracionMinutos);
            horaGuardar = new SimpleDateFormat("HH:mm", Locale.US).format(fecha.getTime());
        }
        SharedPreferences pref = getSharedPreferences(AlarmaProgramador.PREF, MODE_PRIVATE);
        pref.edit().putBoolean("configurada", true)
                .putBoolean("activa", true)
                .putBoolean("modoDuracion", modoPorDuracion && !repetida)
                .putInt("duracionMinutos", duracionMinutos)
                .putString("modoDescanso", "alarma")
                .putString("nombre", nombreAlarma)
                .putString("hora", horaGuardar)
                .putString("horaInicioMonitoreo", horaInicioMonitoreo)
                .putInt("dias", repeticion)
                .putBoolean("diasCriterioDespertarV5", true)
                .putBoolean("monitoreoTelefono", monitoreo)
                .putBoolean("grabacionSonido", sonido)
                .putString("alarmaUid", usuario != null ? usuario.getUid() : "")
                .putBoolean("errorProgramacionAlarma", false).apply();

        if (!AlarmaProgramador.programar(this)) {
            pref.edit().putBoolean("activa", false)
                    .putBoolean("errorProgramacionAlarma", true).apply();
            RepositorioEstadoAlarma.sincronizar(this, "ERROR_PROGRAMACION");
            mensaje("No se pudo programar la alarma. Comprueba el permiso de alarmas exactas");
            return;
        }
        pref.edit().putBoolean("errorProgramacionAlarma", false).apply();
        RepositorioEstadoAlarma.sincronizar(this, "ALARMA_PROGRAMADA");
        if (usuario != null) {
            com.example.sleeptrack.modelo.ConfiguracionAlarmaBean bean =
                    new com.example.sleeptrack.modelo.ConfiguracionAlarmaBean();
            bean.setNombre(nombreAlarma);
            bean.setHoraDespertar(horaGuardar);
            bean.setHoraInicioMonitoreo(horaInicioMonitoreo);
            bean.setDiasMascara(repeticion);
            bean.setDuracionMinutos(duracionMinutos);
            bean.setModoDespertar(modoPorDuracion && !repetida ? "DURACION" : "HORA");
            bean.setTipoProgramacion(AlarmaDatos.tipoProgramacion(repeticion));
            bean.setMonitoreoTelefono(monitoreo);
            bean.setDeteccionSonidos(sonido);
            db.collection("usuarios").document(usuario.getUid())
                    .collection("configuracion").document("alarma")
                    .set(AlarmaDatos.convertir(bean))
                    .addOnFailureListener(e -> mensaje("No se pudo sincronizar la configuración"));
        }
        if (repetida) {
            mensaje("Rutina programada. El descanso comenzará a la hora de inicio elegida");
            return;
        }
        long restante = pref.getLong("proximaAlarma", 0L) - System.currentTimeMillis();
        if (usuario != null && restante > 0 && restante <= MAXIMO) {
            SesionDescansoHelper.iniciar(this, "alarma");
        }
        startActivity(new Intent(this, DescansoActivity.class));
    }

    private void comenzarDescansoLibre() {
        if (usuario == null) {
            mensaje("Inicia sesión para registrar tu descanso");
            return;
        }
        SharedPreferences pref = getSharedPreferences(AlarmaProgramador.PREF, MODE_PRIVATE);
        if (pref.getBoolean("sesionDescansoActiva", false)) {
            startActivity(new Intent(this, DescansoActivity.class));
            return;
        }
        AlarmaProgramador.cancelar(this);
        pref.edit().putString("modoDescanso", "libre")
                .putString("alarmaUid", usuario.getUid())
                .putBoolean("monitoreoTelefono", switchMonitoreo.isChecked())
                .putBoolean("grabacionSonido", switchSonido.isChecked())
                .putLong("proximaAlarma", 0L).apply();
        if (!SesionDescansoHelper.iniciar(this, "libre")) {
            mensaje("No se pudo comenzar tu descanso");
            return;
        }
        startActivity(new Intent(this, DescansoActivity.class));
    }

    private void solicitarPermisoAlarma() {
        new AlertDialog.Builder(this)
                .setTitle("Permiso para la alarma")
                .setMessage("SleepTrack necesita autorización para programar la alarma a la hora exacta.")
                .setPositiveButton("Permitir", (d, w) -> {
                    if (Build.VERSION.SDK_INT >= 31) {
                        esperandoPermisoExacto = true;
                        startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:" + getPackageName())));
                    }
                })
                .setNegativeButton("Cancelar", null).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        MigracionFirebaseV4.migrarUnaVez(this);
        MigracionAlarmaV5.ejecutarSiHaceFalta();
        RepositorioEstadoAlarma.sincronizar(this, "APERTURA_ALARMA");
        if (esperandoPermisoExacto) {
            esperandoPermisoExacto = false;
            if (AlarmaProgramador.tienePermiso(this)) continuarAccion();
            else mensaje("Falta permitir alarmas exactas");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISO_MICROFONO) {
            if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                switchSonido.setChecked(false);
                mensaje("Sin permiso de micrófono, se iniciará sin grabaciones");
            }
            continuarAccion();
        } else if (requestCode == PERMISO_NOTIFICACIONES) {
            continuarAccion();
        }
    }

    private void cargarAlarma() {
        SharedPreferences pref = getSharedPreferences(AlarmaProgramador.PREF, MODE_PRIVATE);
        if (pref.getBoolean("configurada", false)
                && usuario != null
                && usuario.getUid().equals(pref.getString("alarmaUid", ""))) {
            modoPorDuracion = pref.getBoolean("modoDuracion", false);
            duracionMinutos = pref.getInt("duracionMinutos", 30);
            aplicarConfiguracion(pref.getString("nombre", "Mi alarma"),
                    pref.getString("hora", "07:00"),
                    pref.getString("horaInicioMonitoreo", "22:00"), pref.getInt("dias", 0),
                    pref.getBoolean("monitoreoTelefono", false),
                    pref.getBoolean("grabacionSonido", false));
            actualizarTarjetaDuracion();
            return;
        }
        if (usuario == null) return;
        db.collection("usuarios").document(usuario.getUid())
                .collection("configuracion").document("alarma")
                .get().addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        cargarHoraHabitual();
                        return;
                    }
                    com.example.sleeptrack.modelo.ConfiguracionAlarmaBean bean =
                            AlarmaDatos.leer(doc.getData());
                    modoPorDuracion = "DURACION".equals(bean.getModoDespertar());
                    duracionMinutos = bean.getDuracionMinutos();
                    aplicarConfiguracion(bean.getNombre(), bean.getHoraDespertar(),
                            bean.getHoraInicioMonitoreo(), bean.getDiasMascara(),
                            bean.isMonitoreoTelefono(), bean.isDeteccionSonidos());
                    actualizarTarjetaDuracion();
                }).addOnFailureListener(e -> cargarHoraHabitual());
    }

    private void cargarHoraHabitual() {
        if (usuario == null) return;
        db.collection("usuarios").document(usuario.getUid()).get()
                .addOnSuccessListener(doc -> {
                    String hora = doc.getString("horaDespertar");
                    if (hora != null && hora.matches("([01]\\d|2[0-3]):[0-5]\\d")) {
                        horaAlarma = hora;
                        txtHoraAlarma.setText(hora);
                        txtHoraFinMonitoreo.setText(hora);
                    }
                });
    }

    private void aplicarConfiguracion(String nombre, String hora, String horaInicio,
                                     int repeticion, boolean monitoreo, boolean sonido) {
        if (nombre != null && !nombre.trim().isEmpty()) nombreAlarma = nombre;
        if (hora != null && hora.matches("([01]\\d|2[0-3]):[0-5]\\d")) {
            horaAlarma = hora;
            txtHoraAlarma.setText(hora);
        }
        if (horaInicio != null && horaInicio.matches("([01]\\d|2[0-3]):[0-5]\\d")) {
            horaInicioMonitoreo = horaInicio;
        }
        aplicarDias(repeticion);
        switchMonitoreo.setChecked(monitoreo);
        switchSonido.setChecked(sonido);
        actualizarTarjetaDuracion();
    }

    private void configurarNavegacion() {
        NavegacionPrincipal.configurar(this, R.id.navAlarma);
    }


    private void mensaje(String texto) {
        Toast.makeText(this, texto, Toast.LENGTH_SHORT).show();
    }
}
