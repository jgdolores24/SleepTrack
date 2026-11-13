package com.example.sleeptrack;

import static org.junit.Assert.*;

import java.util.Calendar;
import java.util.TimeZone;
import org.junit.Test;

public class ConsejosHorarioTest {
    private static final TimeZone LIMA = TimeZone.getTimeZone("America/Lima");

    private static long instante(int hora) {
        Calendar c = Calendar.getInstance(LIMA);
        c.clear();
        c.set(2026, Calendar.NOVEMBER, 7, hora, 0);
        return c.getTimeInMillis();
    }

    @Test public void detectaTemasDeCafeinaSinFalsosPositivos() {
        assertTrue(ConocimientoDescanso.temas("¿Es bueno tomar CAFÉ antes de dormir?")
                .contains("cafeina"));
        assertFalse(ConocimientoDescanso.temas("¿Antes de dormir?").contains("cafeina"));
        assertEquals(2, ConocimientoDescanso.temas("Café y alcohol antes de dormir").size());
    }

    @Test public void recordatorioCalculaProximaOcurrencia() {
        assertEquals(instante(20), HorarioRecordatorio.siguiente(instante(12), 20, 0, LIMA));
        assertTrue(HorarioRecordatorio.siguiente(instante(21), 20, 0, LIMA) > instante(21));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rechazaHoraInvalida() {
        HorarioRecordatorio.siguiente(0L, 24, 0, LIMA);
    }
}
