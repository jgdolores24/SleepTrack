# Auditoría y refactorización de SleepTrack

## Componentes depurados

- 82 de 196 drawables originales no tenían referencias desde los recursos activos ni el código Java. Se borraron con auditoría estática conservadora; 114 permanecen.
- Se eliminaron las carpetas regenerables `.gradle`, `.idea`, `build` y `app/build`, y el `local.properties` dependiente de la PC original.
- Se eliminaron los directorios `mipmap-*`: el Manifest usa `@drawable/sleeptrack_logo_s` para los íconos de aplicación; estos lanzadores no eran referenciados.
- `ic_launcher-playstore.png` era un recurso publicitario que no se usa en la app actual. Puede recuperarse del ZIP original para publicación.
- `keepRules` contenía solamente comentarios, sin reglas de R8.
- Se eliminó la dependencia duplicada `androidx.core:core-splashscreen:1.0.1`, conservando `1.2.0`.

## Java y POO

- Nuevos `modelo/DiaDescansoBean.java`, `modelo/HallazgoPatronBean.java`, `modelo/IntervaloDescansoBean.java`: encapsulación con constructores vacíos y accesores JavaBean.
- `AnalisisPatrones.java` y `Patrones.java` se adaptaron al modelo. Se conservó la asociación por hora de inicio y la prioridad de patrones sobre recomendaciones generales.
- Nuevo `NavegacionPrincipal.java` centraliza el cambio entre cinco pestañas; no modifica los drawables ni IDs de las barras.
- `ResumenSesiones.java` valida intervalos con el bean e ignora nulos sin fallar; la suma de horas mantiene su contrato de datos.
- `AlarmaProgramador.java` valida HH:mm guardado antes de convertirlo a enteros para evitar excepción por configuración corrupta.
- Se eliminó `Checks.java` de `src/main`; añadidas pruebas JUnit en `src/test`.

## Seguridad de los cambios

- Sin cambios en los datos reales de Firestore, las reglas, las colecciones, los nombres de Activities y servicios ni las rutas de monitoreo.
- Los XML de las 18 pantallas se conservan; se eliminó únicamente lo no referenciado.
- No se agrega ninguna llamada de IA de pago ni se incluye una nueva clave secreta.
- La prueba ejecutada fue Java local, no una compilación completa de Android; Firebase y permisos requieren pruebas en el teléfono.

## Limitación conocida

Una limpieza basada en referencias estáticas identifica el uso *actual*, pero los recursos antiguos pueden ser útiles para diseños futuros. Los 82 nombres están en el inventario y el ZIP original permite recuperarlos. No se realizó una reescritura masiva de Activities para no introducir cambios de funcionalidad difíciles de validar sin Android SDK.
