package com.example.sleeptrack;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;

public class CrearCuenta extends AppCompatActivity {

    private EditText etCorreoRegistro;
    private EditText etContrasenaRegistro;
    private EditText etConfirmarContrasena;

    private Button btnRegistrarCuenta;
    private TextView btnVolverInicio;
    private ImageButton btnAtrasRegistro;

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        androidx.activity.EdgeToEdge.enable(this);
        setContentView(R.layout.activity_crear_cuenta);

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main), (view, insets) -> {
                    androidx.core.graphics.Insets bars = insets.getInsets(
                            androidx.core.view.WindowInsetsCompat.Type.systemBars());
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });

        mAuth = FirebaseAuth.getInstance();

        etCorreoRegistro =
                findViewById(R.id.etCorreoRegistro);

        etContrasenaRegistro =
                findViewById(R.id.etContrasenaRegistro);

        etConfirmarContrasena =
                findViewById(R.id.etConfirmarContrasena);

        btnRegistrarCuenta =
                findViewById(R.id.btnRegistrarCuenta);

        btnVolverInicio =
                findViewById(R.id.btnVolverInicio);
        btnAtrasRegistro = findViewById(R.id.btnAtrasRegistro);

        String correoAnterior =
                getIntent().getStringExtra("correo");

        if (correoAnterior != null) {
            etCorreoRegistro.setText(correoAnterior);
        }

        btnRegistrarCuenta.setOnClickListener(v ->
                registrarUsuario()
        );

        btnVolverInicio.setOnClickListener(v -> finish());
        btnAtrasRegistro.setOnClickListener(v -> finish());
    }

    private void registrarUsuario() {

        String correo = etCorreoRegistro
                .getText().toString().trim();

        String contrasena = etContrasenaRegistro
                .getText().toString();

        String confirmar = etConfirmarContrasena
                .getText().toString();

        if (correo.isEmpty()) {

            etCorreoRegistro.setError(
                    "Ingresa tu correo electrónico"
            );

            etCorreoRegistro.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {

            etCorreoRegistro.setError(
                    "Ingresa un correo válido"
            );

            etCorreoRegistro.requestFocus();
            return;
        }

        if (contrasena.length() < 6) {

            etContrasenaRegistro.setError(
                    "La contraseña debe tener al menos 6 caracteres"
            );

            etContrasenaRegistro.requestFocus();
            return;
        }

        if (!contrasena.equals(confirmar)) {

            etConfirmarContrasena.setError(
                    "Las contraseñas no coinciden"
            );

            etConfirmarContrasena.requestFocus();
            return;
        }

        btnRegistrarCuenta.setEnabled(false);
        btnRegistrarCuenta.setText("Creando cuenta...");

        mAuth.createUserWithEmailAndPassword(
                correo,
                contrasena
        ).addOnCompleteListener(this, task -> {

            if (task.isSuccessful()) {

                Toast.makeText(
                        CrearCuenta.this,
                        "Cuenta creada correctamente",
                        Toast.LENGTH_SHORT
                ).show();

                Intent intent = new Intent(
                        CrearCuenta.this,
                        Datos_analisis.class
                );

                intent.setFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK |
                                Intent.FLAG_ACTIVITY_CLEAR_TASK
                );

                startActivity(intent);
                finish();

            } else {

                btnRegistrarCuenta.setEnabled(true);
                btnRegistrarCuenta.setText("Crear mi cuenta");

                Exception error = task.getException();

                String mensaje = "No se pudo crear la cuenta";

                if (error instanceof FirebaseAuthUserCollisionException) {

                    mensaje = "Este correo ya está registrado";

                } else if (error instanceof FirebaseAuthWeakPasswordException) {

                    mensaje = "La contraseña es demasiado débil";
                }

                Toast.makeText(
                        CrearCuenta.this,
                        mensaje,
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }
}
