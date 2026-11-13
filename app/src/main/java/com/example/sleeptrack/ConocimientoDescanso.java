package com.example.sleeptrack;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ConocimientoDescanso {
    public static final String HABITOS = "https://www.nhlbi.nih.gov/health/sleep-deprivation/healthy-sleep-habits";
    private ConocimientoDescanso() { }

    public static String normalizar(String texto) {
        return Normalizer.normalize(texto.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
    }

    public static boolean contiene(String texto, String... claves) {
        for (String clave : claves) {
            if (texto.contains(clave)) return true;
        }
        return false;
    }

    public static List<String> temas(String pregunta) {
        String p = normalizar(pregunta);
        List<String> temas = new ArrayList<>();
        if (contiene(p, "cafe", "cafeina", "energizante", "energetica", "te verde", "te negro") || p.matches(".*\\bte\\b.*")) temas.add("cafeina");
        if (contiene(p, "alcohol", "cerveza", "vino", "licor", "trago")) temas.add("alcohol");
        if (contiene(p, "pantalla", "celular", "telefono", "television")) temas.add("pantallas");
        if (contiene(p, "suplement", "melatonina", "vitamina", "pastilla", "medicamento", "dosis")) temas.add("suplementos");
        if (contiene(p, "ronqu", "apnea", "respiracion", "ahogo")) temas.add("ronquidos");
        if (contiene(p, "estres", "ansiedad", "relaja")) temas.add("estres");
        if (contiene(p, "dolor", "molestia", "migrana")) temas.add("molestias");
        if (contiene(p, "siesta")) temas.add("siesta");
        if (contiene(p, "ejercicio", "deporte", "entrenar", "gimnasio")) temas.add("ejercicio");
        if (contiene(p, "cena", "comer", "comida")) temas.add("comida");
        if (contiene(p, "cigar", "nicotina", "fumar", "vape")) temas.add("nicotina");
        if (contiene(p, "turno", "trabajo nocturno", "trabajar de noche")) temas.add("turnos");
        if (contiene(p, "habitacion", "temperatura", "oscur", "luz")) temas.add("ambiente");
        if (contiene(p, "insomnio", "no puedo dormir", "no logro dormir")) temas.add("insomnio");
        if (contiene(p, "cuantas horas", "cuanto debo dormir", "cuanto dormir")) temas.add("horas");
        if (temas.isEmpty() && contiene(p, "mejorar", "recomend", "consejo", "rutina", "horario")) temas.add("rutina");
        return temas;
    }

    public static String respuesta(String tema) {
        switch (tema) {
            case "cafeina": return "Conviene evitar cafeína cerca de dormir: es estimulante y puede dificultar conciliar el sueño. Sus efectos pueden durar hasta ocho horas, con variación entre personas.";
            case "alcohol": return "Aunque pueda dar somnolencia, el alcohol puede producir sueño más ligero y despertares. Evítalo cerca de acostarte.";
            case "pantallas": return "Reduce la luz intensa de pantallas durante la hora previa al descanso.";
            case "rutina": return "Procura acostarte y levantarte a horas regulares, también en días libres.";
            case "estres": return "Prueba una rutina tranquila o relajación antes de acostarte; no garantiza resolver la ansiedad.";
            case "siesta": return "Si te cuesta dormir por la noche, limita las siestas y evita hacerlas tarde.";
            case "ejercicio": return "Mantén actividad física durante el día y evita ejercicio intenso justo antes de acostarte.";
            case "comida": return "Evita comidas abundantes en las horas próximas a acostarte.";
            case "nicotina": return "La nicotina es estimulante y puede interferir con el sueño.";
            case "turnos": return "Si trabajas de noche, limita la cafeína al inicio del turno y oscurece la habitación al descansar.";
            case "ambiente": return "Busca una habitación tranquila, oscura y fresca.";
            case "horas": return "La necesidad de sueño depende de la edad. En adultos de 18 a 60 años, CDC recomienda al menos siete horas por noche. El tiempo de una sesión de SleepTrack no confirma cuánto dormiste.";
            case "suplementos": return "La melatonina no es una solución universal. Puede tener interacciones y faltan datos sobre su seguridad a largo plazo. Consulta con un profesional antes de usarla; aquí no se indican dosis ni cambios de tratamiento.";
            case "ronquidos": return "Ronquidos frecuentes con pausas respiratorias o somnolencia pueden requerir evaluación médica. Los audios del teléfono no diagnostican apnea.";
            case "insomnio": return "Si la dificultad para dormir persiste o afecta tu día, consulta con un profesional. El tratamiento depende de la causa; un chat no permite diagnosticar insomnio.";
            default: return "Puedes registrar cuándo aparecen las molestias y su intensidad. Los registros de SleepTrack no permiten determinar su causa.";
        }
    }

    public static String fuente(String tema) {
        switch (tema) {
            case "alcohol": return "https://www.nhlbi.nih.gov/health/insomnia/treatment";
            case "horas": return "https://www.cdc.gov/sleep/about/index.html";
            case "suplementos": return "https://www.nccih.nih.gov/health/melatonin-what-you-need-to-know";
            case "ronquidos": return "https://www.nhlbi.nih.gov/health/sleep-apnea/symptoms";
            case "insomnio": return "https://www.nhlbi.nih.gov/health/insomnia/treatment";
            case "molestias": return "";
            default: return HABITOS;
        }
    }
}
