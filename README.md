# 🌙 SleepTrack — Aplicación de monitoreo del sueño

## 📱 ¿Qué es SleepTrack?

**SleepTrack** es una aplicación móvil desarrollada en Android Studio utilizando Java y Firebase, diseñada para ayudar a los usuarios a registrar, monitorear y analizar sus hábitos de descanso.

La aplicación permite llevar un seguimiento de las horas de sueño, identificar patrones relacionados con las actividades diarias y recibir recomendaciones personalizadas para mejorar los hábitos de descanso.

## ✨ Funcionalidades principales

- **Alarmas inteligentes:** configuración de horarios de descanso, alarmas personalizadas y monitoreo nocturno.
- **Registro del sueño:** visualización del historial de descanso mediante calendarios, gráficos y estadísticas.
- **Análisis de patrones:** identificación de posibles relaciones entre hábitos diarios y calidad del sueño.
- **Asistente de sueño:** recomendaciones y consultas relacionadas con el descanso mediante inteligencia artificial.
- **Perfil de usuario:** administración de información personal, horarios habituales y preferencias.
- **Sincronización en la nube:** autenticación y almacenamiento de información mediante Firebase.

## 🛠️ Tecnologías utilizadas

- **Java:** desarrollo de la lógica de la aplicación.
- **Android Studio:** entorno de desarrollo.
- **Firebase Authentication:** autenticación de usuarios.
- **Cloud Firestore:** almacenamiento y sincronización de datos.
- **XML:** diseño de las interfaces.
- **Gradle:** gestión de dependencias y compilación.

## 📥 ¿Cómo descargar SleepTrack?

**1. Acceder al repositorio**

Ingresa al siguiente enlace:

https://github.com/jgdolores24/SleepTrack

**2. Descargar el proyecto**

- Haz clic en el botón verde **Code**.
- Selecciona **Download ZIP**.
- Descomprime el archivo descargado en una carpeta de tu computadora.

**3. Abrir en Android Studio**

- Abre Android Studio.
- Selecciona **File → Open**.
- Busca la carpeta descargada de SleepTrack.
- Selecciona la carpeta que contiene `settings.gradle.kts`.
- Espera a que Gradle sincronice las dependencias.

**4. Configurar Firebase**

Para ejecutar la aplicación con sus funcionalidades de autenticación y almacenamiento, es necesario contar con la configuración correspondiente de Firebase.

Coloca el archivo `google-services.json` del proyecto Firebase autorizado dentro de la carpeta `app/`.

**5. Ejecutar la aplicación**

- Conecta un dispositivo Android o inicia un emulador compatible.
- Selecciona el dispositivo en Android Studio.
- Haz clic en **Run ▶** para compilar e instalar la aplicación.

## 📂 Estructura general

```text
SleepTrack/
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/
│       │   ├── res/
│       │   └── AndroidManifest.xml
│       └── test/
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## 🎯 Objetivo del proyecto

Facilitar el seguimiento de los hábitos de sueño mediante herramientas digitales que permitan visualizar información sobre el descanso, reconocer patrones y promover mejores hábitos de sueño.

---

**SleepTrack — Monitorea tu descanso, comprende tus hábitos.**
