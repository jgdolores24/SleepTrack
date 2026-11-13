package com.example.sleeptrack;

import com.google.firebase.Timestamp;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/** Formato Firestore v2, sin cambiar las rutas actuales. */
public final class FirestoreOrden {

    private FirestoreOrden() { }

    public static Map<String, Object> alarma(String nombre, String hora,
            int dias, boolean telefono, boolean sonidos) {
        Map<String, Object> resultado = nuevo();
        Map<String, Object> despertador = new HashMap<>();
        despertador.put("nombre", nombre);
        despertador.put("hora", hora);
        despertador.put("diasSemana", dias);
        resultado.put("despertador", despertador);
        Map<String, Object> monitoreo = new HashMap<>();
        monitoreo.put("telefono", telefono);
        monitoreo.put("sonidos", sonidos);
        resultado.put("monitoreo", monitoreo);
        return resultado;
    }

    public static Map<String, Object> inicioSesion(long inicio) {
        Map<String, Object> resultado = nuevo();
        Map<String, Object> sesion = new HashMap<>();
        sesion.put("inicio", marca(inicio));
        sesion.put("fin", null);
        sesion.put("estado", "activa");
        sesion.put("motivoFinalizacion", null);
        resultado.put("sesion", sesion);
        return resultado;
    }

    public static Map<String, Object> finSesion(long fin, String motivo) {
        Map<String, Object> resultado = nuevo();
        Map<String, Object> sesion = new HashMap<>();
        sesion.put("fin", marca(fin));
        sesion.put("estado", "finalizada");
        sesion.put("motivoFinalizacion", motivo);
        resultado.put("sesion", sesion);
        return resultado;
    }

    public static Map<String, Object> evaluacion(int porcentaje, String nivel) {
        Map<String, Object> resultado = nuevo();
        Map<String, Object> eval = new HashMap<>();
        eval.put("porcentaje", porcentaje);
        eval.put("nivel", nivel);
        eval.put("tipo", "percibida");
        eval.put("fecha", com.google.firebase.firestore.FieldValue.serverTimestamp());
        resultado.put("evaluacion", eval);
        return resultado;
    }

    public static Map<String, Object> analisis(AnalizadorSueno.Resultado r) {
        Map<String, Object> resultado = nuevo();
        Map<String, Object> analisis = new HashMap<>();
        analisis.put("latenciaEstimadaMinutos", r.minutosParaDormirEstimados);
        analisis.put("suenoEstimadoMinutos", r.minutosSuenoPosible);
        analisis.put("posiblesInterrupciones", r.posiblesInterrupciones);
        analisis.put("detalle", r.detalle);
        analisis.put("version", 1);
        resultado.put("analisis", analisis);
        if (r.minutosDescanso != null) {
            Map<String, Object> sesion = new HashMap<>();
            sesion.put("duracionMinutos", r.minutosDescanso);
            resultado.put("sesion", sesion);
        }
        return resultado;
    }

    /** Sincroniza el monitoreo plano que aún escribe MonitoreoService. */
    public static Map<String, Object> monitoreoDesdePlano(Map<String, Object> origen) {
        Map<String, Object> resultado = new HashMap<>();
        if (origen == null) return resultado;
        Map<String, Object> grupo = new HashMap<>();
        if (origen.containsKey("inicioMonitoreo")) {
            Object valor = origen.get("inicioMonitoreo");
            long ms = milisegundos(valor);
            grupo.put("inicio", valor == null || ms <= 0 ? null : marca(ms));
        }
        if (origen.containsKey("finMonitoreo")) {
            Object valor = origen.get("finMonitoreo");
            long ms = milisegundos(valor);
            grupo.put("fin", valor == null || ms <= 0 ? null : marca(ms));
        }
        if (origen.containsKey("origenMonitoreo"))
            grupo.put("origen", origen.get("origenMonitoreo"));
        if (origen.containsKey("activacionesTelefono"))
            grupo.put("activacionesPantalla", origen.get("activacionesTelefono"));
        if (!grupo.isEmpty()) resultado.put("monitoreo", grupo);
        return resultado;
    }

    public static Map<String, Object> lecturaAlarma(Map<String, Object> origen) {
        Map<String, Object> vista = copia(origen);
        Map<String, Object> despertador = grupo(vista, "despertador");
        Map<String, Object> monitoreo = grupo(vista, "monitoreo");
        trasladar(vista, "nombre", despertador, "nombre");
        trasladar(vista, "hora", despertador, "hora");
        trasladar(vista, "diasSemana", despertador, "diasSemana");
        trasladar(vista, "monitoreoTelefono", monitoreo, "telefono");
        trasladar(vista, "grabacionSonido", monitoreo, "sonidos");
        return vista;
    }

    /** Adaptador temporal para AnalizadorSueno y registros históricos. */
    public static Map<String, Object> lecturaRegistro(Map<String, Object> origen) {
        Map<String, Object> vista = copia(origen);
        Map<String, Object> sesion = grupo(vista, "sesion");
        Map<String, Object> analisis = grupo(vista, "analisis");
        Map<String, Object> monitoreo = grupo(vista, "monitoreo");
        Map<String, Object> evaluacion = grupo(vista, "evaluacion");
        trasladarTiempo(vista, "inicioDescanso", sesion, "inicio");
        trasladarTiempo(vista, "finDescanso", sesion, "fin");
        trasladar(vista, "minutosDescanso", sesion, "duracionMinutos");
        trasladar(vista, "motivoFinDescanso", sesion, "motivoFinalizacion");
        if (sesion.containsKey("estado")) {
            vista.put("sesionDescansoActiva", "activa".equals(sesion.get("estado")));
        }
        // Las activaciones se escriben aún por MonitoreoService: sus valores
        // planos más recientes tienen prioridad sobre la copia organizada.
        if (!vista.containsKey("inicioMonitoreo"))
            trasladarTiempo(vista, "inicioMonitoreo", monitoreo, "inicio");
        if (!vista.containsKey("finMonitoreo"))
            trasladarTiempo(vista, "finMonitoreo", monitoreo, "fin");
        if (!vista.containsKey("origenMonitoreo"))
            trasladar(vista, "origenMonitoreo", monitoreo, "origen");
        if (!vista.containsKey("activacionesTelefono"))
            trasladar(vista, "activacionesTelefono", monitoreo, "activacionesPantalla");
        trasladar(vista, "minutosParaDormirEstimados", analisis, "latenciaEstimadaMinutos");
        trasladar(vista, "minutosSuenoPosible", analisis, "suenoEstimadoMinutos");
        trasladar(vista, "posiblesInterrupciones", analisis, "posiblesInterrupciones");
        trasladar(vista, "detalleAnalisis", analisis, "detalle");
        trasladar(vista, "versionAnalisisSueno", analisis, "version");
        trasladar(vista, "porcentajeDescanso", evaluacion, "porcentaje");
        trasladar(vista, "calificacionUsuario", evaluacion, "nivel");
        trasladar(vista, "fechaEvaluacion", evaluacion, "fecha");
        return vista;
    }

    /** Crea sólo los mapas que aún faltan, sin eliminar registros anteriores. */
    public static Map<String, Object> migrarAlarma(Map<String, Object> original) {
        Map<String, Object> cambios = new HashMap<>();
        Map<String, Object> d = new HashMap<>();
        Map<String, Object> m = new HashMap<>();
        faltante(d, "nombre", original, "nombre", "despertador");
        faltante(d, "hora", original, "hora", "despertador");
        faltante(d, "diasSemana", original, "diasSemana", "despertador");
        faltante(m, "telefono", original, "monitoreoTelefono", "monitoreo");
        faltante(m, "sonidos", original, "grabacionSonido", "monitoreo");
        if (!d.isEmpty()) cambios.put("despertador", d);
        if (!m.isEmpty()) cambios.put("monitoreo", m);
        if (!cambios.isEmpty()) cambios.put("versionEsquema", 2);
        return cambios;
    }

    public static Map<String, Object> migrarRegistro(Map<String, Object> original) {
        Map<String, Object> cambios = new HashMap<>();
        Map<String, Object> s = new HashMap<>();
        Map<String, Object> m = new HashMap<>();
        Map<String, Object> a = new HashMap<>();
        Map<String, Object> e = new HashMap<>();
        faltanteTiempo(s, "inicio", original, "inicioDescanso", "sesion");
        faltanteTiempo(s, "fin", original, "finDescanso", "sesion");
        faltante(s, "duracionMinutos", original, "minutosDescanso", "sesion");
        faltante(s, "motivoFinalizacion", original, "motivoFinDescanso", "sesion");
        if (!grupo(original, "sesion").containsKey("estado") &&
                original.get("sesionDescansoActiva") instanceof Boolean) {
            s.put("estado", Boolean.TRUE.equals(original.get("sesionDescansoActiva"))
                    ? "activa" : "finalizada");
        }
        faltanteTiempo(m, "inicio", original, "inicioMonitoreo", "monitoreo");
        faltanteTiempo(m, "fin", original, "finMonitoreo", "monitoreo");
        faltante(m, "origen", original, "origenMonitoreo", "monitoreo");
        faltante(m, "activacionesPantalla", original, "activacionesTelefono", "monitoreo");
        faltante(a, "latenciaEstimadaMinutos", original, "minutosParaDormirEstimados", "analisis");
        faltante(a, "suenoEstimadoMinutos", original, "minutosSuenoPosible", "analisis");
        faltante(a, "posiblesInterrupciones", original, "posiblesInterrupciones", "analisis");
        faltante(a, "detalle", original, "detalleAnalisis", "analisis");
        faltante(a, "version", original, "versionAnalisisSueno", "analisis");
        faltante(e, "porcentaje", original, "porcentajeDescanso", "evaluacion");
        faltante(e, "nivel", original, "calificacionUsuario", "evaluacion");
        faltanteTiempo(e, "fecha", original, "fechaEvaluacion", "evaluacion");
        if (!e.isEmpty() && !grupo(original,"evaluacion").containsKey("tipo")) {
            e.put("tipo", "percibida");
        }
        if (!s.isEmpty()) cambios.put("sesion", s);
        if (!m.isEmpty()) cambios.put("monitoreo", m);
        if (!a.isEmpty()) cambios.put("analisis", a);
        if (!e.isEmpty()) cambios.put("evaluacion", e);
        if (!cambios.isEmpty()) cambios.put("versionEsquema", 2);
        return cambios;
    }

    public static long milisegundos(Object valor) {
        if (valor instanceof Timestamp) return ((Timestamp) valor).toDate().getTime();
        if (valor instanceof Date) return ((Date) valor).getTime();
        if (valor instanceof Number) {
            long numero = ((Number) valor).longValue();
            return numero > 100000000000L ? numero : numero * 1000L;
        }
        return 0L;
    }

    private static Timestamp marca(long milis) { return new Timestamp(new Date(milis)); }
    private static Map<String, Object> nuevo() {
        Map<String, Object> r = new HashMap<>(); r.put("versionEsquema", 2); return r;
    }
    private static Map<String, Object> copia(Map<String, Object> data) {
        return data == null ? new HashMap<>() : new HashMap<>(data);
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> grupo(Map<String, Object> data, String nombre) {
        Object valor = data == null ? null : data.get(nombre);
        return valor instanceof Map ? (Map<String, Object>) valor : new HashMap<>();
    }
    private static void trasladar(Map<String, Object> destino, String clave,
                                   Map<String, Object> origen, String campo) {
        if (origen.containsKey(campo)) destino.put(clave, origen.get(campo));
    }
    private static void trasladarTiempo(Map<String, Object> destino, String clave,
                                         Map<String, Object> origen, String campo) {
        if (origen.containsKey(campo)) {
            Object valor = origen.get(campo);
            destino.put(clave, valor == null ? null : milisegundos(valor));
        }
    }
    private static void faltante(Map<String, Object> destino, String nuevo,
                                 Map<String, Object> original, String anterior,
                                 String grupo) {
        if (original != null && original.containsKey(anterior) &&
                !grupo(original, grupo).containsKey(nuevo)) {
            destino.put(nuevo, original.get(anterior));
        }
    }
    private static void faltanteTiempo(Map<String, Object> destino, String nuevo,
                                       Map<String, Object> original, String anterior,
                                       String grupo) {
        if (original != null && original.containsKey(anterior) &&
                !grupo(original, grupo).containsKey(nuevo)) {
            Object valor = original.get(anterior);
            long ms = milisegundos(valor);
            destino.put(nuevo, valor == null || ms <= 0 ? null : marca(ms));
        }
    }
}
