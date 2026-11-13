package com.example.sleeptrack;

import android.util.Log;
import android.content.Context;
import android.content.SharedPreferences;
import com.example.sleeptrack.modelo.ConfiguracionAlarmaBean;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public final class MigracionAlarmaV5 {
    private static final String TAG = "MigracionAlarmaV5";
    private MigracionAlarmaV5() { }

    /** v4 y v5 comparten el criterio: días elegidos = días de despertar. */
    public static void ajustarPreferencias(Context context) {
        // No modificar la máscara: se conserva de lunes a viernes tal como se eligió.
    }

    public static void ejecutarSiHaceFalta() {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) return;
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference configuracion = db.collection("usuarios")
                .document(usuario.getUid()).collection("configuracion").document("alarma");
        DocumentReference respaldo = db.collection("usuarios")
                .document(usuario.getUid()).collection("configuracion")
                .document("alarma_respaldo_pre_v5");
        DocumentReference respaldoDuplicados = db.collection("usuarios")
                .document(usuario.getUid()).collection("configuracion")
                .document("alarma_respaldo_duplicados_v5");
        db.runTransaction(tx -> {
            DocumentSnapshot actual = tx.get(configuracion);
            if (!actual.exists() || actual.getData() == null) return null;
            Object valor = actual.get("versionEsquema");
            int version = valor instanceof Number ? ((Number) valor).intValue() : 0;
            if (version > 5 || (version == 5 && !tieneCamposAntiguos(actual.getData())))
                return null;
            DocumentReference destinoRespaldo = version < 5 ? respaldo : respaldoDuplicados;
            DocumentSnapshot existente = tx.get(destinoRespaldo);
            ConfiguracionAlarmaBean bean = AlarmaDatos.leer(actual.getData());
            if (!existente.exists()) tx.set(destinoRespaldo, actual.getData());
            // set sin merge: elimina los campos antiguos duplicados del documento
            // exclusivamente después de conservar la configuración original.
            tx.set(configuracion, AlarmaDatos.convertir(bean));
            return null;
        }).addOnFailureListener(e -> Log.w(TAG, "Pendiente sincronización del formato v5", e));
    }

    private static boolean tieneCamposAntiguos(java.util.Map<String, Object> datos) {
        if (datos == null) return false;
        for (String clave : new String[]{"hora", "nombre", "diasSemana",
                "duracionMinutos", "horaInicioMonitoreo", "modoDuracion",
                "modoRutina", "monitoreoTelefono", "grabacionSonido", "horario"}) {
            if (datos.containsKey(clave)) return true;
        }
        Object despertador = datos.get("despertador");
        if (despertador instanceof java.util.Map) {
            java.util.Map<?, ?> campos = (java.util.Map<?, ?>) despertador;
            return campos.containsKey("hora") || campos.containsKey("diasSemana");
        }
        return false;
    }
}
