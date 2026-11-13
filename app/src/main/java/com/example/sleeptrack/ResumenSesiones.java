package com.example.sleeptrack;

import com.example.sleeptrack.modelo.IntervaloDescansoBean;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ResumenSesiones {
    private ResumenSesiones() {}

    public static List<Map<String, Object>> registros(Map<String, Object> dia) {
        List<Map<String, Object>> lista = new ArrayList<>();
        if (dia == null || dia.isEmpty()) return lista;
        Map<String, Object> anterior = new HashMap<>(dia);
        anterior.remove("sesionesFinalizadas");
        // La sesión histórica permanece intacta y se cuenta una sola vez.
        if (intervalo(anterior) != null || duracionHistorica(anterior) != null) lista.add(anterior);
        Object indice = dia.get("sesionesFinalizadas");
        if (indice instanceof Map) {
            for (Map.Entry<?, ?> item : ((Map<?, ?>) indice).entrySet()) {
                if (!(item.getValue() instanceof Map)) continue;
                Map<String, Object> registro = new HashMap<>();
                registro.put("sesion", item.getValue());
                registro.put("eventosTelefono", filtrar(dia.get("eventosTelefono"), item.getKey()));
                registro.put("audios", filtrar(dia.get("audios"), item.getKey()));
                if (intervalo(registro) != null) lista.add(registro);
            }
        }
        return lista;
    }

    private static List<Object> filtrar(Object eventos, Object id) {
        List<Object> lista = new ArrayList<>();
        if (eventos instanceof List)
            for (Object evento : (List<?>) eventos) {
                if (evento instanceof Map && id.equals(((Map<?, ?>) evento).get("sesionId")))
                    lista.add(evento);
            }
        return lista;
    }

    public static long[] intervalo(Map<String, Object> registro) {
        Map<String, Object> vista = FirestoreOrden.lecturaRegistro(registro);
        if ("cancelada".equals(vista.get("motivoFinDescanso"))) return null;
        long inicio = FirestoreOrden.milisegundos(vista.get("inicioDescanso"));
        long fin = FirestoreOrden.milisegundos(vista.get("finDescanso"));
        IntervaloDescansoBean intervalo = new IntervaloDescansoBean(inicio, fin);
        if (!intervalo.esValido()) return null;
        return new long[] {intervalo.getInicio(), intervalo.getFin()};
    }

    public static Integer duracionHistorica(Map<String, Object> registro) {
        Map<String, Object> vista = FirestoreOrden.lecturaRegistro(registro);
        if (Boolean.TRUE.equals(vista.get("sesionDescansoActiva"))
                || "cancelada".equals(vista.get("motivoFinDescanso"))
                || FirestoreOrden.milisegundos(vista.get("inicioDescanso")) > 0
                || FirestoreOrden.milisegundos(vista.get("finDescanso")) > 0) return null;
        Object valor = vista.get("minutosDescanso");
        if (!(valor instanceof Number)) valor = vista.get("minutosDormidos");
        if (!(valor instanceof Number)) return null;
        int minutos = ((Number) valor).intValue();
        return minutos >= 0 && minutos <= 1080 ? minutos : null;
    }

    public static Integer totalMinutos(List<Map<String, Object>> registros) {
        List<long[]> intervalos = new ArrayList<>();
        int historico = 0;
        boolean tieneHistorico = false;
        for (Map<String, Object> r : registros) {
            long[] intervalo = intervalo(r);
            if (intervalo != null) intervalos.add(intervalo);
            else if (duracionHistorica(r) != null) {
                historico += duracionHistorica(r);
                tieneHistorico = true;
            }
        }
        if (intervalos.isEmpty()) return tieneHistorico ? historico : null;
        intervalos.sort(Comparator.comparingLong(i -> i[0]));
        long inicio = intervalos.get(0)[0], fin = intervalos.get(0)[1], total = 0;
        for (int i = 1; i < intervalos.size(); i++) {
            long[] siguiente = intervalos.get(i);
            if (siguiente[0] <= fin) fin = Math.max(fin, siguiente[1]);
            else {
                total += fin - inicio;
                inicio = siguiente[0];
                fin = siguiente[1];
            }
        }
        return historico + (int) Math.round((total + fin - inicio) / 60000.0);
    }

    public static Map<String, Object> principal(List<Map<String, Object>> registros) {
        Map<String, Object> principal = null;
        long mayor = -1;
        for (Map<String, Object> r : registros) {
            long[] i = intervalo(r);
            if (i != null && i[1] - i[0] > mayor) {
                mayor = i[1] - i[0];
                principal = r;
            }
        }
        return principal;
    }

    public static Long inicioPrincipal(Map<String, Object> dia) {
        Map<String, Object> r = principal(registros(dia));
        return r == null ? null : intervalo(r)[0];
    }
}
