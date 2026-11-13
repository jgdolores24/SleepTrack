package com.example.sleeptrack.modelo;

/** Datos configurados por el usuario; no son prueba de ejecución en Android. */
public class ConfiguracionAlarmaBean {
    private String nombre;
    private String horaDespertar;
    private String horaInicioMonitoreo;
    private String tipoProgramacion;
    private String modoDespertar;
    private int diasMascara;
    private int duracionMinutos;
    private boolean monitoreoTelefono;
    private boolean deteccionSonidos;

    public ConfiguracionAlarmaBean() { }
    public String getNombre() { return nombre; }
    public void setNombre(String valor) { nombre = valor; }
    public String getHoraDespertar() { return horaDespertar; }
    public void setHoraDespertar(String valor) { horaDespertar = valor; }
    public String getHoraInicioMonitoreo() { return horaInicioMonitoreo; }
    public void setHoraInicioMonitoreo(String valor) { horaInicioMonitoreo = valor; }
    public String getTipoProgramacion() { return tipoProgramacion; }
    public void setTipoProgramacion(String valor) { tipoProgramacion = valor; }
    public String getModoDespertar() { return modoDespertar; }
    public void setModoDespertar(String valor) { modoDespertar = valor; }
    public int getDiasMascara() { return diasMascara; }
    public void setDiasMascara(int valor) { diasMascara = valor; }
    public int getDuracionMinutos() { return duracionMinutos; }
    public void setDuracionMinutos(int valor) { duracionMinutos = valor; }
    public boolean isMonitoreoTelefono() { return monitoreoTelefono; }
    public void setMonitoreoTelefono(boolean valor) { monitoreoTelefono = valor; }
    public boolean isDeteccionSonidos() { return deteccionSonidos; }
    public void setDeteccionSonidos(boolean valor) { deteccionSonidos = valor; }
}
