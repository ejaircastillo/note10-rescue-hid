Dos mejoras nacidas de la duda sobre "USB controlado por" y del fallo de la sonda MTP.

* **La sonda MTP reintenta una vez**: si el primer intento no obtiene respuesta, espera 1 s y vuelve a probar (un cambio de rol USB puede dejar el endpoint recién renegociado).
* **Diagnóstico de interfaces**: si el target no expone interfaz MTP, el error ahora lista todas las interfaces que sí expone (`0(clase 6/1/1, 3 endpoints), 1(clase 255/66/1, 2 endpoints)`), para distinguir "está en sólo cargar" de "expone otra cosa".
* **El registro explica el cambio de rol**: cuando el Note10 se desconecta y era el dispositivo elegido, avisa que tocar "USB controlado por" en *Ajustes de USB* cambia el rol del puerto y re-enumera el dispositivo (y que hay que volver a detectar, conceder el permiso y preparar el HID). Si vuelve a enumerarse el mismo equipo, avisa que el permiso USB y el HID quedaron invalidados.

Sin permisos nuevos, sin red, sin brute force, nada instalado en el Note10. 111 unit tests, 0 fallas.

**Assets**
* `note10-rescue-hid-1.0.12-pin-debug.apk` — con el PIN embebido (publicado a pedido del dueño). sha256 `9f7a77afb43d1ce3bb7a4b72c306d219782305c14c981ad658f8d623350d7394`.
* `note10-rescue-hid-1.0.12-debug.apk` — build público, sin PIN. sha256 `819271227563d6f9f6a6cc14e24f52acf47c96c7f9b176df7d10244be302bc13`.
