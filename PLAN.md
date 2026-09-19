# PLAN.md — Note10 Rescue HID

## Problema

El Note10 (target) tiene pantalla y táctil averiados. No tiene ADB autorizado.
El teléfono bueno (host) puede actuar como USB Host y ve al Note10 por USB-C↔USB-C.

Objetivo: que el host se comporte exactamente como un **teclado USB físico** conectado
al Note10, para escribir el PIN conocido + ENTER. Nada de crackeo, nada de ADB,
nada de root, nada de app en el target.

## Qué se porta de scrcpy (sólo lo mínimo)

Referencias revisadas:

- `app/src/usb/aoa_hid.c` (477 líneas) → **se porta** el uso de `controlTransfer`
  contra Android: `REGISTER_HID`, `SET_HID_REPORT_DESC`, `SEND_HID_EVENT`,
  `UNREGISTER_HID`, con `value = hidId` e `index` según comando.
  - `REGISTER_HID`: `index = longitud del report descriptor`, sin data.
  - `SET_HID_REPORT_DESC`: `value = hidId`, `index = 0`, `data = descriptor` completo
    (scrcpy envía el descriptor en **un solo** control transfer y delega la
    fragmentación en paquetes a la capa USB/kernel; se replica igual).
  - `SEND_HID_EVENT`: `value = hidId`, `index = 0`, `data = report` de 8 bytes.
  - `UNREGISTER_HID`: `value = hidId`, `index = 0`, sin data.
  - bmRequestType: `USB_DIR_OUT | USB_TYPE_VENDOR` = `0x40`; el GET_PROTOCOL usa
    `USB_DIR_IN | USB_TYPE_VENDOR` = `0xC0`. scrcpy **no** manda `ACCESSORY_START`
    para HID: registra el HID sobre la conexión actual (por eso el host no necesita
    cambiar al Note10 a "accessory mode").
- `app/src/usb/keyboard_aoa.c` → **se porta** sólo la idea: cada tecla = report
  de key-down y luego release (report de ceros). **No se porta** la parte de
  sincronización de mod-lock, ni la cola de eventos con acksync, ni el hilo
  productor/consumidor de scrcpy: no hacen falta para un PIN.
- `app/src/hid/hid_keyboard.c` → **se porta el descriptor de reporte**
  (`SC_HID_KEYBOARD_REPORT_DESC`), 63 bytes, con los mismos valores, incluidos
  `Usage Maximum (101)`/`Logical Maximum (101)` que en scrcpy se escriben como
  `SC_HID_KEYBOARD_KEYS - 1` y `SC_HID_KEYBOARD_KEYS`, con `SC_HID_KEYBOARD_KEYS = 0x66`.
  El descriptor es el estándar boot-keyboard HID: 8 bytes por report
  `[mods][reserved][k0..k5]`, y AOA2 no le agrega nada especial.
- `app/src/usb/scrcpy_otg.c` → **no se porta** (es el orquestador de la app de
  escritorio: SDL, ventana, mouse, gamepad).

## Qué NO se porta

cola de eventos / hilo AOA, mouse y gamepad AOA, ack de clipboard, acceso a
`sc_usb_*`, detección de desconexión por libusb, `ACCESSORY_START`, audio AOA,
strings de fabricante/modelo para accessory mode.

## Protocolo implementado

| comando | valor | requestType | value | index | data |
|---|---|---|---|---|---|
| ACCESSORY_GET_PROTOCOL | 51 | 0xC0 (IN) | 0 | 0 | 2 bytes → uint16 LE |
| ACCESSORY_REGISTER_HID | 54 | 0x40 (OUT) | hidId | len(descriptor) | — |
| ACCESSORY_UNREGISTER_HID | 55 | 0x40 (OUT) | hidId | 0 | — |
| ACCESSORY_SET_HID_REPORT_DESC | 56 | 0x40 (OUT) | hidId | 0 | descriptor |
| ACCESSORY_SEND_HID_EVENT | 57 | 0x40 (OUT) | hidId | 0 | report 8 bytes |

`hidId = 1`. Si `protocol < 2` → error `AOA_PROTOCOL_UNSUPPORTED`
(sin loop automático). El usuario puede forzar una vez con el checkbox de fallback.

## Clases

- `MainActivity` — UI única, orquesta; los control transfers corren en un executor
  de un solo hilo (nunca en el hilo de UI).
- `UsbDeviceManager` — enumeración, permiso (`requestPermission` + `PendingIntent` +
  `BroadcastReceiver`), attach/detach, `openDevice`.
- `UsbControlTransport` — única clase que toca `UsbDeviceConnection.controlTransfer()`
  y la que mide duración de cada transfer.
- `AoaHidKeyboard` — lógica AOA HID pura, **sin imports de Android**, con
  `ControlTransport` inyectable → testeable con `FakeTransport`.
- `HidKeyboardDescriptor` — descriptor de 63 bytes.
- `HidKeycodes` / `HidKeyboardReports` — mapeo dígito→keycode y reports de 8 bytes.
- `AoaProtocol` / `AoaError` / `AoaException` — constantes y errores con código.

## Seguridad del PIN

`numberPassword`, sin SharedPreferences/DB/archivo, `saveEnabled=false`, sin
permiso de red, `FLAG_SECURE`, el campo se limpia apenas se lee, los logs nunca
contienen dígitos ni keycodes (`describeSafely` sólo informa mods y cantidad de
teclas). Botón "ENVIAR PIN UNA VEZ" → una secuencia, luego bloqueo de 10 s.

## Build

Gradle 8.10.2 + AGP 8.8.2 + Kotlin 2.1.20, compileSdk 34 (SDK local), minSdk 24,
sin AndroidX (sólo framework) → `./gradlew assembleDebug` y `./gradlew test`.
Detalle del entorno de build y de lo verificado vs. no verificado en `README.md`.

## Estado

Implementado y compilado (`BUILD SUCCESSFUL`, 33 unit tests en verde, APK sin
permisos declarados). La verificación end-to-end con los dos teléfonos físicos
queda pendiente y está listada en la sección "Estado de verificación y
limitaciones" del `README.md`.
