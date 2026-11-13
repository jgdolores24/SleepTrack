package com.example.sleeptrack;

import static org.junit.Assert.*;

import com.example.sleeptrack.modelo.DiaDescansoBean;
import com.example.sleeptrack.modelo.HallazgoPatronBean;
import com.example.sleeptrack.modelo.IntervaloDescansoBean;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.junit.Test;

public class DescansoLogicaTest {
    private static final TimeZone LIMA = TimeZone.getTimeZone("America/Lima");

    private static long instante(int dia, int hora) {
        Calendar c = Calendar.getInstance(LIMA);
        c.clear();
        c.set(2026, Calendar.NOVEMBER, dia, hora, 0);
        return c.getTimeInMillis();
    }

    @Test public void nocheSeAsociaAlDiaDelDespertar() {
        assertEquals("2026-11-06", FechasDescanso.fechaFin(instante(6, 6), LIMA));
        assertEquals("2026-11-05", FechasDescanso.fechaInicio(instante(5, 20), LIMA));
    }

    @Test public void duracionDeNocheYSiesta() {
        IntervaloDescansoBean noche = new IntervaloDescansoBean(instante(5, 20), instante(6, 6));
        IntervaloDescansoBean siesta = new IntervaloDescansoBean(instante(6, 14), instante(6, 15));
        assertTrue(noche.esValido());
        assertTrue(siesta.esValido());
        assertEquals(660L, (noche.duracionMillis()+siesta.duracionMillis())/60000L);
    }

    @Test public void losBeansSePuedenSerializarMediantePropiedades() {
        DiaDescansoBean dia = new DiaDescansoBean();
        dia.setFecha("2026-11-06");
        dia.setMinutosPrincipales(600);
        assertEquals("2026-11-06", dia.getFecha());
        assertEquals(Integer.valueOf(600), dia.getMinutosPrincipales());
        HallazgoPatronBean hallazgo = new HallazgoPatronBean();
        hallazgo.setTitulo("Ejemplo");
        assertEquals("Ejemplo", hallazgo.getTitulo());
    }

    @Test public void sinDatosHayDosConsejosGenerales() {
        List<HallazgoPatronBean> consejos = AnalisisPatrones.analizar(
                Collections.emptyList(), Collections.emptyMap(), 7, true, 42);
        assertEquals(2, consejos.size());
        assertTrue(consejos.get(0).getTitulo().contains("consejo general"));
        assertNotEquals(consejos.get(0).getTitulo(), consejos.get(1).getTitulo());
    }

    @Test public void consejosEstablesDuranteDia() {
        assertEquals(AnalisisPatrones.consejosParaDia(20000, 1).get(0).getTitulo(),
                AnalisisPatrones.consejosParaDia(20000, 1).get(0).getTitulo());
    }
}
