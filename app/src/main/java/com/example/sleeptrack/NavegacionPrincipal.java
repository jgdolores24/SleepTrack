package com.example.sleeptrack;

import android.content.Intent;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;

/** Mantiene una sola definición de las rutas de navegación inferior. */
public final class NavegacionPrincipal {
    private NavegacionPrincipal() { }

    public static void configurar(AppCompatActivity actividad, int seleccionado) {
        vincular(actividad, R.id.navAlarma, alarma.class, seleccionado);
        vincular(actividad, R.id.navRegistros, Registros.class, seleccionado);
        vincular(actividad, R.id.navPatrones, Patrones.class, seleccionado);
        vincular(actividad, R.id.navConsejos, consejos.class, seleccionado);
        vincular(actividad, R.id.navPerfil, perfil.class, seleccionado);
    }

    private static void vincular(AppCompatActivity actividad, int botonId,
            Class<?> destino, int seleccionado) {
        View boton = actividad.findViewById(botonId);
        if (boton == null || botonId == seleccionado) return;
        boton.setOnClickListener(v -> {
            actividad.startActivity(new Intent(actividad, destino));
            actividad.finish();
        });
    }
}
