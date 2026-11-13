package com.example.sleeptrack;

import com.example.sleeptrack.modelo.DiaDescansoBean;
import com.example.sleeptrack.modelo.HallazgoPatronBean;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;
import java.util.Map;
import java.util.Set;

/** Analiza asociaciones descriptivas; no estima causalidad ni efectos de tratamientos. */
public final class AnalisisPatrones {
    private static final long HORA = 3600000L;
    private static final long DIA = 24 * HORA;

    private AnalisisPatrones() {}

    private static final class Sesion {
        final DiaDescansoBean dia;
        final Set<String> habitos;
        Sesion(DiaDescansoBean dia, Set<String> habitos) {
            this.dia = dia;
            this.habitos = habitos;
        }
    }

    private static final class Comparacion {
        final String habito;
        final int expuestos;
        final int controles;
        final int diferencia;
        Comparacion(String habito, int expuestos, int controles, int diferencia) {
            this.habito = habito;
            this.expuestos = expuestos;
            this.controles = controles;
            this.diferencia = diferencia;
        }
    }

    public static List<HallazgoPatronBean> analizar(
            List<DiaDescansoBean> dias,
            Map<String, Map<String, Object>> actividades,
            int periodo,
            boolean consultaActividadesOk) {
        return analizar(dias, actividades, periodo, consultaActividadesOk, 0);
    }

    public static List<HallazgoPatronBean> analizar(
            List<DiaDescansoBean> dias,
            Map<String, Map<String, Object>> actividades,
            int periodo,
            boolean consultaActividadesOk,
            int semillaUsuario) {
        List<HallazgoPatronBean> salida = new ArrayList<>();
        List<Sesion> sesiones = new ArrayList<>();
        for (DiaDescansoBean dia : dias) {
            if (dia.getInicioPrincipal() == null || dia.getFinPrincipal() == null
                    || dia.getMinutosPrincipales() == null || dia.getMinutosPrincipales() < 180) continue;
            sesiones.add(new Sesion(dia,
                    consultaActividadesOk ? habitosPrevios(dia.getInicioPrincipal(), actividades)
                            : Collections.emptySet()));
        }

        if (!sesiones.isEmpty()) {
            // El feedback de la noche sigue disponible incluso si falla la consulta
            // separada de actividades. No asumimos que no hubo hábitos registrados.
            salida.addAll(detectarMolestiasAlDespertar(sesiones,
                    consultaActividadesOk ? actividades : Collections.emptyMap()));
        }
        if (consultaActividadesOk && !sesiones.isEmpty()) {
            Map<String, Integer> frecuencia = new LinkedHashMap<>();
            for (Sesion sesion : sesiones) {
                for (String habito : sesion.habitos) {
                    frecuencia.put(habito, frecuencia.getOrDefault(habito, 0) + 1);
                }
            }
            List<Comparacion> diferencias = new ArrayList<>();
            for (Map.Entry<String, Integer> entrada : frecuencia.entrySet()) {
                int expuestos = entrada.getValue();
                int controles = sesiones.size() - expuestos;
                // Minimo de noches con y sin la actividad para evitar comparaciones
                // de una sola observacion (y nunca usar siestas como controles).
                if (expuestos < 2 || controles < 3) continue;
                long con = 0, sin = 0;
                for (Sesion sesion : sesiones) {
                    if (sesion.habitos.contains(entrada.getKey())) {
                        con += sesion.dia.getMinutosPrincipales();
                    } else {
                        sin += sesion.dia.getMinutosPrincipales();
                    }
                }
                int diferencia = (int) Math.round(con / (double) expuestos
                        - sin / (double) controles);
                // Para solo dos exposiciones exigimos diferencia mayor.
                if (Math.abs(diferencia) >= (expuestos == 2 ? 90 : 60)) {
                    diferencias.add(new Comparacion(
                            entrada.getKey(), expuestos, controles, diferencia));
                }
            }
            diferencias.sort((a, b) -> {
                int comparacion = Integer.compare(Math.abs(b.diferencia), Math.abs(a.diferencia));
                if (comparacion != 0) return comparacion;
                return Integer.compare(b.expuestos, a.expuestos);
            });
            int limite = 5;
            for (Comparacion c : diferencias) {
                if (limite-- <= 0) break;
                String diferencia = duracion(Math.abs(c.diferencia));
                String direccion = c.diferencia >= 0 ? "más" : "menos";
                String nombre = c.habito;
                boolean suplemento = nombre.startsWith("Suplemento: ");
                String titulo = nombre + " · " + diferencia + " " + direccion;
                String resumen = c.expuestos + " noches con registro · comparación disponible";
                String detalle = "En " + c.expuestos + " noches con " + nombre.toLowerCase(Locale.ROOT)
                        + " la sesión nocturna principal duró en promedio " + diferencia
                        + " " + direccion + " que en " + c.controles
                        + " noches sin ese registro, dentro de los últimos " + periodo
                        + " días.\n\n"
                        + (c.expuestos < 3 ? "Coincidencia inicial: hay pocos casos. "
                                : "Coincidencia repetida en tus registros. ")
                        + "Es una comparación descriptiva, no demuestra que la actividad "
                        + "haya causado el cambio. Los valores son duración del monitoreo, "
                        + "no horas de sueño confirmadas.\n\n"
                        + (suplemento
                                ? "No empieces, aumentes o suspendas suplementos por esta "
                                  + "comparación; consúltalo con un profesional de salud."
                                : consejoHabito(nombre));
                salida.add(new HallazgoPatronBean(categoria(nombre), titulo, resumen, detalle));
            }
        }

        // Un hallazgo personal se muestra solo si existe evidencia en las sesiones.
        // Si no hay ninguno, se muestran exactamente dos consejos generales.
        // Una consulta fallida no se interpreta como ausencia de actividades.
        if (salida.isEmpty()) {
            salida.addAll(consejosParaDia(diaLocalActual(), semillaUsuario));
        }
        return salida;
    }

    private static long diaLocalActual() {
        Calendar local = Calendar.getInstance();
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH),
                local.get(Calendar.DAY_OF_MONTH));
        return utc.getTimeInMillis() / DIA;
    }

    // Mantiene los mismos consejos durante un día; cambia al siguiente sin repeticiones
    // hasta recorrer la lista. Orden aleatorio pero estable por usuario.
    static List<HallazgoPatronBean> consejosParaDia(long dia, int semillaUsuario) {
        List<HallazgoPatronBean> opciones = new ArrayList<>();
        opciones.add(new HallazgoPatronBean("sueno", "Horarios · consejo general",
                "Procura dormir y despertar a horas similares.",
                "Mantener un horario regular de acostarse y levantarse, incluso los fines "
                + "de semana, puede facilitar una rutina de sueño estable. Si tienes "
                + "dificultades para dormir, empieza por fijar la hora de despertar."));
        opciones.add(new HallazgoPatronBean("cafe", "Cafeína · consejo general",
                "Evita bebidas estimulantes durante la tarde.",
                "La cafeína puede dificultar que concilies el sueño y sus efectos duran "
                + "varias horas. Si notas dificultad para dormir, limita café, té con "
                + "cafeína y bebidas energéticas especialmente por la tarde y noche."));
        opciones.add(new HallazgoPatronBean("alcohol", "Alcohol · consejo general",
                "Evita el alcohol cerca de la hora de dormir.",
                "Aunque el alcohol puede producir somnolencia, puede fragmentar el descanso "
                + "y provocar despertares. No se recomienda como ayuda para dormir."));
        opciones.add(new HallazgoPatronBean("pantallas", "Pantallas · consejo general",
                "Reduce pantallas y luz intensa antes de acostarte.",
                "Una rutina tranquila y con poca luz antes de dormir puede ayudar a "
                + "prepararte para el descanso. Intenta dejar el teléfono y otras "
                + "pantallas de lado al acercarse la hora de acostarte."));
        opciones.add(new HallazgoPatronBean("cigarrillos", "Nicotina · consejo general",
                "Evita la nicotina antes de dormir.",
                "La nicotina es estimulante y puede interferir con el descanso. "
                + "Evitar el tabaco cerca de acostarte favorece mejores hábitos de sueño."));
        opciones.add(new HallazgoPatronBean("ambiente", "Dormitorio · consejo general",
                "Prepara un lugar oscuro, tranquilo y fresco.",
                "Reduce el ruido, la luz y el calor excesivo en la habitación. "
                + "Un ambiente cómodo, oscuro y fresco puede favorecer el descanso."));
        opciones.add(new HallazgoPatronBean("sueno", "Siestas · consejo general",
                "Si una siesta afecta tu noche, hazla breve.",
                "Si te cuesta conciliar el sueño por la noche, limita las siestas "
                + "y procura hacerlas temprano. Para adultos, las siestas de unos "
                + "20 minutos o menos suelen recomendarse cuando afectan el sueño nocturno."));
        opciones.add(new HallazgoPatronBean("sueno", "Cena · consejo general",
                "Evita comidas demasiado abundantes al acostarte.",
                "Las cenas muy abundantes cerca de la hora de dormir pueden causar "
                + "incomodidad. Si tienes hambre, una merienda ligera puede ser adecuada. "
                + "No hay un alimento que garantice dormir mejor."));
        opciones.add(new HallazgoPatronBean("sueno", "Luz natural · consejo general",
                "Busca exposición a luz natural durante el día.",
                "Pasar tiempo al aire libre y recibir luz natural puede ayudar a "
                + "regular el ciclo diario de sueño y vigilia. Mantén una actividad "
                + "física apropiada durante el día."));
        opciones.add(new HallazgoPatronBean("sueno", "Relajación · consejo general",
                "Reserva unos minutos para bajar el ritmo.",
                "Antes de acostarte prueba una actividad calmada, como leer o "
                + "escuchar música suave. Si el insomnio persiste, consulta por "
                + "tratamientos validados, especialmente terapia cognitivo-conductual "
                + "para el insomnio (TCC-I)."));

        Collections.shuffle(opciones, new Random(918371L + 1000003L * semillaUsuario));
        int cantidad = 2;
        long avance = dia * 2L;
        int inicio = (int) (avance % opciones.size());
        List<HallazgoPatronBean> elegidos = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            elegidos.add(opciones.get((inicio + i) % opciones.size()));
        }
        return elegidos;
    }

    private static String categoria(String nombre) {
        if (nombre.startsWith("Suplemento:")) return "suplementos";
        if (nombre.equals("Alcohol")) return "alcohol";
        if (nombre.startsWith("Bebida:")) return "cafe";
        if (nombre.startsWith("Molestia:")) return "salud";
        if (nombre.equals("Cigarrillos")) return "cigarrillos";
        if (nombre.equals("Trabajo nocturno")) return "trabajo";
        return "actividades";
    }

    private static String consejoHabito(String nombre) {
        if (nombre.equals("Alcohol")) {
            return "El alcohol cerca de la hora de dormir puede fragmentar el sueño. "
                    + "Reducirlo por la noche es una recomendación general respaldada por guías.";
        }
        if (nombre.startsWith("Bebida:")) {
            if (normalizar(nombre).contains("descafeinado")) {
                return "El café descafeinado contiene menos cafeína, pero no siempre cero. "
                        + "Esta comparación por sí sola no muestra una causa del cambio.";
            }
            return "Las bebidas con cafeína pueden interferir con el sueño, especialmente "
                    + "durante la tarde y noche. No se puede atribuir aquí un efecto individual.";
        }
        if (nombre.startsWith("Molestia:")) {
            return "Anota también la intensidad y el horario de la molestia; "
                    + "consulta si es frecuente o interfiere con tus actividades.";
        }
        return "Conviene registrar horarios y otros cambios de rutina para comparar "
                + "varias noches antes de sacar conclusiones.";
    }

    private static List<HallazgoPatronBean> detectarMolestiasAlDespertar(
            List<Sesion> sesiones, Map<String, Map<String, Object>> actividades) {
        List<HallazgoPatronBean> salida = new ArrayList<>();
        DiaDescansoBean ultima = null;
        String sintoma = "";
        int intensidad = 0;
        for (Sesion sesion : sesiones) {
            DiaDescansoBean dia = sesion.dia;
            if (dia.getFinPrincipal() == null || dia.getMinutosPrincipales() == null) continue;
            // Usar la evaluacion subjetiva guardada para la noche principal.
            for (Map<?, ?> evento : dia.getMolestiasAlDespertar()) {
                String nombre = texto(evento.get("nombre"));
                Integer nivel = entero(evento.get("intensidadDespues"));
                if (nivel != null && nivel >= 7 && esDolorCabeza(nombre)
                        && (ultima == null || dia.getFinPrincipal() > ultima.getFinPrincipal())) {
                    ultima = dia;
                    sintoma = nombre;
                    intensidad = nivel;
                }
            }
            // Si la molestia se registra despues de despertar, pertenece al dia del despertar.
            String fecha = fecha(dia.getFinPrincipal());
            Map<String, Object> actividad = actividades.get(fecha);
            for (Map<?, ?> evento : eventos(actividad, "Molestias actuales")) {
                String nombre = texto(evento.get("nombre"));
                Integer nivel = entero(evento.get("intensidad"));
                Long momento = instante(fecha, texto(evento.get("hora")));
                if (nivel != null && nivel >= 7 && esDolorCabeza(nombre)
                        && momento != null && momento >= dia.getFinPrincipal()
                        && momento <= dia.getFinPrincipal() + 3 * HORA
                        && (ultima == null || dia.getFinPrincipal() > ultima.getFinPrincipal())) {
                    ultima = dia;
                    sintoma = nombre;
                    intensidad = nivel;
                }
            }
        }
        if (ultima == null) return salida;
        String duracion = duracion(ultima.getMinutosPrincipales());
        String nombreFinal = sintoma;
        String titulo = nombreFinal + " intensa al despertar";
        String resumen = duracion + " de sesión · intensidad " + intensidad + "/10";
        String detalle = "Al despertar de la sesión nocturna del " + ultima.getFecha()
                + " registraste " + nombreFinal.toLowerCase(Locale.ROOT)
                + " de intensidad " + intensidad + "/10. La sesión principal duró "
                + duracion + ".\n\n"
                + (ultima.getMinutosPrincipales() < 420
                ? "La falta de sueño puede desencadenar migrañas en algunas personas, "
                  + "pero una coincidencia no prueba que sea la causa de tu molestia. "
                : "La migraña y el sueño pueden relacionarse en ambas direcciones; "
                  + "este registro no identifica una causa. ")
                + "La duración del monitoreo no mide sueño confirmado. Si el dolor es "
                + "intenso, nuevo o se repite, busca valoración médica. Si es súbito y "
                + "extremadamente intenso, o aparece con síntomas neurológicos, "
                + "busca atención urgente.";
        salida.add(new HallazgoPatronBean("salud", titulo, resumen, detalle));
        return salida;
    }

    private static boolean esDolorCabeza(String nombre) {
        String n = normalizar(nombre);
        return n.contains("migrana") || n.contains("dolor de cabeza") || n.contains("cefalea");
    }

    private static Set<String> habitosPrevios(long inicio,
            Map<String, Map<String, Object>> actividades) {
        Set<String> resultado = new HashSet<>();
        Calendar dia = Calendar.getInstance();
        dia.setTimeInMillis(inicio);
        String actual = fecha(dia.getTimeInMillis());
        dia.add(Calendar.DAY_OF_YEAR, -1);
        String anterior = fecha(dia.getTimeInMillis());
        for (String fecha : new String[]{anterior, actual}) {
            Map<String, Object> actividad = actividades.get(fecha);
            if (actividad == null) continue;
            agregarEventos(resultado, actividad, fecha, inicio,
                    "Consumo de Alcohol", "Alcohol", false);
            agregarEventos(resultado, actividad, fecha, inicio,
                    "Consumo de Suplementos", "Suplemento: ", true);
            agregarEventos(resultado, actividad, fecha, inicio,
                    "Consumo de Bebidas", "Bebida: ", true);
            agregarEventos(resultado, actividad, fecha, inicio,
                    "Consumo de Cigarrillos", "Cigarrillos", false);
            agregarEventos(resultado, actividad, fecha, inicio,
                    "Molestias actuales", "Molestia: ", true);
            // Registros historicos sin hora: solo asignacion tentativa al dia
            // del inicio y nunca a un dia posterior al inicio del descanso.
            if (actual.equals(fecha)) {
                if (Boolean.TRUE.equals(actividad.get("alcohol"))
                        && eventos(actividad, "Consumo de Alcohol").isEmpty()) {
                    resultado.add("Alcohol");
                }
                if (Boolean.TRUE.equals(actividad.get("trabajoNocturno"))) {
                    resultado.add("Trabajo nocturno");
                }
            }
        }
        return resultado;
    }

    private static void agregarEventos(Set<String> resultado,
            Map<String, Object> actividad, String fecha, long inicio,
            String campo, String prefijo, boolean porNombre) {
        for (Map<?, ?> evento : eventos(actividad, campo)) {
            String nombre = texto(evento.get("nombre")).trim();
            if (porNombre && nombre.isEmpty()) continue;
            Long instante = instante(fecha, texto(evento.get("hora")));
            // La actividad debe ser anterior al inicio. Si falta la hora,
            // solamente se toma la fecha donde comenzo la noche.
            if (instante != null) {
                if (instante > inicio || inicio - instante > DIA) continue;
            } else if (!fecha.equals(fecha(inicio))) {
                continue;
            }
            String etiqueta = porNombre ? prefijo + nombre : prefijo;
            resultado.add(etiqueta);
        }
    }

    private static List<Map<?, ?>> eventos(Map<String, Object> actividad, String campo) {
        List<Map<?, ?>> salida = new ArrayList<>();
        if (actividad == null) return salida;
        Object lista = actividad.get(campo);
        if (!(lista instanceof List)) return salida;
        for (Object objeto : (List<?>) lista) {
            if (objeto instanceof Map) salida.add((Map<?, ?>) objeto);
        }
        return salida;
    }

    private static Long instante(String fecha, String hora) {
        if (hora == null || !hora.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")) return null;
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        fmt.setLenient(false);
        try {
            Date d = fmt.parse(fecha + " " + hora);
            return d == null ? null : d.getTime();
        } catch (Exception error) {
            return null;
        }
    }

    private static String fecha(long instante) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(instante));
    }

    private static String texto(Object valor) {
        return valor == null ? "" : String.valueOf(valor);
    }

    private static Integer entero(Object valor) {
        return valor instanceof Number ? ((Number) valor).intValue() : null;
    }

    private static String normalizar(String valor) {
        return Normalizer.normalize(valor.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").trim();
    }

    private static String duracion(int minutos) {
        if (minutos < 60) return minutos + " min";
        int horas = minutos / 60, resto = minutos % 60;
        return resto == 0 ? horas + " h" : horas + " h " + resto + " min";
    }
}
