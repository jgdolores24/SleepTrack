package com.example.sleeptrack;

import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AnalizadorSueno {

    private static final long MINUTO = 60000L;
    private static final long VENTANA_QUIETUD = 20 * MINUTO;
    private static final long MAX_INICIO = 45 * MINUTO;
    private static final int VERSION = 1;

    private AnalizadorSueno() {}

    public static final class Resultado {
        public final Integer minutosDescanso;
        public final Integer minutosParaDormirEstimados;
        public final Integer minutosSuenoPosible;
        public final Integer posiblesInterrupciones;
        public final Integer calidadPercibida;
        public final String detalle;

        private Resultado(
                Integer descanso,
                Integer latencia,
                Integer sueno,
                Integer interrupciones,
                Integer calidad,
                String detalle) {
            minutosDescanso = descanso;
            minutosParaDormirEstimados = latencia;
            minutosSuenoPosible = sueno;
            posiblesInterrupciones = interrupciones;
            calidadPercibida = calidad;
            this.detalle = detalle;
        }

        public Map<String, Object> paraFirestore() {
            Map<String, Object> campos = new HashMap<>();
            campos.put("versionAnalisisSueno", VERSION);
            campos.put("minutosDescanso", minutosDescanso);
            campos.put("minutosParaDormirEstimados", minutosParaDormirEstimados);
            campos.put("minutosSuenoPosible", minutosSuenoPosible);
            campos.put("posiblesInterrupciones", posiblesInterrupciones);
            campos.put("detalleAnalisis", detalle);
            return campos;
        }
    }

    public static Resultado calcular(Map<String, Object> datos) {
        if (datos == null) {
            return new Resultado(null, null, null, null, null, "Sin registro");
        }
        if (datos.get("sesionesFinalizadas") instanceof Map) {
            List<Map<String, Object>> sesiones = ResumenSesiones.registros(datos);
            Resultado principal = calcular(ResumenSesiones.principal(sesiones));
            Integer calidad =
                    entero(FirestoreOrden.lecturaRegistro(datos).get("porcentajeDescanso"));
            if (calidad != null && (calidad < 0 || calidad > 100)) calidad = null;
            return new Resultado(
                    ResumenSesiones.totalMinutos(sesiones),
                    principal.minutosParaDormirEstimados,
                    sesiones.size() == 1 ? principal.minutosSuenoPosible : null,
                    sesiones.size() == 1 ? principal.posiblesInterrupciones : null,
                    calidad,
                    "Total de sesiones finalizadas; conciliación de la sesión más larga. "
                            + principal.detalle);
        }
        datos = FirestoreOrden.lecturaRegistro(datos);

        Long inicio = tiempo(datos.get("inicioDescanso"), null);
        Long fin = tiempo(datos.get("finDescanso"), null);
        Integer calidad = entero(datos.get("porcentajeDescanso"));
        if (calidad != null && (calidad < 0 || calidad > 100)) calidad = null;

        if (inicio == null || fin == null || fin <= inicio || fin - inicio > 18L * 60 * MINUTO) {
            return new Resultado(
                    ResumenSesiones.duracionHistorica(datos),
                    null,
                    null,
                    null,
                    calidad,
                    "Falta cerrar una sesión válida o es un registro histórico sin horarios");
        }

        int descanso = (int) Math.round((fin - inicio) / (double) MINUTO);
        List<Long> actividadTemprana = new ArrayList<>();
        List<Long> activaciones = new ArrayList<>();
        List<?> eventos = lista(datos.get("eventosTelefono"));
        int activacionesSinHora = 0;

        for (Object objeto : eventos) {
            if (!(objeto instanceof Map)) continue;
            Map<?, ?> evento = (Map<?, ?>) objeto;
            String tipo = texto(evento.get("tipo")).toLowerCase(Locale.ROOT);
            if (!esEventoPantalla(tipo)) continue;
            Long momento = momentoEvento(evento, inicio, fin);

            if (esActivacion(tipo)) {
                if (momento == null) activacionesSinHora++;
                else if (momento >= inicio && momento <= fin) activaciones.add(momento);
            }
            if (momento != null
                    && momento >= inicio
                    && momento <= fin
                    && momento - inicio <= MAX_INICIO) {
                actividadTemprana.add(momento);
            }
        }

        boolean huboActividadDelTelefono = !actividadTemprana.isEmpty();

        // Los ruidos intensos y la voz son indicios de actividad, no pruebas de vigilia.
        // Solo sirven para impedir asumir quietud durante la primera parte de la sesión.
        for (Object objeto : lista(datos.get("audios"))) {
            if (!(objeto instanceof Map)) continue;
            Map<?, ?> audio = (Map<?, ?>) objeto;
            String tipo = texto(audio.get("tipo")).toLowerCase(Locale.ROOT);
            if (!("voz_detectada".equals(tipo) || "ruido_fuerte".equals(tipo))) {
                continue;
            }
            Long momento = momentoEvento(audio, inicio, fin);
            if (momento != null
                    && momento >= inicio
                    && momento <= fin
                    && momento - inicio <= MAX_INICIO) {
                actividadTemprana.add(momento);
            }
        }

        Integer latencia = null;
        Integer suenoPosible = null;
        String detalle = "Datos insuficientes para estimar el inicio del sueño";
        Collections.sort(actividadTemprana);
        if (descanso >= 45 && huboActividadDelTelefono) {
            for (int i = 0; i < actividadTemprana.size(); i++) {
                long evento = actividadTemprana.get(i);
                long siguiente =
                        i + 1 < actividadTemprana.size() ? actividadTemprana.get(i + 1) : fin;
                if (siguiente - evento >= VENTANA_QUIETUD && fin - evento >= VENTANA_QUIETUD) {
                    // Heurística EXPLORATORIA: 10 min tras el último evento inicial,
                    // siempre que después haya al menos 20 min sin nuevos eventos.
                    int calculo =
                            (int) Math.round((evento - inicio + 10 * MINUTO) / (double) MINUTO);
                    if (calculo >= 0 && calculo < descanso) {
                        latencia = calculo;
                        suenoPosible = descanso - calculo;
                        detalle =
                                "Estimación experimental por actividad inicial y 20 min sin"
                                    + " eventos";
                    }
                    break;
                }
            }
        }

        Integer interrupciones = null;
        if (!activaciones.isEmpty()) {
            long referencia = inicio + (latencia == null ? 0 : latencia * MINUTO);
            int cantidad = 0;
            for (Long activacion : activaciones) {
                if (activacion > referencia) cantidad++;
            }
            interrupciones = cantidad;
        } else {
            Integer total = entero(datos.get("activacionesTelefono"));
            if (total != null && total >= 0) interrupciones = total;
            else if (activacionesSinHora > 0) interrupciones = activacionesSinHora;
            else if (!eventos.isEmpty()) interrupciones = 0;
        }
        return new Resultado(descanso, latencia, suenoPosible, interrupciones, calidad, detalle);
    }

    private static List<?> lista(Object valor) {
        return valor instanceof List ? (List<?>) valor : Collections.emptyList();
    }

    private static Integer entero(Object valor) {
        return valor instanceof Number ? ((Number) valor).intValue() : null;
    }

    private static String texto(Object valor) {
        return valor == null ? "" : String.valueOf(valor);
    }

    private static boolean esEventoPantalla(String tipo) {
        return esActivacion(tipo)
                || "pantalla_apagada".equals(tipo)
                || "screen_off".equals(tipo)
                || "bloqueo".equals(tipo)
                || "screen_locked".equals(tipo);
    }

    private static boolean esActivacion(String tipo) {
        return "pantalla_encendida".equals(tipo)
                || "screen_on".equals(tipo)
                || "desbloqueo".equals(tipo)
                || "unlock".equals(tipo);
    }

    private static Long momentoEvento(Map<?, ?> evento, Long inicio, Long fin) {
        for (String campo : new String[] {"timestamp", "tiempo", "momento", "fechaHora", "fecha"}) {
            Long valor = tiempo(evento.get(campo), null);
            if (valor != null) return valor;
        }
        Object hora = evento.get("hora");
        if (!(hora instanceof String) || inicio == null || fin == null) return null;
        String texto = ((String) hora).trim();
        if (!texto.matches("\\d{1,2}:\\d{2}(:\\d{2})?")) return null;
        String[] partes = texto.split(":");
        int h = Integer.parseInt(partes[0]);
        int m = Integer.parseInt(partes[1]);
        int s = partes.length == 3 ? Integer.parseInt(partes[2]) : 0;
        if (h > 23 || m > 59 || s > 59) return null;
        Calendar dia = Calendar.getInstance();
        dia.setTimeInMillis(inicio);
        dia.set(Calendar.HOUR_OF_DAY, h);
        dia.set(Calendar.MINUTE, m);
        dia.set(Calendar.SECOND, s);
        dia.set(Calendar.MILLISECOND, 0);
        long base = dia.getTimeInMillis();
        for (int ajuste = -1; ajuste <= 2; ajuste++) {
            Calendar candidato = (Calendar) dia.clone();
            candidato.add(Calendar.DAY_OF_MONTH, ajuste);
            long t = candidato.getTimeInMillis();
            if (t >= inicio && t <= fin) return t;
        }
        return null;
    }

    private static Long tiempo(Object valor, Long defecto) {
        if (valor instanceof Timestamp) return ((Timestamp) valor).toDate().getTime();
        if (valor instanceof Date) return ((Date) valor).getTime();
        if (valor instanceof Number) {
            long n = ((Number) valor).longValue();
            // Aceptar tanto epoch seconds como epoch milliseconds.
            return n > 100000000000L ? n : n > 1000000000L ? n * 1000L : defecto;
        }
        if (valor instanceof String) {
            try {
                SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                f.setLenient(false);
                Date d = f.parse((String) valor);
                if (d != null) return d.getTime();
            } catch (Exception ignored) {
            }
        }
        return defecto;
    }
}
