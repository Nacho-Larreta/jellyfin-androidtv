# Jellyfin Android TV

Cliente nativo para Android TV / Fire TV. Es el cliente mas independiente del ecosistema web.

## Rol

- Cliente nativo con UI propia.
- Maneja discovery, login, Quick Connect, session restore, playback y settings.
- Publica APKs y AABs.

## Stack

- Gradle
- Kotlin
- Java `21`
- Android SDK
- Jetpack Compose + Leanback + XML views
- Koin
- Kotest / MockK
- Detekt + Android Lint

## Setup local

### Prerequisitos

- Java 21
- Android SDK configurado
- `ANDROID_HOME` o `local.properties` con `sdk.dir=...`
- En este workspace existe un SDK local en `../.android-sdk`
- Para no depender de `~/.android`, `~/Library/.../kotlin` ni `~/.gradle`, usar `./gradlew-local`

### Comandos utiles

```bash
./gradlew-local -version
./gradlew-local :app:compileDebugKotlin
./gradlew-local :app:testDebugUnitTest
./gradlew-local :app:lintDebug
./gradlew-local :app:assembleDebug
```

### Notas del host

- `./gradlew-local` ya encapsula:
  - `ANDROID_HOME`
  - `ANDROID_SDK_ROOT`
  - `ANDROID_USER_HOME`
  - `GRADLE_USER_HOME`
  - `-Duser.home=...`
  - `-Dorg.gradle.vfs.watch=false`
- Si usas `./gradlew` directo en este host, es esperable ruido de analytics/Kotlin daemon.

## Modulos importantes

- `:app`
- `:design`
- `:playback:core`
- `:playback:jellyfin`
- `:playback:media3:exoplayer`
- `:playback:media3:session`
- `:preference`

## Archivos para orientarse rapido

- `settings.gradle.kts`
- `build.gradle.kts`
- `app/build.gradle.kts`
- `app/src/main/java/org/jellyfin/androidtv/auth/repository/AuthenticationRepository.kt`
- `app/src/main/java/org/jellyfin/androidtv/auth/repository/ServerRepository.kt`
- `app/src/main/java/org/jellyfin/androidtv/auth/repository/SessionRepository.kt`
- `app/src/main/java/org/jellyfin/androidtv/auth/store/AuthenticationStore.kt`
- `design/src/main/kotlin/Tokens.kt`

## Auth y sesiones

- `ServerRepository` descubre servers y mantiene metadata/cache.
- `AuthenticationRepository` maneja credenciales, tokens y Quick Connect.
- `SessionRepository` restaura o cambia la sesion activa.
- `AuthenticationStore` persiste servers, users y access tokens en `authentication_store.json` dentro del sandbox de la app.

## Design system y UI

- `:design` expone tokens de color, radius, spacing y typography.
- La UI no es 100% Compose: conviven Compose, Leanback y layouts XML.
- Es una base mejor que la de los wrappers TV, pero todavia hibrida.

## Integracion con otros repos

- No depende del `dist/` web.
- Si cambia auth o Quick Connect del server, revisar este repo.
- Si cambia la API del server o el SDK de Jellyfin, revisar `sdk.version` y compatibilidad.

## CI/CD

- `app-build.yaml`: `assembleDebug` y upload de APKs.
- `app-test.yaml`: `./gradlew test`.
- `app-lint.yaml`: `./gradlew detekt lint`.
- `app-publish.yaml`: build release, firma, APK/AAB, GitHub Release y upload a `repo.jellyfin.org`.

## Packaging y deployment manual

- `assembleDebug` genera APK debug instalable manualmente.
- El release pipeline genera:
  - APK debug
  - APK release
  - AAB release
  - `version.txt`

## Calidad y tests

- Detekt y Android lint activos.
- `testOptions.unitTests.all { useJUnitPlatform() }`.
- Hay tests unitarios, pero la cobertura aparente es bastante menor que el tamaño del app.

## Smoke checks realizados en este host

- `./gradlew-local :app:compileDebugKotlin`: OK
- `./gradlew-local :app:testDebugUnitTest`: OK
- `./gradlew --no-daemon detekt :app:lintDebug :app:assembleDebug` con home aislado: OK
- artefacto generado: `app/build/outputs/apk/debug/jellyfin-androidtv-v0.0.0-dev.1-debug.apk`

Notas del host:

- `detekt` imprime mucha deuda preexistente del repo, pero no rompe el build actual.
- `lintDebug` pasa y deja reporte en `app/build/reports/lint-results-debug.html`.
- `lintDebug` puede imprimir un stack trace interno al generar quick fixes SARIF (`Update targetSdkVersion to 37`), pero el task termina `0`.

## Riesgos y notas

- El build local depende fuerte del host Android.
- La UI hibrida Compose/Leanback/XML aumenta el costo de cambio.
- La infraestructura de tooling esta bastante mejor que en `webOS` y `tizen`, pero el setup local es mas sensible.
- El repo tiene bastante deuda de `detekt` ya existente; no la introdujo esta feature.
