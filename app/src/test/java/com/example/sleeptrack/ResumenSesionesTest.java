package com.example.sleeptrack;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** Mantiene la suma de sesiones independientes sin duplicar tiempos superpuestos. */
public class ResumenSesionesTest {
    private static final long HORA = 60L * 60 * 1000;

    private static Map<String, Object> sesion(long inicio, long fin) {
        Map<String, Object> sesion = new HashMap<>();
        sesion.put("inicio", new Date(inicio));
        sesion.put("fin", new Date(fin));
        sesion.put("estado", "finalizada");
        Map<String, Object> registro = new HashMap<>();
        registro.put("sesion", sesion);
        return registro;
    }

    @Test public void documentosVaciosNoProvocanFallo() {
        assertTrue(ResumenSesiones.registros(null).isEmpty());
        assertTrue(ResumenSesiones.registros(new HashMap<>()).isEmpty());
    }

    @Test public void agregaNocheYSiestaDelMismoDia() {
        long base = 1800000000000L;
        List<Map<String, Object>> sesiones = Arrays.asList(
                sesion(base, base + 10 * HORA),
                sesion(base + 13 * HORA, base + 14 * HORA));
        assertEquals(Integer.valueOf(660), ResumenSesiones.totalMinutos(sesiones));
    }

    @Test public void evitaContarDosVecesSesionesSolapadas() {
        long base = 1800000000000L;
        List<Map<String, Object>> sesiones = Arrays.asList(
                sesion(base, base + 4 * HORA),
                sesion(base + 3 * HORA, base + 5 * HORA));
        assertEquals(Integer.valueOf(300), ResumenSesiones.totalMinutos(sesiones));
    }

    @Test public void noCuentaSesionesCanceladas() {
        long base = 1800000000000L;
        Map<String, Object> registro = sesion(base, base + HORA);
        ((Map<String, Object>) registro.get("sesion")).put("motivoFinalizacion", "cancelada");
        assertNull(ResumenSesiones.totalMinutos(Arrays.asList(registro)));
    }
}
