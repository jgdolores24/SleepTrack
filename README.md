# SleepTrack — proyecto ordenado

Proyecto completo de Android Studio (Java + Firebase Auth + Cloud Firestore). Esta es una refactorización conservadora del ZIP original: la interfaz, los nombres de Activities, las colecciones Firestore y el análisis de sueño se mantienen.

## Cómo abrirlo

1. Haz una copia de seguridad de tu proyecto anterior (también de Firebase antes de pruebas destructivas).
2. Descomprime el ZIP **en una carpeta nueva**, sin mezclarlo con el proyecto anterior.
3. En Android Studio: **File > Open**, selecciona la carpeta `SleepTrack_VersionFinal` (la que contiene `settings.gradle.kts`).
4. Deja que Android Studio configure el SDK y regenere `local.properties`, `.gradle`, `.idea` y `build`.
5. Ejecuta **Sync Project with Gradle Files**; después **Build > Rebuild Project**.
6. Para pruebas locales: `gradlew.bat testDebugUnitTest` (Windows), y luego instala en un dispositivo Android con Firebase disponible.

En este ZIP preparado para GitHub se excluye `app/google-services.json` para no publicar la configuración del proyecto Firebase. Para compilar localmente, copia ese archivo desde tu proyecto original a `app/google-services.json` antes de sincronizar Gradle. No se modificaron tus datos ni reglas de Firebase.

## Organización actual

- `app/src/main/java/com/example/sleeptrack/modelo/`: JavaBeans con campos privados, constructores sin argumentos y getters/setters (`DiaDescansoBean`, `HallazgoPatronBean`, `IntervaloDescansoBean`).
- `NavegacionPrincipal.java`: navegación inferior centralizada para evitar cinco versiones distintas de las rutas.
- `AnalisisPatrones.java`: cálculos de coincidencias y consejos rotativos; ahora usa objetos JavaBean, no campos públicos.
- `ResumenSesiones.java`: suma duraciones, conserva protección contra solapamientos e ignora documentos vacíos.
- `RepositorioDescanso.java`, `FirestoreOrden.java`, `MigracionFirestore.java`: escritura, lectura compatible y migración; se conservaron rutas y estructura de Firestore.
- Activities y servicios conservan su nombre y paquete originales por compatibilidad con Manifest, intents y Firebase.
- `app/src/test/java/com/example/sleeptrack/`: pruebas para fechas, siestas, patrones, consejos y solapamientos.

## Comprobaciones

- 113 archivos XML parseados sin errores de sintaxis.
- 792 referencias de recursos examinadas; sin referencias locales rotas (excluidas las de Android y las generadas por Google Services).
- 82 recursos de `drawable` sin referencias eliminados, listados en `docs/drawable_eliminados.txt`.
- 12 pruebas Java ejecutadas localmente con resultados satisfactorios (para el motor puro y la suma de sesiones).
- **No se pudo ejecutar `assembleDebug` ni lanzar la app con Firebase real** porque este entorno no tiene SDK de Android. Ejecuta las pruebas de compilación y los flujos reales antes de utilizar esta versión como versión final.

## Casos de prueba recomendados en Android

- Abrir login/Google, registro y onboarding; revisar Firestore.
- Navegar por Alarma, Registros, Patrones, Consejos y Perfil; confirmar imágenes y selección visual.
- Inicio jueves 20:00 → fin viernes 06:00 (10 h en viernes), luego siesta viernes (1 h adicional).
- Registrar alcohol el jueves; confirmar actividad en jueves y su comparación con la noche correspondiente.
- Probar alarma normal, por duración, repetida y cancelación; revisar permisos de Android 13/14/15.
- Activar monitoreo y audio, finalizar, ver eventos y evaluación; comprobar acceso y borrado de audio.
- Mostrar dos consejos generales si no hay evidencia y patrones con datos suficientes.

### Importante

Esta versión **no garantiza funcionamiento al 100 %** sin probarla en tu instalación real de Android Studio, SDK y Firebase. Las clases grandes de Activities se mantuvieron en su mayor parte sin cambios para minimizar el riesgo de romper funcionalidades. La modularización es gradual: reutilizamos Beans y helpers en la lógica sensible, en lugar de mover masivamente las 35 clases de golpe.

## Subir a GitHub

1. Descomprime este archivo.
2. Abre GitHub Desktop y elige `File > Add local repository` y la carpeta `SleepTrack_VersionFinal`. Si todavía no es un repositorio, pulsa `create a repository here`.
3. Agrega los archivos en el primer commit y pulsa `Publish repository`.
4. Deja el repositorio privado inicialmente; revisa las credenciales antes de hacerlo publico.

No publiques `local.properties`, configuraciones privadas, claves de Gemini/OpenAI, credenciales de Firebase Admin ni llaves de firmado. Revisa las claves incrustadas en el codigo fuente antes de compartirlo.
