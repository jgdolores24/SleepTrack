package com.example.sleeptrack.modelo;

/** Un intervalo de monitoreo con inicio y fin reales (milisegundos Unix). */
public class IntervaloDescansoBean {
    private long inicio;
    private long fin;

    public IntervaloDescansoBean() { }

    public IntervaloDescansoBean(long inicio, long fin) {
        setInicio(inicio);
        setFin(fin);
    }

    public long getInicio() { return inicio; }
    public void setInicio(long inicio) { this.inicio = inicio; }
    public long getFin() { return fin; }
    public void setFin(long fin) { this.fin = fin; }

    public long duracionMillis() { return fin - inicio; }
    public boolean esValido() { return inicio > 0 && fin > inicio && duracionMillis() <= 18L * 60 * 60000L; }
}
