Corrige un defecto grave del "Modo de prueba": **el modo se leía de la casilla en el momento del envío, no del transporte con el que se había preparado el HID**. Si el HID ya estaba preparado en modo real y después marcabas "Modo de prueba", la app registraba `MODO DE PRUEBA: transporte simulado — no se abre ni se toca el Note10` y **escribía de verdad en el Note10**, sin contarlo como intento.

Detectado en el registro de campo del 2026-09-20: el "modo de prueba" de las 14:32:13 mandó **10 reports al dispositivo** (4 dígitos + ENTER) con la etiqueta de prueba y la auditoría `modo=PRUEBA_sin_envio`.

* **El modo ahora es una propiedad del teclado preparado** (`AoaHidKeyboard.simulated`), no de la casilla: el registro, la auditoría (`transporte=usb|simulado`) y el contador de intentos leen la verdad del transporte.
* **Cambiar la casilla descarta el HID preparado** y avisa: el próximo envío lo vuelve a preparar con el transporte correcto. Un "modo de prueba" ya no puede escribir en el Note10.
* **Nada falla en silencio**: se agregó `catch (Throwable)` al envío (antes, una excepción que no fuera `AoaException` moría dentro del executor y el envío no dejaba ni una línea) y un **watchdog de 15 s** que avisa si un envío no deja resultado.
* Cada toque deja constancia: `Envío #N solicitado (transporte=USB|simulado)`.
* La casilla ahora loguea algo distinto de un envío (`Casilla "Modo de prueba" ACTIVADA/DESACTIVADA`), porque antes el log de la casilla se confundía con un envío sin reports.
* Error nuevo `INTERNAL_ERROR` para fallos inesperados.
* 99 unit tests, 0 fallas. Sin permisos nuevos, sin red, sin brute force, nada instalado en el Note10.

**Assets**
* `note10-rescue-hid-1.0.9-pin-debug.apk` — con el PIN embebido (publicado a pedido del dueño). sha256 `7681cd4e8315fc9eecd1f1aee42dec476300fc69b826257c84a753f311c3f72a`.
* `note10-rescue-hid-1.0.9-debug.apk` — build público, sin PIN. sha256 `24e3907fe9013e7485499158f5256a9ec79b9a276e7370c4cd9849bf4a2da727`.
