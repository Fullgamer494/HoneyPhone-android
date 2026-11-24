# HoneyPhone Android

## Cuentas y servidor

La app requiere una cuenta para abrir sus datos. Puedes crearla con correo y contraseña o continuar con Google. La sesión se conserva cifrada con una clave de Android Keystore, y cada cuenta usa un archivo Room separado; al iniciar sesión por primera vez se conserva la base local anterior dentro de esa cuenta. Los dispositivos, revisiones y sesiones remotas viajan con el token Bearer de esa cuenta.

El build de desarrollo usa `http://127.0.0.1:8081/api/v1/`; para un teléfono/emulador, reenvía ese puerto al API local con `adb reverse tcp:8081 tcp:8000`. El build release exige HTTPS. Configúralo con:

```bash
export HONEYPHONE_API_BASE_URL='https://honeyphone-api.myuspot.org/api/v1/'
export HONEYPHONE_GOOGLE_WEB_CLIENT_ID='tu-cliente-oauth-web.apps.googleusercontent.com'
./gradlew :app:assembleRelease
```

El ID de OAuth es público y no es el `client_secret`. Si el build se ejecuta desde el workspace completo, Gradle también puede leer solo el `client_id` del JSON OAuth de tipo Web en `../Google OAuth/`. Nunca incluyas el JSON ni el secreto de cliente en el APK o el repositorio.

La API debe tener `AUTH_SECRET_KEY` aleatorio y `GOOGLE_CLIENT_IDS` con el mismo ID OAuth Web. La verificación de correo y la recuperación de contraseña todavía dependen de la integración de correo transaccional y no están incluidas en este flujo.

App nativa Compose Material 3, applicationId `dev.honeyphone.app`. La versión actual se define en `app/build.gradle.kts`. Requiere minSdk 29, compileSdk 37, targetSdk 36, JDK 21 y SDK Android API 37.

## Compilar

Desde la raíz de este repositorio:

    ./gradlew :app:assembleDebug
    ./gradlew :app:testDebugUnitTest

El APK se genera en app/build/outputs/apk/debug/app-debug.apk. local.properties indica la ruta del SDK en una máquina y no se debe compartir.

## Release firmado

El release queda unsigned si no se proporcionan credenciales de firma. Para firmarlo localmente, configura `HONEYPHONE_RELEASE_STORE_FILE`, `HONEYPHONE_RELEASE_STORE_PASSWORD`, `HONEYPHONE_RELEASE_KEY_ALIAS` y `HONEYPHONE_RELEASE_KEY_PASSWORD` en el entorno de Gradle y ejecuta `./gradlew :app:assembleRelease`. Si se proporciona solo parte de esas variables, Gradle detiene la compilación para evitar crear un artefacto con una configuración accidental.

Configura también `HONEYPHONE_API_BASE_URL` con la URL base HTTPS de la API desplegada, por ejemplo `https://api.example.com/api/v1/`. La variable no tiene valor por defecto; por eso el release local sin configuración no sincroniza con una API. No guardes el keystore, contraseñas ni la URL de producción en el repositorio.

## Componentes

Compose y ViewModel presentan el flujo. RoomReviewRepository guarda revisiones y outbox. AndroidSignalReader consulta Wi-Fi y APIs del sistema. NetworkApi sincroniza manualmente con FastAPI.

Room usa migraciones explícitas y conserva el payload de reintento. La app no recopila SSID, BSSID, ubicación ni credenciales. DEMO usa fixtures sintéticas identificadas como simulación.

DNS TXT/DoH y HTTPS quedan como NOT_CONFIGURED hasta que se proporcione un destino controlado.

## Conectar el teléfono a la API

La API debe escuchar en 127.0.0.1:8000 en la computadora. Conecta y autoriza un único teléfono y ejecuta:

    scripts/usb-reverse.sh

También puedes usar adb reverse tcp:8081 tcp:8000. La app debug usa http://127.0.0.1:8081/api/v1/. El puente se pierde al desconectar o reiniciar el teléfono.

HTTP cleartext está limitado a loopback en debug. Release mantiene cleartext bloqueado.

Las referencias de interfaz están archivadas fuera del repositorio en `../material/referencias/android-ui/`. Las fuentes empaquetadas incluyen sus licencias en `font-licenses/`.

El señuelo HTTP real está en la pestaña **Señuelo**. Solo escucha cuando lo activas, se limita a la dirección Wi-Fi del teléfono y se detiene al salir de la app o de esa sección. Guarda localmente hasta 100 eventos con IP de origen, método, ruta y User-Agent; nunca lee ni conserva cuerpos de petición. Para probarlo, la Wi-Fi debe permitir comunicación entre clientes; desde otro dispositivo autorizado abre `http://<ip-del-teléfono>:8080/admin`.
