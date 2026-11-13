package com.example.sleeptrack;

import com.example.sleeptrack.modelo.PerfilUsuarioBean;
import java.util.HashMap;
import java.util.Map;

public final class RepositorioPerfil {
    private RepositorioPerfil() { }
    public static Map<String, Object> basicos(PerfilUsuarioBean bean) {
        Map<String, Object> perfil = new HashMap<>();
        perfil.put("nombre", bean.getNombre());
        perfil.put("correo", bean.getCorreo());
        perfil.put("anioNacimiento", bean.getAnioNacimiento());
        perfil.put("sexo", bean.getSexo());
        perfil.put("pesoKg", bean.getPesoKg());
        perfil.put("alturaCm", bean.getAlturaCm());
        return envolver(perfil);
    }
    public static Map<String, Object> horarios(PerfilUsuarioBean bean) {
        Map<String, Object> horarios = new HashMap<>();
        horarios.put("dormir", bean.getHoraDormir());
        horarios.put("despertar", bean.getHoraDespertar());
        Map<String, Object> perfil = new HashMap<>();
        perfil.put("horariosHabituales", horarios);
        return envolver(perfil);
    }
    public static Map<String, Object> salud(PerfilUsuarioBean bean) {
        Map<String, Object> salud = new HashMap<>();
        salud.put("suplementosDeclarados", bean.getSuplementosIniciales());
        salud.put("molestiasDeclaradas", bean.getMolestiasIniciales());
        Map<String, Object> perfil = new HashMap<>();
        perfil.put("saludInicial", salud);
        return envolver(perfil);
    }
    @SuppressWarnings("unchecked")
    public static Map<String, Object> completo(PerfilUsuarioBean bean) {
        Map<String, Object> datos = basicos(bean);
        Map<String, Object> perfil = (Map<String, Object>) datos.get("perfil");
        Map<String, Object> horarios = horarios(bean);
        perfil.putAll((Map<String, Object>) horarios.get("perfil"));
        return datos;
    }
    private static Map<String, Object> envolver(Map<String, Object> perfil) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("versionPerfil", 4);
        datos.put("perfil", perfil);
        return datos;
    }
}
