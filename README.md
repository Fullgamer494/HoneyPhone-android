# HoneyPhone Android

App nativa Compose Material 3, applicationId dev.honeyphone.app, versión 0.4.0. Requiere minSdk 29, compileSdk 37, targetSdk 36, JDK 21 y SDK Android API 37.

## Compilar

Desde la raíz de este repositorio:

    ./gradlew :app:assembleDebug
    ./gradlew :app:testDebugUnitTest

El APK se genera en app/build/outputs/apk/debug/app-debug.apk. local.properties indica la ruta del SDK en una máquina y no se debe compartir.

## Componentes

Compose y ViewModel presentan el flujo. RoomReviewRepository guarda revisiones y outbox. AndroidSignalReader consulta Wi-Fi y APIs del sistema. NetworkApi sincroniza manualmente con FastAPI.

Room usa migraciones explícitas y conserva el payload de reintento. La app no recopila SSID, BSSID, ubicación ni credenciales. DEMO usa fixtures sintéticas identificadas como simulación.

DNS TXT/DoH y HTTPS quedan como NOT_CONFIGURED hasta que se proporcione un destino controlado.

## Conectar el teléfono a la API

La API debe escuchar en 127.0.0.1:8000 en la computadora. Conecta y autoriza un único teléfono y ejecuta:

    scripts/usb-reverse.sh

También puedes usar adb reverse tcp:8081 tcp:8000. La app debug usa http://127.0.0.1:8081/api/v1/. El puente se pierde al desconectar o reiniciar el teléfono.

HTTP cleartext está limitado a loopback en debug. Release mantiene cleartext bloqueado.

La guía visual está en docs/UX_INTERFAZ.md. Las fuentes empaquetadas incluyen sus licencias en font-licenses/.

El señuelo HTTP real está en la pestaña **Señuelo**. Solo escucha cuando lo activas, se limita a la dirección Wi-Fi del teléfono y se detiene al salir de la app o de esa sección. Guarda localmente hasta 100 eventos con IP de origen, método, ruta y User-Agent; nunca lee ni conserva cuerpos de petición. Para probarlo, la Wi-Fi debe permitir comunicación entre clientes; desde otro dispositivo autorizado abre `http://<ip-del-teléfono>:8080/admin`.
