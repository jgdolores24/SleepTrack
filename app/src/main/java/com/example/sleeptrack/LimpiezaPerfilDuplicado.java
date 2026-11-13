package com.example.sleeptrack;

import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class LimpiezaPerfilDuplicado {
    private static final String TAG = "LimpiezaPerfil";

    private LimpiezaPerfilDuplicado() { }

    public static void ejecutarSiEsSeguro() {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) return;
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference principal = db.collection("usuarios").document(usuario.getUid());
        DocumentReference respaldo = principal.collection("configuracion")
                .document("perfil_respaldo_pre_limpieza");
        db.runTransaction(tx -> {
            DocumentSnapshot doc = tx.get(principal);
            if (!doc.exists() || doc.getData() == null) return null;
            Map<String, Object> original = doc.getData();
            Object perfil = original.get("perfil");
            if (!(perfil instanceof Map)) return null;
            if (!sonDuplicados(original, (Map<?, ?>) perfil)) {
                Log.w(TAG, "Se conserva perfil porque contiene datos distintos o desconocidos");
                return null;
            }
            DocumentSnapshot respaldoAnterior = tx.get(respaldo);
            if (!respaldoAnterior.exists()) tx.set(respaldo, original);
            tx.update(principal, "perfil", FieldValue.delete(),
                    "versionPerfil", FieldValue.delete());
            return null;
        }).addOnFailureListener(e -> Log.w(TAG, "Limpieza aplazada; sin cambios", e));
    }

    private static boolean sonDuplicados(Map<String, Object> base, Map<?, ?> perfil) {
        for (Map.Entry<?, ?> campo : perfil.entrySet()) {
            if (!(campo.getKey() instanceof String)) return false;
            String clave = (String) campo.getKey();
            Object valor = campo.getValue();
            Object actual;
            switch (clave) {
                case "nombre": case "correo": case "sexo":
                    actual = base.get(clave); break;
                case "anioNacimiento":
                    actual = base.containsKey("anioNacimiento") ? base.get("anioNacimiento")
                            : base.get("año de Nacimiento"); break;
                case "pesoKg": actual = base.get("peso"); break;
                case "alturaCm": actual = base.get("altura"); break;
                case "horariosHabituales":
                    if (!(valor instanceof Map)) return false;
                    if (!mismoMapa(base, (Map<?, ?>) valor, "horarios")) return false;
                    continue;
                case "saludInicial":
                    if (!(valor instanceof Map)) return false;
                    if (!mismoMapa(base, (Map<?, ?>) valor, "salud")) return false;
                    continue;
                default: return false;
            }
            if (!igual(actual, valor)) return false;
        }
        return !perfil.isEmpty();
    }

    private static boolean mismoMapa(Map<String, Object> base, Map<?, ?> grupo,
                                      String tipo) {
        if (grupo.isEmpty()) return false;
        for (Map.Entry<?, ?> campo : grupo.entrySet()) {
            if (!(campo.getKey() instanceof String)) return false;
            String clave = (String) campo.getKey();
            Object actual;
            if ("horarios".equals(tipo)) {
                if ("dormir".equals(clave)) actual = base.get("horaDormir");
                else if ("despertar".equals(clave)) actual = base.get("horaDespertar");
                else return false;
            } else {
                if ("suplementosDeclarados".equals(clave)) {
                    actual = base.containsKey("vitaminasSuplementos")
                            ? base.get("vitaminasSuplementos") : base.get("suplementosActuales");
                } else if ("molestiasDeclaradas".equals(clave)) {
                    actual = base.get("molestiasActuales");
                } else return false;
            }
            if (!igual(actual, campo.getValue())) return false;
        }
        return true;
    }

    private static boolean igual(Object a, Object b) {
        if (a == null || b == null) return a == b;
        if (a instanceof Number && b instanceof Number)
            return Double.compare(((Number) a).doubleValue(),
                    ((Number) b).doubleValue()) == 0;
        return Objects.equals(a, b);
    }
}
