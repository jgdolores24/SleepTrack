package com.example.sleeptrack;

import android.content.Context;

import org.tensorflow.lite.support.audio.TensorAudio;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.audio.classifier.AudioClassifier;
import org.tensorflow.lite.task.audio.classifier.Classifications;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class ClasificadorSonidos implements AutoCloseable {

    public static final int MUESTRAS = 15600;

    private final AudioClassifier modelo;
    private final TensorAudio entrada;

    public static class Resultado {

        public final String tipo;
        public final float confianza;

        public Resultado(String tipo, float confianza) {
            this.tipo = tipo;
            this.confianza = confianza;
        }
    }

    public ClasificadorSonidos(Context context) throws IOException {

        modelo = AudioClassifier.createFromFile(
                context, "yamnet.tflite");

        if (modelo.getRequiredTensorAudioFormat().getSampleRate() != 16000
                || modelo.getRequiredTensorAudioFormat().getChannels() != 1
                || modelo.getRequiredInputBufferSize() != MUESTRAS) {

            modelo.close();

            throw new IllegalArgumentException(
                    "Formato del modelo YAMNet incompatible");
        }

        entrada = modelo.createInputTensorAudio();
    }

    public Resultado analizar(short[] muestras) {

        if (muestras == null || muestras.length != MUESTRAS) {
            return null;
        }

        entrada.load(muestras);

        List<Classifications> resultados =
                modelo.classify(entrada);

        float ronquido = 0;
        float voz = 0;
        float impacto = 0;

        for (Classifications clasificacion : resultados) {

            for (Category categoria : clasificacion.getCategories()) {

                String etiqueta = categoria.getLabel()
                        .toLowerCase(Locale.US);

                float confianza = categoria.getScore();

                if (etiqueta.equals("snoring")) {

                    ronquido = Math.max(ronquido, confianza);

                } else if (etiqueta.equals("speech")
                        || etiqueta.equals("whispering")
                        || etiqueta.equals("murmur")
                        || etiqueta.equals("mumbling")) {

                    voz = Math.max(voz, confianza);

                } else if (etiqueta.equals("bang")
                        || etiqueta.equals("thump, thud")
                        || etiqueta.equals("slap, smack")
                        || etiqueta.equals("boom")
                        || etiqueta.equals("door slam")) {

                    impacto = Math.max(impacto, confianza);
                }
            }
        }

        if (ronquido >= 0.35f) {
            return new Resultado("ronquido", ronquido);
        }

        if (voz >= 0.40f) {
            return new Resultado("voz_detectada", voz);
        }

        if (impacto >= 0.40f) {
            return new Resultado("ruido_fuerte", impacto);
        }

        double energia = 0;

        for (short valor : muestras) {
            double amplitud = valor / 32768.0;
            energia += amplitud * amplitud;
        }

        double rms = Math.sqrt(energia / muestras.length);

        if (rms >= 0.18) {
            return new Resultado("ruido_fuerte", (float) rms);
        }

        return null;
    }

    @Override
    public void close() {
        modelo.close();
    }
}
