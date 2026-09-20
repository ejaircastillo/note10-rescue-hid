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
  (`SC_HID_KEYBOARD_REPORT_DESC`), 63 bytes, con los mismos valores. Las dos
  últimas constantes de la lista de teclas se escriben en scrcpy como
  `SC_HID_KEYBOARD_KEYS - 1` (en las **dos**: Usage Maximum y Logical Maximum) con
  `SC_HID_KEYBOARD_KEYS = 0x66` definido en `hid_keyboard.h`, o sea **0x65** (101);
  y `SC_HID_KEYBOARD_MAX_KEYS` = 6 como Report Count. v1.0.0 tenía `0x66` en esas
  dos posiciones: v1.0.1 las corrige y agrega un test que compara el descriptor
  completo byte a byte contra el C de scrcpy.
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

`hidId = 1`. `prepare()` decide así, sin loops ni reintentos automáticos:

| GET_PROTOCOL | checkbox "Intentar HID igualmente" | resultado |
|---|---|---|
| responde >= 2 | (cualquiera) | `REGISTER_HID` + `SET_HID_REPORT_DESC` → `HID READY` |
| responde < 2 | desmarcado | `AOA_PROTOCOL_UNSUPPORTED`, no registra nada |
| responde < 2 | marcado | registra el HID igual (`PROTOCOL_UNKNOWN` = modo forzado) |
| falla / incompleto | desmarcado | `AOA_PROTOCOL_QUERY_FAILED`, no registra nada |
| falla / incompleto | marcado | registra el HID igual (`PROTOCOL_UNKNOWN` = modo forzado) |

El fallback es **un único intento** y exige acción explícita del usuario. No
implica que el dispositivo soporte AOA2: sólo manda los comandos HID sin una
consulta de protocolo concluyente.

Si `SET_HID_REPORT_DESC` falla: se ejecuta `UNREGISTER_HID` y se relanza el error
original (`SET_DESCRIPTOR_FAILED`), igual que `sc_aoa_setup_hid()` en `aoa_hid.c`.
El fallo del cleanup no reemplaza el error principal y `registered` queda en
`false` en todos los caminos (register fallido, descriptor fallido, unregister
fallido).

## Clases

- `MainActivity` — UI única, orquesta; los control transfers corren en un executor
  de un solo hilo (nunca en el hilo de UI).
- `UsbDeviceManager` — enumeración, permiso (`requestPermission` + `PendingIntent` +
  `BroadcastReceiver`), attach/detach, `openDevice`. Registro dinámico de receivers
  (mientras la Activity está visible): en API 33+ `ATTACHED`/`DETACHED` necesitan
  `RECEIVER_EXPORTED` y el broadcast propio de permiso `RECEIVER_NOT_EXPORTED`.
- `UsbControlTransport` — única clase que toca `UsbDeviceConnection.controlTransfer()`
  y la que mide duración de cada transfer.
- `AoaHidKeyboard` — lógica AOA HID pura, **sin imports de Android**, con
  `ControlTransport` inyectable → testeable con `FakeTransport`.
- `HidKeyboardDescriptor` — descriptor de 63 bytes.
- `HidKeycodes` / `HidKeyboardReports` — mapeo dígito→keycode y reports de 8 bytes.
- `AoaProtocol` / `AoaError` / `AoaException` — constantes y errores con código.
- `DiagnosticsReport` — arma el reporte técnico que el usuario comparte o copia
  (puro, testeable; nunca incluye el PIN).
- `PinVault` — decodifica el PIN embebido (XOR 0x5A + hex). El valor llega por
  `BuildConfig`, generado desde `pin-local.properties` (versionado a propósito por
  decisión del dueño del equipo).
- `AuditTrail` / `AuditEntry` — bitácora `audit.log` en `filesDir` (sin permisos) y su
  formato puro y testeable.
- `DeviceSnapshot` + `UsbDeviceManager.describeSnapshots()` / `diffSnapshots()` —
  comparación del estado del bus USB antes/después de un envío.
- `SimulatedTransport` — transporte falso del **modo de prueba**: responde como un
  Android con AOA2 (protocolo 2, descriptor aceptado, reports de 8) sin tocar ningún
  dispositivo, para ejercitar el pipeline completo sin gastar intentos de desbloqueo.

## Hallazgo de campo (v1.0.6) y veredicto de desbloqueo (v1.0.7)

El primer ensayo contra un Note10 real (2026-09-20) confirmó el camino elegido
(protocolo 2, `REGISTER_HID` OK, descriptor de 63 bytes aceptado, 12 reports con
`result=8`) y dejó un defecto concreto: **el primer `SEND_HID_EVENT` inmediatamente
después de `SET_HID_REPORT_DESC` es rechazado con `result=-1`**; el mismo envío, un par
de minutos después, pasa sin problema. Es una carrera de inicialización del lado
Android, no un problema del descriptor ni del protocolo.

Correcciones (v1.0.6):

- espera de asentamiento de 1500 ms después del descriptor, antes del primer evento;
- reintento del **mismo** report hasta 3 veces (1 s entre intentos) si el dispositivo lo
  rechaza: una transferencia rechazada no entrega teclas, así que no duplica pulsaciones
  ni consume intentos de desbloqueo (no viola la regla de "sin reintentos automáticos de
  secuencia", que sigue intacta);
- casilla manual "limpiar el campo antes del PIN" (12 BACKSPACE), para que un envío
  cortado a mitad no deje dígitos pegados en el campo del bloqueo.

En el mismo ensayo quedó claro el hueco más importante del reporte: **no decía si el
teléfono se desbloqueó o no**. AOA-HID es unidireccional, así que no se puede leer del
protocolo; la salida (v1.0.7) es registrar los indicios observables y emitir un
veredicto explícito con su fuerza relativa:

- **MTP en una PC** (decisivo): Android sólo expone el almacenamiento desbloqueado.
- **Vibración de rechazo** (alta): Android vibra con el PIN incorrecto.
- **Cambio de configuración USB en el host** (a favor, no concluyente): por eso no
  alcanza para cambiar el veredicto.

`UnlockEvidence` (lógica pura) concentra esa matriz y `DiagnosticsReport` la incluye en
el reporte, junto con la aclaración de que con la vibración del sistema desactivada
"no vibró" no significa nada.

## Permiso USB (v1.0.8): verificar no es pedir

Segundo hallazgo del mismo ensayo: el registro mostró cinco `USB_PERMISSION_DENIED`
seguidos **sin una sola línea de solicitud**. El botón "Preparar HID" comprobaba
`hasPermission()` y fallaba, pero nunca llamaba a `requestPermission()`: mientras el
permiso estaba cacheado de una conexión anterior el flujo andaba, y en cuanto se perdió
(el cable se reconecta, el permiso es por sesión de conexión) el botón quedó en un error
sin salida. Regla: **si el permiso es necesario para la acción, pedilo en ese camino**,
no lo verifiques y abortes.

Además, el resultado del diálogo no se puede dar por seguro (el broadcast puede no
llegar), así que `PermissionPoll` sondea `UsbManager.hasPermission()` cada 500 ms hasta
20 s como vía independiente, y el PendingIntent pasó a `FLAG_MUTABLE` (obligatorio en
API 31+ para que el sistema agregue los extras). El timeout tiene su propio código
(`USB_PERMISSION_TIMEOUT`) para no reportar como "denegado" algo que nunca se preguntó.

## Modo de prueba (v1.0.9): el modo es del transporte, no de la casilla

Tercer hallazgo del mismo ensayo, y el más peligroso: el modo (real/simulado) se leía de
la casilla **en el momento del envío**, pero el transporte se elige al **preparar** el
HID. Con el HID preparado en modo real y la casilla marcada después, la app registraba
`MODO DE PRUEBA: transporte simulado — no se abre ni se toca el Note10` y **escribía de
verdad en el target** (10 reports: 4 dígitos + ENTER), sin contarlo como intento. Un
"ensayo en seco" gastaba un intento real y la auditoría lo etiquetaba como prueba.

Regla: **el modo tiene que ser una propiedad del objeto que envía** (`AoaHidKeyboard`
recibe `simulated`), y el log, la auditoría y el contador leen de ahí. Cambiar la casilla
descarta el HID preparado, porque ya no representa el modo pedido.

Y una segunda regla del mismo episodio: **nada puede fallar en silencio**. El envío sólo
capturaba `AoaException`; cualquier otra excepción moría dentro del executor sin dejar
una línea, y el usuario no podía saber si se había mandado algo. Ahora hay
`catch (Throwable)` con `INTERNAL_ERROR` y un watchdog de 15 s que avisa si un envío no
deja resultado. En una herramienta que gasta intentos irreversibles, el silencio es el
peor comportamiento posible.

## Sonda MTP (v1.0.10): la única confirmación objetiva posible

El usuario no tiene otro cable con datos que el USB-C ↔ USB-C entre los dos teléfonos, así
que la comprobación "¿la PC ve el almacenamiento?" (el indicio decisivo de v1.0.7) no
estaba disponible. Pero el indicio no necesita una PC: **Android sólo expone el
almacenamiento por MTP con el equipo desbloqueado**, así que preguntarle al target por
`GetStorageIDs` responde lo mismo, y se puede hacer por el cable que ya está conectado.

Implementación: `MtpProbe` (contenedores PIMA 15740 y el flujo
`GetDeviceInfo` → `OpenSession` → `GetStorageIDs` → `CloseSession`, lógica pura y
testeable) y `UsbMtpChannel` (reclama la interfaz clase 6 y usa sus endpoints bulk,
reutilizando la conexión del HID: los control transfers de AOA van por EP0 y no chocan con
los bulk de MTP). Un contenedor puede llegar partido en varias transferencias, así que la
lectura usa la longitud declarada para juntarlo.

Resultado: `Unlocked(n)` ⇒ el reporte pasa a `CONFIRMADO`; `Locked` (0 almacenamientos o
`AccessDenied`) ⇒ `NO DESBLOQUEADO`; cualquier otra cosa ⇒ `Inconclusive` con el motivo.
No manda ninguna tecla, así que no gasta intentos.

## Seguridad del PIN

`numberPassword`, sin SharedPreferences/DB/archivo, `saveEnabled=false`, sin
permiso de red, `FLAG_SECURE`, el campo se limpia apenas se lee, los logs nunca
contienen dígitos ni keycodes (`describeSafely` sólo informa mods y cantidad de
teclas). Botón "ENVIAR PIN UNA VEZ" → una secuencia, luego bloqueo de 10 s.

## Observabilidad (v1.0.2)

- `AoaHidKeyboard.sendPinAndEnter(pin, wakeKeyFirst)` devuelve `SequenceStats`
  (dígitos, reports, duración, si mandó la tecla de despertar) y deja un resumen de
  una línea en el registro. Nunca los dígitos ni los keycodes.
- Monitor del bus USB durante 8 s después del envío (`DeviceSnapshot` +
  `diffSnapshots`): desconexión / re-enumeración / reaparición. Es el único indicio
  externo posible del lado del host; no es una confirmación.
- `DiagnosticsReport` arma el texto que se comparte (versión, host, target,
  protocolo, estado, intentos de la sesión, último envío, estado USB observado y las
  últimas 300 líneas). `FLAG_SECURE` se mantiene: el reporte es texto, no captura.
- Contador de intentos de la sesión, en memoria, que suma sólo con el clic del
  usuario (Samsung escala desde los 5 intentos fallidos).

## Envío directo y PIN embebido (v1.0.3)

- Botón **OK**: pipeline completo (dispositivo → permiso → HID) y luego **una**
  secuencia, con el cooldown de 10 s. Sin loops, sin reintentos automáticos. Habilitado
  desde el arranque (v1.0.4: antes quedaba deshabilitado hasta preparar el HID y el
  pipeline era inalcanzable).
- PIN embebido: `pin-local.properties` (versionado a propósito) → `buildConfigField`
  ofuscado (XOR 0x5A + hex, ver `PinVault`). `-PskipPin=true` compila el APK público sin
  PIN; el APK con PIN se publica como asset del release y en
  `dist/note10-rescue-hid-<versión>-pin-debug.apk`.
- Auditoría: `AuditTrail` escribe `audit.log` en `filesDir` (sin permisos) con origen
  del PIN, dígitos, reports, duración, resultado y observaciones USB; el reporte
  compartible la incluye. Nunca el PIN ni los keycodes.

## Build

Gradle 8.10.2 + AGP 8.8.2 + Kotlin 2.1.20, compileSdk 34 (SDK local), minSdk 24,
sin AndroidX (sólo framework) → `./gradlew assembleDebug` y `./gradlew test`.
Detalle del entorno de build y de lo verificado vs. no verificado en `README.md`.

## Estado

v1.0.5: implementado y compilado (`BUILD SUCCESSFUL`) con 71 unit tests en verde
—incluido el pipeline completo contra `SimulatedTransport`—, descriptor idéntico a
scrcpy (0 diferencias byte a byte) y APK público sin permisos ni PIN. La verificación
física de la inyección en los dos teléfonos queda pendiente y está listada en la
sección "Estado de verificación y limitaciones" del `README.md`.
