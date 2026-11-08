# CLAUDE.md — `AlsvinLaunch`

Launcher Android para el head unit (radio) del auto. Paquete `codigo.cafe.alsvinlaunch` (Código y Café).

## Stack

- Android, **Java** (source/target 1.8), Gradle con version catalog (`gradle/libs.versions.toml`).
- compileSdk/targetSdk 36, minSdk 23.
- Dependencias: appcompat, material, palette, Google Play Billing.
- Release con `minifyEnabled` + `shrinkResources`: si agregas reflexión o clases cargadas por nombre, añade reglas en `app/proguard-rules.pro`.

## Dónde está cada cosa (`app/src/main/java/codigo/cafe/alsvinlaunch/`)

| Paquete | Contenido |
| --- | --- |
| `activity` | Activities (launcher principal, ajustes) |
| `managers` | Managers de widgets, media, apps, etc. |
| `services` | Servicios en segundo plano |
| `views` | Vistas personalizadas |
| `model`, `util` | Modelos y utilidades |

- Textos en `res/values/strings.xml` **y** `res/values-en/strings.xml`: agrega cada string en los dos.
- `public/` es material de marketing (volantes, docs generados con Node). **No es código de la app**.

## Reglas

1. **Nunca leas** `keystore.properties`, `*.jks`, `*.keystore` ni `local.properties`.
2. Si cambias algo que va a release, sube `versionCode`/`versionName` en `app/build.gradle` solo si te lo piden.

## Verificación

```bash
./gradlew assembleDebug    # en Windows: gradlew.bat assembleDebug
```
