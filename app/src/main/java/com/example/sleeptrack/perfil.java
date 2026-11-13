package com.example.sleeptrack;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.exceptions.ClearCredentialException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class perfil extends AppCompatActivity {
    private FirebaseAuth auth;
    private FirebaseUser usuario;
    private DocumentReference referencia;
    private DocumentSnapshot datos;
    private ListenerRegistration oyente;
    private ImageView imagen;

    private final ActivityResultLauncher<String> galeria = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) guardarFoto(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(b.left, b.top, b.right, Math.max(0, b.bottom - dp(12)));
            return insets;
        });
        auth = FirebaseAuth.getInstance();
        usuario = auth.getCurrentUser();
        if (usuario == null) {
            volverLogin();
            return;
        }
        referencia = FirebaseFirestore.getInstance().collection("usuarios").document(usuario.getUid());
        imagen = findViewById(R.id.imgFotoPerfil);
        imagen.setClipToOutline(true);
        mostrarFoto();
        imagen.setOnClickListener(v -> galeria.launch("image/*"));
        findViewById(R.id.btnCambiarFoto).setOnClickListener(v -> galeria.launch("image/*"));
        findViewById(R.id.btnEditarPerfil).setOnClickListener(v -> editar());
        findViewById(R.id.btnCerrarSesion).setOnClickListener(v -> confirmarSalida());
        NavegacionPrincipal.configurar(this, R.id.navPerfil);
        mostrar(null);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (referencia == null) return;
        LimpiezaPerfilDuplicado.ejecutarSiEsSeguro();
        oyente = referencia.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                Toast.makeText(this, "No se pudo actualizar el perfil", Toast.LENGTH_SHORT).show();
                return;
            }
            if (snapshot != null) {
                datos = snapshot;
                mostrar(snapshot);
            }
        });
    }

    @Override
    protected void onStop() {
        if (oyente != null) oyente.remove();
        oyente = null;
        super.onStop();
    }

    private Object leer(DocumentSnapshot d, String key) {
        return d == null || !d.exists() ? null : d.get(key);
    }
    private String dato(DocumentSnapshot d, String key, String defecto) {
        Object v = leer(d, key);
        return v == null || v.toString().trim().isEmpty() ? defecto : v.toString();
    }
    private String numero(Object value, String unidad) {
        if (value == null) return "Sin registrar";
        if (value instanceof Number) {
            double n = ((Number) value).doubleValue();
            if (n == Math.floor(n)) return String.format(Locale.US, "%.0f%s", n, unidad);
        }
        return value + unidad;
    }
    private String lista(Object value) {
        if (!(value instanceof List) || ((List<?>) value).isEmpty()) return "Sin registrar";
        StringBuilder texto = new StringBuilder();
        for (Object o : (List<?>) value) {
            if (o == null || o.toString().trim().isEmpty()) continue;
            if (texto.length() > 0) texto.append(", ");
            texto.append(o);
        }
        return texto.length() == 0 ? "Sin registrar" : texto.toString();
    }
    private void setTexto(int id, String value) {
        ((TextView) findViewById(id)).setText(value);
    }
    private void mostrar(DocumentSnapshot d) {
        setTexto(R.id.txtNombrePerfil, dato(d, "nombre",
                usuario.getDisplayName() == null ? "Mi perfil" : usuario.getDisplayName()));
        setTexto(R.id.txtCorreoPerfil, usuario.getEmail() == null ? dato(d, "correo", "") : usuario.getEmail());
        Object anio = leer(d, "anioNacimiento");
        if (anio == null) anio = leer(d, "año de Nacimiento");
        setTexto(R.id.txtAnioPerfil, numero(anio, ""));
        setTexto(R.id.txtSexoPerfil, dato(d, "sexo", "Sin registrar"));
        setTexto(R.id.txtPesoPerfil, numero(leer(d, "peso"), " kg"));
        setTexto(R.id.txtAlturaPerfil, numero(leer(d, "altura"), " cm"));
        setTexto(R.id.txtDormirPerfil, dato(d, "horaDormir", "--:--"));
        setTexto(R.id.txtDespertarPerfil, dato(d, "horaDespertar", "--:--"));
        setTexto(R.id.txtMolestiasPerfil, lista(leer(d, "molestiasActuales")));
        Object suplementos = leer(d, "vitaminasSuplementos");
        if (suplementos == null) suplementos = leer(d, "suplementosActuales");
        setTexto(R.id.txtSuplementosPerfil, lista(suplementos));
    }

    private File archivoFoto() {
        return new File(getFilesDir(), "perfil_" + usuario.getUid() + ".jpg");
    }
    private void mostrarFoto() {
        File f = archivoFoto();
        if (!f.exists()) return;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 2;
        Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
        if (b != null) {
            imagen.setPadding(0, 0, 0, 0);
            imagen.setImageBitmap(b);
        }
    }
    private void guardarFoto(Uri uri) {
        File destino = archivoFoto();
        new Thread(() -> {
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    BitmapFactory.decodeStream(in, null, bounds);
                }
                int escala = 1;
                while (bounds.outWidth / escala > 1100 || bounds.outHeight / escala > 1100) escala *= 2;
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = escala;
                Bitmap b;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    b = BitmapFactory.decodeStream(in, null, opts);
                }
                if (b == null) throw new IllegalArgumentException("Imagen inválida");
                File tmp = new File(destino.getAbsolutePath() + ".tmp");
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    if (!b.compress(Bitmap.CompressFormat.JPEG, 85, out))
                        throw new IllegalStateException("No se pudo guardar");
                }
                b.recycle();
                if (destino.exists() && !destino.delete()) throw new IllegalStateException("No se puede reemplazar");
                if (!tmp.renameTo(destino)) throw new IllegalStateException("No se puede guardar");
                runOnUiThread(() -> {
                    if (!isFinishing()) mostrarFoto();
                    Toast.makeText(this, "Foto actualizada", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "No se pudo cargar la foto", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private EditText campo(LinearLayout panel, String label, String valor, int tipo) {
        TextView titulo = new TextView(this);
        titulo.setText(label);
        titulo.setTextColor(0xFF7053B0);
        titulo.setTextSize(12);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(10);
        panel.addView(titulo, p);
        EditText entrada = new EditText(this);
        entrada.setText(valor == null || valor.equals("Sin registrar") ? "" : valor);
        entrada.setSingleLine(true);
        entrada.setTextSize(15);
        entrada.setInputType(tipo);
        panel.addView(entrada, new LinearLayout.LayoutParams(-1, dp(46)));
        return entrada;
    }
    private void elegirHora(EditText campo) {
        int hh = 23, mm = 0;
        try {
            String[] valores = campo.getText().toString().split(":");
            hh = Integer.parseInt(valores[0]);
            mm = Integer.parseInt(valores[1]);
        } catch (Exception ignored) { }
        new TimePickerDialog(this, (vista, hora, minuto) ->
                campo.setText(String.format(Locale.US, "%02d:%02d", hora, minuto)),
                hh, mm, true).show();
    }
    private void editar() {
        DocumentSnapshot d = datos;
        ScrollView scroll = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setPadding(dp(20), dp(8), dp(20), dp(12));
        panel.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(panel);
        EditText nombre = campo(panel, "Nombre", dato(d, "nombre", usuario.getDisplayName()),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        Object nAnio = leer(d, "anioNacimiento");
        if (nAnio == null) nAnio = leer(d, "año de Nacimiento");
        EditText anio = campo(panel, "Año de nacimiento", nAnio == null ? "" : numero(nAnio, ""), InputType.TYPE_CLASS_NUMBER);
        EditText sexo = campo(panel, "Sexo", dato(d, "sexo", ""), InputType.TYPE_CLASS_TEXT);
        sexo.setFocusable(false);
        sexo.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Sexo").setItems(new String[]{"Masculino", "Femenino"},
                        (dialog, i) -> sexo.setText(i == 0 ? "Masculino" : "Femenino")).show());
        EditText peso = campo(panel, "Peso (kg)", leer(d, "peso") == null ? "" : numero(leer(d, "peso"), ""), InputType.TYPE_CLASS_NUMBER);
        EditText altura = campo(panel, "Altura (cm)", leer(d, "altura") == null ? "" : numero(leer(d, "altura"), ""), InputType.TYPE_CLASS_NUMBER);
        EditText dormir = campo(panel, "Hora habitual de dormir", dato(d, "horaDormir", "23:00"), InputType.TYPE_CLASS_DATETIME);
        EditText despertar = campo(panel, "Hora habitual de despertar", dato(d, "horaDespertar", "07:00"), InputType.TYPE_CLASS_DATETIME);
        dormir.setFocusable(false);
        despertar.setFocusable(false);
        dormir.setOnClickListener(v -> elegirHora(dormir));
        despertar.setOnClickListener(v -> elegirHora(despertar));

        AlertDialog ventana = new AlertDialog.Builder(this).setTitle("Editar perfil")
                .setView(scroll).setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", null).create();
        ventana.setOnShowListener(ignore -> ventana.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    int y, kg, cm;
                    try {
                        y = Integer.parseInt(anio.getText().toString());
                        kg = Integer.parseInt(peso.getText().toString());
                        cm = Integer.parseInt(altura.getText().toString());
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Revisa el año, el peso y la altura", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String n = nombre.getText().toString().trim();
                    String s = sexo.getText().toString().trim();
                    String h1 = dormir.getText().toString();
                    String h2 = despertar.getText().toString();
                    if (n.isEmpty() || s.isEmpty() || y < 1900 || y > Calendar.getInstance().get(Calendar.YEAR)
                            || kg < 20 || kg > 300 || cm < 100 || cm > 250 || !horaValida(h1) || !horaValida(h2)) {
                        Toast.makeText(this, "Revisa los valores ingresados", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Map<String, Object> cambios = new HashMap<>();
                    cambios.put("nombre", n);
                    cambios.put("anioNacimiento", y);
                    cambios.put("sexo", s);
                    cambios.put("peso", kg);
                    cambios.put("altura", cm);
                    cambios.put("horaDormir", h1);
                    cambios.put("horaDespertar", h2);
                    ventana.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    referencia.set(cambios, SetOptions.merge()).addOnSuccessListener(ok -> {
                        Toast.makeText(this, "Perfil actualizado", Toast.LENGTH_SHORT).show();
                        ventana.dismiss();
                    }).addOnFailureListener(error -> {
                        ventana.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        Toast.makeText(this, "No se pudo actualizar", Toast.LENGTH_SHORT).show();
                    });
                }));
        ventana.show();
    }

    private boolean horaValida(String s) {
        if (!s.matches("\\d{2}:\\d{2}")) return false;
        String[] partes = s.split(":");
        int hh = Integer.parseInt(partes[0]);
        int mm = Integer.parseInt(partes[1]);
        return hh >= 0 && hh <= 23 && mm >= 0 && mm <= 59;
    }

    private void confirmarSalida() {
        new AlertDialog.Builder(this)
                .setTitle("Cerrar sesión")
                .setMessage("¿Quieres cerrar tu cuenta en este dispositivo? Tus registros seguirán guardados en Firebase.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Cerrar sesión", (d, which) -> cerrarSesion())
                .show();
    }

    private void cerrarSesion() {
        DescansoActivity.finalizarSesion(this, "cancelada");
        AlarmaProgramador.cancelar(this);
        stopService(new Intent(this, MonitoreoService.class));
        stopService(new Intent(this, MonitoreoSonidoService.class));
        RepositorioEstadoAlarma.sincronizar(this, "CIERRE_SESION_USUARIO");
        auth.signOut();
        try {
            CredentialManager.create(this).clearCredentialStateAsync(
                    new ClearCredentialStateRequest(), null, getMainExecutor(),
                    new CredentialManagerCallback<Void, ClearCredentialException>() {
                        @Override public void onResult(Void ok) { }
                        @Override public void onError(ClearCredentialException error) { }
                    });
        } catch (Exception ignored) { }
        volverLogin();
    }

    private void volverLogin() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }


    private int dp(int valor) {
        return Math.round(valor * getResources().getDisplayMetrics().density);
    }
}
