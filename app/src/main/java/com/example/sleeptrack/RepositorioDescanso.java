package com.example.sleeptrack;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;


public final class RepositorioDescanso {
    private RepositorioDescanso() {}

    private static DocumentReference dia(String uid, String fecha) {
        return FirebaseFirestore.getInstance()
                .collection("usuarios")
                .document(uid)
                .collection("registrosSueno")
                .document(fecha);
    }

    public static Task<Void> finalizar(
            String uid,
            String fechaInicio,
            String fechaFin,
            String id,
            long inicio,
            long fin,
            String motivo,
            String modo) {
        boolean valida = !"cancelada".equals(motivo)
                && inicio > 0 && fin > inicio && fin - inicio <= 18L * 60 * 60000;
        // DescansoActivity proporciona la fecha final usando la zona del inicio.
        Map<String, Object> datos = FirestoreOrden.inicioSesion(inicio);
        @SuppressWarnings("unchecked")
        Map<String, Object> sesion = (Map<String, Object>) datos.get("sesion");
        @SuppressWarnings("unchecked")
        Map<String, Object> cierre =
                (Map<String, Object>) FirestoreOrden.finSesion(fin, motivo).get("sesion");
        sesion.putAll(cierre);
        sesion.put("estado", "cancelada".equals(motivo) ? "cancelada" : "finalizada");
        sesion.put("duracionMinutos", valida ? Math.round((fin - inicio) / 60000.0) : null);
        datos.put("modo", modo);
        datos.put("fechaInicio", fechaInicio);
        datos.put("fechaFin", fechaFin);
        datos.put("fecha", valida ? fechaFin : fechaInicio);
        datos.put("tipoSesion", "libre".equals(modo) ? "LIBRE" : "MONITOREADA");
        datos.put("duracionSesionMinutos", valida ? Math.round((fin - inicio) / 60000.0) : null);
        datos.put("origenRegistro", "ANDROID");
        datos.put("versionEsquema", 4);

        DocumentReference origen = dia(uid, fechaInicio);
        if (!valida || fechaInicio.equals(fechaFin)) {
            WriteBatch lote = FirebaseFirestore.getInstance().batch();
            lote.set(origen.collection("sesiones").document(id), datos, SetOptions.merge());
            if (valida) lote.set(origen, indice(id, sesion, modo, fechaFin), SetOptions.merge());
            return lote.commit();
        }

        DocumentReference destino = dia(uid, fechaFin);
        DocumentReference sesionOrigen = origen.collection("sesiones").document(id);
        DocumentReference sesionDestino = destino.collection("sesiones").document(id);

        // Lectura y traslado atómicos: nunca mezclar con las sesiones de otro día.
        return FirebaseFirestore.getInstance().runTransaction(transaccion -> {
            // Todas las lecturas ocurren antes de escribir (requisito de Firestore).
            DocumentSnapshot viejoDia = transaccion.get(origen);
            DocumentSnapshot viejaSesion = transaccion.get(sesionOrigen);

            Map<String, Object> sesionCompleta = new HashMap<>();
            if (viejaSesion.exists() && viejaSesion.getData() != null) {
                sesionCompleta.putAll(viejaSesion.getData());
            }
            sesionCompleta.putAll(datos);

            Map<String, Object> resumenNuevo = indice(id, sesion, modo, fechaFin);
            Map<String, Object> limpieza = new HashMap<>();
            copiarEventos(viejoDia, viejaSesion, id, "eventosTelefono",
                    sesionCompleta, resumenNuevo, limpieza);
            copiarEventos(viejoDia, viejaSesion, id, "audios",
                    sesionCompleta, resumenNuevo, limpieza);

            transaccion.set(sesionDestino, sesionCompleta, SetOptions.merge());
            transaccion.set(destino, resumenNuevo, SetOptions.merge());
            if (viejoDia.exists()) {
                Object indiceAnterior = viejoDia.get("sesionesFinalizadas");
                if (indiceAnterior instanceof Map
                        && ((Map<?, ?>) indiceAnterior).containsKey(id)) {
                    Map<String, Object> borrarIndice = new HashMap<>();
                    borrarIndice.put(id, FieldValue.delete());
                    limpieza.put("sesionesFinalizadas", borrarIndice);
                }
                if (!limpieza.isEmpty())
                    transaccion.set(origen, limpieza, SetOptions.merge());
            }
            transaccion.delete(sesionOrigen);
            return null;
        });
    }

    private static Map<String, Object> indice(
            String id, Map<String, Object> sesion, String modo, String fecha) {
        Map<String, Object> entrada = new HashMap<>(sesion);
        entrada.put("modo", modo);
        Map<String, Object> sesiones = new HashMap<>();
        sesiones.put(id, entrada);
        Map<String, Object> resumen = new HashMap<>();
        resumen.put("versionEsquema", 4);
        resumen.put("fecha", fecha);
        resumen.put("sesionesFinalizadas", sesiones);
        return resumen;
    }

    private static void copiarEventos(
            DocumentSnapshot diaOrigen,
            DocumentSnapshot sesionOrigen,
            String id,
            String campo,
            Map<String, Object> sesionDestino,
            Map<String, Object> diaDestino,
            Map<String, Object> limpiarOrigen) {
        List<Object> todos = new ArrayList<>();
        List<Object> delDia = eventosDeSesion(diaOrigen.get(campo), id);
        for (Object evento : delDia) if (!todos.contains(evento)) todos.add(evento);
        for (Object evento : eventosDeSesion(sesionOrigen.get(campo), id)) {
            if (!todos.contains(evento)) todos.add(evento);
        }
        if (!todos.isEmpty()) {
            diaDestino.put(campo, FieldValue.arrayUnion(todos.toArray()));
            sesionDestino.put(campo, FieldValue.arrayUnion(todos.toArray()));
        }
        if (!delDia.isEmpty()) {
            limpiarOrigen.put(campo, FieldValue.arrayRemove(delDia.toArray()));
        }
    }

    private static List<Object> eventosDeSesion(Object valor, String id) {
        List<Object> propios = new ArrayList<>();
        if (valor instanceof List) {
            for (Object evento : (List<?>) valor) {
                if (evento instanceof Map
                        && id.equals(((Map<?, ?>) evento).get("sesionId"))) {
                    propios.add(evento);
                }
            }
        }
        return propios;
    }

    public static Task<Void> guardarEvaluacion(
            String uid, String fecha, String id, Map<String, Object> datos) {
        WriteBatch lote = FirebaseFirestore.getInstance().batch();
        DocumentReference dia = dia(uid, fecha);
        if (!id.isEmpty()) {
            datos.put("versionEsquema", 4);
            @SuppressWarnings("unchecked")
            Map<String, Object> evaluacion = (Map<String, Object>) datos.get("evaluacion");
            evaluacion.put("sesionId", id);
            lote.set(dia.collection("sesiones").document(id), datos, SetOptions.merge());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> evaluacionDia = (Map<String, Object>) datos.get("evaluacion");
        Object minutos = evaluacionDia == null ? null : evaluacionDia.get("duracionRegistradaMinutos");
        boolean breve = minutos instanceof Number && ((Number) minutos).intValue() < 180;
        if (!breve || id.isEmpty()) lote.set(dia, datos, SetOptions.merge());
        return lote.commit();
    }

    public static Task<Void> guardarEventos(
            String uid, String fecha, String id, Map<String, Object> datos) {
        WriteBatch lote = FirebaseFirestore.getInstance().batch();
        DocumentReference dia = dia(uid, fecha);
        lote.set(dia, datos, SetOptions.merge());
        if (!id.isEmpty())
            lote.set(dia.collection("sesiones").document(id), datos, SetOptions.merge());
        return lote.commit();
    }

    public static Task<Void> eliminarAudio(String uid, String fecha, Map<String, Object> audio) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("audios", FieldValue.arrayRemove(audio));
        Object id = audio.get("sesionId");
        return guardarEventos(uid, fecha, id instanceof String ? (String) id : "", datos);
    }
}
