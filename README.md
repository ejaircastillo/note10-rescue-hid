# Note10 Rescue HID

App Android mínima (se instala **sólo en el teléfono HOST**) que convierte el host
en un **teclado USB (AOA 2.0 HID)** para escribir el PIN conocido en un Note10 con
pantalla rota. Sin ADB, sin root, sin app en el Note10, sin red.

## Requisitos

**Teléfono HOST** (donde se instala la app)

- Android 7.0 (API 24) o superior, con soporte **USB Host** (`android.hardware.usb.host`;
  la app lo declara como `required=true`, así que no se instala en equipos sin OTG).
- Cable **USB-C ↔ USB-C con datos** (los cables sólo-carga no enumeran).
- Quedarse en la app mientras se envía el PIN: la recepción de `ATTACHED`/`DETACHED`
  y el permiso USB se registran mientras la Activity está visible.

**Teléfono TARGET** (el Note10)

- Encendido y desbloqueado a nivel de arranque (basta con despertarlo con el botón
  lateral). No se instala nada ni se habilita nada en el target.
- Debe exponer las vendor requests de AOA2 en su gadget USB. Es el supuesto de
  scrcpy (ver "Estado de verificación").

**Entorno de build** (sólo si vas a recompilar)

- JDK 17 (`JAVA_HOME` apuntando al JDK; el build no compila con JDK 8/11).
- Android SDK con `platforms;android-34` y `build-tools;34.0.0`.
- `local.properties` en la raíz del clon, no versionado:

  ```properties
  sdk.dir=C:/Users/<usuario>/AppData/Local/Android/Sdk
  ```

- Gradle 8.10.2 (lo baja el wrapper), AGP 8.8.2, Kotlin 2.1.20, `compileSdk 34`,
  `minSdk 24`, `targetSdk 34`, sin AndroidX. No hace falta conexión a internet
  salvo para la primera resolución de dependencias del build.

## Uso

1. Instalar el APK en el teléfono **HOST**. Opciones:
   - Descargar `note10-rescue-hid-1.0-debug.apk` desde la página de
     [releases](https://github.com/ejaircastillo/note10-rescue-hid/releases) y
     abrirlo en el teléfono (hay que permitir "instalar apps de origen desconocido"
     para el navegador o el gestor de archivos que lo abra).
   - O desde una PC con ADB: `adb install -r dist/note10-rescue-hid-1.0-debug.apk`.
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

## Estado de verificación y limitaciones

Verificado en este repositorio (salida de herramientas, no estimaciones):

- `./gradlew clean test assembleDebug` → **BUILD SUCCESSFUL**.
- **45 unit tests, 0 fallas** (por variante: la tarea `test` corre debug y release):
  `AoaHidKeyboardTest` 28, `HidKeyboardReportsTest` 10, `HidKeycodesTest` 7.
- Descriptor HID comparado byte a byte contra `scrcpy/app/src/hid/hid_keyboard.c`
  con las macros resueltas: **63 bytes, 0 diferencias**.
- APK inspeccionado con `aapt2 dump badging` / `dump permissions`: paquete
  `com.ejair.note10rescue`, `versionCode 2`, `versionName 1.0.1`, `minSdk 24`,
  `targetSdk 34`, `uses-feature usb.host`, **cero permisos declarados** (sin `INTERNET`).
- `sha256` del APK publicado (v1.0.1):
  `3f5ca6758ab383b166a7bdb5a75faeb81b4d8d6626f1390a2a58ab47b9670476`.

**No verificado** (requiere los dos teléfonos físicos, que no están disponibles
para quien escribió este código):

- Que el Note10 reciba las pulsaciones como si vinieran de un teclado USB físico
  (criterio de éxito end-to-end). Lo que sí está verificado es que los control
  transfers salen con los valores exactos que usa scrcpy.
- Que el gadget del Note10 responda a la vendor request 51 **estando enumerado
  como MTP**. Es el supuesto de `scrcpy --otg` (que no manda `ACCESSORY_START`).
  Si el gadget no la expone, el síntoma es `AOA_PROTOCOL_QUERY_FAILED` y NO un
  fallo de la app: probá el checkbox de fallback, desactivar la sesión MTP en el
  host, o reconectar.

Otras limitaciones conocidas:

- AOA HID da **sólo entrada**: no hay video ni screenshot del Note10 (por diseño;
  para eso scrcpy necesita ADB, que el Note10 no tiene autorizado).
- La app no tiene ícono propio (usa el default del sistema): es cosmético y se
  dejó fuera a propósito.
- No hay release firmado (ni keystore): el APK es `debug`.

## Interfaz (pantalla única)

```
NOTE10 RESCUE HID

USB
[ Detectar dispositivos ]        <- enumera y lista en radio buttons
( ) Samsung ... VID 0x04E8 PID 0x...   <- se elige el Note10 acá
Dispositivo / VID / PID          <- datos del elegido
AOA protocol: 2                  <- versión informada por el Note10
[x] Intentar HID igualmente si GET_PROTOCOL falla o responde < 2 (un solo intento)
[ Preparar HID (registrar teclado) ]
● HID preparado                  <- estado ("(modo forzado)" si se usó el fallback)

PIN NUMÉRICO
[ •••••••• ]                     <- numberPassword, se limpia al enviar
[ ENVIAR PIN UNA VEZ ]           <- se bloquea 10 s y muestra "Bloqueado N s…"

[ Mostrar controles manuales ]   <- despliega el panel
   [ Enviar TAB ] [ Enviar BACKSPACE ] [ Enviar ENTER ] [ Desregistrar HID ]

REGISTRO TÉCNICO                 <- nunca muestra el PIN
[ Limpiar registro ]
00:00:00  USB device detected: 1
00:00:00  USB permission granted
00:00:00  Connection opened
00:00:00  AOA protocol: 2
00:00:00  REGISTER_HID: OK
00:00:00  SET_HID_REPORT_DESC: OK
00:00:00  HID READY
00:00:00  6 dígitos enviados
```

Estados posibles: `● HID no preparado`, `● Trabajando…`, `● HID preparado`,
`● HID preparado (modo forzado)`, `● HID desregistrado`, `● Desconectado`,
`● Error <CÓDIGO>`.

## Fallback manual ("Intentar HID igualmente")

| Situación | Checkbox **desmarcado** | Checkbox **marcado** |
|---|---|---|
| GET_PROTOCOL responde >= 2 | HID normal | HID normal (igual) |
| GET_PROTOCOL responde < 2 | `AOA_PROTOCOL_UNSUPPORTED`, no registra nada | registra el HID igual → `● HID preparado (modo forzado)` |
| GET_PROTOCOL falla o responde incompleto | `AOA_PROTOCOL_QUERY_FAILED`, no registra nada | registra el HID igual → `● HID preparado (modo forzado)` |

El fallback es **un único intento**: no hay loops, ni reintentos automáticos, ni
"volver a probar solo". Requiere que la casilla la marques vos.

Marcarla **no** significa que el dispositivo soporte AOA2. Significa exactamente esto:
"mandamos los comandos HID directamente aunque la consulta inicial de protocolo no
haya sido concluyente". No hay garantía de que un Samsung (ni ningún equipo) acepte
ese camino: si no lo acepta, el síntoma es `REGISTER_HID_FAILED` o
`SET_DESCRIPTOR_FAILED`, no un `HID preparado`.

En modo forzado la UI muestra `AOA protocol: no disponible / forzado` y
`● HID preparado (modo forzado)` — nunca `AOA protocol: -1`, porque no hay ninguna
versión real que informar.

## Errores

| Código | Significado / qué hacer |
|---|---|
| `NO_USB_DEVICE` | No hay ningún dispositivo USB conectado: revisá el cable y que el Note10 esté encendido. |
| `USB_PERMISSION_DENIED` | Rechazaste el diálogo de permiso: tocá de nuevo Preparar HID y aceptá. |
| `OPEN_DEVICE_FAILED` | El host no pudo abrir la conexión (`openDevice()` = null). Cerrá otras apps que estén usando el USB (MTP/Smart Switch) y reintentá. |
| `AOA_PROTOCOL_QUERY_FAILED` | El control transfer 51 (GET_PROTOCOL) falló: el dispositivo no respondió como accesorio AOA. Sin el checkbox marcado el flujo se detiene acá; con "Intentar HID igualmente" marcado, la app **sí** intenta el HID (modo forzado, un solo intento). Ver "Fallback manual". |
| `AOA_PROTOCOL_UNSUPPORTED` | El dispositivo respondió protocolo < 2 (AOA1): no declara AOA2. Sin el checkbox, se detiene; con "Intentar HID igualmente" marcado, intenta el HID igual (un solo intento). |
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
- Cada control transfer se registra con **request, result y duración** (nunca el
  buffer), así que el registro técnico no permite reconstruir el PIN.
- Si `SET_HID_REPORT_DESC` falla, se ejecuta `UNREGISTER_HID` automáticamente
  (misma semántica que `sc_aoa_setup_hid()` de scrcpy) y el estado interno vuelve
  a "no registrado", aunque el cleanup también falle. El error que se muestra
  sigue siendo `SET_DESCRIPTOR_FAILED`, no el del cleanup.
- No hace brute force: una tecla por dígito escrito a mano, ENTER una sola vez,
  sin reintentos automáticos.

Ver `PLAN.md` para el detalle de qué se portó de scrcpy y qué no.

## Estructura del repositorio

```
PLAN.md                  diseño: qué se portó de scrcpy y qué no
README.md                este archivo
dist/                    APK debug compilado (sha256 en la sección APK)
gradle/, gradlew(.bat)   wrapper de Gradle 8.10.2
app/build.gradle.kts     AGP 8.8.2 / Kotlin 2.1.20, compileSdk 34, sin AndroidX
app/src/main/AndroidManifest.xml   uses-feature usb.host, sin permiso INTERNET
app/src/main/res/        layout (pantalla única) + strings
app/src/main/java/com/ejair/note10rescue/
  MainActivity.kt          UI + orquestación (USB en un executor de 1 hilo)
  UsbDeviceManager.kt      deviceList, permiso USB, attach/detach, openDevice
  UsbControlTransport.kt   única clase que llama a controlTransfer()
  AoaHidKeyboard.kt        protocolo AOA HID puro (sin imports de Android)
  HidKeyboardDescriptor.kt descriptor HID de 63 bytes (portado de scrcpy)
  HidKeycodes.kt           dígito -> HID usage id + etiquetas seguras de log
  HidKeyboardReports.kt    reports de 8 bytes (down / release / ENTER)
  AoaProtocol.kt           51/54/55/56/57 y bmRequestType 0x40 / 0xC0
  AoaError.kt             códigos de error + AoaException
  ControlTransport.kt     interfaz de transporte (inyectable en tests)
app/src/test/java/com/ejair/note10rescue/
  FakeTransport.kt         transporte falso que graba cada control transfer
  AoaHidKeyboardTest.kt    protocolo: LE, orden, fallback, errores, PIN
  HidKeyboardReportsTest.kt reports y descriptor
  HidKeycodesTest.kt       mapeo dígito -> keycode
```

## APK

`dist/note10-rescue-hid-1.0.1-debug.apk` — APK debug de v1.0.1, compilado y verificado
(`BUILD SUCCESSFUL`, 45 unit tests en verde, descriptor idéntico a scrcpy, sin ningún
permiso declarado). 866.003 bytes.

```
sha256  3f5ca6758ab383b166a7bdb5a75faeb81b4d8d6626f1390a2a58ab47b9670476
```

El APK de v1.0.0 (`dist/note10-rescue-hid-1.0-debug.apk`) queda publicado sin cambios
para trazabilidad.

Instalación desde una PC con ADB:

```bash
adb install -r dist/note10-rescue-hid-1.0.1-debug.apk
```

## Build

Requisitos en la sección [Requisitos](#requisitos) (JDK 17, `platforms;android-34`,
`build-tools;34.0.0`, `local.properties`). En Windows conviene exportar antes
`JAVA_HOME` al JDK 17.

```bash
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew test                 # unit tests (debug + release)
./gradlew testDebugUnitTest    # sólo los unit tests de debug
./gradlew clean                # limpiar
```

Los resultados de los tests quedan en `app/build/test-results/testDebugUnitTest/`
(un XML por clase) y el reporte HTML en
`app/build/reports/tests/testDebugUnitTest/index.html`.

## Cambios

### v1.0.1

- Descriptor HID corregido para coincidir con scrcpy: `Usage Maximum` y
  `Logical Maximum` = `0x65` (`SC_HID_KEYBOARD_KEYS - 1`), no `0x66`.
- El checkbox "Intentar HID igualmente" ahora también funciona cuando
  `ACCESSORY_GET_PROTOCOL` **falla** (antes sólo actuaba con protocolo < 2).
- Si `SET_HID_REPORT_DESC` falla, se desregistra el HID automáticamente
  (semántica de `sc_aoa_setup_hid()` de scrcpy) y el estado interno queda en
  "no registrado" pase lo que pase con el cleanup.
- El modo forzado se muestra como `AOA protocol: no disponible / forzado` y
  `● HID preparado (modo forzado)`; nunca un `-1` como si fuera una versión.
- 12 tests nuevos de regresión (modo forzado, cleanup, descriptor byte a byte).
- Sin permisos nuevos, sin red, sin brute force, sin cambios de arquitectura.

### v1.0.0

- Versión inicial: AOA2 HID sobre `UsbDeviceConnection.controlTransfer()`,
  descriptor de 8 bytes por report, PIN manual + ENTER, cooldown de 10 s.

## Licencia

Sin licencia: por defecto, todos los derechos reservados. Si necesitás reusarlo
con otros términos, pedí que se agregue MIT o Apache-2.0.
