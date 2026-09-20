La sonda MTP de v1.0.10 falló en el primer envío (`no se pudo enviar GetDeviceInfo (endpoint bulk OUT)`) sin decir por qué. Ahora la sonda está instrumentada y con el arreglo típico de ese síntoma.

* **`CLEAR_FEATURE(ENDPOINT_HALT)` + reintento** en el bulk OUT y en el bulk IN: un endpoint bulk que quedó en halt rechaza todo hasta que se limpia, y es la causa habitual de un `bulkTransfer` que devuelve -1 sin que el dispositivo esté roto.
* **Timeout de bulk de 2 s a 3 s** y lectura del IN para diagnóstico.
* **Topología en el registro**: antes de la sonda se registra la interfaz MTP que se está usando (`interfaz N, altsetting, clase/subclase/protocolo, endpoints con dirección y tamaño de paquete`), así se distingue "la interfaz no tiene endpoint bulk OUT" de "el target no responde".
* **El error dice los bytes exactos**: `bulk OUT 0x02 devolvió -1 bytes y tras CLEAR_HALT -1 (se esperaban 12)`, más el motivo (`lastError`) en el reporte.
* Aviso explícito cuando la sonda no concluye: un MTP que no atiende es **consistente** con el equipo bloqueado, pero no es prueba.

Sin permisos nuevos, sin red, sin brute force, nada instalado en el Note10. 111 unit tests, 0 fallas.

**Assets**
* `note10-rescue-hid-1.0.11-pin-debug.apk` — con el PIN embebido (publicado a pedido del dueño). sha256 `3283da8ee04fec7ccba7c4c1209b0df35c7247c7eb0b510460007c9d0e8ab34e`.
* `note10-rescue-hid-1.0.11-debug.apk` — build público, sin PIN. sha256 `ba4baee43d662c87cfcd3b43e7ec186f64c24a0715d540c2fdcdc828ace6f6cf`.
