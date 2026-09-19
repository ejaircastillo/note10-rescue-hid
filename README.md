# Note10 Rescue HID

App Android mínima (se instala **sólo en el teléfono HOST**) que convierte el host
en un **teclado USB (AOA 2.0 HID)** para escribir el PIN conocido en un Note10 con
pantalla rota. Sin ADB, sin root, sin app en el Note10, sin red.

## Uso

1. Instalar el APK en el teléfono **HOST**: `app-debug.apk` (adb install o copiar y abrir).
2. Abrir la app "Note10 Rescue HID" en el host.
3. Conectar el Note10 con el cable **USB-C ↔ USB-C** (el que soporta datos).
4. Si Android pregunta, elegir **"Este dispositivo"** como controlador/host USB.
5. Tocar **Detectar dispositivos** y elegir el Note10 en la lista (VID/PID/nombre).
6. Conceder el **permiso USB** cuando aparezca el diálogo.
7. Tocar **Preparar HID (registrar teclado)**. Cuando el estado diga
   **● HID preparado**, el Note10 ya ve un teclado físico.
8. Despertar físicamente el Note10 con el botón lateral (AOA-HID sólo da entrada,
   no video).
9. Escribir el PIN en el campo PIN de la app.
10. Tocar **ENVIAR PIN UNA VEZ** → se envía la secuencia + ENTER y el botón queda
    bloqueado 10 s.

El campo PIN se limpia al enviar y no se guarda en ningún lado. Los controles
TAB / BACKSPACE / ENTER y **Desregistrar HID** están detrás de "Mostrar controles
manuales" y nunca se usan solos.

## Errores

| Código | Significado / qué hacer |
|---|---|
| `NO_USB_DEVICE` | No hay ningún dispositivo USB conectado: revisá el cable y que el Note10 esté encendido. |
| `USB_PERMISSION_DENIED` | Rechazaste el diálogo de permiso: tocá de nuevo Preparar HID y aceptá. |
| `OPEN_DEVICE_FAILED` | El host no pudo abrir la conexión (`openDevice()` = null). Cerrá otras apps que estén usando el USB (MTP/Smart Switch) y reintentá. |
| `AOA_PROTOCOL_QUERY_FAILED` | El control transfer 51 (GET_PROTOCOL) falló: el Note10 no respondió como accesorio. Probá desenchufar/reconectar; en Samsung podés usar el checkbox de fallback para un único intento forzado. |
| `AOA_PROTOCOL_UNSUPPORTED` | El Note10 respondió protocolo < 2 (AOA1): AOA2 HID no está soportado. Usá el checkbox "Intentar HID igualmente" para un único intento (algunos Samsung responden raro). |
| `REGISTER_HID_FAILED` | Falló `ACCESSORY_REGISTER_HID` (54). Probá de nuevo o usá el fallback. |
| `SET_DESCRIPTOR_FAILED` | Falló `SET_HID_REPORT_DESC` (56): el descriptor no se aceptó. |
| `SEND_REPORT_FAILED` | Falló `ACCESSORY_SEND_HID_EVENT` (57) al mandar una tecla. |
| `DEVICE_DISCONNECTED` | Se desconectó el Note10: se limpia la conexión; reconectá y repetí desde el paso 3. |
| `HID_NOT_PREPARED` | Intentaste enviar teclas sin "HID preparado": tocá primero Preparar HID. |
| `INVALID_PIN` | El campo PIN está vacío o tiene caracteres que no son dígitos. |

## Detalles técnicos

- AOA2, `UsbManager` + `UsbDeviceConnection.controlTransfer()`:
  `GET_PROTOCOL=51` (0xC0 IN), `REGISTER_HID=54`, `UNREGISTER_HID=55`,
  `SET_HID_REPORT_DESC=56`, `SEND_HID_EVENT=57` (0x40 OUT), `hidId=1`.
- Descriptor HID de teclado de 63 bytes portado de scrcpy (`hid_keyboard.c`);
  reports de 8 bytes `[modifiers][reserved][6 keycodes]`.
- Cada tecla: KEY DOWN → espera corta (30 ms) → KEY RELEASE, con 80 ms entre eventos.
- No hace brute force: una tecla por dígito escrito a mano, ENTER una sola vez,
  sin reintentos automáticos.

Ver `PLAN.md` para el detalle de qué se portó de scrcpy y qué no.

## APK

`dist/note10-rescue-hid-1.0-debug.apk` — APK debug compilado y verificado
(`BUILD SUCCESSFUL`, 33 unit tests en verde, sin ningún permiso declarado).

```
sha256  66b5511923f08d9e0485128aebb6ac39c084140bb792e9be416060fdb35637f1
```

Instalación desde una PC con ADB:

```bash
adb install -r dist/note10-rescue-hid-1.0-debug.apk
```

## Build

```bash
./gradlew assembleDebug     # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew test              # unit tests (dígito→keycode, reports, protocolo AOA)
```
