package com.example.sleeptrack;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AlarmaSonandoActivity extends AppCompatActivity {
    public static final String DETENER_DESDE_NOTIFICACION =
            "com.example.sleeptrack.DETENER_DESDE_NOTIFICACION";
    private boolean procesando = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(0xFF211634);
        getWindow().setNavigationBarColor(0xFF211634);
        setContentView(R.layout.activity_alarma_sonando);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(b.left, b.top, b.right, b.bottom);
            return insets;
        });

        ((TextView) findViewById(R.id.txtHoraDespertar)).setText(
                new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        findViewById(R.id.btnDetenerSonando).setOnClickListener(v -> detener());
        findViewById(R.id.btnPosponerSonando).setOnClickListener(v -> posponer());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { }
        });
        if (DETENER_DESDE_NOTIFICACION.equals(getIntent().getAction())) detener();
    }

    private void detener() {
        if (procesando) return;
        procesando = true;
        Intent despertar = new Intent(this, DescansoActivity.class);
        despertar.setAction(DescansoActivity.ACCION_DESPERTAR);
        despertar.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(despertar);
        finish();
    }

    private void posponer() {
        if (procesando) return;
        procesando = true;
        Intent posponer = new Intent(this, AlarmaReceiver.class).setAction(AlarmaProgramador.POSPONER);
        sendBroadcast(posponer);
        SharedPreferences pref = getSharedPreferences(AlarmaProgramador.PREF, MODE_PRIVATE);
        Class<?> siguiente = pref.getBoolean("sesionDescansoActiva", false)
                ? DescansoActivity.class : alarma.class;
        startActivity(new Intent(this, siguiente));
        finish();
    }
}
