package com.example.sleeptrack;

import com.example.sleeptrack.modelo.EventoActividadBean;
import com.google.firebase.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Adapta actividades antiguas a una vista uniforme sin modificar sus campos originales. */
public final class RepositorioActividades {
    private static final String[][] TIPOS = {
        {"Consumo de Alcohol", "ALCOHOL"},
        {"Consumo de Bebidas", "BEBIDA"},
        {"Consumo de Suplementos", "SUPLEMENTO"},
        {"Consumo de Cigarrillos", "CIGARRILLO"},
        {"Molestias actuales", "MOLESTIA"}
    };

    private RepositorioActividades() { }

    public static List<Map<String, Object>> normalizar(String fecha, Map<String, Object> datos) {
        List<Map<String, Object>> resultado = new ArrayList<>();
        if (datos == null) return resultado;
        for (String[] categoria : TIPOS) {
            Object eventos = datos.get(categoria[0]);
            if (!(eventos instanceof List)) continue;
            for (Object elemento : (List<?>) eventos) {
                if (!(elemento instanceof Map)) continue;
                Map<?, ?> evento = (Map<?, ?>) elemento;
                EventoActividadBean bean = new EventoActividadBean();
                bean.setTipoActividad(categoria[1]);
                bean.setNombre(cadena(evento.get("nombre")));
                bean.setFechaLocal(fecha);
                bean.setHoraLocal(cadena(evento.get("hora")));
                bean.setCantidad(evento.get("cantidad") instanceof Number
                        ? ((Number) evento.get("cantidad")).intValue() : 1);
                bean.setUnidad("registro");
                bean.setIntensidad(evento.get("intensidad") instanceof Number
                        ? ((Number) evento.get("intensidad")).intValue() : null);
                bean.setOrigen("REGISTRO_USUARIO");
                resultado.add(convertir(bean));
            }
        }
        if (Boolean.TRUE.equals(datos.get("trabajoNocturno"))) {
            Map<String, Object> trabajo = new HashMap<>();
            trabajo.put("tipoActividad", "TRABAJO_NOCTURNO");
            trabajo.put("nombre", "Trabajo nocturno");
            trabajo.put("fechaLocal", fecha);
            trabajo.put("horaLocal", cadena(datos.get("horaInicioTrabajo")));
            trabajo.put("fechaHora", fechaHora(fecha, cadena(datos.get("horaInicioTrabajo"))));
            trabajo.put("horaFinTrabajo", datos.get("horaFinTrabajo"));
            trabajo.put("origen", "REGISTRO_USUARIO");
            resultado.add(trabajo);
        }
        return resultado;
    }

    public static Map<String, Object> convertir(EventoActividadBean bean) {
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("tipoActividad", bean.getTipoActividad());
        resultado.put("nombre", bean.getNombre());
        resultado.put("cantidad", bean.getCantidad());
        resultado.put("unidad", bean.getUnidad());
        resultado.put("intensidad", bean.getIntensidad());
        resultado.put("fechaLocal", bean.getFechaLocal());
        resultado.put("horaLocal", bean.getHoraLocal());
        resultado.put("fechaHora", fechaHora(bean.getFechaLocal(), bean.getHoraLocal()));
        resultado.put("origen", bean.getOrigen());
        return resultado;
    }

    private static String cadena(Object valor) {
        return valor == null ? "" : String.valueOf(valor);
    }

    private static Timestamp fechaHora(String dia, String hora) {
        if (dia == null || hora == null || !dia.matches("\\d{4}-\\d{2}-\\d{2}")
                || !hora.matches("(?:[01]?\\d|2[0-3]):[0-5]\\d")) return null;
        try {
            SimpleDateFormat formato = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            formato.setLenient(false);
            Date fecha = formato.parse(dia + " " + hora);
            return fecha == null ? null : new Timestamp(fecha);
        } catch (ParseException error) {
            return null;
        }
    }
}
