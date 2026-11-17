# Dirección visual y recorrido

Actualizada 2026-10-30 por solicitud del usuario, a partir de sus referencias visuales OTO.

## Identidad

Modo oscuro predeterminado: fondo negro verdoso #080D0B, lima #D4FF33, turquesa #32DEBC, texto marfil #F1F5EC y secundario #A7B5A9. Ámbar para atención; gris para ausencia de datos. Las diferencias de estado se expresan con texto además de color.

Poppins para títulos/cuerpo, Space Mono para etiquetas y Doto para números. Fuentes empaquetadas, utilizables sin internet; licencias OFL en android/font-licenses/. Logotipo de píxeles, cuadrícula sutil, halos radiales y paneles translúcidos dibujados en Compose. Las decoraciones no simulan mediciones.

## Pantallas y lenguaje

- Revisar: explica qué hace la app; “Revisar mi Wi-Fi” inicia captura real y guardado local.
- Resultado: fuente Lectura real / LIVE o DEMO, cantidad de señales con datos, información parcial y avisos presentes. Ese conteo nunca es puntuación de seguridad.
- Indicadores: seguridad Wi-Fi, acceso a la red (portal cautivo), resolución DNS y conexión HTTPS. Tocar cada uno explica observación, propósito y límite.
- Acciones: guardado local visible; “Enviar al servidor” es voluntario; “Compartir archivo JSON” explica el formato; eliminar exige confirmación.
- Historial: fuente y estado Enviada / Enviando / Solo en el teléfono / Sin enviar · reintentar. “Consultar servidor” recupera GET real. Los errores internos no se convierten en etiquetas.
- Guía: qué hace HoneyPhone, pasos de uso, privacidad, límites y ejemplos DEMO.
- Volver: detalle→resultado; resultado de historial→historial; resultado DEMO→galería; galería→origen. Botón del sistema y flecha usan el mismo recorrido.

Controles con altura mínima, texto adaptable, scroll y márgenes del sistema. Se mantiene información disponible y desconocida; no se interpreta disponibilidad API como HTTPS validado.

## Límites

DNS/HTTPS propios sin configurar = UNKNOWN/NOT_CONFIGURED. LIVE usa Android; DEMO usa fixtures etiquetadas y no es una medición. No se ofrecen porcentajes, “red segura” ni “ataque confirmado”. No se guardan SSID/BSSID, ubicación ni credenciales.
