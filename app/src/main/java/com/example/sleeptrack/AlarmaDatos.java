package com.example.sleeptrack;

import com.example.sleeptrack.modelo.ConfiguracionAlarmaBean;
import com.google.firebase.firestore.FieldValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Formato canónico v5. Cada configuración se guarda en un solo lugar. */
public final class AlarmaDatos {
    private static final String[] DIAS = {
        "", "DOMINGO", "LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES", "SABADO"
    };
    private AlarmaDatos() { }

    public static String tipoProgramacion(int mascara) {
        return mascara == 0 ? "UNA_VEZ" : "SEMANAL";
    }

    public static List<String> dias(int mascara) {
        List<String> resultado = new ArrayList<>();
        for (int dia = 1; dia <= 7; dia++) {
            if ((mascara & (1 << dia)) != 0) resultado.add(DIAS[dia]);
        }
        return resultado;
    }

    public static String estado(boolean activa, boolean sonando, boolean posponiendo,
                                boolean programada, boolean fallo) {
        if (sonando) return "SONANDO";
        if (posponiendo && programada) return "POSPUESTA";
        if (fallo) return "ERROR_PROGRAMACION";
        return activa && programada ? "PROGRAMADA" : "INACTIVA";
    }

    public static Map<String, Object> convertir(ConfiguracionAlarmaBean bean) {
        boolean repetida = bean.getDiasMascara() != 0;
        boolean porDuracion = !repetida && "DURACION".equals(bean.getModoDespertar());
        Map<String, Object> datos = new HashMap<>();
        datos.put("versionEsquema", 5);

        Map<String, Object> despertador = new HashMap<>();
        despertador.put("nombre", bean.getNombre());
        despertador.put("modo", porDuracion ? "DURACION" : "HORA");
        despertador.put("horaDespertar", porDuracion ? null : bean.getHoraDespertar());
        despertador.put("duracionMinutos", porDuracion ? bean.getDuracionMinutos() : null);
        datos.put("despertador", despertador);

        Map<String, Object> repeticion = new HashMap<>();
        repeticion.put("activa", repetida);
        int laboral = (1 << java.util.Calendar.MONDAY)
                | (1 << java.util.Calendar.TUESDAY)
                | (1 << java.util.Calendar.WEDNESDAY)
                | (1 << java.util.Calendar.THURSDAY)
                | (1 << java.util.Calendar.FRIDAY);
        repeticion.put("tipo", !repetida ? "UNA_VEZ"
                : bean.getDiasMascara() == laboral ? "LUNES_A_VIERNES" : "PERSONALIZADA");
        repeticion.put("dias", dias(bean.getDiasMascara()));
        repeticion.put("diasMascara", bean.getDiasMascara());
        repeticion.put("criterioDias", "DESPERTAR");
        datos.put("repeticion", repeticion);

        Map<String, Object> programado = new HashMap<>();
        programado.put("activo", repetida);
        programado.put("horaInicio", repetida ? bean.getHoraInicioMonitoreo() : null);
        programado.put("terminaConAlarma", true);
        datos.put("monitoreoProgramado", programado);

        Map<String, Object> sensores = new HashMap<>();
        sensores.put("telefonoHabilitado", bean.isMonitoreoTelefono());
        sensores.put("sonidosHabilitados", bean.isDeteccionSonidos());
        datos.put("monitoreo", sensores);
        datos.put("actualizadoEn", FieldValue.serverTimestamp());
        return datos;
    }

    /** Lee v5 o documentos heredados v2/v4 sin alterar el contenido de Firestore. */
    public static ConfiguracionAlarmaBean leer(Map<String, Object> origen) {
        Map<String, Object> datos = FirestoreOrden.lecturaAlarma(origen);
        Map<String, Object> despertador = mapa(datos.get("despertador"));
        Map<String, Object> repeticion = mapa(datos.get("repeticion"));
        Map<String, Object> programado = mapa(datos.get("monitoreoProgramado"));
        Map<String, Object> sensores = mapa(datos.get("monitoreo"));
        ConfiguracionAlarmaBean bean = new ConfiguracionAlarmaBean();
        bean.setNombre(cadena(despertador.get("nombre"), cadena(datos.get("nombre"), "Mi alarma")));
        bean.setHoraDespertar(cadena(despertador.get("horaDespertar"),
                cadena(datos.get("hora"), "07:00")));
        bean.setHoraInicioMonitoreo(cadena(programado.get("horaInicio"),
                cadena(datos.get("horaInicioMonitoreo"), "22:00")));
        int mascara = numero(repeticion.get("diasMascara"), numero(datos.get("diasSemana"), 0));
        bean.setDiasMascara(mascara);
        bean.setDuracionMinutos(numero(despertador.get("duracionMinutos"),
                numero(datos.get("duracionMinutos"), 30)));
        String modo = cadena(despertador.get("modo"),
                Boolean.TRUE.equals(datos.get("modoDuracion")) ? "DURACION" : "HORA");
        bean.setModoDespertar(mascara == 0 ? modo : "HORA");
        bean.setTipoProgramacion(tipoProgramacion(mascara));
        bean.setMonitoreoTelefono(booleano(sensores, "telefonoHabilitado", "telefono",
                datos.get("monitoreoTelefono")));
        bean.setDeteccionSonidos(booleano(sensores, "sonidosHabilitados", "sonidos",
                datos.get("grabacionSonido")));
        return bean;
    }

    private static boolean booleano(Map<String, Object> mapa, String nuevo, String viejo,
                                    Object heredado) {
        Object valor = mapa.containsKey(nuevo) ? mapa.get(nuevo)
                : mapa.containsKey(viejo) ? mapa.get(viejo) : heredado;
        return Boolean.TRUE.equals(valor);
    }
    private static String cadena(Object valor, String defecto) {
        return valor instanceof String && !((String) valor).trim().isEmpty()
                ? (String) valor : defecto;
    }
    private static int numero(Object valor, int defecto) {
        return valor instanceof Number ? ((Number) valor).intValue() : defecto;
    }
    private static Map<String, Object> mapa(Object valor) {
        if (!(valor instanceof Map)) return new HashMap<>();
        Map<String, Object> resultado = new HashMap<>();
        Map<?, ?> original = (Map<?, ?>) valor;
        for (Map.Entry<?, ?> item : original.entrySet()) {
            if (item.getKey() instanceof String) resultado.put((String) item.getKey(), item.getValue());
        }
        return resultado;
    }
}
