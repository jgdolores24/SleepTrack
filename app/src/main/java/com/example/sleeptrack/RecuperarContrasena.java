package com.example.sleeptrack;

import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class RecuperarContrasena extends AppCompatActivity {

    private EditText etCorreoRecuperacion;
    private Button btnEnviarRecuperacion;
    private TextView btnVolverLogin;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recuperar_contrasena);

        auth = FirebaseAuth.getInstance();
        etCorreoRecuperacion = findViewById(R.id.etCorreoRecuperacion);
        btnEnviarRecuperacion = findViewById(R.id.btnEnviarRecuperacion);
        btnVolverLogin = findViewById(R.id.btnVolverLogin);

        String correo = getIntent().getStringExtra("correo");
        if (correo != null && !correo.isEmpty()) {
            etCorreoRecuperacion.setText(correo);
        }

        btnEnviarRecuperacion.setOnClickListener(v -> enviarCorreoRecuperacion());
        btnVolverLogin.setOnClickListener(v -> finish());
    }

    private void enviarCorreoRecuperacion() {
        String correo = etCorreoRecuperacion.getText().toString().trim();

        if (correo.isEmpty()) {
            etCorreoRecuperacion.setError("Ingresa tu correo electrónico");
            etCorreoRecuperacion.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            etCorreoRecuperacion.setError("Ingresa un correo válido");
            etCorreoRecuperacion.requestFocus();
            return;
        }

        btnEnviarRecuperacion.setEnabled(false);
        btnEnviarRecuperacion.setText("Enviando...");

        auth.sendPasswordResetEmail(correo).addOnCompleteListener(this, task -> {
            btnEnviarRecuperacion.setEnabled(true);
            btnEnviarRecuperacion.setText("Enviar instrucciones");

            if (task.isSuccessful()) {
                new AlertDialog.Builder(RecuperarContrasena.this)
                        .setTitle("Solicitud enviada")
                        .setMessage("Si este correo pertenece a una cuenta registrada, recibirás un enlace para restablecer tu contraseña.\n\nRevisa también tu carpeta de spam.")
                        .setPositiveButton("Entendido", (dialog, which) -> finish())
                        .show();
            } else {
                Toast.makeText(
                        RecuperarContrasena.this,
                        "No se pudo procesar la solicitud. Revisa tu conexión e inténtalo de nuevo.",
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }
}
