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

- `./gradlew clean assembleDebug testDebugUnitTest` → **BUILD SUCCESSFUL**.
- **33 unit tests, 0 fallas**: `AoaHidKeyboardTest` 18, `HidKeyboardReportsTest` 8,
  `HidKeycodesTest` 7.
- APK inspeccionado con `aapt2 dump badging` / `dump permissions`: paquete
  `com.ejair.note10rescue`, `minSdk 24`, `targetSdk 34`, `uses-feature usb.host`,
  **cero permisos declarados** (sin `INTERNET`).
- `sha256` del APK publicado:
  `66b5511923f08d9e0485128aebb6ac39c084140bb792e9be416060fdb35637f1`.

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

## Licencia

Sin licencia: por defecto, todos los derechos reservados. Si necesitás reusarlo
con otros términos, pedí que se agregue MIT o Apache-2.0.
