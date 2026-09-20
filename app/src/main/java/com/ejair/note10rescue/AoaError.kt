package com.ejair.note10rescue

/**
 * Errores explícitos de la app. El código se muestra tal cual en la UI para que
 * sea inequívoco qué falló y en qué paso del protocolo AOA.
 */
enum class AoaError(val code: String, val description: String) {
    NO_USB_DEVICE(
        "NO_USB_DEVICE",
        "No hay ningún dispositivo USB conectado o UsbManager no está disponible. " +
            "Revisá el cable USB-C ↔ USB-C y que el Note10 esté encendido."
    ),
    USB_PERMISSION_DENIED(
        "USB_PERMISSION_DENIED",
        "El sistema respondió que NO se concedió el permiso USB (se canceló el diálogo o " +
            "se tocó 'Denegar'). Volvé a tocar OK o Preparar HID y aceptá el diálogo."
    ),
    USB_PERMISSION_TIMEOUT(
        "USB_PERMISSION_TIMEOUT",
        "Se pidió el permiso USB y el sistema no confirmó nada en el tiempo esperado. El " +
            "diálogo del sistema ('¿Permitir que la app acceda al dispositivo USB?') tiene " +
            "que aparecer en pantalla: si no lo ves, desbloqueá el teléfono host y reintentá; " +
            "si el cable se movió, reconectalo."
    ),
    OPEN_DEVICE_FAILED(
        "OPEN_DEVICE_FAILED",
        "UsbManager.openDevice() devolvió null: el host no pudo abrir la conexión USB."
    ),
    AOA_PROTOCOL_QUERY_FAILED(
        "AOA_PROTOCOL_QUERY_FAILED",
        "Falló ACCESSORY_GET_PROTOCOL (51). El dispositivo no respondió como accesorio AOA."
    ),
    AOA_PROTOCOL_UNSUPPORTED(
        "AOA_PROTOCOL_UNSUPPORTED",
        "El dispositivo respondió un protocolo AOA menor a 2: no soporta AOA2 HID."
    ),
    REGISTER_HID_FAILED(
        "REGISTER_HID_FAILED",
        "Falló ACCESSORY_REGISTER_HID (54)."
    ),
    SET_DESCRIPTOR_FAILED(
        "SET_DESCRIPTOR_FAILED",
        "Falló ACCESSORY_SET_HID_REPORT_DESC (56): el HID report descriptor no se aceptó."
    ),
    SEND_REPORT_FAILED(
        "SEND_REPORT_FAILED",
        "Falló ACCESSORY_SEND_HID_EVENT (57) al enviar un report HID."
    ),
    DEVICE_DISCONNECTED(
        "DEVICE_DISCONNECTED",
        "El dispositivo USB se desconectó durante la operación."
    ),
    HID_NOT_PREPARED(
        "HID_NOT_PREPARED",
        "El teclado HID todavía no está preparado (falta REGISTER_HID + SET_HID_REPORT_DESC)."
    ),
    INVALID_PIN(
        "INVALID_PIN",
        "El PIN está vacío o contiene caracteres que no son dígitos."
    ),
    INTERNAL_ERROR(
        "INTERNAL_ERROR",
        "Fallo inesperado del envío (no es un error del protocolo AOA). El detalle dice " +
            "la excepción: si se repite, reportalo con el registro."
    )
}

/**
 * Excepción con código de error. Nunca debe contener el PIN ni dígitos del PIN
 * en [detail] (el detalle sólo lleva datos técnicos: request, result, duración).
 */
class AoaException(
    val aoaError: AoaError,
    val detail: String = ""
) : Exception(
    if (detail.isEmpty()) {
        "${aoaError.code}: ${aoaError.description}"
    } else {
        "${aoaError.code}: ${aoaError.description} [$detail]"
    }
)
