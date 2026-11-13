package com.example.sleeptrack.modelo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Datos de una fecha de descanso usados por el motor de patrones. */
public class DiaDescansoBean {
    private String fecha;
    private Integer minutosTotales;
    private Integer minutosPrincipales;
    private Long inicioPrincipal;
    private Long finPrincipal;
    private List<Map<?, ?>> molestiasAlDespertar = new ArrayList<>();

    public DiaDescansoBean() { }

    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }
    public Integer getMinutosTotales() { return minutosTotales; }
    public void setMinutosTotales(Integer minutosTotales) { this.minutosTotales = minutosTotales; }
    public Integer getMinutosPrincipales() { return minutosPrincipales; }
    public void setMinutosPrincipales(Integer minutosPrincipales) { this.minutosPrincipales = minutosPrincipales; }
    public Long getInicioPrincipal() { return inicioPrincipal; }
    public void setInicioPrincipal(Long inicioPrincipal) { this.inicioPrincipal = inicioPrincipal; }
    public Long getFinPrincipal() { return finPrincipal; }
    public void setFinPrincipal(Long finPrincipal) { this.finPrincipal = finPrincipal; }
    public List<Map<?, ?>> getMolestiasAlDespertar() { return molestiasAlDespertar; }
    public void setMolestiasAlDespertar(List<Map<?, ?>> molestias) {
        this.molestiasAlDespertar = molestias == null ? new ArrayList<>() : new ArrayList<>(molestias);
    }
}
