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
   - Descargar `note10-rescue-hid-1.0.7-pin-debug.apk` desde la página de
     [releases](https://github.com/ejaircastillo/note10-rescue-hid/releases) y
     abrirlo en el teléfono (hay que permitir "instalar apps de origen desconocido"
     para el navegador o el gestor de archivos que lo abra).
   - O desde una PC con ADB: `adb install -r dist/note10-rescue-hid-1.0.7-pin-debug.apk`.
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
- **Monitor USB**: la app mira el bus USB durante 20 s después del envío y registra
  `CAMBIO USB: re-enumeró ... (VID/PID 0x... -> 0x...)` si el Note10 cambia de
  configuración, que es lo típico al desbloquearse (activa MTP). Que diga
  `sin cambios en la ventana de 20s` **no** prueba que no se haya desbloqueado.
- **Prueba MTP**: desenchufá el Note10 y conectalo a una PC. Android sólo expone el
  almacenamiento con el equipo desbloqueado: si aparece "Almacenamiento interno",
  se desbloqueó.

## Veredicto de desbloqueo (¿entró o no?)

El protocolo AOA-HID es **unidireccional**: la app manda teclas y no recibe ninguna
confirmación, así que no puede "leer" del Note10 si se desbloqueó. Lo que sí hace
(v1.0.7) es juntar los indicios observables, dejar un **veredicto explícito** en la
pantalla y en el reporte, y no afirmar más de lo que se puede sostener.

Sección **RESULTADO OBSERVADO** de la pantalla:

```
RESULTADO OBSERVADO (¿se desbloqueó?)
Desbloqueo: RECHAZADO — el Note10 vibró (Android avisa así el PIN incorrecto)
( ) Sin dato todavía
( ) No vibró
(•) Vibró (Android avisa así el PIN incorrecto)
[ Comprobar desbloqueo (MTP en una PC) ]
```

Marcá la vibración después de cada envío, y cuando puedas hacé la comprobación por
MTP (el botón abre la guía: desenchufar del host → conectar a una PC → mirar si
aparece el almacenamiento). El veredicto se recalcula solo y queda en la bitácora.

**Matriz de decisión** (en este orden de fuerza):

| Indicio | Veredicto | Confianza |
|---|---|---|
| La PC muestra el almacenamiento del Note10 (MTP) | `CONFIRMADO — el Note10 está desbloqueado` | Decisivo |
| La PC no muestra nada | `NO DESBLOQUEADO — la PC no vio el almacenamiento` | Decisivo |
| Vibró al enviar | `RECHAZADO — Android avisa así el PIN incorrecto` | Alta |
| No vibró, sin dato de MTP | `PROBABLE — falta confirmar con MTP` | Media |
| Sin dato de vibración | `INDETERMINADO — falta el dato de vibración` | Nula |
| No hubo envíos | `SIN ENVÍOS — no hubo ningún envío en esta sesión` | — |

El **cambio de configuración USB** en el host es un indicio a favor cuando ocurre,
pero no alcanza para confirmar nada por sí solo: por eso no cambia el veredicto.

Ojo con un detalle importante: si el Note10 tiene la **vibración del sistema
desactivada**, no vas a sentir nada aunque el PIN falle. Por eso `PROBABLE` nunca se
muestra como confirmación, y el reporte lo aclara por escrito.

## Envío directo (botón OK) y PIN embebido

**Botón OK**: un toque hace todo el pipeline cuando hace falta (elegir el dispositivo
—el Samsung si hay uno—, pedir permiso, preparar el HID) y recién entonces envía
**una** secuencia. Está habilitado desde que abrís la app (no hace falta preparar nada
antes). Si el HID ya está preparado, envía directo. Después queda el mismo bloqueo de
10 s y **no hay reintentos automáticos**: cada envío es un toque tuyo.

El PIN que usa sale de esta precedencia:

1. **PIN embebido en el build** (ver abajo), o
2. el que escribas en el campo PIN (botón *ENVIAR PIN UNA VEZ*).

### PIN embebido: cómo se compila el APK

El PIN vive en `pin-local.properties` y **está publicado en este repositorio a pedido
del dueño del dispositivo** (es el PIN de un Note10 viejo, de pantalla rota, que no se
reutiliza en ningún otro servicio). Cualquiera puede leerlo y extraerlo del APK: es una
decisión explícita del dueño, no un descuido.

```bash
./gradlew assembleDebug            # APK CON el PIN embebido (por defecto)
./gradlew assembleDebug -PskipPin=true   # APK SIN PIN (build público)
./build-privado.sh                 # compila, copia a dist/ con el nombre de la versión y calcula sha256
```

- El PIN se embebe **ofuscado** (XOR 0x5A + hex): no aparece como texto plano en el
  `.dex`, pero es recuperable en un minuto (la ofuscación está documentada acá mismo).
- El APK con PIN está publicado como asset del release
  (`note10-rescue-hid-<versión>-pin-debug.apk`) y también en `dist/`.
- El PIN nunca se muestra en la app, nunca se registra en el log ni en la auditoría.

Advertencias que siguen valiendo:

- Si algún día querés dejar de exponerlo, hay que **cambiar el PIN en el teléfono** y
  reescribir el historial del repo: mientras el repositorio sea público, el valor queda
  en los commits viejos, en las copias y en los mirrors.
- Como la pantalla del Note10 está rota, hoy **no podés cambiar el PIN** desde el
  equipo: eso recién es posible después de desbloquearlo.

## Modo de prueba (validar el flujo sin gastar intentos)

Con la casilla **"Modo de prueba"** marcada, la app usa un transporte simulado que
responde exactamente como un Android con AOA2 (protocolo 2, descriptor aceptado,
reports de 8 bytes) pero **no abre ni toca ningún dispositivo**. Un toque en OK
recorre todo el pipeline —elegir dispositivo, preparar HID, tecla de despertar,
secuencia, resumen, monitor USB, auditoría— y lo deja en el registro. Cada corrida se
etiqueta `Prueba #N (no cuenta como intento)` y en la auditoría queda como
`modo=PRUEBA_sin_envio`, así que **no consume intentos de desbloqueo** en el target.

Sirve para comprobar que la app está en condiciones antes de gastar un intento real.

## Envío rechazado (`result=-1`): no gasta un intento

Medido en un Note10 real (reporte de campo del 2026-09-20): si el primer
`SEND_HID_EVENT` sale **inmediatamente** después de `SET_HID_REPORT_DESC`, el
dispositivo lo rechaza con `result=-1`; dos minutos después, los mismos reports
pasan con `result=8` sin tocar nada. Es una carrera de inicialización del lado
Android: el dispositivo HID todavía no está listo para recibir eventos.

Desde v1.0.6 la app cubre ese caso sola:

- Después de `SET_HID_REPORT_DESC` espera **1500 ms** (`esperando 1500ms a que el
  dispositivo acepte eventos HID` → `listo para enviar`) antes del primer evento.
- Si un report igual es rechazado, **reintenta el mismo report** hasta 3 veces, con
  1 s entre intentos, y lo deja en el registro
  (`SEND_HID_EVENT rechazado (result=-1); reintento 1/3 en 1000ms`).

Eso **no** es un reintento de PIN: una transferencia rechazada no entrega ninguna
tecla, así que no duplica pulsaciones ni cuenta como intento de desbloqueo. El
reintento de secuencia sigue siendo tuyo: la app no vuelve a mandar el PIN sola.

Si un envío **se corta a mitad** de la secuencia (se agotan los reintentos), el
campo del bloqueo puede quedar con dígitos pegados. Para eso está la casilla
**"Limpiar el campo antes del PIN (12 BACKSPACE)"**: manda 12 BACKSPACE antes de la
secuencia, así cada intento arranca de un campo vacío. Es opt-in porque agrega ~2 s.

## Reportar un problema

Dos botones abajo del registro:

- **Compartir registro**: arma un reporte con la versión de la app, el modelo y la
  versión de Android del host, el dispositivo elegido (VID/PID), el protocolo
  informado, el estado, los intentos de la sesión, el resumen del último envío, el
  estado USB observado, el bloque de desbloqueo con indicios y veredicto, las últimas
  300 líneas del registro **y la bitácora de auditoría** (`audit.log`, en el
  almacenamiento privado de la app). Lo manda por el
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

- `./gradlew clean test assembleDebug -PskipPin=true` → **BUILD SUCCESSFUL**.
- **89 unit tests, 0 fallas** (por variante: la tarea `test` corre debug y release):
  `AoaHidKeyboardTest` 35, `UnlockEvidenceTest` 14, `HidKeyboardReportsTest` 10,
  `HidKeycodesTest` 7, `UsbBusStateTest` 6, `PinVaultTest` 5, `SimulatedTransportTest` 5,
  `AuditEntryTest` 4, `DiagnosticsReportTest` 3.
- **Matriz del veredicto de desbloqueo** cubierta por tests: MTP visible ⇒ confirmado
  (manda sobre cualquier otro indicio), MTP ausente ⇒ no desbloqueado, vibración ⇒
  rechazado, sin vibración ⇒ probable (nunca confirmado), sin datos ⇒ indeterminado, y
  el reporte explica sus propios límites.
- El pipeline completo (GET_PROTOCOL → REGISTER_HID → SET_DESC → reports) se ejerce
  contra `SimulatedTransport`, que responde igual que un Android con AOA2: 12 reports
  para 4 dígitos con tecla de despertar, sin tocar ningún dispositivo.
- **Reintento de report rechazado** cubierto por tests: con el transporte fallando el
  primer evento (el caso real medido), la secuencia se completa igual, el conteo de
  entregados no cambia y el registro marca `1 reintento de transferencia`.
- Build con PIN verificado con el PIN real: el `.dex` **no** contiene el PIN en texto
  plano y **sí** la forma ofuscada; el APK público no contiene ninguna de las dos.
- Descriptor HID comparado byte a byte contra `scrcpy/app/src/hid/hid_keyboard.c`
  con las macros resueltas: **63 bytes, 0 diferencias**.
- APK inspeccionado con `aapt2 dump badging` / `dump permissions`: paquete
  `com.ejair.note10rescue`, `versionCode 8`, `versionName 1.0.7`, `minSdk 24`,
  `targetSdk 34`, `uses-feature usb.host`, **cero permisos declarados** (sin `INTERNET`).
- `sha256` del APK público publicado (v1.0.7):
  `071c8cea0721867129fdadaa843a601a64f1a1e40318385ecbb0c04d00d65089`.

**Verificado contra el hardware real** (reporte de campo del 2026-09-20, host
`SM-A366E` / Android 16, target `SAMSUNG_Android` VID `0x04E8` PID `0x6860`):

- El host detecta el Note10 por USB y el usuario concede el permiso.
- `Connection opened` → `GET_PROTOCOL: request=51 result=2` → **AOA protocol: 2**.
- `REGISTER_HID: request=54 result=0` → **OK**.
- `SET_HID_REPORT_DESC: request=56 result=63` → **OK** (el descriptor de 63 bytes se
  acepta tal cual).
- `HID READY` y **12 `SEND_HID_EVENT` con `result=8`** para una secuencia de 4 dígitos
  con tecla de despertar previa (687 ms).
- Ese mismo reporte dejó el hallazgo de la carrera de inicialización (primer report
  rechazado con `result=-1`), que es lo que corrige v1.0.6.

**No verificado** (y no se puede verificar desde este canal): que el Note10 haya
interpretado esas pulsaciones como teclado físico y se haya desbloqueado. AOA-HID es
de una sola dirección: no hay confirmación, ni error, ni video. Los indicios
indirectos están en "Cómo saber si entró".

Otras limitaciones conocidas:

- AOA HID da **sólo entrada**: no hay video ni screenshot del Note10 (por diseño;
  para eso scrcpy necesita ADB, que el Note10 no tiene autorizado).
- La app no tiene ícono propio (usa el default del sistema): es cosmético y se
  dejó fuera a propósito.
- No hay release firmado (ni keystore): el APK es `debug`.

## Interfaz (pantalla única)

```
NOTE10 RESCUE HID
v1.0.7                            <- versión en el registro

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
[x] Limpiar el campo antes del PIN (12 BACKSPACE)    <- si un envío se cortó a mitad
[ OK ]                           <- envío directo: un toque = una secuencia
   Envío directo listo: un toque = una secuencia.
[ ENVIAR PIN UNA VEZ ]           <- usa el PIN escrito a mano

RESULTADO OBSERVADO (¿se desbloqueó?)     <- v1.0.7: veredicto de desbloqueo
Desbloqueo: PROBABLE — sin vibración de rechazo; falta confirmar con MTP
( ) Sin dato todavía
( ) No vibró
(•) Vibró (Android avisa así el PIN incorrecto)
[ Comprobar desbloqueo (MTP en una PC) ]

[ Mostrar controles manuales ]   <- despliega el panel
   [ Enviar TAB ] [ Enviar BACKSPACE ] [ Enviar ENTER ] [ Desregistrar HID ]

REGISTRO TÉCNICO                 <- nunca muestra el PIN, y hace auto-scroll
[ Compartir registro ] [ Copiar ] [ Limpiar registro ]
00:00:00  Note10 Rescue HID v1.0.7 — todo lo que pasa queda en este registro
00:00:00  USB device detected: 1
00:00:00  USB permission granted
00:00:00  Connection opened
00:00:00  AOA protocol: 2
00:00:00  REGISTER_HID: OK
00:00:00  SET_HID_REPORT_DESC: OK
00:00:00  HID READY
00:00:00  esperando 1500ms a que el dispositivo acepte eventos HID
00:00:01  listo para enviar
00:00:01  wake key (TAB) sent first
00:00:02  6 digit sequence sent
00:00:02  ENTER sent
00:00:02  sequence summary: 6 dígitos, 14 reports OK, 1240ms
00:00:02  Monitor USB: /dev/bus/usb/001/002 (VID 0x04E8 PID 0x6860)
00:00:22  Monitor USB: fin de la ventana de 20s
00:00:22  Monitor USB: sin cambios en 20s — no prueba nada por sí solo, mirá el veredicto
00:00:22  Indicios de desbloqueo registrados: PROBABLE — sin vibración de rechazo; falta confirmar con MTP
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
  SimulatedTransport.kt    transporte simulado del modo de prueba (no toca el target)
  UnlockEvidence.kt        indicios de desbloqueo + veredicto (puro, testeable)
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
  SimulatedTransportTest.kt el pipeline completo contra el transporte simulado
  UnlockEvidenceTest.kt    matriz del veredicto de desbloqueo y sus límites
pin-local.properties             PIN embebido (versionado a propósito, ver arriba)
```

## APK

`dist/note10-rescue-hid-1.0.7-debug.apk` — APK debug de v1.0.7 **sin PIN** (build
público con `-PskipPin=true`), compilado y verificado (`BUILD SUCCESSFUL`, 89 unit
tests en verde, descriptor idéntico a scrcpy, sin ningún permiso declarado).
896.871 bytes.

```
sha256  071c8cea0721867129fdadaa843a601a64f1a1e40318385ecbb0c04d00d65089
```

`dist/note10-rescue-hid-1.0.7-pin-debug.apk` — el mismo código **con el PIN embebido**,
publicado a pedido del dueño del dispositivo (ver más arriba). También está como asset
del release v1.0.7 y en `pin-local.properties` (versionado a propósito).
896.899 bytes.

```
sha256  0d662def4d97e70a2afffe20bc1363c69d9cac63782febbd45ea57645b5e8050
```

Los APK de v1.0.6, v1.0.5, v1.0.4, v1.0.3, v1.0.2, v1.0.1 y v1.0.0 quedan publicados sin
cambios para trazabilidad (los de v1.0.0 a v1.0.4 no llevan PIN; los de v1.0.5 en
adelante sí, publicados a pedido del dueño). Todos están firmados con la misma clave de
debug, así que las actualizaciones se instalan encima sin desinstalar.

Instalación desde una PC con ADB:

```bash
adb install -r dist/note10-rescue-hid-1.0.7-pin-debug.apk    # con PIN embebido
adb install -r dist/note10-rescue-hid-1.0.7-debug.apk        # sin PIN
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

### v1.0.7

El reporte ahora **responde si el Note10 se desbloqueó o no**, que era el punto que
quedaba a interpretación. AOA-HID no devuelve confirmación (es unidireccional), así que
en vez de inventar un canal que no existe, la app junta los indicios observables y
emite un veredicto explícito.

- **Sección "RESULTADO OBSERVADO"** en la pantalla: marcás si el Note10 vibró (Android
  vibra cuando el PIN es incorrecto) y el veredicto se recalcula al instante.
- **Botón "Comprobar desbloqueo (MTP en una PC)"**: guía de 3 pasos y registro de la
  respuesta. Es el único indicio **decisivo**: Android sólo expone el almacenamiento con
  el equipo desbloqueado.
- **Matriz de veredicto**: `CONFIRMADO` (MTP visible) / `NO DESBLOQUEADO` (MTP ausente) /
  `RECHAZADO` (vibró) / `PROBABLE` (no vibró, sin MTP) / `INDETERMINADO` (sin dato) /
  `SIN ENVÍOS`. MTP manda sobre todo lo demás; el cambio de bus USB es un indicio a
  favor pero no cambia el veredicto.
- **El reporte compartible lleva el bloque "desbloqueo (indicios y veredicto)"** con los
  indicios, su fuerza relativa y la aclaración de que, con la vibración del sistema
  desactivada, "no vibró" no significa nada.
- **Monitor USB de 8 s → 20 s** (el cambio de configuración del target puede tardar) y
  aviso explícito de que la ausencia de cambios no prueba nada.
- `UnlockEvidence` (lógica pura, sin Android) + 14 tests nuevos (89 en total).
- Sin permisos nuevos, sin red, sin brute force, nada instalado en el Note10.

### v1.0.6

Primera versión informada por una prueba **contra el Note10 real**. El reporte de campo
mostró el pipeline completo funcionando (protocolo 2, `REGISTER_HID` OK, descriptor
aceptado, 12 reports con `result=8`) y un defecto real: **el primer `SEND_HID_EVENT`
inmediatamente después del descriptor es rechazado con `result=-1`**, mientras que el
mismo envío, minutos después, pasa sin problema.

- **Espera de asentamiento**: después de `SET_HID_REPORT_DESC` la app espera 1500 ms
  antes del primer evento HID (carrera de inicialización del lado Android).
- **Reintento del report rechazado**: si un `SEND_HID_EVENT` devuelve `result=-1`, se
  reintenta **el mismo report** hasta 3 veces con 1 s de espera. Es seguro porque una
  transferencia rechazada no entrega ninguna tecla: no duplica pulsaciones ni consume
  intentos de desbloqueo. Si se agotan, el error sigue siendo `SEND_REPORT_FAILED` y el
  registro aclara que ese envío **no contó como intento**.
- **Casilla "Limpiar el campo antes del PIN (12 BACKSPACE)"** (opt-in): manda 12
  BACKSPACE antes de la secuencia, para que un envío cortado a mitad no deje dígitos
  pegados en el campo del bloqueo y arruine el intento siguiente.
- El resumen y la auditoría ahora informan `campoLimpiado` y `reintentos`.
- 4 tests nuevos (75 en total): asentamiento posterior al descriptor, reintento exitoso
  del report rechazado (con la secuencia entregada intacta), agotamiento de reintentos y
  limpieza de campo con los BACKSPACE.
- Sin cambios de arquitectura, permisos, ni en el camino manual (TAB/BACKSPACE/ENTER).

### v1.0.5

- **Modo de prueba**: casilla que recorre todo el pipeline con un transporte simulado
  (responde como un dispositivo AOA2 real) sin abrir ni tocar el target, para validar
  la app sin gastar intentos de desbloqueo. Las corridas quedan marcadas como
  `PRUEBA_sin_envio` en la auditoría y **no** incrementan el contador de intentos.
- `SimulatedTransport` + 5 tests nuevos (71 en total) que ejercitan el pipeline
  completo contra ese transporte.
- El registro avisa explícitamente antes de cada envío si es `MODO REAL` o
  `MODO DE PRUEBA`.

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
