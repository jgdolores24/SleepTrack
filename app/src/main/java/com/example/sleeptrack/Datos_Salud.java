package com.example.sleeptrack;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Datos_Salud extends AppCompatActivity {

    private TextView spVitaminas, spMolestias;
    private Button btnSiguienteSalud;

    private final String[] vitaminas = {
            "Melatonina", "Magnesio", "Vitamina D", "Vitamina B12",
            "Multivitamínico", "Omega 3", "Otros", "Ninguno"
    };

    private final String[] molestias = {
            "Migraña", "Dolor de cabeza", "Estrés", "Ansiedad",
            "Dolor muscular", "Insomnio", "Otros", "Ninguno"
    };

    private final ArrayList<String> vitaminasSeleccionadas = new ArrayList<>();
    private final ArrayList<String> molestiasSeleccionadas = new ArrayList<>();

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
                EdgeToEdge.enable(this);
        setContentView(R.layout.activity_datos_salud);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main), (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(systemBars.left, systemBars.top,
                            systemBars.right, systemBars.bottom);
                    return insets;
                });

        spVitaminas = findViewById(R.id.spVitaminas);
        spMolestias = findViewById(R.id.spMolestias);
        btnSiguienteSalud = findViewById(R.id.btnSiguienteSalud);

        TextView txtSeleccionarVitaminas = findViewById(R.id.txtSeleccionarVitaminas);
        TextView txtSeleccionarMolestias = findViewById(R.id.txtSeleccionarMolestias);
        View contenedorVitaminas = findViewById(R.id.contenedorVitaminas);
        View contenedorMolestias = findViewById(R.id.contenedorMolestias);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        if (savedInstanceState != null) {
            ArrayList<String> v = savedInstanceState.getStringArrayList("vitaminasSeleccionadas");
            ArrayList<String> m = savedInstanceState.getStringArrayList("molestiasSeleccionadas");
            if (v != null) vitaminasSeleccionadas.addAll(v);
            if (m != null) molestiasSeleccionadas.addAll(m);
        }

        actualizarCampo(spVitaminas, vitaminasSeleccionadas, "Seleccionar suplementos");
        actualizarCampo(spMolestias, molestiasSeleccionadas, "Seleccionar molestias");

        View.OnClickListener abrirVitaminas = v -> mostrarSelector(
                "Vitaminas o suplementos", vitaminas, vitaminasSeleccionadas,
                spVitaminas, "Seleccionar suplementos");

        View.OnClickListener abrirMolestias = v -> mostrarSelector(
                "Molestias actuales", molestias, molestiasSeleccionadas,
                spMolestias, "Seleccionar molestias");

        contenedorVitaminas.setOnClickListener(abrirVitaminas);
        spVitaminas.setOnClickListener(abrirVitaminas);
        txtSeleccionarVitaminas.setOnClickListener(abrirVitaminas);

        contenedorMolestias.setOnClickListener(abrirMolestias);
        spMolestias.setOnClickListener(abrirMolestias);
        txtSeleccionarMolestias.setOnClickListener(abrirMolestias);

        btnSiguienteSalud.setOnClickListener(v -> guardarDatosSalud());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList("vitaminasSeleccionadas",
                new ArrayList<>(vitaminasSeleccionadas));
        outState.putStringArrayList("molestiasSeleccionadas",
                new ArrayList<>(molestiasSeleccionadas));
    }

    private void actualizarCampo(TextView campo,
                                 ArrayList<String> seleccionadas,
                                 String textoInicial) {
        campo.setText(seleccionadas.isEmpty()
                ? textoInicial : TextUtils.join(", ", seleccionadas));
    }

    private void mostrarSelector(String titulo, String[] opciones,
                                 ArrayList<String> seleccionadas,
                                 TextView campo, String textoInicial) {

        ArrayList<String> temporales = new ArrayList<>(seleccionadas);
        boolean[] marcadas = new boolean[opciones.length];

        for (int i = 0; i < opciones.length; i++) {
            marcadas[i] = temporales.contains(opciones[i]);
        }

        AlertDialog dialogo = new AlertDialog.Builder(this)
                .setTitle(titulo)
                .setMultiChoiceItems(opciones, marcadas, (dialog, indice, seleccionado) -> {
                    String opcion = opciones[indice];

                    if (seleccionado) {
                        if (opcion.equals("Ninguno")) {
                            temporales.clear();
                            temporales.add("Ninguno");
                        } else {
                            temporales.remove("Ninguno");
                            if (!temporales.contains(opcion)) temporales.add(opcion);
                        }
                    } else {
                        temporales.remove(opcion);
                    }

                    ListView lista = ((AlertDialog) dialog).getListView();
                    for (int i = 0; i < opciones.length; i++) {
                        lista.setItemChecked(i, temporales.contains(opciones[i]));
                    }
                })
                .setPositiveButton("Aceptar", (dialog, which) -> {
                    seleccionadas.clear();
                    seleccionadas.addAll(temporales);
                    actualizarCampo(campo, seleccionadas, textoInicial);
                })
                .setNegativeButton("Cancelar", null)
                .create();

        dialogo.show();
    }

    private void guardarDatosSalud() {
        if (vitaminasSeleccionadas.isEmpty()) {
            Toast.makeText(this, "Selecciona tus suplementos o Ninguno",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (molestiasSeleccionadas.isEmpty()) {
            Toast.makeText(this, "Selecciona tus molestias o Ninguno",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser usuario = mAuth.getCurrentUser();
        if (usuario == null) {
            Toast.makeText(this, "Debes iniciar sesión",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> datos = new HashMap<>();
        datos.put("vitaminasSuplementos", new ArrayList<>(vitaminasSeleccionadas));
        datos.put("molestiasActuales", new ArrayList<>(molestiasSeleccionadas));
        datos.put("registroCompleto", true);

        btnSiguienteSalud.setEnabled(false);

        db.collection("usuarios")
                .document(usuario.getUid())
                .set(datos, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Registro completado correctamente",
                            Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(Datos_Salud.this, Registros.class);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnSiguienteSalud.setEnabled(true);
                    Toast.makeText(this, "Error al guardar: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }
}
