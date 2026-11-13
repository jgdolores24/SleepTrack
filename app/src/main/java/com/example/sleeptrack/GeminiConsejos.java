package com.example.sleeptrack;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerativeBackend;

public final class GeminiConsejos {

    // Stable Gemini Developer API model supported by Firebase AI Logic.
    private static final String MODELO = "gemini-3.5-flash-lite";

    public interface Resultado {
        void correcto(String texto);
        void error(String mensaje);
    }

    private GeminiConsejos() { }

    public static void responder(Context contexto, String solicitud, Resultado callback) {
        try {
            GenerativeModel modeloBase = FirebaseAI.getInstance(GenerativeBackend.googleAI())
                    .generativeModel(MODELO);
            GenerativeModelFutures modelo = GenerativeModelFutures.from(modeloBase);
            Content contenido = new Content.Builder().addText(solicitud).build();
            ListenableFuture<GenerateContentResponse> tarea = modelo.generateContent(contenido);
            Futures.addCallback(tarea, new FutureCallback<GenerateContentResponse>() {
                @Override
                public void onSuccess(GenerateContentResponse resultado) {
                    String texto = resultado == null ? null : resultado.getText();
                    if (texto == null || texto.trim().isEmpty()) {
                        callback.error("Gemini no devolvió texto. Reintenta la pregunta.");
                    } else {
                        callback.correcto(texto.trim());
                    }
                }

                @Override
                public void onFailure(@NonNull Throwable error) {
                    callback.error("No fue posible consultar Gemini. Comprueba Internet, App Check y la cuota de la API.");
                }
            }, ContextCompat.getMainExecutor(contexto));
        } catch (Exception error) {
            callback.error("No se pudo iniciar Gemini. Revisa Firebase AI Logic y las dependencias de Gradle.");
        }
    }
}
