package com.example.sleeptrack;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Días seleccionados = días en que debe sonar la alarma al despertar. */
public final class HorarioRepeticion {
    private HorarioRepeticion() { }

    public static final class SesionProgramada {
        private final long inicio;
        private final long despertar;
        public SesionProgramada(long inicio, long despertar) {
            this.inicio = inicio;
            this.despertar = despertar;
        }
        public long getInicio() { return inicio; }
        public long getDespertar() { return despertar; }
    }

    private static final String[] ABREVIADOS = {
            "", "dom", "lun", "mar", "mié", "jue", "vie", "sáb"
    };

    public static boolean horaValida(String hora) {
        return hora != null && hora.matches("(?:[01]\\d|2[0-3]):[0-5]\\d");
    }

    /** Devuelve los días de inicio que corresponden a los días de despertar. */
    public static int diasInicio(int diasDespertar, String horaInicio, String horaDespertar) {
        if (!horaValida(horaInicio) || !horaValida(horaDespertar)) return diasDespertar;
        if (horaInicio.compareTo(horaDespertar) < 0) return diasDespertar;
        int resultado = 0;
        for (int d = Calendar.SUNDAY; d <= Calendar.SATURDAY; d++) {
            if ((diasDespertar & (1 << d)) == 0) continue;
            int anterior = d == Calendar.SUNDAY ? Calendar.SATURDAY : d - 1;
            resultado |= (1 << anterior);
        }
        return resultado;
    }

    /** Mensaje claro para la interfaz, incluidos días personalizados. */
    public static String descripcion(int diasDespertar, String horaInicio, String horaDespertar) {
        if (diasDespertar == 0) return "Una sola alarma";
        int inicios = diasInicio(diasDespertar, horaInicio, horaDespertar);
        return "Monitoreo: " + diasCortos(inicios) + " desde " + horaInicio
                + ". Despertar: " + diasCortos(diasDespertar) + " a las " + horaDespertar + ".";
    }

    private static String diasCortos(int mascara) {
        List<String> dias = new ArrayList<>();
        for (int dia = Calendar.MONDAY; dia <= Calendar.SATURDAY; dia++) {
            if ((mascara & (1 << dia)) != 0) dias.add(ABREVIADOS[dia]);
        }
        if ((mascara & (1 << Calendar.SUNDAY)) != 0) dias.add("dom");
        StringBuilder salida = new StringBuilder();
        for (String dia : dias) {
            if (salida.length() > 0) salida.append(", ");
            salida.append(dia);
        }
        return salida.toString();
    }

    /**
     * Busca la próxima sesión tomando el día de DESPERTAR como día seleccionado.
     * Si inicio=22:00 y despertar=06:00, el inicio ocurre la víspera.
     * recuperarSesionActiva permite volver a programar el despertador después
     * de un reinicio sin crear un segundo inicio de monitoreo.
     */
    public static SesionProgramada siguiente(int diasDespertar, String horaInicio,
                                             String horaDespertar, long ahora,
                                             boolean recuperarSesionActiva) {
        if (diasDespertar == 0 || !horaValida(horaInicio) || !horaValida(horaDespertar)
                || horaInicio.equals(horaDespertar)) return null;
        int horasInicio = Integer.parseInt(horaInicio.substring(0, 2));
        int minutosInicio = Integer.parseInt(horaInicio.substring(3));
        int horasFin = Integer.parseInt(horaDespertar.substring(0, 2));
        int minutosFin = Integer.parseInt(horaDespertar.substring(3));
        boolean empiezaVispera = horaInicio.compareTo(horaDespertar) > 0;
        Calendar actual = Calendar.getInstance();
        actual.setTimeInMillis(ahora);
        SesionProgramada siguiente = null;
        for (int dia = 0; dia <= 8; dia++) {
            Calendar fin = (Calendar) actual.clone();
            fin.add(Calendar.DAY_OF_MONTH, dia);
            if ((diasDespertar & (1 << fin.get(Calendar.DAY_OF_WEEK))) == 0) continue;
            fin.set(Calendar.HOUR_OF_DAY, horasFin);
            fin.set(Calendar.MINUTE, minutosFin);
            fin.set(Calendar.SECOND, 0);
            fin.set(Calendar.MILLISECOND, 0);
            Calendar comienzo = (Calendar) fin.clone();
            if (empiezaVispera) comienzo.add(Calendar.DAY_OF_MONTH, -1);
            comienzo.set(Calendar.HOUR_OF_DAY, horasInicio);
            comienzo.set(Calendar.MINUTE, minutosInicio);
            comienzo.set(Calendar.SECOND, 0);
            comienzo.set(Calendar.MILLISECOND, 0);
            long inicio = comienzo.getTimeInMillis();
            long despertar = fin.getTimeInMillis();
            if (despertar <= ahora || inicio >= despertar) continue;
            if (inicio <= ahora && !recuperarSesionActiva) continue;
            SesionProgramada candidato = new SesionProgramada(inicio, despertar);
            if (siguiente == null || candidato.getInicio() < siguiente.getInicio()) {
                siguiente = candidato;
            }
        }
        return siguiente;
    }
}
