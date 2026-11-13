package com.example.sleeptrack.modelo;

/** Representación uniforme de un hábito para patrones y futuras consultas de IA. */
public class EventoActividadBean {
    private String tipoActividad;
    private String nombre;
    private String fechaLocal;
    private String horaLocal;
    private String unidad;
    private int cantidad;
    private Integer intensidad;
    private String origen;

    public EventoActividadBean() { }
    public String getTipoActividad() { return tipoActividad; }
    public void setTipoActividad(String valor) { tipoActividad = valor; }
    public String getNombre() { return nombre; }
    public void setNombre(String valor) { nombre = valor; }
    public String getFechaLocal() { return fechaLocal; }
    public void setFechaLocal(String valor) { fechaLocal = valor; }
    public String getHoraLocal() { return horaLocal; }
    public void setHoraLocal(String valor) { horaLocal = valor; }
    public String getUnidad() { return unidad; }
    public void setUnidad(String valor) { unidad = valor; }
    public int getCantidad() { return cantidad; }
    public void setCantidad(int valor) { cantidad = valor; }
    public Integer getIntensidad() { return intensidad; }
    public void setIntensidad(Integer valor) { intensidad = valor; }
    public String getOrigen() { return origen; }
    public void setOrigen(String valor) { origen = valor; }
}
