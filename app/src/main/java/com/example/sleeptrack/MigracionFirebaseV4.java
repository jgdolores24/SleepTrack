package com.example.sleeptrack;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Migración v4 idempotente, por páginas y NO destructiva. Nunca se deduce el
 * estado de los servicios Android de la configuración guardada en Firestore.
 */
public final class MigracionFirebaseV4 {
    private static final String TAG = "SleepTrackMigracionV4";
    private static final int TAMANIO_PAGINA = 100;
    private static boolean ejecutando;

    private MigracionFirebaseV4() { }

    public static synchronized void migrarUnaVez(Context context) {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null || ejecutando) return;
        final SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(
                "sleeptrack_migraciones", Context.MODE_PRIVATE);
        final String clave = "firestore_v4_" + usuario.getUid();
        if (prefs.getBoolean(clave, false)) return;
        ejecutando = true;
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference base = db.collection("usuarios").document(usuario.getUid());
        // Solo migrar actividades; mantener los campos originales del perfil.
        migrarAlarma(db, base.collection("configuracion").document("alarma"))
                .addOnSuccessListener(ok -> migrarPagina(db, base, prefs, clave, null))
                .addOnFailureListener(MigracionFirebaseV4::fallo);
    }

    // La alarma se convierte en v5 con respaldo, mediante MigracionAlarmaV5.
    private static Task<Void> migrarAlarma(FirebaseFirestore db, DocumentReference ref) {
        return Tasks.forResult(null);
    }

    private static void migrarPagina(FirebaseFirestore db, DocumentReference base,
                                    SharedPreferences preferencias, String clave,
                                    DocumentSnapshot anterior) {
        Query consulta = base.collection("actividades").orderBy(FieldPath.documentId())
                .limit(TAMANIO_PAGINA);
        if (anterior != null) consulta = consulta.startAfter(anterior);
        consulta.get().addOnSuccessListener(documentos -> {
            List<Task<Void>> tareas = new ArrayList<>();
            List<DocumentSnapshot> lista = documentos.getDocuments();
            for (DocumentSnapshot actividad : lista) {
                Object valor = actividad.get("versionEsquema");
                Number version = valor instanceof Number ? (Number) valor : null;
                if (version != null && version.intValue() >= 4) continue;
                tareas.add(migrarActividad(db, actividad.getReference(), actividad.getId()));
            }
            Tasks.whenAll(tareas).addOnSuccessListener(ok -> {
                if (lista.size() == TAMANIO_PAGINA) {
                    migrarPagina(db, base, preferencias, clave, lista.get(lista.size() - 1));
                } else {
                    preferencias.edit().putBoolean(clave, true).apply();
                    Log.i(TAG, "Migración completada; campos anteriores preservados");
                    terminar();
                }
            }).addOnFailureListener(MigracionFirebaseV4::fallo);
        }).addOnFailureListener(MigracionFirebaseV4::fallo);
    }

    private static Task<Void> migrarActividad(FirebaseFirestore db, DocumentReference ref,
                                               String fecha) {
        return db.runTransaction(transaccion -> {
            DocumentSnapshot actual = transaccion.get(ref);
            if (!actual.exists() || actual.getData() == null) return null;
            Object valor = actual.get("versionEsquema");
            Number version = valor instanceof Number ? (Number) valor : null;
            if (version != null && version.intValue() >= 4) return null;
            Map<String, Object> cambios = new HashMap<>();
            cambios.put("fechaLocal", fecha);
            cambios.put("versionEsquema", 4);
            cambios.put("eventosNormalizados", RepositorioActividades.normalizar(
                    fecha, actual.getData()));
            cambios.put("actualizadoEn", FieldValue.serverTimestamp());
            transaccion.set(ref, cambios, SetOptions.merge());
            return null;
        });
    }

    private static String cadena(Object v, String defecto) {
        return v instanceof String && !((String) v).trim().isEmpty() ? (String) v : defecto;
    }
    private static int numero(Object v, int defecto) {
        return v instanceof Number ? ((Number) v).intValue() : defecto;
    }
    private static synchronized void terminar() { ejecutando = false; }
    private static void fallo(Exception error) {
        Log.w(TAG, "Migración pendiente; se reintentará sin borrar datos", error);
        terminar();
    }
}
