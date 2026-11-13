
package com.example.sleeptrack;

import android.os.Bundle;
import android.app.AlertDialog;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;
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

import java.util.HashMap;
import java.util.Map;
import android.content.Intent;

public class Datos_analisis extends AppCompatActivity {
    private TextView spAnioNacimiento, spSexo, spPeso, spAltura;
    private EditText etNombre;
    private Button btnSiguiente;
    private int anioSeleccionado = -1;
    private int pesoSeleccionado = -1;
    private int alturaSeleccionada = -1;
    private String sexoSeleccionado = "";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_datos_analisis);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main), (v, insets) -> {
                    Insets systemBars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars());

                    v.setPadding(systemBars.left, systemBars.top,
                            systemBars.right, systemBars.bottom);

                    return insets;
                });

        etNombre = findViewById(R.id.etNombre);

        spAnioNacimiento = findViewById(R.id.spAnioNacimiento);
        spSexo = findViewById(R.id.spSexo);
        spPeso = findViewById(R.id.spPeso);
        spAltura = findViewById(R.id.spAltura);

        btnSiguiente = findViewById(R.id.btnSiguiente);

        TextView txtSeleccionarAnio =
                findViewById(R.id.txtSeleccionarAnio);
        TextView txtSeleccionarSexo =
                findViewById(R.id.txtSeleccionarSexo);
        TextView txtSeleccionarPeso =
                findViewById(R.id.txtSeleccionarPeso);
        TextView txtSeleccionarAltura =
                findViewById(R.id.txtSeleccionarAltura);

        spAnioNacimiento.setOnClickListener(v -> seleccionarAnio());
        txtSeleccionarAnio.setOnClickListener(v -> seleccionarAnio());

        spSexo.setOnClickListener(v -> seleccionarSexo());
        txtSeleccionarSexo.setOnClickListener(v -> seleccionarSexo());

        spPeso.setOnClickListener(v -> seleccionarPeso());
        txtSeleccionarPeso.setOnClickListener(v -> seleccionarPeso());

        spAltura.setOnClickListener(v -> seleccionarAltura());
        txtSeleccionarAltura.setOnClickListener(v -> seleccionarAltura());

        btnSiguiente.setOnClickListener(v -> guardarDatos());
    }

    private void seleccionarAnio() {

        NumberPicker selector = new NumberPicker(this);

        selector.setMinValue(1970);
        selector.setMaxValue(2026);
        selector.setValue(
                anioSeleccionado == -1
                        ? 2000 : anioSeleccionado);
        selector.setWrapSelectorWheel(false);

        new AlertDialog.Builder(this)
                .setTitle("Selecciona tu año de nacimiento")
                .setView(selector)
                .setPositiveButton("Aceptar", (dialog, which) -> {

                    anioSeleccionado = selector.getValue();

                    spAnioNacimiento.setText(
                            "Año de nacimiento: " + anioSeleccionado);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void seleccionarSexo() {

        String[] opciones = {"Masculino", "Femenino"};

        new AlertDialog.Builder(this)
                .setTitle("Selecciona tu sexo")
                .setItems(opciones, (dialog, which) -> {

                    sexoSeleccionado = opciones[which];

                    spSexo.setText("Sexo: " + sexoSeleccionado);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void seleccionarPeso() {

        NumberPicker selector = new NumberPicker(this);

        selector.setMinValue(20);
        selector.setMaxValue(300);
        selector.setValue(
                pesoSeleccionado == -1
                        ? 65 : pesoSeleccionado);
        selector.setWrapSelectorWheel(false);
        selector.setFormatter(valor -> valor + " kg");

        new AlertDialog.Builder(this)
                .setTitle("Seleccionar peso")
                .setView(selector)
                .setPositiveButton("Aceptar", (dialog, which) -> {

                    pesoSeleccionado = selector.getValue();

                    spPeso.setText(
                            "Peso: " + pesoSeleccionado + " kg");
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
    private void seleccionarAltura() {

        NumberPicker selector = new NumberPicker(this);

        selector.setMinValue(100);
        selector.setMaxValue(250);
        selector.setValue(
                alturaSeleccionada == -1
                        ? 170 : alturaSeleccionada);
        selector.setWrapSelectorWheel(false);
        selector.setFormatter(valor -> valor + " cm");

        new AlertDialog.Builder(this)
                .setTitle("Seleccionar altura")
                .setView(selector)
                .setPositiveButton("Aceptar", (dialog, which) -> {

                    alturaSeleccionada = selector.getValue();

                    spAltura.setText(
                            "Altura: " + alturaSeleccionada + " cm");
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
    private void guardarDatos() {

        String nombre = etNombre.getText().toString().trim();

        if (nombre.isEmpty()) {
            etNombre.setError("Ingresa tu nombre");
            etNombre.requestFocus();
            return;
        }
        if (anioSeleccionado == -1) {
            Toast.makeText(this,
                    "Selecciona tu año de nacimiento",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (sexoSeleccionado.isEmpty()) {
            Toast.makeText(this,
                    "Selecciona tu sexo",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (pesoSeleccionado == -1) {
            Toast.makeText(this,
                    "Selecciona tu peso",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (alturaSeleccionada == -1) {
            Toast.makeText(this,
                    "Selecciona tu altura",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        FirebaseUser usuario =
                FirebaseAuth.getInstance().getCurrentUser();

        if (usuario == null) {
            Toast.makeText(this,
                    "Debes iniciar sesión primero",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> datos = new HashMap<>();

        datos.put("nombre", nombre);
        datos.put("correo", usuario.getEmail());
        datos.put("año de Nacimiento", anioSeleccionado);
        datos.put("sexo", sexoSeleccionado);
        datos.put("peso", pesoSeleccionado);
        datos.put("altura", alturaSeleccionada);

        btnSiguiente.setEnabled(false);

        FirebaseFirestore.getInstance()
                .collection("usuarios")
                .document(usuario.getUid())
                .set(datos, SetOptions.merge())
                .addOnSuccessListener(unused -> {

                    btnSiguiente.setEnabled(true);

                    Toast.makeText(this,
                            "Datos guardados correctamente",
                            Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(
                            Datos_analisis.this,
                            Horario_Dormir.class
                    );
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {

                    btnSiguiente.setEnabled(true);

                    Toast.makeText(this,
                            "Error al guardar: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }
}
