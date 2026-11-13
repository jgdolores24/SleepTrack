package com.example.sleeptrack;

import java.util.Calendar;
import java.util.TimeZone;

public final class HorarioRecordatorio {
    private HorarioRecordatorio() { }

    public static long siguiente(long ahora, int hora, int minuto, TimeZone zona) {
        if (hora < 0 || hora > 23 || minuto < 0 || minuto > 59) {
            throw new IllegalArgumentException("Hora inválida");
        }
        Calendar fecha = Calendar.getInstance(zona);
        fecha.setTimeInMillis(ahora);
        fecha.set(Calendar.HOUR_OF_DAY, hora);
        fecha.set(Calendar.MINUTE, minuto);
        fecha.set(Calendar.SECOND, 0);
        fecha.set(Calendar.MILLISECOND, 0);
        if (fecha.getTimeInMillis() <= ahora) fecha.add(Calendar.DAY_OF_YEAR, 1);
        return fecha.getTimeInMillis();
    }
}
