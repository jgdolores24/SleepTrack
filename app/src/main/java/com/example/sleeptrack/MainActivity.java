package com.example.sleeptrack;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.splashscreen.SplashScreen;
import com.google.android.gms.tasks.Tasks;
import java.util.concurrent.TimeUnit;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;

public class MainActivity extends AppCompatActivity {

    private EditText etCorreo, etContrasena;
    private Button btnContinuar, btnGoogle, btnCrearCuenta;
    private TextView btnOlvidar;
    private FirebaseAuth mAuth;
    private boolean mantenerSplash = true;
    private boolean formularioMostrado = false;
    private boolean verificandoPerfil = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            FirebaseApp.initializeApp(this);

            FirebaseAppCheck.getInstance()
                    .installAppCheckProviderFactory(
                            DebugAppCheckProviderFactory.getInstance()
                    );

            FirebaseAppCheck.getInstance()
                    .getAppCheckToken(false);
        }
        splashScreen.setKeepOnScreenCondition(() -> mantenerSplash);

        mAuth = FirebaseAuth.getInstance();

        if (mAuth.getCurrentUser() != null) {
            verificarPerfil();
        } else {
            mostrarFormulario();
        }
    }

    private void mostrarFormulario() {
        if (formularioMostrado || isFinishing() || isDestroyed()) return;
        formularioMostrado = true;

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main), (vista, insets) -> {
                    Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    vista.setPadding(barras.left, barras.top, barras.right, barras.bottom);
                    return insets;
                }
        );

        etCorreo = findViewById(R.id.etCorreo);
        etContrasena = findViewById(R.id.etContrasena);
        btnContinuar = findViewById(R.id.btn_continuar);
        btnGoogle = findViewById(R.id.btn_google);
        btnCrearCuenta = findViewById(R.id.btn_crearcuenta);
        btnOlvidar = findViewById(R.id.btn_olvidar);

        mantenerSplash = false;

        btnContinuar.setOnClickListener(v -> iniciarSesion());
        btnGoogle.setOnClickListener(v -> iniciarSesionGoogle());

        btnCrearCuenta.setOnClickListener(v -> {
            Intent intent = new Intent(this, CrearCuenta.class);
            intent.putExtra("correo", etCorreo.getText().toString().trim());
            startActivity(intent);
        });

        btnOlvidar.setOnClickListener(v -> {
            Intent intent = new Intent(this, RecuperarContrasena.class);
            intent.putExtra("correo", etCorreo.getText().toString().trim());
            startActivity(intent);
        });
    }

    private boolean validarCorreo() {
        String correo = etCorreo.getText().toString().trim();

        if (TextUtils.isEmpty(correo)) {
            etCorreo.setError("Ingresa tu correo");
            etCorreo.requestFocus();
            return false;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            etCorreo.setError("Correo electrónico inválido");
            etCorreo.requestFocus();
            return false;
        }

        return true;
    }

    private void iniciarSesion() {
        if (!validarCorreo()) return;

        String correo = etCorreo.getText().toString().trim();
        String contrasena = etContrasena.getText().toString();

        if (TextUtils.isEmpty(contrasena)) {
            etContrasena.setError("Ingresa tu contraseña");
            etContrasena.requestFocus();
            return;
        }

        btnContinuar.setEnabled(false);

        mAuth.signInWithEmailAndPassword(correo, contrasena)
                .addOnCompleteListener(this, tarea -> {
                    if (tarea.isSuccessful()) {
                        verificarPerfil();
                    } else {
                        btnContinuar.setEnabled(true);
                        Toast.makeText(this,
                                "No se pudo iniciar sesión. Verifica tus datos.",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void iniciarSesionGoogle() {
        GetSignInWithGoogleOption googleOption =
                new GetSignInWithGoogleOption.Builder(
                        getString(R.string.default_web_client_id)
                ).build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleOption)
                .build();

        CredentialManager credentialManager = CredentialManager.create(this);
        btnGoogle.setEnabled(false);

        credentialManager.getCredentialAsync(
                this,
                request,
                null,
                ContextCompat.getMainExecutor(MainActivity.this),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse response) {
                        Credential credential = response.getCredential();
                        if (credential instanceof CustomCredential) {
                            CustomCredential customCredential = (CustomCredential) credential;
                            if (GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                    .equals(customCredential.getType())) {
                                try {
                                    GoogleIdTokenCredential googleCredential =
                                            GoogleIdTokenCredential.createFrom(customCredential.getData());
                                    autenticarGoogleFirebase(googleCredential.getIdToken());
                                    return;
                                } catch (Exception e) {
                                    Toast.makeText(MainActivity.this,
                                            "Error al obtener la cuenta de Google",
                                            Toast.LENGTH_LONG).show();
                                }
                            }
                        }
                        btnGoogle.setEnabled(true);
                    }

                    @Override
                    public void onError(GetCredentialException error) {
                        btnGoogle.setEnabled(true);
                        Toast.makeText(MainActivity.this,
                                "No se pudo iniciar sesión con Google",
                                Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void autenticarGoogleFirebase(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);

        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, tarea -> {
                    if (tarea.isSuccessful()) {
                        verificarPerfil();
                    } else {
                        btnGoogle.setEnabled(true);
                        Toast.makeText(this,
                                "Error de autenticación con Firebase",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void verificarPerfil() {
        if (verificandoPerfil || isFinishing() || isDestroyed()) return;

        FirebaseUser usuario = mAuth.getCurrentUser();
        if (usuario == null) {
            mostrarFormulario();
            habilitarBotones();
            return;
        }

        verificandoPerfil = true;

        Tasks.withTimeout(FirebaseFirestore.getInstance()
                .collection("usuarios")
                .document(usuario.getUid())
                .get(), 12, TimeUnit.SECONDS)
                .addOnSuccessListener(documento -> {
                    if (isFinishing() || isDestroyed()) return;

                    Class<?> destino = Boolean.TRUE.equals(documento.getBoolean("registroCompleto"))
                            ? Registros.class : Datos_analisis.class;
                    startActivity(new Intent(MainActivity.this, destino));
                    overridePendingTransition(0, 0);
                    finish();
                })
                .addOnFailureListener(error -> {
                    verificandoPerfil = false;
                    if (isFinishing() || isDestroyed()) return;
                    mostrarFormulario();
                    habilitarBotones();
                    Toast.makeText(this,
                            "No se pudo verificar tu perfil: " + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void habilitarBotones() {
        if (!formularioMostrado) return;
        btnContinuar.setEnabled(true);
        btnGoogle.setEnabled(true);
        btnCrearCuenta.setEnabled(true);
        btnOlvidar.setEnabled(true);
    }
}
