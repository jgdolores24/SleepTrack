package com.example.sleeptrack.modelo;

import java.util.ArrayList;
import java.util.List;

/** Perfil de usuario separado de sus eventos diarios y de las alarmas. */
public class PerfilUsuarioBean {
    private String nombre;
    private String correo;
    private String sexo;
    private Integer anioNacimiento;
    private Integer pesoKg;
    private Integer alturaCm;
    private String horaDormir;
    private String horaDespertar;
    private List<String> suplementosIniciales = new ArrayList<>();
    private List<String> molestiasIniciales = new ArrayList<>();

    public PerfilUsuarioBean() { }
    public String getNombre() { return nombre; }
    public void setNombre(String valor) { nombre = valor; }
    public String getCorreo() { return correo; }
    public void setCorreo(String valor) { correo = valor; }
    public String getSexo() { return sexo; }
    public void setSexo(String valor) { sexo = valor; }
    public Integer getAnioNacimiento() { return anioNacimiento; }
    public void setAnioNacimiento(Integer valor) { anioNacimiento = valor; }
    public Integer getPesoKg() { return pesoKg; }
    public void setPesoKg(Integer valor) { pesoKg = valor; }
    public Integer getAlturaCm() { return alturaCm; }
    public void setAlturaCm(Integer valor) { alturaCm = valor; }
    public String getHoraDormir() { return horaDormir; }
    public void setHoraDormir(String valor) { horaDormir = valor; }
    public String getHoraDespertar() { return horaDespertar; }
    public void setHoraDespertar(String valor) { horaDespertar = valor; }
    public List<String> getSuplementosIniciales() { return new ArrayList<>(suplementosIniciales); }
    public void setSuplementosIniciales(List<String> valor) {
        suplementosIniciales = valor == null ? new ArrayList<>() : new ArrayList<>(valor);
    }
    public List<String> getMolestiasIniciales() { return new ArrayList<>(molestiasIniciales); }
    public void setMolestiasIniciales(List<String> valor) {
        molestiasIniciales = valor == null ? new ArrayList<>() : new ArrayList<>(valor);
    }
}
