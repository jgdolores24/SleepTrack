package com.example.sleeptrack.modelo;

/** Hallazgo descriptivo o consejo general que se muestra en Patrones. */
public class HallazgoPatronBean {
    private String categoria;
    private String titulo;
    private String resumen;
    private String detalle;

    public HallazgoPatronBean() { }

    public HallazgoPatronBean(String categoria, String titulo, String resumen, String detalle) {
        this.categoria = categoria;
        this.titulo = titulo;
        this.resumen = resumen;
        this.detalle = detalle;
    }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getResumen() { return resumen; }
    public void setResumen(String resumen) { this.resumen = resumen; }
    public String getDetalle() { return detalle; }
    public void setDetalle(String detalle) { this.detalle = detalle; }
}
