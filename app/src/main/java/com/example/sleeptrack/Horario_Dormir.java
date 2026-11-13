package com.example.sleeptrack;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class Horario_Dormir extends AppCompatActivity {

    private TextView txtHoraDormir, txtHoraDespertar;
    private Button btnSiguienteHorario;
    private String horaDormir = "22:00";
    private String horaDespertar = "07:00";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
                EdgeToEdge.enable(this);
        setContentView(R.layout.activity_horario_dormir);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main), (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(systemBars.left, systemBars.top,
                            systemBars.right, systemBars.bottom);
                    return insets;
                });

        txtHoraDormir = findViewById(R.id.txtHoraDormir);
        txtHoraDespertar = findViewById(R.id.txtHoraDespertar);
        btnSiguienteHorario = findViewById(R.id.btnSiguienteHorario);

        if (savedInstanceState != null) {
            horaDormir = savedInstanceState.getString("horaDormir", "22:00");
            horaDespertar = savedInstanceState.getString("horaDespertar", "07:00");
        }
        txtHoraDormir.setText(horaDormir);
        txtHoraDespertar.setText(horaDespertar);

        txtHoraDormir.setOnClickListener(v -> seleccionarHora(true));
        txtHoraDespertar.setOnClickListener(v -> seleccionarHora(false));
        findViewById(R.id.contenedorDormir).setOnClickListener(v -> seleccionarHora(true));
        findViewById(R.id.contenedorDespertar).setOnClickListener(v -> seleccionarHora(false));
        findViewById(R.id.txtFlechaDormir).setOnClickListener(v -> seleccionarHora(true));
        findViewById(R.id.txtFlechaDespertar).setOnClickListener(v -> seleccionarHora(false));
        btnSiguienteHorario.setOnClickListener(v -> guardarHorarios());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("horaDormir", horaDormir);
        outState.putString("horaDespertar", horaDespertar);
        super.onSaveInstanceState(outState);
    }

    private void seleccionarHora(boolean dormir) {
        String actual = dormir ? horaDormir : horaDespertar;
        String[] partes = actual.split(":");

        TimePickerDialog dialogo = new TimePickerDialog(
                this,
                (view, hora, minuto) -> {
                    String seleccion = String.format(Locale.US, "%02d:%02d", hora, minuto);
                    if (dormir) {
                        horaDormir = seleccion;
                        txtHoraDormir.setText(seleccion);
                    } else {
                        horaDespertar = seleccion;
                        txtHoraDespertar.setText(seleccion);
                    }
                },
                Integer.parseInt(partes[0]),
                Integer.parseInt(partes[1]),
                true
        );
        dialogo.show();
    }

    private void guardarHorarios() {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) {
            Toast.makeText(this, "Debes iniciar sesión primero", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> horarios = new HashMap<>();
        horarios.put("horaDormir", horaDormir);
        horarios.put("horaDespertar", horaDespertar);

        btnSiguienteHorario.setEnabled(false);
        FirebaseFirestore.getInstance()
                .collection("usuarios")
                .document(usuario.getUid())
                .set(horarios, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    btnSiguienteHorario.setEnabled(true);
                    startActivity(new Intent(Horario_Dormir.this, Datos_Salud.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnSiguienteHorario.setEnabled(true);
                    Toast.makeText(this, "Error al guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}
