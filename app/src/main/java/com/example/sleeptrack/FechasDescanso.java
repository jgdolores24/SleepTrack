package com.example.sleeptrack;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Conserva las fechas reales de inicio y finalización de cada descanso. */
public final class FechasDescanso {
    private FechasDescanso() {}

    public static String fechaInicio(long inicio, TimeZone zonaHoraria) {
        return fecha(inicio, zonaHoraria);
    }

    public static String fechaFin(long fin, TimeZone zonaHoraria) {
        return fecha(fin, zonaHoraria);
    }

    private static String fecha(long instante, TimeZone zonaHoraria) {
        SimpleDateFormat formato = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        formato.setTimeZone(zonaHoraria);
        return formato.format(new Date(instante));
    }
}
