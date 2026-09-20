Sonda MTP por USB: la app ahora puede decir si el Note10 está desbloqueado **sin una PC**, por el mismo cable USB-C ↔ USB-C que ya usa para el HID.

**Por qué sirve**: Android sólo expone el almacenamiento por MTP con el equipo desbloqueado (es el mismo motivo por el que una PC no ve los archivos de un teléfono bloqueado). La app reclama la interfaz MTP del target y le pregunta `GetStorageIDs`:

* devuelve almacenamientos → **ESTÁ DESBLOQUEADO** (el reporte pasa a veredicto `CONFIRMADO`)
* devuelve 0 o `AccessDenied` → **SIGUE BLOQUEADO** (veredicto `NO DESBLOQUEADO`)

**No gasta intentos**: no manda ninguna tecla, sólo lee.

* Botón nuevo: **"Comprobar desbloqueo por USB (sonda MTP, sin PC)"**.
* El resultado entra solo en el bloque de desbloqueo del reporte (indicio MTP, el decisivo).
* MTP implementado en `MtpProbe` (contenedores PIMA 15740 con endianness, `GetDeviceInfo` → `OpenSession` → `GetStorageIDs` → `CloseSession`) y `UsbMtpChannel` (`claimInterface` + `bulkTransfer` sobre los endpoints bulk de la interfaz clase 6).
* Errores nuevos: `MTP_INTERFACE_NOT_FOUND` (el target está en modo "sólo cargar": poné "Transferir archivos" en "Usar USB para") y `MTP_CLAIM_FAILED`.
* 12 tests nuevos (111 en total, 0 fallas). Sin permisos nuevos, sin red, sin brute force, nada instalado en el Note10.

**Assets**
* `note10-rescue-hid-1.0.10-pin-debug.apk` — con el PIN embebido (publicado a pedido del dueño). sha256 `c83169b64e6cde3ff98ca4f0cf7ee9f666118ec587f380c005eaf697841b3c63`.
* `note10-rescue-hid-1.0.10-debug.apk` — build público, sin PIN. sha256 `2643e9a1ef1d3c0188dbcde6c271e1063f7869e2a2c0dc81b2998c9400a631f2`.
