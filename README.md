# CyCLaunch

Launcher para el head unit (radio Android) del auto, pensado para pantallas horizontales y para usarse mientras conduces: botones grandes, información clara y acceso rápido a lo que más usas.

Desarrollado por **Código y Café** · Paquete `codigo.cafe.alsvinlaunch` · Licencia [GPL-3.0](LICENSE)

[Disponible en Google Play](https://play.google.com/store/apps/details?id=codigo.cafe.alsvinlaunch)

![Pantalla principal de AlsvinLaunch](docs/screenshots/home.jpeg)

## Funciones

- **Accesos directos**: cinco botones configurables (música, navegación, teléfono, radio, ajustes…). Mantén presionado para cambiar la app de cada uno.
- **Velocímetro GPS**: muestra tu velocidad en km/h.
- **Indicador de marcha**: estima la marcha según la velocidad, con rangos configurables por tipo de motor.
- **Reproductor de audio**: controla música, radio o cualquier app de audio sin salir del launcher.
- **Reloj y fecha**, con pantalla de bloqueo (doble toque para desbloquear).
- **Estado de WiFi y Bluetooth** en tiempo real.
- **Contactos rápidos** por Bluetooth: importa contactos del teléfono y llama con un toque.
- **Control de volumen** por canal (música, Bluetooth, tono, alarma, notificaciones, sistema).
- **Lista de apps** con pestañas para apps normales y apps del sistema del head unit (cámara de reversa, configuración, USB…).
- **Modo kiosk**, temas y color de fondo dinámico.
- **Tour guiado** la primera vez que se abre.
- Idiomas: **español** e **inglés**.

## Requisitos

- Android **6.0 (API 23)** o superior.
- Pantalla horizontal (head units de 7"/9"/10").
- Para usarlo como pantalla de inicio, elige CyCLaunch como launcher predeterminado.

### Permisos que usa

| Permiso | Para qué |
| --- | --- |
| Ubicación | Velocímetro GPS |
| Bluetooth | Estado BT, contactos y llamadas |
| Acceso a notificaciones | Widget de música |
| WiFi / red | Indicador de conexión |
| Inicio al arrancar | Abrir el launcher al encender el radio |
| Cerrar procesos en segundo plano | Liberar RAM desde el launcher |
| Facturación de Google Play | Donaciones opcionales |

## Compilar

Necesitas [Android Studio](https://developer.android.com/studio) (o el Android SDK) y JDK 17.

```bash
git clone https://github.com/CodigoyCafe01/cyc-launcher.git
cd cyc-launcher
./gradlew assembleDebug        # en Windows: gradlew.bat assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/`. Para instalarlo en el radio por USB o red:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Build de release (opcional)

Solo hace falta para publicar. Copia `keystore.properties.example` como `keystore.properties` y rellena los datos de tu propio keystore. Ese archivo y los `.jks`/`.keystore` están en `.gitignore` y **nunca** deben subirse al repositorio.

```bash
./gradlew bundleRelease
```

## Estructura

```
app/src/main/java/codigo/cafe/alsvinlaunch/
├── MainActivity.java   Pantalla principal
├── activity/           Lista de apps, selector, volumen, bloqueo, tema 2…
├── managers/           Velocímetro, marchas, media, Bluetooth, reloj, kiosk, donaciones…
├── services/           Servicio de notificaciones y arranque al encender
├── views/              Vistas personalizadas (velocímetro)
├── model/, util/       Modelos y utilidades
docs/screenshots/       Capturas para este README
```

## Stack

Java 8 · Gradle (version catalog) · compileSdk/targetSdk 36 · minSdk 23 · AppCompat · Material Components · Palette · Google Play Billing.

## Contribuir

Los issues y pull requests son bienvenidos. Al enviar un pull request aceptas que tu contribución se publique bajo la licencia GPL-3.0 del proyecto.

- Cada texto nuevo va en `res/values/strings.xml` **y** en `res/values-en/strings.xml`.
- Mantén la compatibilidad con Android 6 (API 23): si usas una API más nueva, protégela con `Build.VERSION.SDK_INT`.
- Comprueba que `./gradlew assembleDebug` compila antes de abrir el PR.

## Apoyar el proyecto

CyCLaunch es gratis. Si te resulta útil, puedes apoyar con una donación desde el botón **Apoyar** dentro de la app.

## Licencia

Copyright (C) 2026 Código y Café

Este programa es software libre: puedes redistribuirlo y/o modificarlo bajo los términos de la **GNU General Public License versión 3** publicada por la Free Software Foundation. Se distribuye sin ninguna garantía. Consulta el archivo [LICENSE](LICENSE) para ver el texto completo.

En resumen: puedes usar, estudiar, modificar y compartir el código, pero si distribuyes una versión modificada (por ejemplo, en Google Play o preinstalada en radios) tienes que publicar su código fuente bajo la misma licencia.

**Términos adicionales** (sección 7 de la GPL-3.0):

- **Marca:** la licencia no concede derechos sobre los nombres *CyCLaunch* y *Código y Café*, el ícono, los logotipos ni el identificador `codigo.cafe.alsvinlaunch`. Si publicas tu propia versión, usa otro nombre, otro ícono y otro `applicationId`. Detalles en [MARCA.md](MARCA.md).
- **Bibliotecas de Google Play:** como permiso adicional, se permite combinar este programa con Google Play Billing Library y otras bibliotecas de Google Play y distribuir el resultado, aunque esas bibliotecas no estén bajo la GPL.
