---
name: release-aab
description: Genera un nuevo Android App Bundle (.aab) firmado de AlsvinLaunch listo para subir a Google Play. Úsalo cuando el usuario pida "generar el aab", "build de release", "nueva versión para la Play Store" o similar.
---

# Generar AAB de release para Google Play

Objetivo: dejar un `.aab` firmado con la upload key, con un `versionCode` nuevo, más el `mapping.txt` de R8, en `app/release/`.

## Reglas que no se rompen

- **Nunca leas** `keystore.properties`, `*.jks`, `*.keystore` ni `local.properties`. Solo puedes comprobar si existen (`test -f`).
- No hagas commit, push ni tags salvo que el usuario lo pida.
- No subas nada a Play Console: el usuario sube el archivo a mano.

## Pasos

### 1. Pre-chequeo

Ejecuta desde la raíz del repo:

```bash
test -f keystore.properties && echo OK || echo FALTA
git status --short
grep -nE "versionCode|versionName" app/build.gradle
```

- Si `keystore.properties` **falta**, detente: sin él Gradle genera un AAB **sin firmar** que Play rechaza. Dile al usuario que lo cree copiando `keystore.properties.example` y rellenando sus datos (él mismo, tú no).
- Si hay cambios sin commitear, menciónalos en una línea (van a ir dentro del build) y sigue.

### 2. Versión

Play exige que cada subida tenga un `versionCode` mayor que el anterior. Pregunta al usuario con `AskUserQuestion`, mostrando los valores actuales:

- `versionCode`: propone actual + 1 (recomendado).
- `versionName`: propone el siguiente (p. ej. `"7"` → `"8"`), o mantenerlo.

Aplica lo elegido editando `defaultConfig` en `app/build.gradle` con Edit. Si el usuario dice que no subas versión, no la toques (pero adviértele que Play rechazará un `versionCode` repetido).

### 3. Build

```bash
./gradlew clean bundleRelease
```

Tarda varios minutos: usa `timeout` de 600000 ms o `run_in_background`. Si falla:

- Error de R8/ProGuard (`Missing class`, `ClassNotFoundException` en reflexión): añade reglas `-keep` en `app/proguard-rules.pro`, no desactives `minifyEnabled`.
- Error de firma (`Keystore was tampered with`, `password was incorrect`, `alias not found`): no intentes arreglarlo; dile al usuario que revise `keystore.properties`.
- Cualquier otro error: muestra las líneas relevantes y corrígelo si es del código.

### 4. Verificar firma

```bash
jarsigner -verify app/build/outputs/bundle/release/app-release.aab
```

Debe decir `jar verified.` (los avisos de certificado autofirmado son normales). Si dice `jar is unsigned`, el AAB no sirve: vuelve al paso 1.

### 5. Copiar con nombre de versión

`app/release/` está en `.gitignore`. Con `<name>` = versionName y `<code>` = versionCode:

```bash
mkdir -p app/release
cp app/build/outputs/bundle/release/app-release.aab "app/release/AlsvinLaunch-v<name>-<code>.aab"
cp app/build/outputs/mapping/release/mapping.txt "app/release/mapping-v<name>-<code>.txt"
ls -la app/release/AlsvinLaunch-v<name>-<code>.aab
```

### 6. Entregar

- Envía el `.aab` con `SendUserFile` (`display: "attach"`).
- Resume: versionName/versionCode, ruta del `.aab`, tamaño, firma verificada.
- Recuérdale subir el `mapping-v<name>-<code>.txt` en Play Console (App bundle explorer → Descargas → "Archivo de desofuscación") para tener stack traces legibles.
- Ofrece hacer commit del cambio de versión en `app/build.gradle` (solo si acepta).
