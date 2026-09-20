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

## Cómo maximizar que el PIN entre (y cómo saber si entró)

AOA-HID es un canal de **una sola dirección**: la app puede ver que los reports
salieron (el registro muestra `result=8` en cada `SEND_HID_EVENT`), pero Android
**no devuelve ninguna confirmación** de desbloqueo. Ese canal no existe.

Procedimiento recomendado:

1. Despertá el Note10 con el **botón lateral** (físico).
2. En la app, marcá **"Enviar TAB antes del PIN"**.
3. Escribí el PIN y tocá **ENVIAR PIN UNA VEZ**.
4. Esperá **3-5 s** antes de concluir nada (el primer desbloqueo tras un reinicio
   tarda por el desencriptado).

**Por qué la TAB previa**: Android suele **consumir el primer evento de teclado**
para despertar la pantalla. Si la pantalla estaba apagada, el PIN entra *corrido*
(sin el primer dígito) y falla sin dejar ningún error en el registro. Si el TAB no
era necesario, no molesta: en la pantalla de PIN no hace nada.

Indicios de que entró (ninguno es confirmación dura):

- **Vibración**: tené el Note10 en la mano al enviar. PIN incorrecto → aviso corto
  de fallo; correcto → desbloqueo.
- **Monitor USB**: la app mira el bus USB durante 8 s después del envío y registra
  `CAMBIO USB: re-enumeró ... (VID/PID 0x... -> 0x...)` si el Note10 cambia de
  configuración, que es lo típico al desbloquearse (activa MTP). Que diga
  `sin cambios en el bus USB` **no** prueba que no se haya desbloqueado.
- **Prueba MTP**: desenchufá el Note10 y conectalo a una PC. Android sólo expone el
  almacenamiento con el equipo desbloqueado: si aparece "Almacenamiento interno",
  se desbloqueó.

## Envío directo (botón OK) y PIN embebido

**Botón OK**: un toque hace todo el pipeline cuando hace falta (elegir el dispositivo
—el Samsung si hay uno—, pedir permiso, preparar el HID) y recién entonces envía
**una** secuencia. Está habilitado desde que abrís la app (no hace falta preparar nada
antes). Si el HID ya está preparado, envía directo. Después queda el mismo bloqueo de
10 s y **no hay reintentos automáticos**: cada envío es un toque tuyo.

El PIN que usa sale de esta precedencia:

1. **PIN embebido en el build** (build privado, ver abajo), o
2. el que escribas en el campo PIN (botón *ENVIAR PIN UNA VEZ*).

### PIN embebido: cómo se compila el APK privado

El repositorio es público, así que el PIN **no** puede estar en el código ni en el
APK publicado. El build lo lee de un archivo local que **está en .gitignore**:

```bash
cp pin-local.properties.example pin-local.properties
# editar pin-local.properties ->  pin=<tus 4 dígitos>
./gradlew assembleDebug
# o, directo:  ./build-privado.sh
```

- Con ese archivo, Gradle embebe el PIN **ofuscado** (XOR 0x5A + hex) en el APK y
  avisa por consola: `PIN embebido de N dígitos -> APK PRIVADO, no publicar`.
- Sin ese archivo (o sea, en cualquier clon del repo), el campo queda vacío, el
  botón OK avisa que no hay PIN y **el APK no lleva ningún PIN**.
- Si el archivo existe pero querés compilar el APK **público** (sin PIN):
  `./gradlew assembleDebug -PskipPin=true`.

Advertencias concretas:

- La ofuscación evita que aparezca como texto plano en el `.dex`; **no es
  criptografía**. Quien tenga ese APK puede recuperarlo. Por eso ese APK **no se
  publica ni se comparte**, y conviene que el PIN no sea el mismo que usás en otros
  equipos, cuentas o tarjetas.
- `pin-local.properties` nunca se versiona (verificado: está en `.gitignore`).
- El PIN nunca se muestra, nunca se registra en el log ni en la auditoría, y se
  descarta al cerrar la app (nada de SharedPreferences, archivos ni red).

## Reportar un problema

Dos botones abajo del registro:

- **Compartir registro**: arma un reporte con la versión de la app, el modelo y la
  versión de Android del host, el dispositivo elegido (VID/PID), el protocolo
  informado, el estado, los intentos de la sesión, el resumen del último envío, el
  estado USB observado, las últimas 300 líneas del registro **y la bitácora de
  auditoría** (`audit.log`, en el almacenamiento privado de la app). Lo manda por el
  menú de compartir (mail, mensajería, Drive…) — sin permisos y sin que la app use
  internet.
- **Copiar**: lo mismo, al portapapeles.

El registro **nunca** contiene el PIN ni los keycodes de los dígitos: sólo
cantidades (`6 dígitos`, `14 reports OK`) y metadatos técnicos de cada transfer
(request, result, duración). `FLAG_SECURE` sigue activo (no se pueden tomar
capturas): por eso el reporte se comparte como texto.

La app también muestra **"Intentos enviados en esta sesión: N"**, que suma sólo
cuando vos tocás el botón, y avisa a partir de 5 (Samsung empieza a demorar; con
"Restablecer de fábrica automático" activo, a los 15 intentos se borra solo). El
contador vive en memoria: se reinicia cuando cerrás la app, no guarda nada.

## Estado de verificación y limitaciones

Verificado en este repositorio (salida de herramientas, no estimaciones):

- `./gradlew clean test assembleDebug` → **BUILD SUCCESSFUL**.
- **66 unit tests, 0 fallas** (por variante: la tarea `test` corre debug y release):
  `AoaHidKeyboardTest` 31, `HidKeyboardReportsTest` 10, `HidKeycodesTest` 7,
  `UsbBusStateTest` 6, `PinVaultTest` 5, `AuditEntryTest` 4, `DiagnosticsReportTest` 3.
- Build privado verificado con un PIN real: el `.dex` **no** contiene el PIN en texto
  plano y **sí** la forma ofuscada; el APK público no contiene ninguna de las dos.
- Descriptor HID comparado byte a byte contra `scrcpy/app/src/hid/hid_keyboard.c`
  con las macros resueltas: **63 bytes, 0 diferencias**.
- APK inspeccionado con `aapt2 dump badging` / `dump permissions`: paquete
  `com.ejair.note10rescue`, `versionCode 5`, `versionName 1.0.4`, `minSdk 24`,
  `targetSdk 34`, `uses-feature usb.host`, **cero permisos declarados** (sin `INTERNET`).
- `sha256` del APK público publicado (v1.0.4):
  `866354b3f04fdd1afe42face0b613bca4b9ac5646657933541131e41bbcc3bda`.

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
v1.0.2                            <- versión en el registro

USB
[ Detectar dispositivos ]        <- enumera y lista en radio buttons
( ) Samsung ... VID 0x04E8 PID 0x...   <- se elige el Note10 acá
Dispositivo / VID / PID          <- datos del elegido
AOA protocol: 2                  <- versión informada por el Note10
[x] Intentar HID igualmente si GET_PROTOCOL falla o responde < 2 (un solo intento)
[ Preparar HID (registrar teclado) ]
● HID preparado                  <- estado ("(modo forzado)" si se usó el fallback)
Último envío: 6 dígitos, 14 reports OK, 1240ms       <- resumen en una línea
Intentos enviados en esta sesión: 1                  <- contador manual + aviso

PIN NUMÉRICO
[ •••••••• ]                     <- numberPassword, se limpia al enviar
[x] Enviar TAB antes del PIN     <- para el caso "se comió la primera tecla"
[ OK ]                           <- envío directo: un toque = una secuencia
   Envío directo listo: un toque = una secuencia.
[ ENVIAR PIN UNA VEZ ]           <- usa el PIN escrito a mano

[ Mostrar controles manuales ]   <- despliega el panel
   [ Enviar TAB ] [ Enviar BACKSPACE ] [ Enviar ENTER ] [ Desregistrar HID ]

REGISTRO TÉCNICO                 <- nunca muestra el PIN, y hace auto-scroll
[ Compartir registro ] [ Copiar ] [ Limpiar registro ]
00:00:00  Note10 Rescue HID v1.0.2 — todo lo que pasa queda en este registro
00:00:00  USB device detected: 1
00:00:00  USB permission granted
00:00:00  Connection opened
00:00:00  AOA protocol: 2
00:00:00  REGISTER_HID: OK
00:00:00  SET_HID_REPORT_DESC: OK
00:00:00  HID READY
00:00:00  wake key (TAB) sent first
00:00:00  6 digit sequence sent
00:00:00  ENTER sent
00:00:00  sequence summary: 6 dígitos, 14 reports OK, 1240ms
00:00:00  Monitor USB: /dev/bus/usb/001/002 (VID 0x04E8 PID 0x6860)
00:00:08  Monitor USB: fin de la ventana de 8s
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
  DiagnosticsReport.kt     arma el reporte compartible (nunca incluye el PIN)
  PinVault.kt              PIN embebido del build privado (decodifica el XOR+hex)
  AuditTrail.kt            audit.log en almacenamiento privado + formato de auditoría
  AoaProtocol.kt           51/54/55/56/57 y bmRequestType 0x40 / 0xC0
  AoaError.kt             códigos de error + AoaException
  ControlTransport.kt     interfaz de transporte (inyectable en tests)
app/src/test/java/com/ejair/note10rescue/
  FakeTransport.kt         transporte falso que graba cada control transfer
  AoaHidKeyboardTest.kt    protocolo: LE, orden, fallback, errores, PIN
  HidKeyboardReportsTest.kt reports y descriptor
  HidKeycodesTest.kt       mapeo dígito -> keycode
  UsbBusStateTest.kt       comparación de estado del bus USB
  DiagnosticsReportTest.kt el reporte compartible no filtra el PIN
  PinVaultTest.kt          formato del PIN embebido (ida y vuelta)
  AuditEntryTest.kt        líneas de auditoría sin filtrar el PIN
pin-local.properties.example   plantilla del build privado (el real está en .gitignore)
```

## APK

`dist/note10-rescue-hid-1.0.4-debug.apk` — APK debug de v1.0.4, compilado y verificado
(`BUILD SUCCESSFUL`, 66 unit tests en verde, descriptor idéntico a scrcpy, sin ningún
permiso declarado, **sin PIN embebido**: es el build público). 884.435 bytes.

```
sha256  866354b3f04fdd1afe42face0b613bca4b9ac5646657933541131e41bbcc3bda
```

El APK con el PIN embebido (build privado desde `pin-local.properties`, verificado
también: 0 apariciones del PIN en texto plano en el `.dex`) **no se publica**: se
compila localmente y se instala a mano.

Los APK de v1.0.3, v1.0.2, v1.0.1 y v1.0.0 quedan publicados sin cambios para
trazabilidad. Al estar todos firmados con la misma clave de debug, las actualizaciones
se instalan encima sin desinstalar.

Instalación desde una PC con ADB:

```bash
adb install -r dist/note10-rescue-hid-1.0.4-debug.apk
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

### v1.0.4

- **Corregido el botón OK**: antes quedaba deshabilitado hasta que el HID estuviera
  preparado, así que el pipeline automático (elegir dispositivo → permiso → preparar
  → enviar) era inalcanzable. Ahora está disponible desde que abrís la app y hace todo
  lo que falte antes de enviar.
- Nueva bandera `-PskipPin=true` para compilar el APK público aunque exista
  `pin-local.properties` en la máquina.
- Build privado verificado con un PIN real: 0 apariciones en texto plano en el `.dex`,
  1 aparición de la forma ofuscada.
- 66 unit tests, 0 fallas.

### v1.0.3

Envío directo y auditoría. Sigue sin permisos nuevos, sin red, sin brute force y sin
nada instalado en el Note10.

- **Botón OK** ("envío directo"): un toque encadena lo que falte (elegir dispositivo,
  permiso USB, preparar HID) y envía **una** secuencia, con el mismo bloqueo de 10 s.
  No hay loops ni reintentos automáticos.
- **PIN embebido opcional** para ese botón: se inyecta en tiempo de build desde
  `pin-local.properties` (en `.gitignore`), **ofuscado** (XOR 0x5A + hex). El repo y el
  APK publicado no contienen ningún PIN; el APK privado no se publica.
- **Bitácora de auditoría** (`audit.log`, almacenamiento privado de la app, sin
  permisos): una línea por intento con origen del PIN (*embebido*/ingresado), cantidad
  de dígitos, reports, duración, resultado, y las observaciones del bus USB. Se incluye
  en el reporte que compartís.
- 9 tests nuevos (66 en total): formato del PIN ofuscado y de la auditoría.
- El envío directo resuelve el PIN así: embebido si existe, si no el del campo PIN.

### v1.0.2

Sólo observabilidad y ayudas manuales; la función, la arquitectura y los permisos
son los mismos.

- **"Compartir registro"** y **"Copiar"**: exportan un reporte con contexto
  (versión, host, dispositivo, protocolo, estado, intentos, resumen del último
  envío) + las últimas 300 líneas del registro. Sin permisos, sin internet.
   `FLAG_SECURE` sigue activo, por eso el reporte es texto y no captura.
- **Auto-scroll** del registro: siempre queda a la vista lo último que pasó.
- **Resumen del último envío en una línea**: `6 dígitos, 14 reports OK, 1240ms`.
- **Monitor del bus USB 8 s después del envío**: registra si el Note10 se
  desconecta, re-enumera o reaparece (indicio de cambio de estado; no es
  confirmación).
- **Contador de intentos de la sesión** con aviso a partir de 5 (Samsung demora;
  15 con auto-reset borra el equipo). Sólo suma con tu clic, vive en memoria.
- **Casilla "Enviar TAB antes del PIN"**: opt-in, de un solo disparo, para el caso
  conocido en que Android consume el primer evento de teclado al despertar la
  pantalla y el PIN entraría corrido.
- 12 tests nuevos (57 en total): comparación de estado USB, reporte sin PIN,
  tecla de despertar y resumen de secuencia.

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
