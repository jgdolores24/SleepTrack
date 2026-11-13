package com.example.sleeptrack.modelo;

/** Fotografía del estado local de Android, distinta de la configuración solicitada. */
public class EstadoAlarmaBean {
    private String estado;
    private boolean alarmaProgramada;
    private boolean alarmaSonando;
    private boolean monitoreoActivo;
    private String sesionId;
    private long proximaAlarmaMs;
    private long proximoInicioMonitoreoMs;

    public EstadoAlarmaBean() { }
    public String getEstado() { return estado; }
    public void setEstado(String valor) { estado = valor; }
    public boolean isAlarmaProgramada() { return alarmaProgramada; }
    public void setAlarmaProgramada(boolean valor) { alarmaProgramada = valor; }
    public boolean isAlarmaSonando() { return alarmaSonando; }
    public void setAlarmaSonando(boolean valor) { alarmaSonando = valor; }
    public boolean isMonitoreoActivo() { return monitoreoActivo; }
    public void setMonitoreoActivo(boolean valor) { monitoreoActivo = valor; }
    public String getSesionId() { return sesionId; }
    public void setSesionId(String valor) { sesionId = valor; }
    public long getProximaAlarmaMs() { return proximaAlarmaMs; }
    public void setProximaAlarmaMs(long valor) { proximaAlarmaMs = valor; }
    public long getProximoInicioMonitoreoMs() { return proximoInicioMonitoreoMs; }
    public void setProximoInicioMonitoreoMs(long valor) { proximoInicioMonitoreoMs = valor; }
}
